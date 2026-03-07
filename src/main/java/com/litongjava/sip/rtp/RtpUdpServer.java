package com.litongjava.sip.rtp;

import java.net.SocketException;

import com.litongjava.sip.rtp.media.MediaProcessor;
import com.litongjava.sip.rtp.server.RtpUdpHandler;
import com.litongjava.sip.server.session.CallSessionManager;
import com.litongjava.tio.core.udp.UdpServer;
import com.litongjava.tio.core.udp.UdpServerConf;

public class RtpUdpServer {

  private final int port;
  private final CallSessionManager sessionManager;
  private UdpServer udpServer;

  public RtpUdpServer(int port, CallSessionManager sessionManager) {
    this.port = port;
    this.sessionManager = sessionManager;
  }

  public void start() throws SocketException {
    RtpUdpHandler udpHandler = new RtpUdpHandler(port, sessionManager);
    start(udpHandler);
  }

  public void start(MediaProcessor mediaProcessor) throws SocketException {
    RtpUdpHandler udpHandler = new RtpUdpHandler(port, sessionManager, mediaProcessor);
    start(udpHandler);
  }

  public void start(RtpUdpHandler udpHandler) throws SocketException {
    UdpServerConf conf = new UdpServerConf(port, udpHandler, 5000);
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