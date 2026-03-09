package com.litongjava.sip.rtp.codec;

import com.litongjava.sip.model.CallSession;
import com.litongjava.sip.sdp.CodecSpec;

public final class NegotiatedAudioFormatResolver {

  private NegotiatedAudioFormatResolver() {
  }

  public static int resolveSessionPcmSampleRate(CallSession session) {
    if (session == null) {
      return 8000;
    }
    return resolveSessionPcmSampleRate(session.getSelectedCodec());
  }

  public static int resolveSessionPcmSampleRate(CodecSpec codecSpec) {
    if (codecSpec == null || codecSpec.getCodecName() == null) {
      return 8000;
    }

    String codecName = codecSpec.getCodecName();
    if ("G722".equalsIgnoreCase(codecName)) {
      return 16000;
    }
    if ("PCMU".equalsIgnoreCase(codecName)) {
      return 8000;
    }
    if ("PCMA".equalsIgnoreCase(codecName)) {
      return 8000;
    }

    return codecSpec.getClockRate() > 0 ? codecSpec.getClockRate() : 8000;
  }

  public static int resolveChannels(CallSession session) {
    return 1;
  }
}