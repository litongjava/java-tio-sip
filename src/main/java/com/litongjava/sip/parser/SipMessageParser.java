package com.litongjava.sip.parser;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.litongjava.sip.model.SipMessage;
import com.litongjava.sip.model.SipRequest;
import com.litongjava.sip.model.SipResponse;

public class SipMessageParser {

  private static final Map<String, String> COMPACT_HEADERS = new HashMap<>();

  static {
    COMPACT_HEADERS.put("v", "Via");
    COMPACT_HEADERS.put("f", "From");
    COMPACT_HEADERS.put("t", "To");
    COMPACT_HEADERS.put("i", "Call-ID");
    COMPACT_HEADERS.put("l", "Content-Length");
    COMPACT_HEADERS.put("c", "Content-Type");
    COMPACT_HEADERS.put("m", "Contact");
  }

  public SipMessage parse(byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      throw new IllegalArgumentException("empty sip message");
    }

    int headerEnd = indexOf(bytes, "\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
    if (headerEnd < 0) {
      throw new IllegalArgumentException("invalid sip message, no header terminator");
    }

    int headerBlockEnd = headerEnd + 4;

    String headerText = new String(bytes, 0, headerBlockEnd, StandardCharsets.US_ASCII);
    String[] lines = headerText.split("\r\n");

    if (lines.length == 0) {
      throw new IllegalArgumentException("invalid sip start line");
    }

    String startLine = lines[0];
    SipMessage message = parseStartLine(startLine);

    for (int i = 1; i < lines.length; i++) {
      String line = lines[i];
      if (line == null || line.isEmpty()) {
        continue;
      }

      int idx = line.indexOf(':');
      if (idx <= 0) {
        continue;
      }

      String name = line.substring(0, idx).trim();
      String value = line.substring(idx + 1).trim();
      name = normalizeHeaderName(name);

      message.addHeader(name, value);
    }

    int contentLength = parseContentLength(message);
    if (contentLength > 0) {
      if (bytes.length < headerBlockEnd + contentLength) {
        throw new IllegalArgumentException("sip body not complete");
      }

      byte[] body = new byte[contentLength];
      System.arraycopy(bytes, headerBlockEnd, body, 0, contentLength);
      message.setBody(body);
    } else {
      message.setBody(new byte[0]);
    }

    return message;
  }

  private SipMessage parseStartLine(String startLine) {
    if (startLine.startsWith("SIP/2.0")) {
      String[] parts = startLine.split(" ", 3);
      if (parts.length < 3) {
        throw new IllegalArgumentException("invalid sip response line: " + startLine);
      }

      SipResponse resp = new SipResponse();
      resp.setVersion(parts[0]);
      resp.setStatusCode(Integer.parseInt(parts[1]));
      resp.setReasonPhrase(parts[2]);
      return resp;
    } else {
      String[] parts = startLine.split(" ", 3);
      if (parts.length < 3) {
        throw new IllegalArgumentException("invalid sip request line: " + startLine);
      }

      SipRequest req = new SipRequest();
      req.setMethod(parts[0]);
      req.setRequestUri(parts[1]);
      req.setVersion(parts[2]);
      return req;
    }
  }

  private String normalizeHeaderName(String name) {
    String compact = COMPACT_HEADERS.get(name);
    return compact != null ? compact : name;
  }

  private int parseContentLength(SipMessage message) {
    String v = message.getHeader("Content-Length");
    if (v == null || v.isEmpty()) {
      return 0;
    }
    return Integer.parseInt(v.trim());
  }

  private int indexOf(byte[] src, byte[] pat) {
    for (int i = 0; i <= src.length - pat.length; i++) {
      boolean ok = true;
      for (int j = 0; j < pat.length; j++) {
        if (src[i + j] != pat[j]) {
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
}