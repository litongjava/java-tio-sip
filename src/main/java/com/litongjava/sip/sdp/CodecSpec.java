package com.litongjava.sip.sdp;

public class CodecSpec {

  private final int payloadType;
  private final String codecName;
  private final int clockRate;

  public CodecSpec(int payloadType, String codecName, int clockRate) {
    this.payloadType = payloadType;
    this.codecName = codecName;
    this.clockRate = clockRate;
  }

  public int getPayloadType() {
    return payloadType;
  }

  public String getCodecName() {
    return codecName;
  }

  public int getClockRate() {
    return clockRate;
  }

  public boolean isSameCodec(String codecName, int clockRate) {
    if (codecName == null) {
      return false;
    }
    return this.codecName.equalsIgnoreCase(codecName) && this.clockRate == clockRate;
  }

  public boolean isStaticPcmu() {
    return payloadType == 0 && "PCMU".equalsIgnoreCase(codecName) && clockRate == 8000;
  }

  public boolean isStaticPcma() {
    return payloadType == 8 && "PCMA".equalsIgnoreCase(codecName) && clockRate == 8000;
  }

  @Override
  public String toString() {
    return "CodecSpec{" + "payloadType=" + payloadType + ", codecName='" + codecName + '\'' + ", clockRate=" + clockRate
        + '}';
  }
}