package com.litongjava.sip.rtp.codec;

public interface AudioCodec {

  String codecName();

  int payloadType();

  int sampleRate();

  short[] decode(byte[] payload);

  byte[] encode(short[] pcm16);
}