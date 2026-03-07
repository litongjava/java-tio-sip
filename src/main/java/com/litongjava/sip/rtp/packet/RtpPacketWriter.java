package com.litongjava.sip.rtp.packet;

public class RtpPacketWriter {

  public byte[] write(RtpPacket packet) {
    byte[] payload = packet.getPayload();
    if (payload == null) {
      payload = new byte[0];
    }

    byte[] out = new byte[12 + payload.length];

    int b0 = 0;
    b0 |= (2 & 0x03) << 6; // version=2
    if (packet.isPadding()) {
      b0 |= 1 << 5;
    }
    if (packet.isExtension()) {
      b0 |= 1 << 4;
    }
    b0 |= (packet.getCsrcCount() & 0x0F);

    int b1 = 0;
    if (packet.isMarker()) {
      b1 |= 1 << 7;
    }
    b1 |= (packet.getPayloadType() & 0x7F);

    out[0] = (byte) b0;
    out[1] = (byte) b1;

    int seq = packet.getSequenceNumber() & 0xFFFF;
    out[2] = (byte) ((seq >> 8) & 0xFF);
    out[3] = (byte) (seq & 0xFF);

    long ts = packet.getTimestamp() & 0xFFFFFFFFL;
    out[4] = (byte) ((ts >> 24) & 0xFF);
    out[5] = (byte) ((ts >> 16) & 0xFF);
    out[6] = (byte) ((ts >> 8) & 0xFF);
    out[7] = (byte) (ts & 0xFF);

    long ssrc = packet.getSsrc() & 0xFFFFFFFFL;
    out[8] = (byte) ((ssrc >> 24) & 0xFF);
    out[9] = (byte) ((ssrc >> 16) & 0xFF);
    out[10] = (byte) ((ssrc >> 8) & 0xFF);
    out[11] = (byte) (ssrc & 0xFF);

    System.arraycopy(payload, 0, out, 12, payload.length);
    return out;
  }
}