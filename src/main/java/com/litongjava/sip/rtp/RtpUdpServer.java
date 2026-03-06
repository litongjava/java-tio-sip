package com.litongjava.sip.rtp;

import java.net.SocketException;

import com.litongjava.tio.core.udp.UdpServer;
import com.litongjava.tio.core.udp.UdpServerConf;

public class RtpUdpServer {
  private final int port;
  private UdpServer udpServer;

  public RtpUdpServer(int port) {
    this.port = port;
  }

  public void start() throws SocketException {
    UdpServerConf conf = new UdpServerConf(port, new RtpEchoUdpHandler(), 5000);
    this.udpServer = new UdpServer(conf);
    this.udpServer.start();
  }

  public void stop() {
    if (udpServer != null) {
      udpServer.stop();
    }
  }

  public int port() {
    return port;
  }
}