下面我按你当前的技术栈（tio/tio-boot）给你一套“能落地”的方案，覆盖：

1. 你现在这个 UDP Server 是否支持连续端口监听（以及业务是否真的需要）
2. 用 tio 写一个最小可运行的 SIP(TCP 5060) + RTP(UDP 动态端口 30000-40000) 服务骨架
3. 写一个 sip-client / rtp-client 先跑通 echo（最小链路验证）

---

## 1）你这个 tio UDP Server 是否支持“连续端口监听”

你贴的 `UdpServerConf` 构造函数是：

```java
public UdpServerConf(int port, UdpHandler udpHandler, int timeout) {
  super(timeout);
  this.setUdpHandler(udpHandler);
  this.setServerNode(new Node(null, port));
}
```

结论很明确：**一个 `UdpServer` 实例只能 bind 一个端口**（`serverNode` 只有一个 port），不支持“端口范围一次性监听”。

### 业务是否需要监听整个 30000-40000？

通常“不需要”。

RTP 的正确姿势是：

* 每通电话在 30000-40000 内 **分配一个端口**
* Java 在这个端口上 **bind 一个 DatagramSocket / UdpServer**
* 只监听这个端口即可
* 呼叫结束就 close，端口回收

也就是说：**动态分配一个端口并监听它**，而不是一口气监听 10001 个端口。

如果你要“全范围监听”，只有两种情况：

* 你无法提前知道对端会把 RTP 发到你哪个端口（但 SIP/SDP 本来就会告诉对端端口，所以不成立）
* 你要做非常特殊的被动探测/抓包类业务（正常 VoIP 不这么做）

因此：用 tio 做 RTP 动态端口是可行的，方式是“每个呼叫启动一个 UDP 监听实例”或“用 DatagramSocket 直接 bind”。

---

## 2）用 tio 做 SIP(TCP) + RTP(UDP 动态端口) 的核心策略

### 2.1 SIP：TCP 5060

* 你已经有 tio TCP Server 模板
* SIP 的解码要从“读多少算一个包”升级为：
  **以 `\r\n\r\n` 分隔头部，再根据 `Content-Length` 读取 body**

否则 INVITE/200OK 这种带 SDP 的消息会被拆包粘包搞坏。

### 2.2 RTP：UDP 30000-40000 动态端口

* 收到 INVITE 后分配一个端口，例如 31234
* 启动 UDP 监听 `0.0.0.0:31234`
* 在 SDP answer 里写：

  * `c=IN IP4 192.168.3.219`
  * `m=audio 31234 RTP/AVP 0`
  * `a=rtpmap:0 PCMU/8000`
* RTP echo：收到 RTP 包后，直接把 payload 原样发回（先跑通链路）

---

## 3）最小可运行代码骨架（建议直接复制到工程）

下面我给你分文件给出骨架，尽量不依赖第三方 SIP/RTP 库。

### 3.1 RTP 端口分配器（30000-40000）

```java
package com.litongjava.sip.rtp;

import java.io.IOException;
import java.net.DatagramSocket;
import java.util.BitSet;

public class RtpPortAllocator {
  private final int start = 30000;
  private final int end = 40000;
  private final BitSet used = new BitSet(end - start + 1);

  // 简单同步分配：顺序扫 + 试 bind
  public synchronized int allocate() {
    for (int p = start; p <= end; p++) {
      int idx = p - start;
      if (used.get(idx)) continue;

      if (canBind(p)) {
        used.set(idx, true);
        return p;
      }
    }
    throw new IllegalStateException("No available RTP port in range 30000-40000");
  }

  public synchronized void release(int port) {
    if (port < start || port > end) return;
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
```

---

### 3.2 RTP UDP Echo Server（tio UdpServer）

你的 tio UDP handler 已经可以 echo 文本，这里改成 echo 二进制 RTP 包。

```java
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
```

启动一个 RTP 监听实例：

```java
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
```

> 你后面接入 Gemini 时，把 `RtpEchoUdpHandler` 的 “原包回显” 换成：
> PCMU → PCM16(8k) → 16k → Gemini，再把 Gemini 输出回注到 RTP。

---

### 3.3 SIP Packet（建议用 ByteBufferPacket）

你给的 `ByteBufferPacket` 很适合做“粘包缓存 + decode”。

一个最小 SIP Packet：

```java
package com.litongjava.sip.sip;

import com.litongjava.aio.ByteBufferPacket;

public class SipPacket extends ByteBufferPacket {
  private static final long serialVersionUID = 1L;
  public SipPacket() {}
}
```

---

