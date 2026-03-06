package com.litongjava.sip.rtp;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

import com.litongjava.tio.core.Node;
import com.litongjava.tio.core.udp.UdpPacket;
import com.litongjava.tio.core.udp.intf.UdpHandler;

public class RtpEchoUdpHandler implements UdpHandler {
  @Override
  public void handler(UdpPacket udpPacket, DatagramSocket datagramSocket) {
    byte[] data = udpPacket.getData();
    Node remote = udpPacket.getRemote();

    // 先做最简单：原包回显（用于验证链路）
    DatagramPacket resp = new DatagramPacket(
        data, data.length, new InetSocketAddress(remote.getIp(), remote.getPort())
    );
    try {
      datagramSocket.send(resp);
    } catch (Exception e) {
      // 生产里用日志
      e.printStackTrace();
    }
  }
}