package nscn.bolaris.services

import cats.effect.IO
import cats.effect.Ref
import cats.effect.std.Queue
import cats.syntax.all.*
import fs2.concurrent.SignallingRef
import fs2.concurrent.Topic
import io.circe.Codec
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

enum ServerState {
  case Registering(
      _id: Long,
      _lastUpdateAt: OffsetDateTime,
      _sendQueue: Queue[IO, WebSocketFrame],
      _recvTopic: Topic[IO, WebSocketFrame]
  )
  case Normal(
      _id: Long,
      _info: ServerInfo,
      _lastUpdateAt: OffsetDateTime,
      _sendQueue: Queue[IO, WebSocketFrame],
      _recvTopic: Topic[IO, WebSocketFrame]
  )

  def id: Long = this match
    case Registering(id, _, _, _) => id
    case Normal(id, _, _, _, _)   => id

  def info = this match
    case Registering(_, _, _, _)  => None
    case Normal(_, info, _, _, _) => Some(info)

  def sendQueue = this match
    case Registering(_, _, sendQueue, _) => sendQueue
    case Normal(_, _, _, sendQueue, _)   => sendQueue

  def recvTopic = this match
    case Registering(_, _, _, recvTopic) => recvTopic
    case Normal(_, _, _, _, recvTopic)   => recvTopic

  def lastUpdateAt = this match
    case Registering(_, lastUpdateAt, _, _) => lastUpdateAt
    case Normal(_, _, lastUpdateAt, _, _)   => lastUpdateAt

  def withInfo(newInfo: ServerInfo, now: OffsetDateTime) =
    this match
      case Registering(id, _, sendQueue, recvTopic) =>
        Normal(id, newInfo, now, sendQueue, recvTopic)
      case Normal(id, _, lastUpdateAt, sendQueue, recvTopic) =>
        Normal(id, newInfo, now, sendQueue, recvTopic)

  def isRegistering = this match
    case Registering(_, _, _, _) => true
    case Normal(_, _, _, _, _)   => false

  def isNormal = !isRegistering
}

object ServerState {
  def make(using ev: GenId[IO]): IO[ServerState] = for {
    id <- ev.genId
    createdAt <- IO.delay(OffsetDateTime.now())
    sendQueue <- Queue.bounded[IO, WebSocketFrame](128)
    recvTopic <- Topic[IO, WebSocketFrame]
  } yield ServerState.Registering(
    id,
    createdAt,
    sendQueue,
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

  def newServer(using ev: GenId[IO]) = for {
    state <- ServerState.make
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
