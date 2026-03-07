package com.litongjava.sip.rtp.media;

import com.litongjava.sip.model.CallSession;

public class EchoMediaProcessor implements MediaProcessor {

  @Override
  public AudioFrame process(AudioFrame input, CallSession session) {
    return input;
  }
}