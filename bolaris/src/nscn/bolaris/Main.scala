package nscn.bolaris

import cats.effect.IOApp
import cats.effect.ExitCode
import cats.effect.IO
import nscn.bolaris.util.GenId
import cats.effect.std.Random
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = for {
    given Random[IO] <- Random.scalaUtilRandom[IO]
    given GenId[IO] <- GenId[IO](1L)
    _ <- {
      def duu: IO[Unit] = GenId[IO].genId.flatMap(IO.println(_) *> IO.sleep(Duration.apply(0, TimeUnit.MILLISECONDS)) *> duu)
      duu
    }
  } yield ExitCode.Success
}
