import main.scala.Server
import main.scala.ServerContext

object Main extends App {
  new Server(new ServerContext).run()
}
