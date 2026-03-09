package com.litongjava.sip.rtp.server;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

import com.litongjava.sip.model.CallSession;
import com.litongjava.sip.rtp.codec.AudioCodec;
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

//      log.info(
//          "RTP packet received, callId={}, localPort={}, selectedCodec={}, selectedPt={}, selectedSampleRate={}, remoteRtp={}:{}",
//          session.getCallId(), localPort,
//          session.getSelectedCodec() != null ? session.getSelectedCodec().getCodecName() : null,
//          session.getSelectedCodec() != null ? session.getSelectedCodec().getPayloadType() : -1,
//          session.getSelectedCodec() != null ? session.getSelectedCodec().getClockRate() : -1, session.getRemoteRtpIp(),
//          session.getRemoteRtpPort());

      Node remote = udpPacket.getRemote();
      byte[] data = udpPacket.getData();
      if (data == null || data.length < 12) {
        return;
      }

      RtpPacket in = rtpPacketParser.parse(data);

//      log.info("RTP parsed, callId={}, inPt={}, seq={}, ts={}, ssrc={}, payloadBytes={}", session.getCallId(),
//          in.getPayloadType(), in.getSequenceNumber(), in.getTimestamp(), in.getSsrc(),
//          in.getPayload() != null ? in.getPayload().length : 0);

      // 更新远端 RTP 地址，适配首次学习或端口漂移
      if (session.getRemoteRtpIp() == null || session.getRemoteRtpIp().isEmpty()) {
        session.setRemoteRtpIp(remote.getIp());
      }
      if (session.getRemoteRtpPort() <= 0) {
        session.setRemoteRtpPort(remote.getPort());
      }

      // DTMF event 先忽略，不做 echo
      if (session.isTelephoneEventSupported() && in.getPayloadType() == session.getRemoteTelephoneEventPayloadType()) {
        session.setUpdatedTime(System.currentTimeMillis());
        return;
      }

      AudioCodec codec = chooseCodec(session);
      if (codec == null) {
        return;
      }
//      log.info("RTP codec chosen, callId={}, codecName={}, payloadType={}, sampleRate={}", session.getCallId(),
//          codec.codecName(), codec.payloadType(), codec.sampleRate());

      // 有些终端可能发来的 payload type 和协商结果不一致，先只按 session 选中 codec 解码
      short[] pcm = codec.decode(in.getPayload());
      AudioFrame inputFrame = new AudioFrame(pcm, codec.sampleRate(), 1, in.getTimestamp());

//      log.info("RTP decoded to PCM, callId={}, pcmSamples={}, frameSampleRate={}, channels={}, rtpTimestamp={}",
//          session.getCallId(), pcm != null ? pcm.length : 0, inputFrame.getSampleRate(), inputFrame.getChannels(),
//          inputFrame.getRtpTimestamp());

      AudioFrame outputFrame = mediaProcessor.process(inputFrame, session);
      if (outputFrame == null || outputFrame.getSamples() == null || outputFrame.getSamples().length == 0) {
//        log.info("MediaProcessor returned no audio, callId={}", session.getCallId());
        return;
      }

//      log.info("MediaProcessor output, callId={}, outSamples={}, outSampleRate={}, outChannels={}, outRtpTimestamp={}",
//          session.getCallId(), outputFrame.getSamples().length, outputFrame.getSampleRate(), outputFrame.getChannels(),
//          outputFrame.getRtpTimestamp());

      byte[] outPayload = codec.encode(outputFrame.getSamples());
//      log.info("RTP encoded from PCM, callId={}, outPayloadBytes={}, codecName={}, codecSampleRate={}",
//          session.getCallId(), outPayload != null ? outPayload.length : 0, codec.codecName(), codec.sampleRate());

      RtpPacket out = new RtpPacket();
      out.setVersion(2);
      out.setPadding(false);
      out.setExtension(false);
      out.setCsrcCount(0);
      out.setMarker(false);
      out.setPayloadType(session.getSelectedCodec().getPayloadType());
      out.setSequenceNumber(session.nextSendSequence());
      out.setTimestamp(session.nextSendTimestamp(outputFrame.sampleCount()));
      out.setSsrc(session.getLocalSsrc());
      out.setPayload(outPayload);

      byte[] outBytes = rtpPacketWriter.write(out);
//      log.info("RTP send, callId={}, outPt={}, seq={}, ts={}, ssrc={}, packetBytes={}, remoteRtp={}:{}",
//          session.getCallId(), out.getPayloadType(), out.getSequenceNumber(), out.getTimestamp(), out.getSsrc(),
//          outBytes != null ? outBytes.length : 0, session.getRemoteRtpIp(), session.getRemoteRtpPort());


      DatagramPacket resp = new DatagramPacket(outBytes, outBytes.length,
          new InetSocketAddress(session.getRemoteRtpIp(), session.getRemoteRtpPort()));
      socket.send(resp);

      session.setUpdatedTime(System.currentTimeMillis());
    } catch (Exception e) {
      log.error("rtp handler error, localPort={}", localPort, e);
    }
  }

  private AudioCodec chooseCodec(CallSession session) {
    if (session.getSelectedCodec() == null) {
      return null;
    }

    String codecName = session.getSelectedCodec().getCodecName();
    if ("PCMU".equalsIgnoreCase(codecName)) {
      return pcmuCodec;
    }
    if ("PCMA".equalsIgnoreCase(codecName)) {
      return pcmaCodec;
    }
    return null;
  }
}