package nscn.bolaris.routes.v2

import cats.effect.IO
import cats.effect.Ref
import cats.syntax.all.*
import fs2.Stream
import io.circe.Codec
import io.circe.DecodingFailure
import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax.*
import io.github.chronoscala.Imports.*
import nscn.bolaris.services.ServerInfo
import nscn.bolaris.services.ServerService
import nscn.bolaris.services.ServerState
import nscn.bolaris.util.GenId
import org.http4s.HttpRoutes
import org.http4s.circe.*
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import scribe.Scribe
import scribe.cats.effect

import scala.jdk.DurationConverters.*

case class MsgMetadata(`type`: Int, id: Long) derives Codec

case class MsgError(`type`: Int, msg: String) derives Codec

case class MsgRegistrationRequest(
    info: ServerInfo,
    regToken: String
) derives Codec

case class MsgRegistrationResponse(
    metadata: MsgMetadata,
    success: Boolean,
    id: Option[Long],
    error: Option[MsgError]
) derives Codec

object MsgRegistrationResponse {
  def succeeded(metadata: MsgMetadata, id: Long) =
    MsgRegistrationResponse(metadata.copy(`type` = 2), true, Some(id), None)
  def failed(metadata: MsgMetadata, errorType: Int, errorMsg: String) =
    MsgRegistrationResponse(
      metadata.copy(`type` = 2),
      false,
      None,
      Some(MsgError(errorType, errorMsg))
    )
}

class ServerWebSocketProtocol(
    stateRef: Ref[IO, ServerState]
) {
  def pingStream(state: ServerState): Stream[IO, WebSocketFrame] = Stream
    .awakeEvery[IO](1.seconds.toScala)
    .interruptWhen(state.interruptSignal)
    .evalMapFilter(_ =>
      for {
        state <- stateRef.get
      } yield
        if (state.isNormal) {
          Some(WebSocketFrame.Ping())
        } else {
          None
        }
    )

  def register(metadata: MsgMetadata, docReq: Json) = (for {
    req <- IO.delay(docReq.as[MsgRegistrationRequest]).rethrow
    id <- stateRef.modify(s =>
      (s.withInfo(req.info, OffsetDateTime.now()), s.id)
    )
    _ <- Scribe[IO].info(s"Server $id \"${req.info.name}\" has registered")
  } yield MsgRegistrationResponse(
    metadata.copy(`type` = 2),
    true,
    Some(id),
    None
  ).asJson).recover { case s: DecodingFailure =>
    MsgRegistrationResponse(
      metadata.copy(`type` = 2),
      false,
      None,
      Some(MsgError(1, s.getMessage))
    ).asJson
  }

  def processMsg(doc: Json): IO[Boolean] = for {
    metadata <- IO
      .delay(doc.hcursor.downField("metadata").as[MsgMetadata])
      .rethrow
    res <- metadata.`type` match
      case 1 =>
        register(metadata, doc).map(Some(_))
      case _ => IO.pure(None)
    _ <- res match
      case Some(res) =>
        stateRef.get.flatMap(
          _.sendQueue.offer(WebSocketFrame.Text(res.noSpaces, true))
        )
      case None => IO.pure(())
  } yield res.isDefined

  def processPong(doc: Json): IO[Unit] = for {
    newInfo <- IO.delay(doc.as[ServerInfo]).rethrow
    _ <- stateRef.tryUpdate(s =>
      if (s.isNormal) { s.withInfo(newInfo, OffsetDateTime.now()) }
      else { s }
    )
  } yield ()

  def process(f: WebSocketFrame): IO[Option[WebSocketFrame]] = for {
    filtered <- f match
      case WebSocketFrame.Text(str, last) =>
        IO.delay(parse(str))
          .rethrow
          .flatMap(processMsg)
          .map(filtered =>
            if (filtered) { None }
            else { Some(f) }
          )
      // case WebSocketFrame.Pong(data) =>
      //   IO.delay(data.decodeUtf8.map(parse))
      //     .rethrow
      //     .rethrow
      //     .flatMap(processPong)
      //     .map(_ => None)
      case _ => IO.pure(Some(f))
  } yield filtered
}

object ServerRoutes {
  def make(
      wsb: WebSocketBuilder[IO],
      service: ServerService
  )(using GenId[IO]) = {
    import org.http4s.dsl.io.*
    HttpRoutes.of[IO] {
      case GET -> Root / "v2" / "server" / "ws" =>
        for {
          serverRef <- service.newServer
          server <- serverRef.get
          protocol = ServerWebSocketProtocol(serverRef)
          res <- wsb
            .withFilterPingPongs(false)
            .withDefragment(false)
            .withOnClose(for {
              server <- serverRef.get
              _ <- service.removeServer(server.id)
              _ <- Scribe[IO].info(
                s"Server ${server.id} \"${server.info.map(_.name).getOrElse("UNREGISTERED")}\" disconnected"
              )
            } yield ())
            .build(
              Stream
                .fromQueueUnterminated(server.sendQueue)
                .merge(protocol.pingStream(server))
                .interruptWhen(server.interruptSignal),
              _.interruptWhen(server.interruptSignal)
                .evalMapFilter(protocol.process)
                .through(server.recvTopic.publish)
            )
        } yield res
      case GET -> Root / "v2" / "server" / "list" =>
        for {
          infos <- service.listServers()
          json = infos
            .map((id, info) => info.asJson.mapObject(("id" -> id.asJson) +: _))
            .asJson
          res <- Ok(json)
        } yield res
    }
  }
}
