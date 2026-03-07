package com.litongjava.sip.server.session;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.litongjava.sip.model.CallSession;

public class CallSessionManager {

  /**
   * 主索引：callId -> session
   */
  private final ConcurrentMap<String, CallSession> sessionsByCallId = new ConcurrentHashMap<>();

  /**
   * 辅助索引：localRtpPort -> callId
   * 用 callId 而不是 session，避免同一个 session 被多处索引直接引用时更难维护一致性
   */
  private final ConcurrentMap<Integer, String> callIdByLocalRtpPort = new ConcurrentHashMap<>();

  public CallSession getByCallId(String callId) {
    if (callId == null) {
      return null;
    }
    return sessionsByCallId.get(callId);
  }

  public CallSession getByLocalRtpPort(int localRtpPort) {
    String callId = callIdByLocalRtpPort.get(localRtpPort);
    if (callId == null) {
      return null;
    }
    return sessionsByCallId.get(callId);
  }

  /**
   * 创建或更新 session
   *
   * 注意：
   * 1. callId 不能为空
   * 2. localRtpPort 如果会变化，需要同步更新辅助索引
   */
  public CallSession createOrUpdate(CallSession session) {
    if (session == null || session.getCallId() == null) {
      throw new IllegalArgumentException("call session or callId is null");
    }

    final String callId = session.getCallId();
    final int newLocalRtpPort = session.getLocalRtpPort();
    final long now = System.currentTimeMillis();
    session.setUpdatedTime(now);

    sessionsByCallId.compute(callId, (key, oldSession) -> {
      if (oldSession != null) {
        int oldLocalRtpPort = oldSession.getLocalRtpPort();
        if (oldLocalRtpPort > 0 && oldLocalRtpPort != newLocalRtpPort) {
          callIdByLocalRtpPort.remove(oldLocalRtpPort, callId);
        }
      }

      if (newLocalRtpPort > 0) {
        callIdByLocalRtpPort.put(newLocalRtpPort, callId);
      }

      return session;
    });

    return session;
  }

  public void markAckReceived(String callId) {
    if (callId == null) {
      return;
    }

    CallSession session = sessionsByCallId.get(callId);
    if (session != null) {
      session.setAckReceived(true);
      session.setUpdatedTime(System.currentTimeMillis());
    }
  }

  public void markTerminated(String callId) {
    if (callId == null) {
      return;
    }

    CallSession session = sessionsByCallId.get(callId);
    if (session != null) {
      session.setTerminated(true);
      session.setUpdatedTime(System.currentTimeMillis());
    }
  }

  public void remove(String callId) {
    if (callId == null) {
      return;
    }

    CallSession removed = sessionsByCallId.remove(callId);
    if (removed != null) {
      int localRtpPort = removed.getLocalRtpPort();
      if (localRtpPort > 0) {
        callIdByLocalRtpPort.remove(localRtpPort, callId);
      }
    }
  }

  public Map<String, CallSession> snapshot() {
    return Collections.unmodifiableMap(new HashMap<>(sessionsByCallId));
  }

  public int size() {
    return sessionsByCallId.size();
  }
}