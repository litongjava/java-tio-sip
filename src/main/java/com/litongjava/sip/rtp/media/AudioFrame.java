package com.litongjava.sip.rtp.media;

public class AudioFrame {

  private short[] samples;
  private int sampleRate;
  private int channels;
  private long rtpTimestamp;

  public AudioFrame() {
  }

  public AudioFrame(short[] samples, int sampleRate, int channels, long rtpTimestamp) {
    this.samples = samples;
    this.sampleRate = sampleRate;
    this.channels = channels;
    this.rtpTimestamp = rtpTimestamp;
  }

  public short[] getSamples() {
    return samples;
  }

  public void setSamples(short[] samples) {
    this.samples = samples;
  }

  public int getSampleRate() {
    return sampleRate;
  }

  public void setSampleRate(int sampleRate) {
    this.sampleRate = sampleRate;
  }

  public int getChannels() {
    return channels;
  }

  public void setChannels(int channels) {
    this.channels = channels;
  }

  public long getRtpTimestamp() {
    return rtpTimestamp;
  }

  public void setRtpTimestamp(long rtpTimestamp) {
    this.rtpTimestamp = rtpTimestamp;
  }

  public int sampleCount() {
    return samples == null ? 0 : samples.length;
  }
}