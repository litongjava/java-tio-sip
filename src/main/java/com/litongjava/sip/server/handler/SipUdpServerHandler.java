package com.litongjava.sip.server.handler;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.litongjava.sip.model.CallSession;
import com.litongjava.sip.model.SipMessage;
import com.litongjava.sip.model.SipRequest;
import com.litongjava.sip.model.SipResponse;
import com.litongjava.sip.parser.SipMessageEncoder;
import com.litongjava.sip.parser.SipMessageParser;
import com.litongjava.sip.rtp.RtpServerManager;
import com.litongjava.sip.rtp.media.MediaProcessor;
import com.litongjava.sip.sdp.SdpAnswerBuilder;
import com.litongjava.sip.sdp.SdpNegotiationResult;
import com.litongjava.sip.sdp.SdpParser;
import com.litongjava.sip.server.session.CallSessionManager;
import com.litongjava.tio.core.Node;
import com.litongjava.tio.core.udp.UdpPacket;
import com.litongjava.tio.core.udp.intf.UdpHandler;

public class SipUdpServerHandler implements UdpHandler {

  private final String localIp;
  private final SipMessageParser messageParser = new SipMessageParser();
  private final SipMessageEncoder messageEncoder = new SipMessageEncoder();

  private final SdpParser sdpParser = new SdpParser();
  private final SdpAnswerBuilder sdpAnswerBuilder = new SdpAnswerBuilder();

  private final CallSessionManager sessionManager;
  private final RtpServerManager rtpServerManager;
  private final MediaProcessor mediaProcessor;

  public SipUdpServerHandler(String localIp, CallSessionManager sessionManager, RtpServerManager rtpServerManager,
      MediaProcessor mediaProcessor) {
    this.localIp = localIp;
    this.sessionManager = sessionManager;
    this.rtpServerManager = rtpServerManager;
    this.mediaProcessor = mediaProcessor;
  }

  @Override
  public void handler(UdpPacket udpPacket, DatagramSocket socket) {
    try {
      Node remote = udpPacket.getRemote();
      byte[] data = udpPacket.getData();

      SipMessage msg = messageParser.parse(data);
      if (!(msg instanceof SipRequest)) {
        return;
      }

      SipRequest req = (SipRequest) msg;
      String method = req.getMethod();

      if ("INVITE".equalsIgnoreCase(method)) {
        handleInvite(req, remote, socket);
        return;
      }

      if ("ACK".equalsIgnoreCase(method)) {
        handleAck(req);
        return;
      }

      if ("BYE".equalsIgnoreCase(method)) {
        handleBye(req, remote, socket);
        return;
      }

      SipResponse resp = buildSimpleResponse(req, 200, "OK", null);
      send(socket, remote, resp);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void handleInvite(SipRequest req, Node remote, DatagramSocket socket) throws Exception {
    String callId = req.getHeader("Call-ID");
    CallSession exist = sessionManager.getByCallId(callId);

    if (exist != null && exist.getLast200Ok() != null) {
      sendRaw(socket, remote, exist.getLast200Ok());
      return;
    }

    SdpNegotiationResult negotiation = sdpParser.negotiate(req.getBody());
    if (!negotiation.isSuccess()) {
      SipResponse fail = buildSimpleResponse(req, 488, "Not Acceptable Here", null);
      send(socket, remote, fail);
      return;
    }

    String toTag = "java" + System.nanoTime();

    CallSession session = new CallSession();
    session.setCallId(callId);
    session.setFromTag(parseTag(req.getHeader("From")));
    session.setToTag(toTag);
    session.setTransport("UDP");
    session.setRemoteSipIp(remote.getIp());
    session.setRemoteSipPort(remote.getPort());
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

    SipResponse trying = buildSimpleResponse(req, 100, "Trying", null);
    send(socket, remote, trying);

    SipResponse ok = buildInvite200Ok(req, session, negotiation);
    byte[] encoded = messageEncoder.encodeResponse(ok);
    String raw200 = new String(encoded, StandardCharsets.US_ASCII);

    session.setLast200Ok(raw200);
    sessionManager.createOrUpdate(session);

    sendBytes(socket, remote, encoded);
  }

  private void handleAck(SipRequest req) {
    String callId = req.getHeader("Call-ID");
    sessionManager.markAckReceived(callId);
  }

  private void handleBye(SipRequest req, Node remote, DatagramSocket socket) throws Exception {
    String callId = req.getHeader("Call-ID");
    CallSession session = sessionManager.getByCallId(callId);

    SipResponse resp = buildSimpleResponse(req, 200, "OK", session != null ? session.getToTag() : null);
    send(socket, remote, resp);

    if (session != null) {
      rtpServerManager.stopAndRelease(session);
      sessionManager.markTerminated(callId);
    }
  }

  private void send(DatagramSocket socket, Node remote, SipResponse response) throws Exception {
    byte[] bytes = messageEncoder.encodeResponse(response);
    sendBytes(socket, remote, bytes);
  }

  private void sendRaw(DatagramSocket socket, Node remote, String text) throws Exception {
    byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
    sendBytes(socket, remote, bytes);
  }

  private void sendBytes(DatagramSocket socket, Node remote, byte[] bytes) throws Exception {
    DatagramPacket packet = new DatagramPacket(bytes, bytes.length,
        new InetSocketAddress(remote.getIp(), remote.getPort()));
    socket.send(packet);
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