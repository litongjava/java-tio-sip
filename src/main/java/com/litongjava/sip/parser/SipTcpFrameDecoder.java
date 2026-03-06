package com.litongjava.sip.parser;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import com.litongjava.tio.core.ChannelContext;

public class SipTcpFrameDecoder {

  private static final String ATTR_ACC = "sip_tcp_acc_buf";
  private static final int DEFAULT_BUFFER_SIZE = 64 * 1024;
  private static final int MAX_SIP_MESSAGE_SIZE = 1024 * 1024; // 1MB

  public byte[] decode(ByteBuffer buffer, int readableLength, ChannelContext ctx) {
    if (readableLength <= 0) {
      return null;
    }

    ByteBuffer acc = (ByteBuffer) ctx.getAttribute(ATTR_ACC);
    if (acc == null) {
      acc = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE);
      ctx.setAttribute(ATTR_ACC, acc);
    }

    byte[] chunk = new byte[readableLength];
    buffer.get(chunk);

    acc = ensureCapacity(acc, chunk.length, ctx);
    acc.put(chunk);

    acc.flip();
    int frameLen = tryParseOneFrameLength(acc);
    if (frameLen <= 0) {
      acc.compact();
      return null;
    }

    byte[] frame = new byte[frameLen];
    acc.get(frame);
    acc.compact();
    return frame;
  }

  private ByteBuffer ensureCapacity(ByteBuffer acc, int incoming, ChannelContext ctx) {
    if (acc.remaining() >= incoming) {
      return acc;
    }

    int needed = acc.position() + incoming;
    int newCap = acc.capacity();

    while (newCap < needed) {
      newCap = newCap * 2;
      if (newCap > MAX_SIP_MESSAGE_SIZE) {
        throw new IllegalStateException("SIP accumulate buffer too large: " + newCap);
      }
    }

    ByteBuffer bigger = ByteBuffer.allocate(newCap);
    acc.flip();
    bigger.put(acc);
    ctx.setAttribute(ATTR_ACC, bigger);
    return bigger;
  }

  private int tryParseOneFrameLength(ByteBuffer acc) {
    int start = acc.position();
    int limit = acc.limit();

    if ((limit - start) > MAX_SIP_MESSAGE_SIZE) {
      throw new IllegalStateException("SIP message too large");
    }

    int headerEnd = indexOf(acc, "\r\n\r\n".getBytes(StandardCharsets.US_ASCII), start, limit);
    if (headerEnd < 0) {
      return -1;
    }

    int headerBlockEnd = headerEnd + 4;
    String head = sliceToString(acc, start, headerBlockEnd);

    int contentLength = parseContentLength(head);
    if (contentLength < 0) {
      throw new IllegalStateException("Invalid Content-Length");
    }

    int totalLength = (headerBlockEnd - start) + contentLength;
    if ((limit - start) < totalLength) {
      return -1;
    }

    return totalLength;
  }

  private int parseContentLength(String headers) {
    String[] lines = headers.split("\r\n");
    for (String line : lines) {
      int idx = line.indexOf(':');
      if (idx <= 0) {
        continue;
      }

      String name = line.substring(0, idx).trim();
      String value = line.substring(idx + 1).trim();

      if ("Content-Length".equalsIgnoreCase(name) || "l".equalsIgnoreCase(name)) {
        try {
          return Integer.parseInt(value);
        } catch (NumberFormatException e) {
          return -1;
        }
      }
    }
    return 0;
  }

  private int indexOf(ByteBuffer buf, byte[] pat, int from, int to) {
    for (int i = from; i <= to - pat.length; i++) {
      boolean ok = true;
      for (int j = 0; j < pat.length; j++) {
        if (buf.get(i + j) != pat[j]) {
          ok = false;
          break;
        }
      }
      if (ok) {
        return i;
      }
    }
    return -1;
  }

  private String sliceToString(ByteBuffer buf, int from, int to) {
    byte[] bytes = new byte[to - from];
    for (int i = 0; i < bytes.length; i++) {
      bytes[i] = buf.get(from + i);
    }
    return new String(bytes, StandardCharsets.US_ASCII);
  }
}