package com.litongjava.sip.parser;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.litongjava.sip.model.SipResponse;

public class SipMessageEncoder {

  public byte[] encodeResponse(SipResponse response) {
    StringBuilder sb = new StringBuilder();

    sb.append(response.getVersion())
      .append(' ')
      .append(response.getStatusCode())
      .append(' ')
      .append(response.getReasonPhrase())
      .append("\r\n");

    for (Map.Entry<String, List<String>> e : response.getHeaders().entrySet()) {
      String name = e.getKey();
      List<String> values = e.getValue();
      if (values == null || values.isEmpty()) {
        continue;
      }
      for (String value : values) {
        sb.append(name).append(": ").append(value).append("\r\n");
      }
    }

    byte[] body = response.getBody();
    if (body == null) {
      body = new byte[0];
    }

    if (response.getHeader("Content-Length") == null) {
      sb.append("Content-Length: ").append(body.length).append("\r\n");
    }

    sb.append("\r\n");

    byte[] head = sb.toString().getBytes(StandardCharsets.US_ASCII);

    byte[] all = new byte[head.length + body.length];
    System.arraycopy(head, 0, all, 0, head.length);
    if (body.length > 0) {
      System.arraycopy(body, 0, all, head.length, body.length);
    }
    return all;
  }
}