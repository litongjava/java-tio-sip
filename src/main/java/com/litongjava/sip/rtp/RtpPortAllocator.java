package com.litongjava.sip.rtp;

import java.io.IOException;
import java.net.DatagramSocket;
import java.util.BitSet;

public class RtpPortAllocator {

  private final int start;
  private final int end;

  private final BitSet used;

  public RtpPortAllocator() {
    this(30000, 40000);
  }

  public RtpPortAllocator(int start, int end) {
    this.start = start;
    this.end = end;
    this.used = new BitSet(end - start + 1);
  }

  public synchronized int allocate() {
    for (int p = start; p <= end; p++) {
      int idx = p - start;

      if (used.get(idx)) {
        continue;
      }

      if (canBind(p)) {
        used.set(idx, true);
        return p;
      }
    }

    throw new IllegalStateException("No available RTP port in range " + start + "-" + end);
  }

  public synchronized void release(int port) {
    if (port < start || port > end) {
      return;
    }
    used.clear(port - start);
  }

  private boolean canBind(int port) {
    try (DatagramSocket ignored = new DatagramSocket(port)) {
      ignored.setReuseAddress(false);
      return true;
    } catch (IOException e) {
      return false;
    }
  }
}