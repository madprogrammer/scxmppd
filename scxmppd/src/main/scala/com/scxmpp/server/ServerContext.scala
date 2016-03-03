package com.scxmpp.server

class ServerContext extends Context {
  val servers = config.getConfigList("servers")
}

