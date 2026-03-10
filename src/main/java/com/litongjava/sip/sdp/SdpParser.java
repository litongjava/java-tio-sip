package com.litongjava.sip.sdp;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SdpParser {

  private final List<CodecSpec> localSupportedCodecs;

  public SdpParser() {
    this.localSupportedCodecs = defaultSupportedCodecs();
  }

  public SdpParser(List<CodecSpec> localSupportedCodecs) {
    this.localSupportedCodecs = localSupportedCodecs;
  }

  public SdpNegotiationResult negotiate(byte[] sdpBytes) {
    if (sdpBytes == null || sdpBytes.length == 0) {
      return SdpNegotiationResult.fail("missing sdp offer");
    }

    String sdp = new String(sdpBytes, StandardCharsets.US_ASCII);
    String[] lines = sdp.split("\r\n");

    String sessionConnectionIp = null;
    String mediaConnectionIp = null;
    int remoteAudioPort = 0;
    int ptime = 20;

    List<Integer> offeredPayloadTypes = new ArrayList<>();
    Map<Integer, CodecSpec> offeredCodecMap = new HashMap<>();

    boolean inAudioMedia = false;
    boolean telephoneEventSupported = false;
    int telephoneEventPt = -1;

    for (String line : lines) {
      if (line == null || line.isEmpty()) {
        continue;
      }

      if (line.startsWith("c=")) {
        String[] parts = line.split(" ");
        if (parts.length >= 3) {
          String ip = parts[2].trim();
          if (inAudioMedia) {
            mediaConnectionIp = ip;
          } else {
            sessionConnectionIp = ip;
          }
        }
        continue;
      }

      if (line.startsWith("m=")) {
        inAudioMedia = false;

        String[] parts = line.split(" ");
        if (parts.length >= 4 && parts[0].startsWith("m=audio")) {
          inAudioMedia = true;
          try {
            remoteAudioPort = Integer.parseInt(parts[1].trim());
          } catch (Exception e) {
            return SdpNegotiationResult.fail("invalid remote audio port");
          }

          for (int i = 3; i < parts.length; i++) {
            try {
              offeredPayloadTypes.add(Integer.parseInt(parts[i].trim()));
            } catch (Exception ignore) {
            }
          }
        }
        continue;
      }

      if (!inAudioMedia) {
        continue;
      }

      if (line.startsWith("a=rtpmap:")) {
        try {
          int colon = line.indexOf(':');
          int space = line.indexOf(' ');
          if (colon < 0 || space < 0 || space <= colon) {
            continue;
          }

          int pt = Integer.parseInt(line.substring(colon + 1, space).trim());
          String[] enc = line.substring(space + 1).trim().split("/");
          if (enc.length < 2) {
            continue;
          }

          String codecName = enc[0].trim();
          int clockRate = Integer.parseInt(enc[1].trim());

          CodecSpec spec = new CodecSpec(pt, codecName, clockRate);
          offeredCodecMap.put(pt, spec);

          if ("telephone-event".equalsIgnoreCase(codecName) && clockRate == 8000) {
            telephoneEventSupported = true;
            telephoneEventPt = pt;
          }
        } catch (Exception ignore) {
        }
        continue;
      }

      if (line.startsWith("a=ptime:")) {
        try {
          ptime = Integer.parseInt(line.substring("a=ptime:".length()).trim());
        } catch (Exception ignore) {
        }
      }
    }

    if (remoteAudioPort <= 0) {
      return SdpNegotiationResult.fail("missing audio media");
    }

    String remoteIp = mediaConnectionIp != null ? mediaConnectionIp : sessionConnectionIp;
    if (remoteIp == null || remoteIp.isEmpty()) {
      return SdpNegotiationResult.fail("missing connection address");
    }

    CodecSpec selected = chooseCodec(offeredPayloadTypes, offeredCodecMap);
    if (selected == null) {
      return SdpNegotiationResult.fail("no supported audio codec");
    }

    SdpNegotiationResult result = SdpNegotiationResult.ok();
    result.setRemoteRtpIp(remoteIp);
    result.setRemoteRtpPort(remoteAudioPort);
    result.setSelectedCodec(selected);
    result.setTelephoneEventSupported(telephoneEventSupported);
    result.setRemoteTelephoneEventPayloadType(telephoneEventPt);
    result.setPtime(ptime);
    return result;
  }

  private CodecSpec chooseCodec(List<Integer> offeredPayloadTypes, Map<Integer, CodecSpec> offeredCodecMap) {
    for (CodecSpec local : localSupportedCodecs) {
      for (Integer pt : offeredPayloadTypes) {
        CodecSpec offered = offeredCodecMap.get(pt);

        if (offered != null) {
          if (local.isSameCodec(offered.getCodecName(), offered.getClockRate())) {
            return new CodecSpec(pt, offered.getCodecName(), offered.getClockRate());
          }
        } else {
          if (pt == 0 && local.isStaticPcmu()) {
            return new CodecSpec(0, "PCMU", 8000);
          }
          if (pt == 8 && local.isStaticPcma()) {
            return new CodecSpec(8, "PCMA", 8000);
          }
          if (pt == 9 && "G722".equalsIgnoreCase(local.getCodecName()) && local.getClockRate() == 8000) {
            return new CodecSpec(9, "G722", 8000);
          }
        }
      }
    }
    return null;
  }

  public static List<CodecSpec> defaultSupportedCodecs() {
    List<CodecSpec> codecs = new ArrayList<>();
    codecs.add(new CodecSpec(9, "G722", 8000));
    codecs.add(new CodecSpec(0, "PCMU", 8000));
    codecs.add(new CodecSpec(8, "PCMA", 8000));
    return codecs;
  }
}