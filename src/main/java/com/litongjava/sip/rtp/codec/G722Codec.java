package com.litongjava.sip.rtp.codec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.litongjava.media.MediaCodec;

/**
 * 基于 java-media-codec JNI 的 G.722 codec。
 *
 * 设计说明：
 * 1. 一个 G722Codec 实例对应一个媒体流/会话，不要跨多个通话共享。
 * 2. encoder / decoder 都是有状态的 native 对象。
 * 3. encode / decode 分别加锁，避免同一实例并发访问同一个 native handle。
 * 4. 内部使用 DirectByteBuffer，满足 JNI zero-copy 要求。
 */
public class G722Codec implements AudioCodec, AutoCloseable {

  private static final int CODEC_TYPE = MediaCodec.CODEC_G722;
  private static final int DEFAULT_SAMPLE_RATE = 16000;
  private static final int DEFAULT_CHANNELS = 1;
  private static final int DEFAULT_BITRATE = 64000;
  private static final int DEFAULT_OPTIONS = 0;

  private final int sampleRate;
  private final int channels;
  private final int bitrate;
  private final int options;

  private final Object encodeLock = new Object();
  private final Object decodeLock = new Object();

  private long encoder;
  private long decoder;

  private ByteBuffer encodePcmBuffer;
  private ByteBuffer encodeOutBuffer;

  private ByteBuffer decodeInBuffer;
  private ByteBuffer decodePcmBuffer;

  public G722Codec() {
    this(DEFAULT_SAMPLE_RATE, DEFAULT_CHANNELS, DEFAULT_BITRATE, DEFAULT_OPTIONS);
  }

  public G722Codec(int bitrate) {
    this(DEFAULT_SAMPLE_RATE, DEFAULT_CHANNELS, bitrate, DEFAULT_OPTIONS);
  }

  public G722Codec(int sampleRate, int channels, int bitrate, int options) {
    if (sampleRate != 16000) {
      throw new IllegalArgumentException("G722 PCM sampleRate must be 16000, but got: " + sampleRate);
    }
    if (channels != 1) {
      throw new IllegalArgumentException("G722 currently only supports mono, but got channels=" + channels);
    }
    if (bitrate != 64000 && bitrate != 56000 && bitrate != 48000) {
      throw new IllegalArgumentException("G722 bitrate must be one of 64000 / 56000 / 48000, but got: " + bitrate);
    }

    this.sampleRate = sampleRate;
    this.channels = channels;
    this.bitrate = bitrate;
    this.options = options;
  }

  @Override
  public String codecName() {
    return "G722";
  }

  @Override
  public int payloadType() {
    return 9;
  }

  /**
   * RTP/SDP 中 G722 常写为 G722/8000，
   * 这里返回媒体处理层的 PCM 采样率：16000。
   */
  @Override
  public int sampleRate() {
    return sampleRate;
  }

  @Override
  public short[] decode(byte[] payload) {
    if (payload == null || payload.length == 0) {
      return new short[0];
    }

    synchronized (decodeLock) {
      ensureDecoder();

      int encodedLen = payload.length;
      int pcmSamplesCapacity = estimateDecodedSamples(encodedLen);
      int pcmBytesCapacity = pcmSamplesCapacity * 2;

      decodeInBuffer = ensureDirectBuffer(decodeInBuffer, encodedLen, ByteOrder.BIG_ENDIAN);
      decodePcmBuffer = ensureDirectBuffer(decodePcmBuffer, pcmBytesCapacity, ByteOrder.LITTLE_ENDIAN);

      decodeInBuffer.clear();
      decodePcmBuffer.clear();

      decodeInBuffer.put(payload);

      int decodedSamples = MediaCodec.decodeDirect(decoder, decodeInBuffer, encodedLen, decodePcmBuffer);
      if (decodedSamples < 0) {
        throw new IllegalStateException("G722 decodeDirect failed, code=" + decodedSamples);
      }

      short[] out = new short[decodedSamples];
      for (int i = 0; i < decodedSamples; i++) {
        out[i] = decodePcmBuffer.getShort(i * 2);
      }
      return out;
    }
  }

