package com.litongjava.sip.sdp;

public class SdpNegotiationResult {

  private boolean success;
  private String failureReason;

  private String remoteRtpIp;
  private int remoteRtpPort;

  private CodecSpec selectedCodec;

  private boolean telephoneEventSupported;
  private int remoteTelephoneEventPayloadType = -1;

  private int ptime = 20;

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public void setFailureReason(String failureReason) {
    this.failureReason = failureReason;
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

  public static SdpNegotiationResult fail(String reason) {
    SdpNegotiationResult r = new SdpNegotiationResult();
    r.setSuccess(false);
    r.setFailureReason(reason);
    return r;
  }

  public static SdpNegotiationResult ok() {
    SdpNegotiationResult r = new SdpNegotiationResult();
    r.setSuccess(true);
    return r;
  }
}