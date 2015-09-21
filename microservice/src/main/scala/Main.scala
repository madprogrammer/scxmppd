import main.scala.Server
import main.scala.MicroserviceContext

object Main extends App {
  new Server(new MicroserviceContext).run()
}