### 3.4 SIP 解码：按 `\r\n\r\n` + `Content-Length` 切包（核心）

下面是一个可用的 decode 思路：把 socket 的数据累积到 session buffer 里再切包。
tio 的 handler `decode` 里拿到的是本次 buffer 的可读片段，你需要在 `ChannelContext` 上挂一个累积 ByteBuffer。

给你一个最小实现（思路清晰，直接用）：

```java
package com.litongjava.sip.sip;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import com.litongjava.aio.ByteBufferPacket;
import com.litongjava.aio.Packet;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.TioConfig;
import com.litongjava.tio.core.exception.TioDecodeException;
import com.litongjava.tio.server.intf.ServerAioHandler;

public class SipTcpServerHandler implements ServerAioHandler {

  private static final String ATTR_BUF = "sip_acc_buf";

  @Override
  public Packet decode(ByteBuffer buffer, int limit, int position, int readableLength, ChannelContext ctx)
      throws TioDecodeException {

    if (readableLength <= 0) return null;

    // 取出累积 buffer
    ByteBuffer acc = (ByteBuffer) ctx.getAttribute(ATTR_BUF);
    if (acc == null) {
      acc = ByteBuffer.allocate(1024 * 64);
      ctx.setAttribute(ATTR_BUF, acc);
    }

    // 追加本次数据
    byte[] chunk = new byte[readableLength];
    buffer.get(chunk);
    if (acc.remaining() < chunk.length) {
      // 简单扩容
      ByteBuffer bigger = ByteBuffer.allocate(acc.capacity() + 1024 * 64);
      acc.flip();
      bigger.put(acc);
      acc = bigger;
      ctx.setAttribute(ATTR_BUF, acc);
    }
    acc.put(chunk);

    // 尝试切出完整 SIP 消息
    acc.flip();
    int msgLen = tryParseOneSipMessageLength(acc);
    if (msgLen <= 0) {
      acc.compact();
      return null;
    }

    byte[] msg = new byte[msgLen];
    acc.get(msg);
    acc.compact();

    ByteBufferPacket p = new ByteBufferPacket(ByteBuffer.wrap(msg));
    return p;
  }

  // 返回完整 SIP message 的字节长度；不够则返回 -1
  private int tryParseOneSipMessageLength(ByteBuffer acc) {
    int startPos = acc.position();
    int limit = acc.limit();

    // 找 \r\n\r\n
    int headerEnd = indexOf(acc, "\r\n\r\n".getBytes(StandardCharsets.US_ASCII), startPos, limit);
    if (headerEnd < 0) return -1;

    int headerLen = headerEnd + 4 - startPos;
    String headers = sliceToString(acc, startPos, headerEnd + 4);

    int contentLength = parseContentLength(headers);
    int totalLen = headerLen + contentLength;

    // 判断 body 是否够
    if ((limit - startPos) < totalLen) return -1;
    return totalLen;
  }

  private int parseContentLength(String headers) {
    // 简单解析 Content-Length
    String[] lines = headers.split("\r\n");
    for (String line : lines) {
      int idx = line.indexOf(':');
      if (idx <= 0) continue;
      String k = line.substring(0, idx).trim();
      if ("Content-Length".equalsIgnoreCase(k)) {
        String v = line.substring(idx + 1).trim();
        try { return Integer.parseInt(v); } catch (Exception ignore) {}
      }
    }
    return 0;
  }

  private int indexOf(ByteBuffer buf, byte[] pat, int from, int to) {
    for (int i = from; i <= to - pat.length; i++) {
      boolean ok = true;
      for (int j = 0; j < pat.length; j++) {
        if (buf.get(i + j) != pat[j]) { ok = false; break; }
      }
      if (ok) return i;
    }
    return -1;
  }

  private String sliceToString(ByteBuffer buf, int from, int to) {
    byte[] b = new byte[to - from];
    for (int i = 0; i < b.length; i++) {
      b[i] = buf.get(from + i);
    }
    return new String(b, StandardCharsets.US_ASCII);
  }

  @Override
  public ByteBuffer encode(Packet packet, TioConfig tioConfig, ChannelContext ctx) {
    ByteBufferPacket p = (ByteBufferPacket) packet;
    ByteBuffer bb = p.getByteBuffer();
    // 直接返回一份可读 ByteBuffer（注意 position/limit）
    if (bb.position() != 0) bb.rewind();
    return bb;
  }

  @Override
  public void handler(Packet packet, ChannelContext ctx) throws Exception {
    // 在这里做 SIP 业务：INVITE -> 200OK（带 SDP）
    // 下面第 3.5 给你完整示例
  }
}
```

---

