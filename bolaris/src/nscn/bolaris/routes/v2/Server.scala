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

case class Server(
    name: String,
    desc: String,
    port: Int,
    map: String,
    playlist: String,
    curPlayers: Long,
    maxPlayers: Long,
    password: Option[String],
    state: Int
)

object ServerRoutes {
  def service[F[_]: Concurrent] = for {
    servers <- Ref.of[F, Map[Long, Server]](Map.empty)
  } yield (wsb: WebSocketBuilder[F]) =>
    HttpRoutes.of[F] { case GET -> Root / "v2" / "ws" =>
      for {
        queue <- Queue.unbounded[F, WebSocketFrame]
        res <- wsb.build(
          Stream.fromQueueUnterminated(queue),
          _.foreach(f => queue.offer(f))
        )
      } yield res
    }
}
