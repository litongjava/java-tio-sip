package com.litongjava.sip.rtp.codec;

public class PcmuCodec implements AudioCodec {

  @Override
  public String codecName() {
    return "PCMU";
  }

  @Override
  public int payloadType() {
    return 0;
  }

  @Override
  public int sampleRate() {
    return 8000;
  }

  @Override
  public short[] decode(byte[] payload) {
    short[] out = new short[payload.length];
    for (int i = 0; i < payload.length; i++) {
      out[i] = ulawToLinear(payload[i]);
    }
    return out;
  }

  @Override
  public byte[] encode(short[] pcm16) {
    byte[] out = new byte[pcm16.length];
    for (int i = 0; i < pcm16.length; i++) {
      out[i] = linearToUlaw(pcm16[i]);
    }
    return out;
  }

  private short ulawToLinear(byte ulaw) {
    int u = ~ulaw & 0xFF;
    int sign = u & 0x80;
    int exponent = (u >> 4) & 0x07;
    int mantissa = u & 0x0F;
    int sample = ((mantissa << 3) + 0x84) << exponent;
    sample -= 0x84;
    return (short) (sign != 0 ? -sample : sample);
  }

  private byte linearToUlaw(short sample) {
    final int BIAS = 0x84;
    final int CLIP = 32635;

    int pcm = sample;
    int sign = (pcm >> 8) & 0x80;
    if (sign != 0) {
      pcm = -pcm;
    }
    if (pcm > CLIP) {
      pcm = CLIP;
    }

    pcm += BIAS;

    int exponent = 7;
    for (int expMask = 0x4000; (pcm & expMask) == 0 && exponent > 0; exponent--, expMask >>= 1) {
    }

    int mantissa = (pcm >> (exponent + 3)) & 0x0F;
    int ulaw = ~(sign | (exponent << 4) | mantissa) & 0xFF;
    return (byte) ulaw;
  }
}