package nscn.bolaris

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.std.Random
import com.comcast.ip4s.*
import nscn.bolaris.routes.v2.ServerRoutes
import nscn.bolaris.util.GenId
import org.http4s.ember.server.EmberServerBuilder
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

object Main extends IOApp {
  given LoggerFactory[IO] = Slf4jFactory.create[IO]

  override def run(args: List[String]): IO[ExitCode] =
    for {
      given Random[IO] <- Random.scalaUtilRandom[IO]
      given GenId[IO] <- GenId[IO](1L)
      // routes <- ServerRoutes.service[IO]
      _ <- EmberServerBuilder
        .default[IO]
        .withHost(host"127.0.0.1")
        .withPort(port"7779")
        // .withHttpWebSocketApp(wsb => routes(wsb).orNotFound)
        .build
        .useForever
    } yield ExitCode.Success
}
