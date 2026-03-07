package com.litongjava.sip.server.session;

import java.util.Map;

import com.litongjava.sip.model.CallSession;
import com.litongjava.sip.rtp.RtpServerManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CallSessionReaper implements Runnable {

  private final CallSessionManager sessionManager;
  private final RtpServerManager rtpServerManager;

  // 已发 200 OK 但未 ACK，超时回收
  private final long ackTimeoutMs;

  // 已 terminated 的会话多留一小会儿再删
  private final long terminatedRetentionMs;

  public CallSessionReaper(CallSessionManager sessionManager, RtpServerManager rtpServerManager) {
    this(sessionManager, rtpServerManager, 32000L, 5000L);
  }

  public CallSessionReaper(CallSessionManager sessionManager, RtpServerManager rtpServerManager, long ackTimeoutMs,
      long terminatedRetentionMs) {
    this.sessionManager = sessionManager;
    this.rtpServerManager = rtpServerManager;
    this.ackTimeoutMs = ackTimeoutMs;
    this.terminatedRetentionMs = terminatedRetentionMs;
  }

  @Override
  public void run() {
    long now = System.currentTimeMillis();
    Map<String, CallSession> snapshot = sessionManager.snapshot();

    for (Map.Entry<String, CallSession> entry : snapshot.entrySet()) {
      String callId = entry.getKey();
      CallSession session = entry.getValue();
      if (session == null) {
        continue;
      }

      try {
        reapAckTimeout(callId, session, now);
        reapTerminated(callId, session, now);
      } catch (Exception e) {
        log.error("reap session error, callId={}", callId, e);
      }
    }
  }

  private void reapAckTimeout(String callId, CallSession session, long now) {
    if (session.isTerminated()) {
      return;
    }

    if (session.isAckReceived()) {
      return;
    }

    long deadline = session.getAckDeadline();
    if (deadline <= 0) {
      return;
    }

    // 支持沿用传入 session 的 deadline，也支持兜底 createdTime + ackTimeoutMs
    long actualDeadline = deadline > 0 ? deadline : session.getCreatedTime() + ackTimeoutMs;
    if (now < actualDeadline) {
      return;
    }

    log.info("ACK timeout, release callId={}, localRtpPort={}", callId, session.getLocalRtpPort());
    rtpServerManager.stopAndRelease(session);
    session.setTerminated(true);
    session.setUpdatedTime(now);
  }

  private void reapTerminated(String callId, CallSession session, long now) {
    if (!session.isTerminated()) {
      return;
    }

    long base = session.getUpdatedTime() > 0 ? session.getUpdatedTime() : session.getCreatedTime();
    if (now - base < terminatedRetentionMs) {
      return;
    }

    sessionManager.remove(callId);
    log.info("session removed, callId={}", callId);
  }
}