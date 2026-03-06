package com.litongjava.sip.client;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;

public class RtpUdpEchoClient {
  public static void main(String[] args) throws Exception {
    String host = "192.168.3.219";
    int port = 30000; // 换成服务端分配的 RTP 端口

    byte[] rtp = buildFakeRtpPacket();

    try (DatagramSocket s = new DatagramSocket()) {
      DatagramPacket p = new DatagramPacket(rtp, rtp.length, InetAddress.getByName(host), port);
      s.send(p);

      byte[] buf = new byte[2048];
      DatagramPacket resp = new DatagramPacket(buf, buf.length);
      s.setSoTimeout(2000);
      s.receive(resp);

      System.out.println("echo len=" + resp.getLength());
      System.out.println(Arrays.toString(Arrays.copyOf(resp.getData(), resp.getLength())));
    }
  }

  private static byte[] buildFakeRtpPacket() {
    // 12字节 RTP header + 160字节 payload（20ms PCMU）
    byte[] b = new byte[12 + 160];
    b[0] = (byte) 0x80;  // V=2
    b[1] = (byte) 0x00;  // PT=0 PCMU
    b[2] = 0x00; b[3] = 0x01; // seq=1
    b[4] = 0x00; b[5] = 0x00; b[6] = 0x00; b[7] = (byte) 0xA0; // ts=160
    b[8] = 0x11; b[9] = 0x22; b[10] = 0x33; b[11] = 0x44; // ssrc
    // payload 随便填
    for (int i = 12; i < b.length; i++) b[i] = (byte) 0xFF;
    return b;
  }
}