  @Override
  public byte[] encode(short[] pcm16) {
    if (pcm16 == null || pcm16.length == 0) {
      return new byte[0];
    }

    synchronized (encodeLock) {
      ensureEncoder();

      int pcmSamples = pcm16.length;
      int pcmBytes = pcmSamples * 2;
      int encodedCapacity = estimateEncodedBytes(pcmSamples);

      encodePcmBuffer = ensureDirectBuffer(encodePcmBuffer, pcmBytes, ByteOrder.LITTLE_ENDIAN);
      encodeOutBuffer = ensureDirectBuffer(encodeOutBuffer, encodedCapacity, ByteOrder.BIG_ENDIAN);

      encodePcmBuffer.clear();
      encodeOutBuffer.clear();

      for (int i = 0; i < pcmSamples; i++) {
        encodePcmBuffer.putShort(i * 2, pcm16[i]);
      }

      int encodedLen = MediaCodec.encodeDirect(encoder, encodePcmBuffer, pcmSamples, encodeOutBuffer);
      if (encodedLen < 0) {
        throw new IllegalStateException("G722 encodeDirect failed, code=" + encodedLen);
      }

      byte[] out = new byte[encodedLen];
      for (int i = 0; i < encodedLen; i++) {
        out[i] = encodeOutBuffer.get(i);
      }
      return out;
    }
  }

  private void ensureEncoder() {
    if (encoder != 0) {
      return;
    }
    encoder = MediaCodec.createEncoder(CODEC_TYPE, sampleRate, channels, bitrate, options);
    if (encoder == 0) {
      throw new IllegalStateException("Failed to create G722 encoder");
    }
  }

  private void ensureDecoder() {
    if (decoder != 0) {
      return;
    }
    decoder = MediaCodec.createDecoder(CODEC_TYPE, sampleRate, channels, bitrate, options);
    if (decoder == 0) {
      throw new IllegalStateException("Failed to create G722 decoder");
    }
  }

  /**
   * 估算编码后字节数。
   *
   * 公式：
   * encodedBytes = pcmSamples * bitrate / 8 / sampleRate
   *
   * 再额外预留少量冗余，避免 native 写满。
   */
  private int estimateEncodedBytes(int pcmSamples) {
    long bytes = ((long) pcmSamples * bitrate + (sampleRate * 8L - 1)) / (sampleRate * 8L);
    return (int) Math.max(bytes + 16, 64);
  }

  /**
   * 估算解码后的 PCM sample 数。
   *
   * 公式：
   * pcmSamples = encodedBytes * 8 * sampleRate / bitrate
   *
   * 再额外预留少量冗余。
   */
  private int estimateDecodedSamples(int encodedBytes) {
    long samples = ((long) encodedBytes * 8L * sampleRate + bitrate - 1) / bitrate;
    return (int) Math.max(samples + 16, 160);
  }

  private static ByteBuffer ensureDirectBuffer(ByteBuffer buffer, int capacity, ByteOrder order) {
    if (buffer != null && buffer.capacity() >= capacity) {
      buffer.clear();
      buffer.order(order);
      return buffer;
    }
    return ByteBuffer.allocateDirect(capacity).order(order);
  }

  @Override
  public void close() {
    synchronized (encodeLock) {
      if (encoder != 0) {
        MediaCodec.destroyEncoder(encoder);
        encoder = 0;
      }
      encodePcmBuffer = null;
      encodeOutBuffer = null;
    }

    synchronized (decodeLock) {
      if (decoder != 0) {
        MediaCodec.destroyDecoder(decoder);
        decoder = 0;
      }
      decodeInBuffer = null;
      decodePcmBuffer = null;
    }
  }

  @Override
  protected void finalize() throws Throwable {
    try {
      close();
    } finally {
      super.finalize();
    }
  }

  @Override
  public String toString() {
    return "G722Codec{" + "sampleRate=" + sampleRate + ", channels=" + channels + ", bitrate=" + bitrate + ", options="
        + options + '}';
  }
}