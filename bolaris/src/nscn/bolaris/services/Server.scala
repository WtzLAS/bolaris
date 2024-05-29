package nscn.bolaris.services

import cats.effect.IO
import cats.effect.kernel.Ref
import cats.effect.std.Queue
import cats.syntax.all.*
import fs2.concurrent.Topic
import io.circe.Codec
import io.circe.Json
import io.github.chronoscala.Imports.*
import nscn.bolaris.util.GenId
import org.http4s.websocket.WebSocketFrame

case class ServerInfo(
    name: String,
    desc: String,
    port: Int,
    map: String,
    playlist: String,
    curPlayers: Long,
    maxPlayers: Long,
    password: Option[String],
    gameState: Int
) derives Codec

case class ServerState(
    id: Long,
    info: Option[ServerInfo],
    lastUpdate: OffsetDateTime,
    sendQueue: Queue[IO, Json],
    rawSendQueue: Queue[IO, WebSocketFrame],
    recvTopic: Topic[IO, Json]
) {
  def isRegistered = info.isDefined

  def withInfo(info: ServerInfo, now: OffsetDateTime) =
    this.copy(info = Some(info), lastUpdate = now)
}

object ServerState {
  def make()(using ev: GenId[IO]): IO[ServerState] = for {
    id <- ev.genId
    createdAt <- IO.delay(OffsetDateTime.now())
    sendQueue <- Queue.bounded[IO, Json](128)
    rawSendQueue <- Queue.bounded[IO, WebSocketFrame](128)
    recvTopic <- Topic[IO, Json]
  } yield ServerState(
    id,
    None,
    createdAt,
    sendQueue,
    rawSendQueue,
    recvTopic
  )

  def make(info: ServerInfo)(using ev: GenId[IO]): IO[ServerState] = for {
    id <- ev.genId
    createdAt <- IO.delay(OffsetDateTime.now())
    sendQueue <- Queue.bounded[IO, Json](128)
    rawSendQueue <- Queue.bounded[IO, WebSocketFrame](128)
    recvTopic <- Topic[IO, Json]
  } yield ServerState(
    id,
    Some(info),
    createdAt,
    sendQueue,
    rawSendQueue,
    recvTopic
  )
}

class ServerService(
    servers: Ref[IO, Map[Long, Ref[IO, ServerState]]]
) {
  def listServers() =
    servers.get.flatMap(
      _.values
        .map(_.get.map(s => s.info.map((s.id, _))))
        .toSeq
        .parSequenceFilter
    )

  def insertServer(state: ServerState) = for {
    stateRef <- Ref[IO].of(state)
    _ <- servers.update(_.updated(state.id, stateRef))
  } yield stateRef

  def removeServer(id: Long) = servers.update(_.updatedWith(id)(_ => None))
}

object ServerService {
  def make = for {
    servers <- Ref[IO].of(Map.empty)
  } yield ServerService(servers)
}
