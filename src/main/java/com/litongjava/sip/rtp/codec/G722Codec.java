package com.litongjava.sip.rtp.codec;

public class G722Codec implements AudioCodec {

  @Override
  public String codecName() {
    return "G722";
  }

  @Override
  public int payloadType() {
    return 9;
  }

  /**
   * RTP/SDP 里 G722 常见写法是 G722/8000，
   * 但这里返回的是媒体处理层使用的 PCM 采样率。
   */
  @Override
  public int sampleRate() {
    return 16000;
  }

  @Override
  public short[] decode(byte[] payload) {
    throw new UnsupportedOperationException(
        "G722 decode is not implemented yet. Please replace this class with your actual G722 decoder implementation.");
  }

  @Override
  public byte[] encode(short[] pcm16) {
    throw new UnsupportedOperationException(
        "G722 encode is not implemented yet. Please replace this class with your actual G722 encoder implementation.");
  }
}