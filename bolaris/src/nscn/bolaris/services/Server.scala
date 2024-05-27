package nscn.bolaris.services

import cats.Monad
import cats.Parallel
import cats.effect.kernel.Ref
import cats.syntax.all.*
import io.circe.Codec
import io.github.chronoscala.Imports.*
import nscn.bolaris.util.GenId
import cats.effect.kernel.Sync

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
) derives Codec

enum ServerState {
  case Registering(id: Long, createdAt: OffsetDateTime)
  case Normal(info: ServerInfo, lastUpdateAt: OffsetDateTime)

  def toInfo: Option[ServerInfo] = this match
    case Registering(id, createdAt) => None
    case Normal(info, lastUpdateAt) => Some(info)
}

object ServerState {
  def apply[F[_]: GenId: Sync] = for {
    id <- GenId[F].genId
    createdAt <- Sync[F].delay(OffsetDateTime.now())
  } yield ServerState.Registering(id, createdAt)
}

class ServerService[F[_]: Monad: Parallel](
    servers: Ref[F, Map[Long, Ref[F, ServerState]]]
) {
  def listServers =
    servers.get.flatMap(
      _.values.map(_.get.map(_.toInfo)).toSeq.parSequenceFilter
    )
  
  def newServer(id: Long) = for {

  } yield ???
}
