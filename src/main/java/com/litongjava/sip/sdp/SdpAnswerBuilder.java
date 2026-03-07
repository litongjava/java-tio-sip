package com.litongjava.sip.sdp;

public class SdpAnswerBuilder {

  public String buildAnswer(String localIp, int localRtpPort, SdpNegotiationResult result) {
    CodecSpec codec = result.getSelectedCodec();
    int ptime = result.getPtime() > 0 ? result.getPtime() : 20;

    StringBuilder sb = new StringBuilder();
    sb.append("v=0\r\n");
    sb.append("o=- 1 1 IN IP4 ").append(localIp).append("\r\n");
    sb.append("s=JavaSip\r\n");
    sb.append("c=IN IP4 ").append(localIp).append("\r\n");
    sb.append("t=0 0\r\n");

    sb.append("m=audio ").append(localRtpPort).append(" RTP/AVP ").append(codec.getPayloadType());

    if (result.isTelephoneEventSupported()) {
      sb.append(" 101");
    }
    sb.append("\r\n");

    sb.append("a=rtpmap:").append(codec.getPayloadType()).append(" ").append(codec.getCodecName()).append("/")
        .append(codec.getClockRate()).append("\r\n");

    if (result.isTelephoneEventSupported()) {
      sb.append("a=rtpmap:101 telephone-event/8000\r\n");
      sb.append("a=fmtp:101 0-15\r\n");
    }

    sb.append("a=ptime:").append(ptime).append("\r\n");
    sb.append("a=sendrecv\r\n");

    return sb.toString();
  }
}