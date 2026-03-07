package com.litongjava.sip.rtp.media;

import com.litongjava.sip.model.CallSession;

public interface MediaProcessor {
  AudioFrame process(AudioFrame input, CallSession session);
}