package com.litongjava.sip.client;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class SipTcpClient {
  public static void main(String[] args) throws Exception {
    String host = "192.168.3.219";
    int port = 5060;

    try (Socket s = new Socket(host, port)) {
      s.setSoTimeout(5000);
      OutputStream out = s.getOutputStream();
      InputStream in = s.getInputStream();

      String invite =
          "INVITE sip:1001@" + host + ":" + port + " SIP/2.0\r\n" +
          "Via: SIP/2.0/TCP 192.168.3.219:40000;branch=z9hG4bK-1\r\n" +
          "From: <sip:u1@x>;tag=111\r\n" +
          "To: <sip:1001@" + host + ">\r\n" +
          "Call-ID: 123456\r\n" +
          "CSeq: 1 INVITE\r\n" +
          "Contact: <sip:u1@192.168.3.219:40000>\r\n" +
          "Content-Length: 0\r\n\r\n";

      out.write(invite.getBytes(StandardCharsets.US_ASCII));
      out.flush();

      byte[] buf = new byte[8192];
      int n = in.read(buf);
      System.out.println(new String(buf, 0, n, StandardCharsets.US_ASCII));
    }
  }
}