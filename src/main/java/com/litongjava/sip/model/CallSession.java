package com.litongjava.sip.model;

import com.litongjava.sip.rtp.RtpUdpServer;

public class CallSession {

  private String callId;
  private String fromTag;
  private String toTag;

  private String transport; // TCP / UDP

  private String remoteSipIp;
  private int remoteSipPort;

  private String remoteRtpIp;
  private int remoteRtpPort;
  private int localRtpPort;

  private long createdTime;
  private long updatedTime;
  private long ackDeadline;

  private boolean ackReceived;
  private boolean terminated;

  private String last200Ok;

  private RtpUdpServer rtpServer;

  public String getCallId() {
    return callId;
  }

  public void setCallId(String callId) {
    this.callId = callId;
  }

  public String getFromTag() {
    return fromTag;
  }

  public void setFromTag(String fromTag) {
    this.fromTag = fromTag;
  }

  public String getToTag() {
    return toTag;
  }

  public void setToTag(String toTag) {
    this.toTag = toTag;
  }

  public String getTransport() {
    return transport;
  }

  public void setTransport(String transport) {
    this.transport = transport;
  }

  public String getRemoteSipIp() {
    return remoteSipIp;
  }

  public void setRemoteSipIp(String remoteSipIp) {
    this.remoteSipIp = remoteSipIp;
  }

  public int getRemoteSipPort() {
    return remoteSipPort;
  }

  public void setRemoteSipPort(int remoteSipPort) {
    this.remoteSipPort = remoteSipPort;
  }

  public String getRemoteRtpIp() {
    return remoteRtpIp;
  }

  public void setRemoteRtpIp(String remoteRtpIp) {
    this.remoteRtpIp = remoteRtpIp;
  }

  public int getRemoteRtpPort() {
    return remoteRtpPort;
  }

  public void setRemoteRtpPort(int remoteRtpPort) {
    this.remoteRtpPort = remoteRtpPort;
  }

  public int getLocalRtpPort() {
    return localRtpPort;
  }

  public void setLocalRtpPort(int localRtpPort) {
    this.localRtpPort = localRtpPort;
  }

  public long getCreatedTime() {
    return createdTime;
  }

  public void setCreatedTime(long createdTime) {
    this.createdTime = createdTime;
  }

  public long getUpdatedTime() {
    return updatedTime;
  }

  public void setUpdatedTime(long updatedTime) {
    this.updatedTime = updatedTime;
  }

  public long getAckDeadline() {
    return ackDeadline;
  }

  public void setAckDeadline(long ackDeadline) {
    this.ackDeadline = ackDeadline;
  }

  public boolean isAckReceived() {
    return ackReceived;
  }

  public void setAckReceived(boolean ackReceived) {
    this.ackReceived = ackReceived;
  }

  public boolean isTerminated() {
    return terminated;
  }

  public void setTerminated(boolean terminated) {
    this.terminated = terminated;
  }

  public String getLast200Ok() {
    return last200Ok;
  }

  public void setLast200Ok(String last200Ok) {
    this.last200Ok = last200Ok;
  }

  public RtpUdpServer getRtpServer() {
    return rtpServer;
  }

  public void setRtpServer(RtpUdpServer rtpServer) {
    this.rtpServer = rtpServer;
  }
}