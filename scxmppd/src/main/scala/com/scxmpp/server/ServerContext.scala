package com.scxmpp.server

import com.scxmpp.db.KeyValueStorage

import scala.collection.immutable

class ServerContext extends Context {
  val servers = config.getConfigList("servers")
  val keyValueStore = dynamicAccess.createInstanceFor[KeyValueStorage](
    config.getString("db.keyvalue"), immutable.Seq.empty).get
}

