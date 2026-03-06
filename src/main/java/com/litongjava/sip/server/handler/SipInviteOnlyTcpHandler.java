package com.litongjava.sip.server.handler;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.litongjava.aio.ByteBufferPacket;
import com.litongjava.aio.Packet;
import com.litongjava.sip.rtp.RtpPortAllocator;
import com.litongjava.sip.rtp.RtpUdpServer;
import com.litongjava.sip.server.SipDecoder;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.Tio;
import com.litongjava.tio.server.intf.ServerAioHandler;

public class SipInviteOnlyTcpHandler implements ServerAioHandler {

  private String localIp;
  private final RtpPortAllocator allocator = new RtpPortAllocator();

  // 一个 SIP TCP 连接对应一个 RTP server（简单起见）
  private final Map<String, RtpUdpServer> rtpByConn = new ConcurrentHashMap<>();

  public SipInviteOnlyTcpHandler(String localIp) {
    this.localIp = localIp;
  }

  @Override
  public Packet decode(ByteBuffer buffer, int limit, int position, int readableLength, ChannelContext ctx)
      throws Exception {
    if (readableLength <= 0) {
      return null;
    }
    ByteBufferPacket p = SipDecoder.decode(buffer, readableLength, ctx);
    return p;
  }

  @Override
  public ByteBuffer encode(Packet packet, com.litongjava.tio.core.TioConfig tioConfig, ChannelContext channelContext) {
    ByteBufferPacket p = (ByteBufferPacket) packet;
    ByteBuffer bb = p.getByteBuffer();
    if (bb.position() != 0)
      bb.rewind();
    return bb;
  }

  @Override
  public void handler(Packet packet, ChannelContext ctx) throws Exception {
    ByteBufferPacket p = (ByteBufferPacket) packet;
    ByteBuffer bb = p.getByteBuffer();
    byte[] msgBytes = new byte[bb.remaining()];
    bb.get(msgBytes);
    String sip = new String(msgBytes, StandardCharsets.US_ASCII);

    if (sip.startsWith("INVITE ")) {
      int rtpPort = allocator.allocate();
      RtpUdpServer rtpServer = new RtpUdpServer(rtpPort);
      rtpServer.start();
      rtpByConn.put(ctx.getId(), rtpServer);

      String resp = build200OkForInvite(sip, localIp, rtpPort);
      Tio.send(ctx, new ByteBufferPacket(ByteBuffer.wrap(resp.getBytes(StandardCharsets.US_ASCII))));
      return;
    }

    // 最小链路：ACK 不处理也能测 RTP，但建议至少识别 ACK/ BYE
    if (sip.startsWith("BYE ")) {
      closeRtp(ctx);
      String resp = buildSimpleResponse(sip, 200, "OK");
      Tio.send(ctx, new ByteBufferPacket(ByteBuffer.wrap(resp.getBytes(StandardCharsets.US_ASCII))));
      return;
    }

    // 其它先 200 OK（调试用）
    String resp = buildSimpleResponse(sip, 200, "OK");
    Tio.send(ctx, new ByteBufferPacket(ByteBuffer.wrap(resp.getBytes(StandardCharsets.US_ASCII))));
  }

  private void closeRtp(ChannelContext ctx) {
    RtpUdpServer s = rtpByConn.remove(ctx.getId());
    if (s != null) {
      s.stop();
      allocator.release(s.port());
    }
  }

  // 只做示例：真实项目要正确解析 Via/From/To/Call-ID/CSeq/Contact 等并回填 tag
  private String build200OkForInvite(String invite, String ip, int rtpPort) {
    String via = header(invite, "Via");
    String from = header(invite, "From");
    String to = header(invite, "To");
    String callId = header(invite, "Call-ID");
    String cseq = header(invite, "CSeq");

    // To 必须带 tag（最小实现随便生成一个）
    if (!to.toLowerCase().contains("tag=")) {
      to = to + ";tag=java1234";
    }

    String sdp = "v=0\r\n" + "o=- 1 1 IN IP4 " + ip + "\r\n" + "s=JavaSip\r\n" + "c=IN IP4 " + ip + "\r\n" + "t=0 0\r\n"
        + "m=audio " + rtpPort + " RTP/AVP 0\r\n" + "a=rtpmap:0 PCMU/8000\r\n" + "a=ptime:20\r\n" + "a=sendrecv\r\n";

    byte[] sdpBytes = sdp.getBytes(StandardCharsets.US_ASCII);

    String resp = "SIP/2.0 200 OK\r\n" + (via.isEmpty() ? "" : "Via: " + via + "\r\n")
        + (from.isEmpty() ? "" : "From: " + from + "\r\n") + (to.isEmpty() ? "" : "To: " + to + "\r\n")
        + (callId.isEmpty() ? "" : "Call-ID: " + callId + "\r\n") + (cseq.isEmpty() ? "" : "CSeq: " + cseq + "\r\n")
        + "Contact: <sip:java@" + ip + ":5060>\r\n" + "Content-Type: application/sdp\r\n" + "Content-Length: "
        + sdpBytes.length + "\r\n" + "\r\n" + sdp;

    return resp;
  }

  private String buildSimpleResponse(String req, int code, String reason) {
    String via = header(req, "Via");
    String from = header(req, "From");
    String to = header(req, "To");
    String callId = header(req, "Call-ID");
    String cseq = header(req, "CSeq");
    if (!to.toLowerCase().contains("tag=")) {
      to = to + ";tag=java1234";
    }
    return "SIP/2.0 " + code + " " + reason + "\r\n" + (via.isEmpty() ? "" : "Via: " + via + "\r\n")
        + (from.isEmpty() ? "" : "From: " + from + "\r\n") + (to.isEmpty() ? "" : "To: " + to + "\r\n")
        + (callId.isEmpty() ? "" : "Call-ID: " + callId + "\r\n") + (cseq.isEmpty() ? "" : "CSeq: " + cseq + "\r\n")
        + "Content-Length: 0\r\n\r\n";
  }

  // 只取第一条同名 header（最小实现）
  private String header(String sip, String name) {
    String[] lines = sip.split("\r\n");
    for (String line : lines) {
      int idx = line.indexOf(':');
      if (idx <= 0)
        continue;
      String k = line.substring(0, idx).trim();
      if (k.equalsIgnoreCase(name)) {
        return line.substring(idx + 1).trim();
      }
    }
    return "";
  }
}