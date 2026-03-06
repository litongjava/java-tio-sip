package com.litongjava.sip.model;

import com.litongjava.sip.rtp.RtpUdpServer;

public class CallSession {

  public final String callId;
  public final int rtpPort;
  public final String toTag;
  public final RtpUdpServer rtpServer;

  public volatile String last200Ok;

  public CallSession(String callId, int rtpPort, String toTag, RtpUdpServer rtpServer) {
    this.callId = callId;
    this.rtpPort = rtpPort;
    this.toTag = toTag;
    this.rtpServer = rtpServer;
  }
}