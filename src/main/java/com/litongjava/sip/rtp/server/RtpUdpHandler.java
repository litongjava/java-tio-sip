package com.litongjava.sip.rtp.server;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

import com.litongjava.sip.model.CallSession;
import com.litongjava.sip.rtp.codec.AudioCodec;
import com.litongjava.sip.rtp.codec.AudioResampler;
import com.litongjava.sip.rtp.codec.CodecName;
import com.litongjava.sip.rtp.codec.G722Codec;
import com.litongjava.sip.rtp.codec.NegotiatedAudioFormatResolver;
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

  private final AudioCodec pcmuCodec = new PcmuCodec();
  private final AudioCodec pcmaCodec = new PcmaCodec();
  private final AudioCodec g722Codec = new G722Codec();

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

      AudioCodec codec = chooseCodec(session);
      if (codec == null) {
        log.warn("No codec selected for callId={}", session.getCallId());
        return;
      }

      short[] pcm = codec.decode(in.getPayload());
      AudioFrame inputFrame = new AudioFrame(pcm, codec.sampleRate(),
          NegotiatedAudioFormatResolver.resolveChannels(session), in.getTimestamp());

      AudioFrame outputFrame = mediaProcessor.process(inputFrame, session);
      if (outputFrame == null || outputFrame.getSamples() == null || outputFrame.getSamples().length == 0) {
        // log.info("MediaProcessor returned no audio, callId={}", session.getCallId());
        return;
      }

      int targetSampleRate = codec.sampleRate();
      short[] outputSamples = outputFrame.getSamples();
      int outputSampleRate = outputFrame.getSampleRate() > 0 ? outputFrame.getSampleRate() : targetSampleRate;

      if (outputSampleRate != targetSampleRate) {
        outputSamples = AudioResampler.resample(outputSamples, outputSampleRate, targetSampleRate);
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
    } catch (Exception e) {
      log.error("rtp handler error, localPort={}", localPort, e);
    }
  }

  private AudioCodec chooseCodec(CallSession session) {
    if (session.getSelectedCodec() == null || session.getSelectedCodec().getCodecName() == null) {
      return null;
    }

    String codecName = session.getSelectedCodec().getCodecName();
    if (CodecName.G722.equalsIgnoreCase(codecName)) {
      return g722Codec;
    }
    if (CodecName.PCMU.equalsIgnoreCase(codecName)) {
      return pcmuCodec;
    }
    if (CodecName.PCMA.equalsIgnoreCase(codecName)) {
      return pcmaCodec;
    }
    return null;
  }
}