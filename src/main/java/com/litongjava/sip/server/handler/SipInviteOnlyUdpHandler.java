package com.litongjava.sip.server.handler;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.litongjava.sip.model.CallSession;
import com.litongjava.sip.rtp.RtpPortAllocator;
import com.litongjava.sip.rtp.RtpUdpServer;
import com.litongjava.tio.core.Node;
import com.litongjava.tio.core.udp.UdpPacket;
import com.litongjava.tio.core.udp.intf.UdpHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SipInviteOnlyUdpHandler implements UdpHandler {

  private final String localIp;

  private final RtpPortAllocator allocator = new RtpPortAllocator();

  private final Map<String, CallSession> sessions = new ConcurrentHashMap<>();

  public SipInviteOnlyUdpHandler(String localIp) {
    this.localIp = localIp;
  }

  @Override
  public void handler(UdpPacket udpPacket, DatagramSocket socket) {

    Node remote = udpPacket.getRemote();

    String sip = new String(udpPacket.getData(), StandardCharsets.US_ASCII);

    log.info("SIP UDP recv from {}:{}\n{}", remote.getIp(), remote.getPort(), sip);

    try {

      if (sip.startsWith("INVITE ")) {
        handleInvite(socket, remote, sip);
        return;
      }

      if (sip.startsWith("ACK ")) {
        return;
      }

      if (sip.startsWith("BYE ")) {
        handleBye(socket, remote, sip);
        return;
      }

      send(socket, remote, buildSimpleResponse(sip, 200, "OK", null));

    } catch (Exception e) {
      log.error("SIP handler error", e);
    }
  }

  private void handleInvite(DatagramSocket socket, Node remote, String sip) throws Exception {

    String callId = header(sip, "Call-ID");

    CallSession exist = sessions.get(callId);

    if (exist != null) {
      log.info("INVITE retransmit {}", callId);
      send(socket, remote, exist.last200Ok);
      return;
    }

    send(socket, remote, buildSimpleResponse(sip, 100, "Trying", null));

    int rtpPort = allocator.allocate();

    RtpUdpServer rtp = new RtpUdpServer(rtpPort);

    rtp.start();

    String toTag = "java" + System.nanoTime();

    String ok200 = build200OkForInvite(sip, localIp, rtpPort, toTag);

    CallSession cs = new CallSession(callId, rtpPort, toTag, rtp);

    cs.last200Ok = ok200;

    sessions.put(callId, cs);

    send(socket, remote, ok200);

    log.info("INVITE ok callId:{} rtpPort:{}", callId, rtpPort);
  }

  private void handleBye(DatagramSocket socket, Node remote, String sip) throws Exception {

    String callId = header(sip, "Call-ID");

    CallSession cs = sessions.remove(callId);

    if (cs != null) {
      cs.rtpServer.stop();
      allocator.release(cs.rtpPort);
    }

    send(socket, remote, buildSimpleResponse(sip, 200, "OK", cs != null ? cs.toTag : null));

    log.info("BYE callId:{} closed", callId);
  }

  private void send(DatagramSocket socket, Node remote, String msg) throws Exception {

    byte[] bytes = msg.getBytes(StandardCharsets.US_ASCII);

    DatagramPacket packet = new DatagramPacket(bytes, bytes.length,
        new InetSocketAddress(remote.getIp(), remote.getPort()));

    socket.send(packet);

    log.info("SIP UDP send\n{}", msg);
  }

  private String build200OkForInvite(String invite, String ip, int rtpPort, String toTag) {

    String via = header(invite, "Via");
    String from = header(invite, "From");
    String to = header(invite, "To");
    String callId = header(invite, "Call-ID");
    String cseq = header(invite, "CSeq");

    if (!to.toLowerCase().contains("tag=")) {
      to = to + ";tag=" + toTag;
    }

    String sdp = "v=0\r\n" + "o=- 1 1 IN IP4 " + ip + "\r\n" + "s=JavaSip\r\n" + "c=IN IP4 " + ip + "\r\n" + "t=0 0\r\n"
        + "m=audio " + rtpPort + " RTP/AVP 0\r\n" + "a=rtpmap:0 PCMU/8000\r\n" + "a=ptime:20\r\n" + "a=sendrecv\r\n";

    byte[] sdpBytes = sdp.getBytes(StandardCharsets.US_ASCII);

    return "SIP/2.0 200 OK\r\n" + "Via: " + via + "\r\n" + "From: " + from + "\r\n" + "To: " + to + "\r\n" + "Call-ID: "
        + callId + "\r\n" + "CSeq: " + cseq + "\r\n" + "Contact: <sip:java@" + ip + ":5060>\r\n"
        + "Content-Type: application/sdp\r\n" + "Content-Length: " + sdpBytes.length + "\r\n" + "\r\n" + sdp;
  }

  private String buildSimpleResponse(String req, int code, String reason, String toTag) {

    String via = header(req, "Via");
    String from = header(req, "From");
    String to = header(req, "To");
    String callId = header(req, "Call-ID");
    String cseq = header(req, "CSeq");

    if (toTag != null && !to.toLowerCase().contains("tag=")) {
      to = to + ";tag=" + toTag;
    }

    return "SIP/2.0 " + code + " " + reason + "\r\n" + "Via: " + via + "\r\n" + "From: " + from + "\r\n" + "To: " + to
        + "\r\n" + "Call-ID: " + callId + "\r\n" + "CSeq: " + cseq + "\r\n" + "Content-Length: 0\r\n\r\n";
  }

  private String header(String sip, String name) {

    String[] lines = sip.split("\r\n");

    for (String line : lines) {

      int idx = line.indexOf(':');

      if (idx <= 0) {
        continue;
      }

      String k = line.substring(0, idx).trim();

      if (k.equalsIgnoreCase(name)) {
        return line.substring(idx + 1).trim();
      }
    }

    return "";
  }
}