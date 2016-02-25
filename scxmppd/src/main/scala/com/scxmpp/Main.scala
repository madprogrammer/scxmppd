import com.scxmpp.server.Server
import com.scxmpp.server.ServerContext

package com.scxmpp {

  object Main extends App {
    new Server(new ServerContext).run()
  }

}