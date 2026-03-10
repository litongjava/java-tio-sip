package com.litongjava.sip.server.session;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.litongjava.sip.model.CallSession;
import com.litongjava.sip.rtp.codec.NegotiatedAudioFormatResolver;

public class CallSessionManager {

  private final ConcurrentMap<String, CallSession> sessionsByCallId = new ConcurrentHashMap<>();
  private final ConcurrentMap<Integer, String> callIdByLocalRtpPort = new ConcurrentHashMap<>();

  public CallSession getByCallId(String callId) {
    return callId == null ? null : sessionsByCallId.get(callId);
  }

  public CallSession getByLocalRtpPort(int localRtpPort) {
    String callId = callIdByLocalRtpPort.get(localRtpPort);
    return callId == null ? null : sessionsByCallId.get(callId);
  }

  public CallSession createOrUpdate(CallSession session) {
    if (session == null || session.getCallId() == null) {
      throw new IllegalArgumentException("call session or callId is null");
    }

    initNegotiatedMediaFormat(session);

    final String callId = session.getCallId();
    final int newPort = session.getLocalRtpPort();
    final long now = System.currentTimeMillis();
    session.setUpdatedTime(now);

    sessionsByCallId.compute(callId, (k, oldSession) -> {
      if (oldSession != null) {
        int oldPort = oldSession.getLocalRtpPort();
        if (oldPort > 0 && oldPort != newPort) {
          callIdByLocalRtpPort.remove(oldPort, callId);
        }

        if (session.getAudioCodec() == null) {
          session.setAudioCodec(oldSession.getAudioCodec());
        }
        if (session.getPcmSampleRate() <= 0) {
          session.setPcmSampleRate(oldSession.getPcmSampleRate());
        }
        if (session.getChannels() <= 0) {
          session.setChannels(oldSession.getChannels());
        }
      }

      if (newPort > 0) {
        String existingCallId = callIdByLocalRtpPort.put(newPort, callId);
        if (existingCallId != null && !existingCallId.equals(callId)) {
          // 如需严格防冲突，这里可以选择抛异常或记录日志
        }
      }

      return session;
    });

    return session;
  }

  private void initNegotiatedMediaFormat(CallSession session) {
    if (session == null) {
      return;
    }

    if (session.getPcmSampleRate() <= 0) {
      session.setPcmSampleRate(NegotiatedAudioFormatResolver.resolveSessionPcmSampleRate(session));
    }

    if (session.getChannels() <= 0) {
      session.setChannels(NegotiatedAudioFormatResolver.resolveChannels(session));
    }
  }

  public boolean updateLocalRtpPort(String callId, int newLocalRtpPort) {
    if (callId == null) {
      return false;
    }

    return sessionsByCallId.computeIfPresent(callId, (k, session) -> {
      int oldPort = session.getLocalRtpPort();
      if (oldPort > 0 && oldPort != newLocalRtpPort) {
        callIdByLocalRtpPort.remove(oldPort, callId);
      }

      session.setLocalRtpPort(newLocalRtpPort);
      session.setUpdatedTime(System.currentTimeMillis());

      if (newLocalRtpPort > 0) {
        callIdByLocalRtpPort.put(newLocalRtpPort, callId);
      }

      return session;
    }) != null;
  }

  public boolean markAckReceived(String callId) {
    if (callId == null) {
      return false;
    }

    CallSession session = sessionsByCallId.get(callId);
    if (session == null) {
      return false;
    }

    session.setAckReceived(true);
    session.setUpdatedTime(System.currentTimeMillis());
    return true;
  }

  public boolean markTerminated(String callId) {
    if (callId == null) {
      return false;
    }

    CallSession session = sessionsByCallId.get(callId);
    if (session == null) {
      return false;
    }

    session.setTerminated(true);
    session.setUpdatedTime(System.currentTimeMillis());
    return true;
  }

  public boolean remove(String callId) {
    if (callId == null) {
      return false;
    }

    CallSession removed = sessionsByCallId.remove(callId);
    if (removed == null) {
      return false;
    }

    int localRtpPort = removed.getLocalRtpPort();
    if (localRtpPort > 0) {
      callIdByLocalRtpPort.remove(localRtpPort, callId);
    }

    removed.release();
    return true;
  }

  public int removeExpired(long expireBefore) {
    int removedCount = 0;
    for (Map.Entry<String, CallSession> entry : sessionsByCallId.entrySet()) {
      String callId = entry.getKey();
      CallSession session = entry.getValue();
      if (session != null && session.getUpdatedTime() < expireBefore) {
        if (sessionsByCallId.remove(callId, session)) {
          int localRtpPort = session.getLocalRtpPort();
          if (localRtpPort > 0) {
            callIdByLocalRtpPort.remove(localRtpPort, callId);
          }
          session.release();
          removedCount++;
        }
      }
    }
    return removedCount;
  }

  public Map<String, CallSession> snapshot() {
    return Collections.unmodifiableMap(new HashMap<>(sessionsByCallId));
  }

  public int size() {
    return sessionsByCallId.size();
  }
}