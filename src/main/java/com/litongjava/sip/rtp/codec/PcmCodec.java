package com.litongjava.sip.rtp.codec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * PCM 编解码工具
 */
public final class PcmCodec {

  private PcmCodec() {
  }

  public static byte[] shortsToLittleEndianBytes(short[] samples) {
    if (samples == null || samples.length == 0) {
      return new byte[0];
    }

    ByteBuffer buffer = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
    for (short s : samples) {
      buffer.putShort(s);
    }
    return buffer.array();
  }

  public static short[] littleEndianBytesToShorts(byte[] bytes) {
    if (bytes == null || bytes.length < 2) {
      return new short[0];
    }

    int len = bytes.length / 2;
    short[] out = new short[len];
    ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    for (int i = 0; i < len; i++) {
      out[i] = buffer.getShort();
    }
    return out;
  }
}