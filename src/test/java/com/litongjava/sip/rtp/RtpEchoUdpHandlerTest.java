package com.litongjava.sip.rtp;

import java.net.SocketException;

import org.junit.Test;

import com.litongjava.tio.core.udp.UdpServer;
import com.litongjava.tio.core.udp.UdpServerConf;

public class RtpEchoUdpHandlerTest {

  @Test
  public void test() {
    UdpServerConf udpServerConf = new UdpServerConf(30000, new RtpEchoUdpHandler(), 5000);
    UdpServer udpServer;
    try {
      udpServer = new UdpServer(udpServerConf);
      udpServer.start();
    } catch (SocketException e) {
      e.printStackTrace();
    }
    
  }

}