### 3.5 SIP handler：INVITE → 200 OK（PCMU only）+ 启动 RTP UDP 端口

这是最小能跑通“对端接起 + RTP echo”的关键。

```java
package com.litongjava.sip.sip;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.litongjava.aio.ByteBufferPacket;
import com.litongjava.aio.Packet;
import com.litongjava.sip.rtp.RtpPortAllocator;
import com.litongjava.sip.rtp.RtpUdpServer;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.Tio;
import com.litongjava.tio.server.intf.ServerAioHandler;

public class SipInviteOnlyHandler implements ServerAioHandler {

  private final String localIp = "192.168.3.219";
  private final RtpPortAllocator allocator = new RtpPortAllocator();

  // 一个 SIP TCP 连接对应一个 RTP server（简单起见）
  private final Map<String, RtpUdpServer> rtpByConn = new ConcurrentHashMap<>();

  @Override
  public Packet decode(ByteBuffer buffer, int limit, int position, int readableLength, ChannelContext channelContext)
      throws Exception {
    // 直接复用上面 SipTcpServerHandler 的 decode（你可以继承/组合）
    throw new UnsupportedOperationException("use SipTcpServerHandler.decode");
  }

  @Override
  public ByteBuffer encode(Packet packet, com.litongjava.tio.core.TioConfig tioConfig, ChannelContext channelContext) {
    ByteBufferPacket p = (ByteBufferPacket) packet;
    ByteBuffer bb = p.getByteBuffer();
    if (bb.position() != 0) bb.rewind();
    return bb;
  }

  @Override
  public void handler(Packet packet, ChannelContext ctx) throws Exception {
    ByteBufferPacket p = (ByteBufferPacket) packet;
    ByteBuffer bb = p.getByteBuffer();
    byte[] msgBytes = new byte[bb.remaining()];
    bb.get(msgBytes);
    String sip = new String(msgBytes, StandardCharsets.US_ASCII);

    if (sip.startsWith("INVITE ")) {
      int rtpPort = allocator.allocate();
      RtpUdpServer rtpServer = new RtpUdpServer(rtpPort);
      rtpServer.start();
      rtpByConn.put(ctx.getId(), rtpServer);

      String resp = build200OkForInvite(sip, localIp, rtpPort);
      Tio.send(ctx, new ByteBufferPacket(ByteBuffer.wrap(resp.getBytes(StandardCharsets.US_ASCII))));
      return;
    }

    // 最小链路：ACK 不处理也能测 RTP，但建议至少识别 ACK/ BYE
    if (sip.startsWith("BYE ")) {
      closeRtp(ctx);
      String resp = buildSimpleResponse(sip, 200, "OK");
      Tio.send(ctx, new ByteBufferPacket(ByteBuffer.wrap(resp.getBytes(StandardCharsets.US_ASCII))));
      return;
    }

    // 其它先 200 OK（调试用）
    String resp = buildSimpleResponse(sip, 200, "OK");
    Tio.send(ctx, new ByteBufferPacket(ByteBuffer.wrap(resp.getBytes(StandardCharsets.US_ASCII))));
  }

  private void closeRtp(ChannelContext ctx) {
    RtpUdpServer s = rtpByConn.remove(ctx.getId());
    if (s != null) {
      s.stop();
      allocator.release(s.port());
    }
  }

  // 只做示例：真实项目要正确解析 Via/From/To/Call-ID/CSeq/Contact 等并回填 tag
  private String build200OkForInvite(String invite, String ip, int rtpPort) {
    String via = header(invite, "Via");
    String from = header(invite, "From");
    String to = header(invite, "To");
    String callId = header(invite, "Call-ID");
    String cseq = header(invite, "CSeq");

    // To 必须带 tag（最小实现随便生成一个）
    if (!to.toLowerCase().contains("tag=")) {
      to = to + ";tag=java1234";
    }

    String sdp =
        "v=0\r\n" +
        "o=- 1 1 IN IP4 " + ip + "\r\n" +
        "s=JavaSip\r\n" +
        "c=IN IP4 " + ip + "\r\n" +
        "t=0 0\r\n" +
        "m=audio " + rtpPort + " RTP/AVP 0\r\n" +
        "a=rtpmap:0 PCMU/8000\r\n" +
        "a=ptime:20\r\n" +
        "a=sendrecv\r\n";

    byte[] sdpBytes = sdp.getBytes(StandardCharsets.US_ASCII);

    String resp =
        "SIP/2.0 200 OK\r\n" +
        (via.isEmpty() ? "" : "Via: " + via + "\r\n") +
        (from.isEmpty() ? "" : "From: " + from + "\r\n") +
        (to.isEmpty() ? "" : "To: " + to + "\r\n") +
        (callId.isEmpty() ? "" : "Call-ID: " + callId + "\r\n") +
        (cseq.isEmpty() ? "" : "CSeq: " + cseq + "\r\n") +
        "Contact: <sip:java@" + ip + ":5060>\r\n" +
        "Content-Type: application/sdp\r\n" +
        "Content-Length: " + sdpBytes.length + "\r\n" +
        "\r\n" +
        sdp;

    return resp;
  }

  private String buildSimpleResponse(String req, int code, String reason) {
    String via = header(req, "Via");
    String from = header(req, "From");
    String to = header(req, "To");
    String callId = header(req, "Call-ID");
    String cseq = header(req, "CSeq");
    if (!to.toLowerCase().contains("tag=")) {
      to = to + ";tag=java1234";
    }
    return
      "SIP/2.0 " + code + " " + reason + "\r\n" +
      (via.isEmpty() ? "" : "Via: " + via + "\r\n") +
      (from.isEmpty() ? "" : "From: " + from + "\r\n") +
      (to.isEmpty() ? "" : "To: " + to + "\r\n") +
      (callId.isEmpty() ? "" : "Call-ID: " + callId + "\r\n") +
      (cseq.isEmpty() ? "" : "CSeq: " + cseq + "\r\n") +
      "Content-Length: 0\r\n\r\n";
  }

  // 只取第一条同名 header（最小实现）
  private String header(String sip, String name) {
    String[] lines = sip.split("\r\n");
    for (String line : lines) {
      int idx = line.indexOf(':');
      if (idx <= 0) continue;
      String k = line.substring(0, idx).trim();
      if (k.equalsIgnoreCase(name)) {
        return line.substring(idx + 1).trim();
      }
    }
    return "";
  }
}
```

