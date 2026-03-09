package com.litongjava.sip.server.handler;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import com.litongjava.aio.ByteBufferPacket;
import com.litongjava.aio.Packet;
import com.litongjava.sip.model.CallSession;
import com.litongjava.sip.model.SipMessage;
import com.litongjava.sip.model.SipRequest;
import com.litongjava.sip.model.SipResponse;
import com.litongjava.sip.parser.SipMessageEncoder;
import com.litongjava.sip.parser.SipMessageParser;
import com.litongjava.sip.parser.SipTcpFrameDecoder;
import com.litongjava.sip.rtp.RtpServerManager;
import com.litongjava.sip.rtp.media.MediaProcessor;
import com.litongjava.sip.sdp.SdpAnswerBuilder;
import com.litongjava.sip.sdp.SdpNegotiationResult;
import com.litongjava.sip.sdp.SdpParser;
import com.litongjava.sip.server.session.CallSessionManager;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.Tio;
import com.litongjava.tio.core.TioConfig;
import com.litongjava.tio.server.intf.ServerAioHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SipTcpServerHandler implements ServerAioHandler {

  private final String localIp;
  private final SipTcpFrameDecoder frameDecoder = new SipTcpFrameDecoder();
  private final SipMessageParser messageParser = new SipMessageParser();
  private final SipMessageEncoder messageEncoder = new SipMessageEncoder();
  private final CallSessionManager sessionManager;
  private final RtpServerManager rtpServerManager;
  private final MediaProcessor mediaProcessor;

  private final SdpParser sdpParser = new SdpParser();
  private final SdpAnswerBuilder sdpAnswerBuilder = new SdpAnswerBuilder();

  public SipTcpServerHandler(String localIp, CallSessionManager sessionManager, RtpServerManager rtpServerManager,
      MediaProcessor mediaProcessor) {
    this.localIp = localIp;
    this.sessionManager = sessionManager;
    this.rtpServerManager = rtpServerManager;
    this.mediaProcessor = mediaProcessor;
  }

  @Override
  public Packet decode(ByteBuffer buffer, int limit, int position, int readableLength, ChannelContext ctx)
      throws Exception {
    byte[] frame = frameDecoder.decode(buffer, readableLength, ctx);
    if (frame == null) {
      return null;
    }
    return new ByteBufferPacket(ByteBuffer.wrap(frame));
  }

  @Override
  public ByteBuffer encode(Packet packet, TioConfig tioConfig, ChannelContext ctx) {
    ByteBufferPacket p = (ByteBufferPacket) packet;
    ByteBuffer bb = p.getByteBuffer();
    if (bb.position() != 0) {
      bb.rewind();
    }
    return bb;
  }

  @Override
  public void handler(Packet packet, ChannelContext ctx) throws Exception {
    ByteBufferPacket p = (ByteBufferPacket) packet;
    ByteBuffer bb = p.getByteBuffer();

    byte[] bytes = new byte[bb.remaining()];
    bb.get(bytes);

    SipMessage msg = messageParser.parse(bytes);
    if (!(msg instanceof SipRequest)) {
      return;
    }

    SipRequest req = (SipRequest) msg;
    String method = req.getMethod();

    if ("INVITE".equalsIgnoreCase(method)) {
      handleInvite(req, ctx);
      return;
    }

    if ("ACK".equalsIgnoreCase(method)) {
      handleAck(req);
      return;
    }

    if ("BYE".equalsIgnoreCase(method)) {
      handleBye(req, ctx);
      return;
    }

    SipResponse resp = buildSimpleResponse(req, 200, "OK", null);
    send(ctx, resp);
  }

  private void handleInvite(SipRequest req, ChannelContext ctx) throws Exception {
    String callId = req.getHeader("Call-ID");
    byte[] sdpBody = req.getBody();
    String rawSdp = sdpBody == null ? "" : new String(sdpBody, StandardCharsets.US_ASCII);

    String from = req.getHeader("From");
    String to = req.getHeader("To");
    String remoteIp = ctx.getClientNode() != null ? ctx.getClientNode().getIp() : null;
    int remotePort = ctx.getClientNode() != null ? ctx.getClientNode().getPort() : 0;

    log.info("TCP INVITE received, callId={}, from={}, to={}, remoteSip={}:{}, rawSdp=\n{}", callId, from, to, remoteIp,
        remotePort, rawSdp);

    CallSession exist = sessionManager.getByCallId(callId);

    if (exist != null && exist.getLast200Ok() != null) {
      sendRaw(ctx, exist.getLast200Ok());
      return;
    }

    SdpNegotiationResult negotiation = sdpParser.negotiate(req.getBody());
    if (!negotiation.isSuccess()) {
      SipResponse fail = buildSimpleResponse(req, 488, "Not Acceptable Here", null);
      send(ctx, fail);
      log.warn("TCP SDP negotiate failed, callId={}, reason={}", callId, negotiation.getFailureReason());
      return;
    } else {
      log.info(
          "TCP SDP negotiate success, callId={}, codec={}, pt={}, sampleRate={}, ptime={}, remoteRtp={}:{}, telephoneEventSupported={}, remoteTelephoneEventPt={}",
          callId, negotiation.getSelectedCodec() != null ? negotiation.getSelectedCodec().getCodecName() : null,
          negotiation.getSelectedCodec() != null ? negotiation.getSelectedCodec().getPayloadType() : -1,
          negotiation.getSelectedCodec() != null ? negotiation.getSelectedCodec().getClockRate() : -1,
          negotiation.getPtime(), negotiation.getRemoteRtpIp(), negotiation.getRemoteRtpPort(),
          negotiation.isTelephoneEventSupported(), negotiation.getRemoteTelephoneEventPayloadType());
    }

    String toTag = "java" + System.nanoTime();

    CallSession session = new CallSession();
    session.setCallId(callId);
    session.setFromTag(parseTag(from));
    session.setToTag(toTag);
    session.setTransport("TCP");
    session.setRemoteSipIp(remoteIp);
    session.setRemoteSipPort(remotePort);
    session.setCreatedTime(System.currentTimeMillis());
    session.setUpdatedTime(System.currentTimeMillis());
    session.setAckDeadline(System.currentTimeMillis() + 32000);

    session.setRemoteRtpIp(negotiation.getRemoteRtpIp());
    session.setRemoteRtpPort(negotiation.getRemoteRtpPort());
    session.setSelectedCodec(negotiation.getSelectedCodec());
    session.setTelephoneEventSupported(negotiation.isTelephoneEventSupported());
    session.setRemoteTelephoneEventPayloadType(negotiation.getRemoteTelephoneEventPayloadType());
    session.setPtime(negotiation.getPtime());

    rtpServerManager.allocateAndStart(session, mediaProcessor);

    log.info(
        "TCP session created, callId={}, selectedCodec={}, selectedPt={}, selectedSampleRate={}, localRtpPort={}, remoteRtp={}:{}, ptime={}",
        callId, session.getSelectedCodec() != null ? session.getSelectedCodec().getCodecName() : null,
        session.getSelectedCodec() != null ? session.getSelectedCodec().getPayloadType() : -1,
        session.getSelectedCodec() != null ? session.getSelectedCodec().getClockRate() : -1, session.getLocalRtpPort(),
        session.getRemoteRtpIp(), session.getRemoteRtpPort(), session.getPtime());

    SipResponse resp = buildInvite200Ok(req, session, negotiation);
    byte[] encoded = messageEncoder.encodeResponse(resp);
    String raw200 = new String(encoded, StandardCharsets.US_ASCII);

    session.setLast200Ok(raw200);
    sessionManager.createOrUpdate(session);

    Tio.send(ctx, new ByteBufferPacket(ByteBuffer.wrap(encoded)));
  }

  private void handleAck(SipRequest req) {
    String callId = req.getHeader("Call-ID");
    log.info("TCP ACK received, callId={}", callId);
    sessionManager.markAckReceived(callId);
  }

  private void handleBye(SipRequest req, ChannelContext ctx) throws Exception {
    String callId = req.getHeader("Call-ID");
    CallSession session = sessionManager.getByCallId(callId);

    log.info("TCP BYE received, callId={}, localRtpPort={}, remoteRtp={}:{}", callId,
        session != null ? session.getLocalRtpPort() : -1, session != null ? session.getRemoteRtpIp() : null,
        session != null ? session.getRemoteRtpPort() : -1);

    SipResponse resp = buildSimpleResponse(req, 200, "OK", session != null ? session.getToTag() : null);
    send(ctx, resp);

    if (session != null) {
      rtpServerManager.stopAndRelease(session);
      sessionManager.markTerminated(callId);
    }
  }

  private void send(ChannelContext ctx, SipResponse response) {
    byte[] bytes = messageEncoder.encodeResponse(response);
    Tio.send(ctx, new ByteBufferPacket(ByteBuffer.wrap(bytes)));
  }

  private void sendRaw(ChannelContext ctx, String text) {
    byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
    Tio.send(ctx, new ByteBufferPacket(ByteBuffer.wrap(bytes)));
  }

  private SipResponse buildInvite200Ok(SipRequest req, CallSession session, SdpNegotiationResult negotiation) {
    SipResponse resp = new SipResponse();
    resp.setStatusCode(200);
    resp.setReasonPhrase("OK");

    copyIfPresent(req, resp, "Via");
    copyIfPresent(req, resp, "From");

    String to = req.getHeader("To");
    if (to != null && !to.toLowerCase().contains("tag=")) {
      to = to + ";tag=" + session.getToTag();
    }
    if (to != null) {
      resp.addHeader("To", to);
    }

    copyIfPresent(req, resp, "Call-ID");
    copyIfPresent(req, resp, "CSeq");
    resp.addHeader("Contact", "<sip:java@" + localIp + ":5060>");
    resp.addHeader("Content-Type", "application/sdp");

    String sdp = sdpAnswerBuilder.buildAnswer(localIp, session.getLocalRtpPort(), negotiation);
    resp.setBody(sdp.getBytes(StandardCharsets.US_ASCII));
    return resp;
  }

  private SipResponse buildSimpleResponse(SipRequest req, int code, String reason, String toTag) {
    SipResponse resp = new SipResponse();
    resp.setStatusCode(code);
    resp.setReasonPhrase(reason);

    copyIfPresent(req, resp, "Via");
    copyIfPresent(req, resp, "From");

    String to = req.getHeader("To");
    if (toTag != null && to != null && !to.toLowerCase().contains("tag=")) {
      to = to + ";tag=" + toTag;
    }
    if (to != null) {
      resp.addHeader("To", to);
    }

    copyIfPresent(req, resp, "Call-ID");
    copyIfPresent(req, resp, "CSeq");
    resp.setBody(new byte[0]);
    return resp;
  }

  private void copyIfPresent(SipRequest req, SipResponse resp, String headerName) {
    for (String v : req.getHeaders(headerName)) {
      resp.addHeader(headerName, v);
    }
  }

  private String parseTag(String headerValue) {
    if (headerValue == null) {
      return null;
    }

    String lower = headerValue.toLowerCase();
    int idx = lower.indexOf("tag=");
    if (idx < 0) {
      return null;
    }

    String sub = headerValue.substring(idx + 4);
    int semi = sub.indexOf(';');
    if (semi >= 0) {
      sub = sub.substring(0, semi);
    }
    return sub.trim();
  }
}