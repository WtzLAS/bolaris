package nscn.bolaris.routes.v2

import cats.effect.IO
import cats.effect.kernel.Ref
import cats.syntax.all.*
import fs2.Pipe
import fs2.Stream
import fs2.data.json
import fs2.data.json.circe.*
import io.circe.Codec
import io.circe.Encoder
import io.circe.Json
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
import scodec.bits.ByteVector
import scribe.Scribe
import scribe.cats.effect

case class MsgMetadata(`type`: Int, id: Long) derives Codec

case class MsgError(`type`: Int, msg: String) derives Codec

case class MsgGeneralResponse(
    metadata: MsgMetadata,
    success: Boolean,
    error: Option[MsgError]
) derives Codec

object MsgGeneralResponse {
  def succeeded(metadata: MsgMetadata) =
    MsgGeneralResponse(metadata.copy(`type` = -metadata.`type`), true, None)
  def failed(metadata: MsgMetadata, errorType: Int, errorMsg: String) =
    MsgGeneralResponse(
      metadata.copy(`type` = -metadata.`type`),
      false,
      Some(MsgError(errorType, errorMsg))
    )
}

case class MsgRegistrationRequest(
    metadata: MsgMetadata,
    info: ServerInfo,
    regToken: String
) derives Codec

extension (stateRef: Ref[IO, ServerState]) {
  def close(code: Int): IO[Unit] = for {
    state <- stateRef.get
    statusCode = ByteVector.view(
      Array[Byte](((code >> 8) & 0xff).toByte, (code & 0xff).toByte)
    )
    _ <- state.rawSendQueue.offer(WebSocketFrame.Close(statusCode))
  } yield ()

  def closeIfTimeout: IO[Unit] = for {
    state <- stateRef.get
    now <- IO.delay(OffsetDateTime.now())
    _ <-
      if (state.lastUpdate + 30.seconds < now) {
        close(1000)
      } else {
        IO.unit
      }
  } yield ()

  def update(info: ServerInfo): IO[Unit] = for {
    now <- IO.delay(OffsetDateTime.now())
    _ <- stateRef.update(_.withInfo(info, now))
  } yield ()

  def send[T](input: T)(using Encoder[T]): IO[Unit] = for {
    state <- stateRef.get
    _ <- state.sendQueue.offer(input.asJson)
  } yield ()

  def processMsg(doc: Json): IO[Unit] = for {
    metadata <- IO.fromEither(doc.hcursor.downField("metadata").as[MsgMetadata])
    _ <- metadata.`type` match
      case 1 =>
        for {
          req <- IO.fromEither(doc.as[MsgRegistrationRequest])
          _ <- update(req.info)
          _ <- send(MsgGeneralResponse.succeeded(metadata))
        } yield ()
      case _ => IO.unit
  } yield ()

  def startProcess: IO[Unit] = for {
    state <- stateRef.get
    _ <- state.recvTopic
      .subscribe(128)
      .evalMap(processMsg)
      .compile
      .drain
      .start
  } yield ()
}

object ServerRoutes {
  private def onClose(service: ServerService, ref: Ref[IO, ServerState]) = for {
    state <- ref.get
    _ <- service.removeServer(state.id)
    _ <- Scribe[IO].info(
      s"Server ${state.id} \"${state.info.map(_.name).getOrElse("UNREGISTERED")}\" disconnected"
    )
  } yield ()

  private def makeSend(
      state: ServerState
  ): Stream[IO, WebSocketFrame] =
    Stream
      .fromQueueUnterminated(state.sendQueue)
      .map(_.noSpaces)
      .map(WebSocketFrame.Text(_))
      .merge(Stream.fromQueueUnterminated(state.rawSendQueue))

  private def makeRecv(
      state: ServerState
  ): Pipe[IO, WebSocketFrame, Unit] =
    (s: Stream[IO, WebSocketFrame]) => {
      s.mapFilter {
        case WebSocketFrame.Text(str, last) => Some(str)
        case _                              => None
      }.through(json.ast.parse)
        .through(state.recvTopic.publish)
    }

  def make(
      wsb: WebSocketBuilder[IO],
      service: ServerService
  )(using GenId[IO]) = {
    import org.http4s.dsl.io.*

    HttpRoutes.of[IO] {
      case GET -> Root / "v2" / "server" / "ws" =>
        for {
          state <- ServerState.make()
          stateRef <- service.insertServer(state)
          _ <- stateRef.startProcess
          res <- wsb
            .withFilterPingPongs(true)
            .withDefragment(true)
            .withOnClose(onClose(service, stateRef))
            .build(makeSend(state), makeRecv(state))
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
