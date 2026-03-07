package com.litongjava.sip.rtp.packet;

public class RtpPacketParser {

  public RtpPacket parse(byte[] data) {
    if (data == null || data.length < 12) {
      throw new IllegalArgumentException("rtp packet too short");
    }

    int b0 = data[0] & 0xFF;
    int b1 = data[1] & 0xFF;

    int version = (b0 >> 6) & 0x03;
    if (version != 2) {
      throw new IllegalArgumentException("unsupported rtp version: " + version);
    }

    boolean padding = ((b0 >> 5) & 0x01) == 1;
    boolean extension = ((b0 >> 4) & 0x01) == 1;
    int csrcCount = b0 & 0x0F;

    boolean marker = ((b1 >> 7) & 0x01) == 1;
    int payloadType = b1 & 0x7F;

    int sequenceNumber = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);

    long timestamp =
        ((long) (data[4] & 0xFF) << 24) |
        ((long) (data[5] & 0xFF) << 16) |
        ((long) (data[6] & 0xFF) << 8) |
        ((long) (data[7] & 0xFF));

    long ssrc =
        ((long) (data[8] & 0xFF) << 24) |
        ((long) (data[9] & 0xFF) << 16) |
        ((long) (data[10] & 0xFF) << 8) |
        ((long) (data[11] & 0xFF));

    int headerLen = 12 + csrcCount * 4;

    if (data.length < headerLen) {
      throw new IllegalArgumentException("invalid rtp header length");
    }

    if (extension) {
      if (data.length < headerLen + 4) {
        throw new IllegalArgumentException("invalid rtp extension header");
      }
      int extLenWords = ((data[headerLen + 2] & 0xFF) << 8) | (data[headerLen + 3] & 0xFF);
      headerLen += 4 + extLenWords * 4;
      if (data.length < headerLen) {
        throw new IllegalArgumentException("invalid rtp extension payload");
      }
    }

    int payloadLen = data.length - headerLen;
    if (padding) {
      int paddingCount = data[data.length - 1] & 0xFF;
      payloadLen -= paddingCount;
      if (payloadLen < 0) {
        throw new IllegalArgumentException("invalid rtp padding");
      }
    }

    byte[] payload = new byte[payloadLen];
    System.arraycopy(data, headerLen, payload, 0, payloadLen);

    RtpPacket packet = new RtpPacket();
    packet.setVersion(version);
    packet.setPadding(padding);
    packet.setExtension(extension);
    packet.setCsrcCount(csrcCount);
    packet.setMarker(marker);
    packet.setPayloadType(payloadType);
    packet.setSequenceNumber(sequenceNumber);
    packet.setTimestamp(timestamp);
    packet.setSsrc(ssrc);
    packet.setPayload(payload);
    return packet;
  }
}