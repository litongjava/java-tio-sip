package com.litongjava.sip.rtp.codec;

public class PcmaCodec implements AudioCodec {

  @Override
  public String codecName() {
    return "PCMA";
  }

  @Override
  public int payloadType() {
    return 8;
  }

  @Override
  public int sampleRate() {
    return 8000;
  }

  @Override
  public short[] decode(byte[] payload) {
    short[] out = new short[payload.length];
    for (int i = 0; i < payload.length; i++) {
      out[i] = alawToLinear(payload[i]);
    }
    return out;
  }

  @Override
  public byte[] encode(short[] pcm16) {
    byte[] out = new byte[pcm16.length];
    for (int i = 0; i < pcm16.length; i++) {
      out[i] = linearToAlaw(pcm16[i]);
    }
    return out;
  }

  private short alawToLinear(byte alaw) {
    int a = alaw ^ 0x55;
    int sign = a & 0x80;
    int exponent = (a & 0x70) >> 4;
    int mantissa = a & 0x0F;

    int sample;
    if (exponent == 0) {
      sample = (mantissa << 4) + 8;
    } else {
      sample = ((mantissa << 4) + 0x108) << (exponent - 1);
    }

    return (short) (sign == 0 ? sample : -sample);
  }

  private byte linearToAlaw(short sample) {
    int pcm = sample;
    int sign;
    int exponent;
    int mantissa;
    int alaw;

    sign = (pcm & 0x8000) >> 8;
    if (sign != 0) {
      pcm = -pcm;
    }

    if (pcm > 32767) {
      pcm = 32767;
    }

    if (pcm >= 256) {
      exponent = 7;
      for (int expMask = 0x4000; (pcm & expMask) == 0 && exponent > 0; exponent--, expMask >>= 1) {
      }
      mantissa = (pcm >> (exponent + 3)) & 0x0F;
      alaw = (exponent << 4) | mantissa;
    } else {
      alaw = pcm >> 4;
    }

    alaw ^= (sign ^ 0x55);
    return (byte) alaw;
  }
}