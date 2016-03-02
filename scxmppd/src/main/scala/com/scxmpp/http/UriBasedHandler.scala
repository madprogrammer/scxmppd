package com.scxmpp.http

import io.netty.handler.codec.http.HttpRequest

trait UriBasedHandler {
  def process (request: HttpRequest, buf: StringBuilder)
  def getContentType: String = "text/plain; charset=UTF-8"
}
