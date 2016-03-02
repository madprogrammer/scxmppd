package com.scxmpp.http

import io.netty.handler.codec.http.HttpRequest

class HealthCheckWebHandler extends UriBasedHandler {
  def process(request: HttpRequest, buf: StringBuilder) {
      buf.append("OK")
  }
}
