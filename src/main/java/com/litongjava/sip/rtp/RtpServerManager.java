package com.litongjava.sip.rtp;

import com.litongjava.sip.model.CallSession;

public class RtpServerManager {

  private final String localIp;
  private final RtpPortAllocator allocator;

  public RtpServerManager(String localIp) {
    this(localIp, new RtpPortAllocator());
  }

  public RtpServerManager(String localIp, RtpPortAllocator allocator) {
    this.localIp = localIp;
    this.allocator = allocator;
  }

  public CallSession allocateAndStart(CallSession session) throws Exception {
    int rtpPort = allocator.allocate();
    RtpUdpServer rtpServer = new RtpUdpServer(rtpPort);
    rtpServer.start();

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