package com.litongjava.sip.rtp.codec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.litongjava.media.MediaCodec;

public class PcmaCodec implements AudioCodec, AutoCloseable {

  private static final int CODEC_TYPE = MediaCodec.CODEC_PCMA;
  private static final int SAMPLE_RATE = 8000;
  private static final int CHANNELS = 1;
  private static final int BITRATE = 0;
  private static final int OPTIONS = 0;

  private final Object encodeLock = new Object();
  private final Object decodeLock = new Object();

  private long encoder;
  private long decoder;

  private ByteBuffer encodePcmBuffer;
  private ByteBuffer encodeOutBuffer;

  private ByteBuffer decodeInBuffer;
  private ByteBuffer decodePcmBuffer;

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
    return SAMPLE_RATE;
  }

  @Override
  public short[] decode(byte[] payload) {
    if (payload == null || payload.length == 0) {
      return new short[0];
    }

    synchronized (decodeLock) {
      ensureDecoder();

      int encodedLen = payload.length;
      int pcmSamplesCapacity = encodedLen;
      int pcmBytesCapacity = pcmSamplesCapacity * 2;

      decodeInBuffer = ensureDirectBuffer(decodeInBuffer, encodedLen, ByteOrder.BIG_ENDIAN);
      decodePcmBuffer = ensureDirectBuffer(decodePcmBuffer, pcmBytesCapacity, ByteOrder.LITTLE_ENDIAN);

      decodeInBuffer.clear();
      decodePcmBuffer.clear();

      decodeInBuffer.put(payload);

      int decodedSamples = MediaCodec.decodeDirect(decoder, decodeInBuffer, encodedLen, decodePcmBuffer);
      if (decodedSamples < 0) {
        throw new IllegalStateException("PCMA decodeDirect failed, code=" + decodedSamples);
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
      int encodedCapacity = pcmSamples;

      encodePcmBuffer = ensureDirectBuffer(encodePcmBuffer, pcmBytes, ByteOrder.LITTLE_ENDIAN);
      encodeOutBuffer = ensureDirectBuffer(encodeOutBuffer, encodedCapacity, ByteOrder.BIG_ENDIAN);

      encodePcmBuffer.clear();
      encodeOutBuffer.clear();

      for (int i = 0; i < pcmSamples; i++) {
        encodePcmBuffer.putShort(i * 2, pcm16[i]);
      }

      int encodedLen = MediaCodec.encodeDirect(encoder, encodePcmBuffer, pcmSamples, encodeOutBuffer);
      if (encodedLen < 0) {
        throw new IllegalStateException("PCMA encodeDirect failed, code=" + encodedLen);
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
    encoder = MediaCodec.createEncoder(CODEC_TYPE, SAMPLE_RATE, CHANNELS, BITRATE, OPTIONS);
    if (encoder == 0) {
      throw new IllegalStateException("Failed to create PCMA encoder");
    }
  }

  private void ensureDecoder() {
    if (decoder != 0) {
      return;
    }
    decoder = MediaCodec.createDecoder(CODEC_TYPE, SAMPLE_RATE, CHANNELS, BITRATE, OPTIONS);
    if (decoder == 0) {
      throw new IllegalStateException("Failed to create PCMA decoder");
    }
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
}