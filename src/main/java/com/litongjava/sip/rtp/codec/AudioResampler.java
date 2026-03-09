package com.litongjava.sip.rtp.codec;

import java.util.Arrays;
import java.util.Objects;

/**
 * 简单线性插值重采样
 * 适合当前语音链路对接，依赖少、易落地
 */
public final class AudioResampler {

  private AudioResampler() {
  }

  public static short[] resample(short[] input, int srcRate, int dstRate) {
    Objects.requireNonNull(input, "input");
    if (input.length == 0) {
      return new short[0];
    }
    if (srcRate <= 0 || dstRate <= 0) {
      throw new IllegalArgumentException("sample rate must be > 0");
    }
    if (srcRate == dstRate) {
      return Arrays.copyOf(input, input.length);
    }

    double ratio = (double) dstRate / (double) srcRate;
    int outputLength = Math.max(1, (int) Math.round(input.length * ratio));
    short[] output = new short[outputLength];

    for (int i = 0; i < outputLength; i++) {
      double srcIndex = i / ratio;
      int left = (int) Math.floor(srcIndex);
      int right = Math.min(left + 1, input.length - 1);
      double frac = srcIndex - left;

      double sample = input[left] * (1.0 - frac) + input[right] * frac;
      output[i] = clampToShort(sample);
    }
    return output;
  }

  private static short clampToShort(double v) {
    if (v > Short.MAX_VALUE) {
      return Short.MAX_VALUE;
    }
    if (v < Short.MIN_VALUE) {
      return Short.MIN_VALUE;
    }
    return (short) Math.round(v);
  }
}