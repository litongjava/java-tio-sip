package com.litongjava.sip.rtp;

import com.litongjava.sip.model.CallSession;
import com.litongjava.sip.rtp.media.MediaProcessor;
import com.litongjava.sip.server.session.CallSessionManager;

public class RtpServerManager {

  private final String localIp;
  private final RtpPortAllocator allocator;
  private final CallSessionManager sessionManager;

  public RtpServerManager(String localIp, CallSessionManager sessionManager) {
    this(localIp, new RtpPortAllocator(), sessionManager);
  }

  public RtpServerManager(String localIp, RtpPortAllocator allocator, CallSessionManager sessionManager) {
    this.localIp = localIp;
    this.allocator = allocator;
    this.sessionManager = sessionManager;
  }

  public CallSession allocateAndStart(CallSession session, MediaProcessor mediaProcessor) throws Exception {
    int rtpPort = allocator.allocate();
    RtpUdpServer rtpServer = new RtpUdpServer(rtpPort, sessionManager);
    rtpServer.start(mediaProcessor);

    session.setLocalRtpPort(rtpPort);
    session.setRtpServer(rtpServer);
    session.setUpdatedTime(System.currentTimeMillis());
    return session;
  }

  public void stopAndRelease(CallSession session) {
    if (session == null) {
      return;
    }

    try {
      if (session.getRtpServer() != null) {
        session.getRtpServer().stop();
      }
    } finally {
      if (session.getLocalRtpPort() > 0) {
        allocator.release(session.getLocalRtpPort());
      }
    }
  }

  public String getLocalIp() {
    return localIp;
  }
}