> 上面为了突出核心策略，`decode` 我建议你直接复用我给的“按 Content-Length 切包”的版本，然后 handler 用这个 INVITE 逻辑即可。

---

## 4）sip-client：先完成 echo/握手验证（TCP）

SIP 客户端你可以先不做完整 UA，只做最小“发 INVITE、读 200OK”。

```java
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
```

---

## 5）rtp-client：UDP 发一个“伪 RTP 包”验证 echo

你先验证 UDP echo 是否正常（不依赖软电话）。

```java
package com.litongjava.sip.client;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;

public class RtpUdpEchoClient {
  public static void main(String[] args) throws Exception {
    String host = "192.168.3.219";
    int port = 31234; // 换成服务端分配的 RTP 端口

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
    b[4] = 0x00; b[5] = 0x00; b[6] = 0x00; b[7] = 0xA0; // ts=160
    b[8] = 0x11; b[9] = 0x22; b[10] = 0x33; b[11] = 0x44; // ssrc
    // payload 随便填
    for (int i = 12; i < b.length; i++) b[i] = (byte) 0xFF;
    return b;
  }
}
```

---

## 6）你下一步接入 GeminiLiveBridge 的最小改动点

你前面那段“7.1/7.2”策略完全正确。落地到代码上就是：

* RTP 收到 PCMU payload：

  * PCMU → PCM16(8k) → resample 16k → `bridge.sendPcm16k(bytes)`
* Gemini 回 `sendBinary(pcm24k)`：

  * 24k → 8k → PCM16 → PCMU → 按 20ms 打 RTP 包发回对端

建议你先完成里程碑 2：RTP echo（已经跑通）后，再把 echo 替换成“Gemini 回注”。

---

## 7）关于“在真实项目中处理 TCP 和 UDP 用 ByteBufferPacket”

可以，推荐这么做：

* TCP（SIP）：`ByteBufferPacket` 用于承载完整 SIP message（header+body）
* UDP（RTP）：不一定非要 Packet（tio udp 已经给 raw bytes），但你可以自己封装 `ByteBufferPacket` 统一处理风格

---

如果你希望我把上面的骨架进一步“补齐成可直接跑的 tio-boot 工程结构”，我需要你确认两点（不需要你再回答也行，我按默认写死）：

* SIP 用 TCP 还是 UDP（你现在要求 TCP，我就按 TCP 5060 写）
* 本机 SDP 要写的内网 IP（你之前是 192.168.3.219，我就按它写死）

你确认后，我可以把：启动配置类、listener、handler 组合成一套完整可启动的 tio-boot 代码清单。


上面我给的骨架里，RTP 的部分是“真正的 echo”，SIP 的部分不是 echo（它是最小 SIP 服务器：收到 INVITE 回 200 OK，并在回 200 OK 时启动一个 RTP UDP 端口）。所以你可以分两步测试：

