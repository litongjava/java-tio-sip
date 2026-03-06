package com.litongjava.sip.server.session;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.litongjava.sip.model.CallSession;

public class CallSessionManager {

  private final Map<String, CallSession> sessions = new ConcurrentHashMap<>();

  public CallSession getByCallId(String callId) {
    if (callId == null) {
      return null;
    }
    return sessions.get(callId);
  }

  public CallSession createOrUpdate(CallSession session) {
    if (session == null || session.getCallId() == null) {
      throw new IllegalArgumentException("call session or callId is null");
    }
    session.setUpdatedTime(System.currentTimeMillis());
    sessions.put(session.getCallId(), session);
    return session;
  }

  public void markAckReceived(String callId) {
    CallSession session = sessions.get(callId);
    if (session != null) {
      session.setAckReceived(true);
      session.setUpdatedTime(System.currentTimeMillis());
    }
  }

  public void terminate(String callId) {
    CallSession session = sessions.remove(callId);
    if (session != null) {
      session.setTerminated(true);
      session.setUpdatedTime(System.currentTimeMillis());
      if (session.getRtpServer() != null) {
        session.getRtpServer().stop();
      }
    }
  }

  public void remove(String callId) {
    sessions.remove(callId);
  }

  public Map<String, CallSession> snapshot() {
    return Map.copyOf(sessions);
  }
}