package com.litongjava.sip.rtp;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

import com.litongjava.tio.core.Node;
import com.litongjava.tio.core.udp.UdpPacket;
import com.litongjava.tio.core.udp.intf.UdpHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RtpEchoUdpHandler implements UdpHandler {
  @Override
  public void handler(UdpPacket udpPacket, DatagramSocket datagramSocket) {
    byte[] data = udpPacket.getData();
    Node remote = udpPacket.getRemote();

    // 先做最简单：原包回显（用于验证链路）
    InetSocketAddress address = new InetSocketAddress(remote.getIp(), remote.getPort());
    DatagramPacket resp = new DatagramPacket(data, data.length, address);
    try {
      datagramSocket.send(resp);
    } catch (Exception e) {
      log.error(e.getMessage(), e);
    }
  }
}