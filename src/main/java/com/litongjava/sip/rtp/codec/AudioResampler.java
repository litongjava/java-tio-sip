package com.litongjava.sip.rtp.codec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

import com.litongjava.media.MediaCodec;

/**
 * JNI Audio Resampler 封装
 *
 * 支持两种用法：
 * 1. 静态一次性调用：兼容旧代码
 * 2. 实例复用调用：适合连续流式音频
 */
public final class AudioResampler implements AutoCloseable {

  private static final int DEFAULT_CHANNELS = 1;
  private static final int DEFAULT_QUALITY = 5;
  private static final int DEFAULT_OPTIONS = 0;

  private final int channels;
  private final int srcRate;
  private final int dstRate;
  @SuppressWarnings("unused")
  private final int quality;
  @SuppressWarnings("unused")
  private final int options;

  private final Object lock = new Object();

  private long handle;
  private ByteBuffer inputBuffer;
  private ByteBuffer outputBuffer;

  public AudioResampler(int channels, int srcRate, int dstRate) {
    this(channels, srcRate, dstRate, DEFAULT_QUALITY, DEFAULT_OPTIONS);
  }

  public AudioResampler(int channels, int srcRate, int dstRate, int quality, int options) {
    if (channels <= 0) {
      throw new IllegalArgumentException("channels must be > 0");
    }
    if (srcRate <= 0) {
      throw new IllegalArgumentException("srcRate must be > 0");
    }
    if (dstRate <= 0) {
      throw new IllegalArgumentException("dstRate must be > 0");
    }
    if (quality < 0 || quality > 10) {
      throw new IllegalArgumentException("quality must be between 0 and 10");
    }

    this.channels = channels;
    this.srcRate = srcRate;
    this.dstRate = dstRate;
    this.quality = quality;
    this.options = options;

    this.handle = MediaCodec.createResampler(channels, srcRate, dstRate, quality, options);
    if (this.handle == 0) {
      throw new IllegalStateException("Failed to create resampler, channels=" + channels + ", srcRate=" + srcRate
          + ", dstRate=" + dstRate + ", quality=" + quality);
    }
  }

  /**
   * 兼容旧代码：默认单声道，一次性重采样。
   */
  public static short[] resample(short[] input, int srcRate, int dstRate) {
    return resample(input, DEFAULT_CHANNELS, srcRate, dstRate, DEFAULT_QUALITY, DEFAULT_OPTIONS);
  }

  /**
   * 兼容旧代码：可指定完整参数，一次性重采样。
   */
  public static short[] resample(short[] input, int channels, int srcRate, int dstRate, int quality, int options) {
    try (AudioResampler resampler = new AudioResampler(channels, srcRate, dstRate, quality, options)) {
      return resampler.resample(input);
    }
  }

  /**
   * 实例复用方式：适合连续流式音频。
   */
  public short[] resample(short[] input) {
    Objects.requireNonNull(input, "input");

    if (input.length == 0) {
      return new short[0];
    }
    if (srcRate == dstRate) {
      return input.clone();
    }
    if (input.length % channels != 0) {
      throw new IllegalArgumentException(
          "input length must be divisible by channels, length=" + input.length + ", channels=" + channels);
    }

    synchronized (lock) {
      ensureOpen();

      int inputSamplesPerChannel = input.length / channels;
      int outputSamplesPerChannel = MediaCodec.getResamplerExpectedOutputSamples(handle, inputSamplesPerChannel);
      if (outputSamplesPerChannel < 0) {
        throw new IllegalStateException("getResamplerExpectedOutputSamples failed, code=" + outputSamplesPerChannel);
      }

      int inputBytes = input.length * 2;
      int outputBytes = outputSamplesPerChannel * channels * 2;

      inputBuffer = ensureDirectBuffer(inputBuffer, inputBytes);
      outputBuffer = ensureDirectBuffer(outputBuffer, outputBytes);

      inputBuffer.clear();
      outputBuffer.clear();

      for (int i = 0; i < input.length; i++) {
        inputBuffer.putShort(i * 2, input[i]);
      }

      int actualOutputSamplesPerChannel = MediaCodec.resampleDirect(handle, inputBuffer, inputSamplesPerChannel,
          outputBuffer);
      if (actualOutputSamplesPerChannel < 0) {
        throw new IllegalStateException("resampleDirect failed, code=" + actualOutputSamplesPerChannel);
      }

      int totalOutputSamples = actualOutputSamplesPerChannel * channels;
      short[] out = new short[totalOutputSamples];
      for (int i = 0; i < totalOutputSamples; i++) {
        out[i] = outputBuffer.getShort(i * 2);
      }
      return out;
    }
  }

  public void reset() {
    synchronized (lock) {
      ensureOpen();
      int code = MediaCodec.resetResampler(handle);
      if (code < 0) {
        throw new IllegalStateException("resetResampler failed, code=" + code);
      }
    }
  }

  public int getChannels() {
    return channels;
  }

  public int getSrcRate() {
    return srcRate;
  }

  public int getDstRate() {
    return dstRate;
  }

  private void ensureOpen() {
    if (handle == 0) {
      throw new IllegalStateException("AudioResampler already closed");
    }
  }

  private static ByteBuffer ensureDirectBuffer(ByteBuffer buffer, int capacity) {
    if (buffer != null && buffer.capacity() >= capacity) {
      buffer.clear();
      buffer.order(ByteOrder.LITTLE_ENDIAN);
      return buffer;
    }
    return ByteBuffer.allocateDirect(capacity).order(ByteOrder.LITTLE_ENDIAN);
  }

  @Override
  public void close() {
    synchronized (lock) {
      if (handle != 0) {
        MediaCodec.destroyResampler(handle);
        handle = 0;
      }
      inputBuffer = null;
      outputBuffer = null;
    }
  }
}