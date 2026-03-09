package com.litongjava.sip.buffer;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单生产者 单消费者 ring buffer
 * 无锁实现
 */
public class AudioRingBuffer {

  private final short[] buffer;
  private final int capacity;

  private final AtomicInteger writePos = new AtomicInteger(0);
  private final AtomicInteger readPos = new AtomicInteger(0);

  public AudioRingBuffer(int capacity) {
    this.capacity = capacity;
    this.buffer = new short[capacity];
  }

  /**
   * 写入音频
   */
  public void write(short[] samples) {

    int w = writePos.get();
    int r = readPos.get();

    for (short s : samples) {

      int next = (w + 1) % capacity;

      if (next == r) {
        // buffer满 → 丢掉最旧
        r = (r + 1) % capacity;
        readPos.set(r);
      }

      buffer[w] = s;

      w = next;
    }

    writePos.set(w);
  }

  /**
   * 读取音频
   */
  public int read(short[] out, int len) {

    int r = readPos.get();
    int w = writePos.get();

    if (r == w) {
      return 0;
    }

    int count = 0;

    while (r != w && count < len) {

      out[count++] = buffer[r];

      r = (r + 1) % capacity;
    }

    readPos.set(r);

    return count;
  }

  public void clear() {
    readPos.set(0);
    writePos.set(0);
  }

}