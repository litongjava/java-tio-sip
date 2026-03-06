package com.litongjava.sip.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class SipMessage {

  private final Map<String, List<String>> headers = new LinkedHashMap<>();
  private byte[] body;

  public Map<String, List<String>> getHeaders() {
    return headers;
  }

  public void addHeader(String name, String value) {
    headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
  }

  public String getHeader(String name) {
    for (Map.Entry<String, List<String>> e : headers.entrySet()) {
      if (e.getKey().equalsIgnoreCase(name)) {
        List<String> vals = e.getValue();
        return vals == null || vals.isEmpty() ? null : vals.get(0);
      }
    }
    return null;
  }

  public List<String> getHeaders(String name) {
    for (Map.Entry<String, List<String>> e : headers.entrySet()) {
      if (e.getKey().equalsIgnoreCase(name)) {
        return e.getValue();
      }
    }
    return List.of();
  }

  public byte[] getBody() {
    return body;
  }

  public void setBody(byte[] body) {
    this.body = body;
  }

  public int contentLength() {
    return body == null ? 0 : body.length;
  }
}