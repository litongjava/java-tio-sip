package com.litongjava.sip.server.handler;

import java.nio.ByteBuffer;

import com.litongjava.aio.ByteBufferPacket;
import com.litongjava.aio.Packet;
import com.litongjava.sip.server.SipDecoder;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.TioConfig;
import com.litongjava.tio.core.exception.TioDecodeException;
import com.litongjava.tio.server.intf.ServerAioHandler;

public class SipTcpServerHandler implements ServerAioHandler {

  @Override
  public Packet decode(ByteBuffer buffer, int limit, int position, int readableLength, ChannelContext ctx)
      throws TioDecodeException {

    if (readableLength <= 0) {
      return null;
    }
    ByteBufferPacket p = SipDecoder.decode(buffer, readableLength, ctx);
    return p;
  }

  @Override
  public ByteBuffer encode(Packet packet, TioConfig tioConfig, ChannelContext ctx) {
    ByteBufferPacket p = (ByteBufferPacket) packet;
    ByteBuffer bb = p.getByteBuffer();
    // 直接返回一份可读 ByteBuffer（注意 position/limit）
    if (bb.position() != 0)
      bb.rewind();
    return bb;
  }

  @Override
  public void handler(Packet packet, ChannelContext ctx) throws Exception {
    // 在这里做 SIP 业务：INVITE -> 200OK（带 SDP）
    // 下面第 3.5 给你完整示例
  }
}