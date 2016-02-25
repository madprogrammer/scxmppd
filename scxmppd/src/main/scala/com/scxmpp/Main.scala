import com.scxmpp.server.Server
import com.scxmpp.server.ServerContext

object Main extends App {
  new Server(new ServerContext).run()
}
