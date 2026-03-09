package com.litongjava.sip.model;

import com.litongjava.sip.rtp.RtpUdpServer;
import com.litongjava.sip.sdp.CodecSpec;

public class CallSession {

  private String callId;
  private String fromTag;
  private String toTag;

  private String transport;

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

  private CodecSpec selectedCodec;
  private boolean telephoneEventSupported;
  private int remoteTelephoneEventPayloadType = -1;
  private int ptime = 20;

  private long localSsrc = System.nanoTime() & 0xFFFFFFFFL;
  private int sendSequence = 0;
  private long sendTimestamp = 0;
  private boolean rtpInitialized = false;

  public synchronized int nextSendSequence() {
    sendSequence = (sendSequence + 1) & 0xFFFF;
    return sendSequence;
  }

  public synchronized long nextSendTimestamp(int sampleCount) {
    if (!rtpInitialized) {
      rtpInitialized = true;
      sendTimestamp = sampleCount;
      return sendTimestamp;
    }
    sendTimestamp = (sendTimestamp + sampleCount) & 0xFFFFFFFFL;
    return sendTimestamp;
  }

  public long getLocalSsrc() {
    return localSsrc;
  }

  public void setLocalSsrc(long localSsrc) {
    this.localSsrc = localSsrc;
  }

  public int getSendSequence() {
    return sendSequence;
  }

  public void setSendSequence(int sendSequence) {
    this.sendSequence = sendSequence;
  }

  public long getSendTimestamp() {
    return sendTimestamp;
  }

  public void setSendTimestamp(long sendTimestamp) {
    this.sendTimestamp = sendTimestamp;
  }

  public boolean isRtpInitialized() {
    return rtpInitialized;
  }

  public void setRtpInitialized(boolean rtpInitialized) {
    this.rtpInitialized = rtpInitialized;
  }

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

  public CodecSpec getSelectedCodec() {
    return selectedCodec;
  }

  public void setSelectedCodec(CodecSpec selectedCodec) {
    this.selectedCodec = selectedCodec;
  }

  public boolean isTelephoneEventSupported() {
    return telephoneEventSupported;
  }

  public void setTelephoneEventSupported(boolean telephoneEventSupported) {
    this.telephoneEventSupported = telephoneEventSupported;
  }

  public int getRemoteTelephoneEventPayloadType() {
    return remoteTelephoneEventPayloadType;
  }

  public void setRemoteTelephoneEventPayloadType(int remoteTelephoneEventPayloadType) {
    this.remoteTelephoneEventPayloadType = remoteTelephoneEventPayloadType;
  }

  public int getPtime() {
    return ptime;
  }

  public void setPtime(int ptime) {
    this.ptime = ptime;
  }
}