1. 先只测 RTP echo（最简单、最可靠）
2. 再测 SIP + RTP 的整条链路（INVITE 拿到 RTP 端口，再用 RTP client 打那个端口）

下面给你一套按顺序的测试方法。

---

## 1）确认：哪些代码已经实现了 echo

### 已实现 echo

* `RtpEchoUdpHandler`：收到 UDP 包后原样回发（原包 echo）

### 未实现 echo（但实现了 SIP 最小应答）

* SIP handler：收到 INVITE 返回 200 OK（带 SDP），不是 echo

---

## 2）先跑 RTP echo（不依赖 SIP）

### 2.1 启动一个固定端口的 UDP echo 服务

先把你 udp 配置改成监听一个固定端口，比如 30000：

```java
UdpServerConf udpServerConf = new UdpServerConf(30000, new RtpEchoUdpHandler(), 5000);
UdpServer udpServer = new UdpServer(udpServerConf);
udpServer.start();
```

### 2.2 用我给的 `RtpUdpEchoClient` 测试

把 client 里的端口写成 30000：

```java
int port = 30000;
```

运行后，预期结果：

* client 输出 `echo len=172`（12 + 160）
* 输出数组内容与发送一致（至少长度一致）

如果没回包，先用抓包确认：

```bash
sudo tcpdump -i any -n udp port 30000
```

---

## 3）再跑 SIP + RTP（完整链路）

这个测试会稍微麻烦一点，因为 SIP INVITE 里最好带 SDP，这样你能完整模拟通话协商。但你可以先用“简化 INVITE + 200 OK”跑通端口分配与 RTP echo。

### 3.1 启动 SIP TCP 服务（5060）

确认你的 tio TCP server 监听 5060，并且 handler 用的是：

* 我给的“按 \r\n\r\n + Content-Length 切包 decode”
* `SipInviteOnlyHandler`（INVITE 回 200 OK + 启动 RTP 端口）

注意：你不能用最开始那个“读多少算一个包”的 decode，否则 SIP 一旦粘包拆包就会出问题。

### 3.2 用 `SipTcpClient` 发 INVITE

运行 `SipTcpClient`，预期会收到类似：

```
SIP/2.0 200 OK
...
Content-Type: application/sdp
Content-Length: xxx

v=0
...
m=audio 31xxx RTP/AVP 0
a=rtpmap:0 PCMU/8000
```

你要关注这一行：

* `m=audio <rtpPort> RTP/AVP 0`

记下这个 `<rtpPort>`。

如果 client 没收到响应：

* 先看 SIP server 的日志有没有打印 INVITE
* 再抓包：

```bash
sudo tcpdump -i any -n tcp port 5060
```

### 3.3 用 `RtpUdpEchoClient` 打刚才的 rtpPort

把 `RtpUdpEchoClient` 改成：

```java
int port = 31xxx; // 200OK 里的端口
```

运行预期：

* 收到 echo 回包（len 一样）

如果收不到：

* 抓包看 server 有没有收到 RTP：

```bash
sudo tcpdump -i any -n udp port 31xxx
```

---

## 4）如何用软电话验证 RTP echo（可选）

等第 3 步通了，你再用 Linphone/MicroSIP 测就简单：

* 软电话发 INVITE 到你的 Java SIP server
* Java 回 200 OK，告诉它 RTP 发到 192.168.3.219:<rtpPort>
* 软电话开始发 RTP
* 你的 UDP echo 会把 RTP 原包回发给软电话

现象：

* 软电话里你对着麦克风说话，应该能在耳机里听到“回声/延迟回放”
* 如果听不到但抓包看到双向 RTP，说明软电话有回声消除或播放策略，需要在软电话里关回声消除测试

---

## 5）你现在最可能卡住的点

1. SIP decode 不正确（没有按 Content-Length 切包）
2. SDP 里 `c=IN IP4` 写错（必须写 192.168.3.219，不要写 0.0.0.0）
3. RTP 端口没开放（Linux 防火墙/安全组）
4. 你在同一台机器上启动多个 UdpServer，端口冲突或未释放

---

## 6）我建议你按这个最短路径验证

1. 固定 UDP 30000 echo（用 RtpUdpEchoClient）
2. SIP INVITE 拿到 rtpPort
3. RtpUdpEchoClient 打 rtpPort

你把第 3.2 收到的 200 OK（只要 SDP 那几行：c=、m=、a=rtpmap）贴出来，我就能判断你下一步接入 softphone 是否会成功。
