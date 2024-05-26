package nscn.bolaris.routes.v2

import cats.effect.Concurrent
import cats.effect.kernel.Ref
import cats.syntax.all.catsSyntaxApplicativeId
import cats.syntax.all.toFunctorOps
import org.http4s.HttpRoutes
import org.http4s.Response
import org.http4s.Status
import org.http4s.dsl.io.*
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import fs2.Stream
import cats.syntax.all.toFlatMapOps
import cats.effect.std.Queue
import nscn.bolaris.util.GenId
import cats.effect.std.AtomicCell
import fs2.concurrent.Topic
import fs2.concurrent.SignallingRef

case class MessageMetadata(`type`: Int, id: Long)

enum ServerState {
  case Registering(id: Long)
  case Normal(info: ServerInfo)
}

case class ServerInfo(
    id: Long,
    name: String,
    desc: String,
    port: Int,
    map: String,
    playlist: String,
    curPlayers: Long,
    maxPlayers: Long,
    password: Option[String],
    gameState: Int
)

class ServerWebSocketProtocol[F[_]](stateRef: Ref[F, ServerState]) {
  def register() = {
    
  }
}

// object ServerWebSocketProtocol {
//   def make[F[_]](stateRef: Ref[F, ServerState]) = {
    
//   }
// }

object ServerRoutes {
  def service[F[_]: Concurrent: GenId] = ???
  //   for {
  //   servers <- AtomicCell[F].of(Map.empty[Long, ServerEntry[F]])
  // } yield (wsb: WebSocketBuilder[F]) =>
  //   HttpRoutes.of[F] { case GET -> Root / "v2" / "ws" =>
  //     for {
  //       entry <- ServerEntry[F]
  //       _ <- servers.update(m =>
  //         m.updated(entry.id, entry)
  //       )
  //       res <- wsb.build(
  //         Stream.fromQueueNoneTerminated(entry.send).interruptWhen(entry.sig),
  //         _.interruptWhen(entry.sig).through(entry.recv.publish)
  //       )
  //     } yield res
  //   }
}
