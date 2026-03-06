package com.litongjava.sip.server;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import com.litongjava.aio.ByteBufferPacket;
import com.litongjava.tio.core.ChannelContext;

public class SipDecoder {

  private static final String ATTR_BUF = "sip_acc_buf";

  public static final ByteBufferPacket decode(ByteBuffer buffer, int readableLength, ChannelContext ctx) {
    // 取出累积 buffer
    ByteBuffer acc = (ByteBuffer) ctx.getAttribute(ATTR_BUF);
    if (acc == null) {
      acc = ByteBuffer.allocate(1024 * 64);
      ctx.setAttribute(ATTR_BUF, acc);
    }

    // 追加本次数据
    byte[] chunk = new byte[readableLength];
    buffer.get(chunk);
    if (acc.remaining() < chunk.length) {
      // 简单扩容
      ByteBuffer bigger = ByteBuffer.allocate(acc.capacity() + 1024 * 64);
      acc.flip();
      bigger.put(acc);
      acc = bigger;
      ctx.setAttribute(ATTR_BUF, acc);
    }
    acc.put(chunk);

    // 尝试切出完整 SIP 消息
    acc.flip();
    int msgLen = tryParseOneSipMessageLength(acc);
    if (msgLen <= 0) {
      acc.compact();
      return null;
    }

    byte[] msg = new byte[msgLen];
    acc.get(msg);
    acc.compact();

    ByteBufferPacket p = new ByteBufferPacket(ByteBuffer.wrap(msg));
    return p;
  }
  
  // 返回完整 SIP message 的字节长度；不够则返回 -1
  private static int tryParseOneSipMessageLength(ByteBuffer acc) {
    int startPos = acc.position();
    int limit = acc.limit();

    // 找 \r\n\r\n
    int headerEnd = indexOf(acc, "\r\n\r\n".getBytes(StandardCharsets.US_ASCII), startPos, limit);
    if (headerEnd < 0)
      return -1;

    int headerLen = headerEnd + 4 - startPos;
    String headers = sliceToString(acc, startPos, headerEnd + 4);

    int contentLength = parseContentLength(headers);
    int totalLen = headerLen + contentLength;

    // 判断 body 是否够
    if ((limit - startPos) < totalLen)
      return -1;
    return totalLen;
  }
  

  private static int parseContentLength(String headers) {
    // 简单解析 Content-Length
    String[] lines = headers.split("\r\n");
    for (String line : lines) {
      int idx = line.indexOf(':');
      if (idx <= 0)
        continue;
      String k = line.substring(0, idx).trim();
      if ("Content-Length".equalsIgnoreCase(k)) {
        String v = line.substring(idx + 1).trim();
        try {
          return Integer.parseInt(v);
        } catch (Exception ignore) {
        }
      }
    }
    return 0;
  }

  private static int indexOf(ByteBuffer buf, byte[] pat, int from, int to) {
    for (int i = from; i <= to - pat.length; i++) {
      boolean ok = true;
      for (int j = 0; j < pat.length; j++) {
        if (buf.get(i + j) != pat[j]) {
          ok = false;
          break;
        }
      }
      if (ok)
        return i;
    }
    return -1;
  }

  private static String sliceToString(ByteBuffer buf, int from, int to) {
    byte[] b = new byte[to - from];
    for (int i = 0; i < b.length; i++) {
      b[i] = buf.get(from + i);
    }
    return new String(b, StandardCharsets.US_ASCII);
  }

}
