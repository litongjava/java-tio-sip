package com.litongjava.sip.rtp.server;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

import com.litongjava.sip.model.CallSession;
import com.litongjava.sip.rtp.codec.AudioCodec;
import com.litongjava.sip.rtp.codec.AudioResampler;
import com.litongjava.sip.rtp.codec.CodecName;
import com.litongjava.sip.rtp.codec.G722Codec;
import com.litongjava.sip.rtp.codec.PcmaCodec;
import com.litongjava.sip.rtp.codec.PcmuCodec;
import com.litongjava.sip.rtp.media.AudioFrame;
import com.litongjava.sip.rtp.media.EchoMediaProcessor;
import com.litongjava.sip.rtp.media.MediaProcessor;
import com.litongjava.sip.rtp.packet.RtpPacket;
import com.litongjava.sip.rtp.packet.RtpPacketParser;
import com.litongjava.sip.rtp.packet.RtpPacketWriter;
import com.litongjava.sip.server.session.CallSessionManager;
import com.litongjava.tio.core.Node;
import com.litongjava.tio.core.udp.UdpPacket;
import com.litongjava.tio.core.udp.intf.UdpHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RtpUdpHandler implements UdpHandler {

  private final int localPort;
  private final CallSessionManager sessionManager;

  private final RtpPacketParser rtpPacketParser = new RtpPacketParser();
  private final RtpPacketWriter rtpPacketWriter = new RtpPacketWriter();
  private final MediaProcessor mediaProcessor;

  public RtpUdpHandler(int localPort, CallSessionManager sessionManager) {
    this(localPort, sessionManager, new EchoMediaProcessor());
  }

  public RtpUdpHandler(int localPort, CallSessionManager sessionManager, MediaProcessor mediaProcessor) {
    this.localPort = localPort;
    this.sessionManager = sessionManager;
    this.mediaProcessor = mediaProcessor;
  }

  @Override
  public void handler(UdpPacket udpPacket, DatagramSocket socket) {
    try {
      CallSession session = sessionManager.getByLocalRtpPort(localPort);
      if (session == null || session.isTerminated()) {
        return;
      }

      Node remote = udpPacket.getRemote();
      byte[] data = udpPacket.getData();
      if (data == null || data.length < 12) {
        return;
      }

      if (!looksLikeRtp(data)) {
        log.debug("ignore non-rtp packet, localPort={}, from={}:{}, len={}", localPort, remote.getIp(),
            remote.getPort(), data.length);
        return;
      }

      RtpPacket in = rtpPacketParser.parse(data);

      if (session.getRemoteRtpIp() == null || session.getRemoteRtpIp().isEmpty()) {
        session.setRemoteRtpIp(remote.getIp());
      }
      if (session.getRemoteRtpPort() <= 0) {
        session.setRemoteRtpPort(remote.getPort());
      }

      if (session.isTelephoneEventSupported() && in.getPayloadType() == session.getRemoteTelephoneEventPayloadType()) {
        session.setUpdatedTime(System.currentTimeMillis());
        return;
      }

      AudioCodec codec = getOrCreateSessionCodec(session);
      if (codec == null) {
        log.warn("No codec selected for callId={}", session.getCallId());
        return;
      }

      int sessionSampleRate = session.getPcmSampleRate() > 0 ? session.getPcmSampleRate() : codec.sampleRate();
      int sessionChannels = session.getChannels() > 0 ? session.getChannels() : 1;

      short[] pcm = codec.decode(in.getPayload());
      AudioFrame inputFrame = new AudioFrame(pcm, sessionSampleRate, sessionChannels, in.getTimestamp());

      AudioFrame outputFrame = mediaProcessor.process(inputFrame, session);
      if (outputFrame == null || outputFrame.getSamples() == null || outputFrame.getSamples().length == 0) {
        return;
      }

      short[] outputSamples = outputFrame.getSamples();
      int outputSampleRate = outputFrame.getSampleRate() > 0 ? outputFrame.getSampleRate() : sessionSampleRate;
      int targetSampleRate = codec.sampleRate();

      if (outputSampleRate != targetSampleRate) {
        AudioResampler rtpResampler = session.getOrCreateRtpResampler(outputSampleRate, targetSampleRate);
        outputSamples = rtpResampler.resample(outputSamples);
      }

      byte[] outPayload = codec.encode(outputSamples);

      RtpPacket out = new RtpPacket();
      out.setVersion(2);
      out.setPadding(false);
      out.setExtension(false);
      out.setCsrcCount(0);
      out.setMarker(false);
      out.setPayloadType(session.getSelectedCodec().getPayloadType());
      out.setSequenceNumber(session.nextSendSequence());
      out.setTimestamp(session.nextSendTimestamp(outputSamples.length));
      out.setSsrc(session.getLocalSsrc());
      out.setPayload(outPayload);

      byte[] outBytes = rtpPacketWriter.write(out);

      DatagramPacket resp = new DatagramPacket(outBytes, outBytes.length,
          new InetSocketAddress(session.getRemoteRtpIp(), session.getRemoteRtpPort()));
      socket.send(resp);

      session.setUpdatedTime(System.currentTimeMillis());
    } catch (IllegalArgumentException e) {
      log.debug("ignore invalid/non-rtp packet, localPort={}, msg={}", localPort, e.getMessage());
    } catch (Exception e) {
      log.error("rtp handler error, localPort={}", localPort, e);
    }
  }

  private boolean looksLikeRtp(byte[] data) {
    if (data == null || data.length < 12) {
      return false;
    }
    int b0 = data[0] & 0xFF;
    int version = (b0 >> 6) & 0x03;
    return version == 2;
  }

  private AudioCodec getOrCreateSessionCodec(CallSession session) {
    AudioCodec codec = session.getAudioCodec();
    if (codec != null) {
      return codec;
    }

    synchronized (session) {
      codec = session.getAudioCodec();
      if (codec != null) {
        return codec;
      }

      codec = createCodec(session);
      session.setAudioCodec(codec);
      return codec;
    }
  }

  private AudioCodec createCodec(CallSession session) {
    if (session.getSelectedCodec() == null || session.getSelectedCodec().getCodecName() == null) {
      return null;
    }

    String codecName = session.getSelectedCodec().getCodecName();

    if (CodecName.G722.equalsIgnoreCase(codecName)) {
      return new G722Codec();
    }
    if (CodecName.PCMU.equalsIgnoreCase(codecName)) {
      return new PcmuCodec();
    }
    if (CodecName.PCMA.equalsIgnoreCase(codecName)) {
      return new PcmaCodec();
    }
    return null;
  }
}