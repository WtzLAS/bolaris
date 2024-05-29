import mill._, scalalib._

object V {
  val catsEffect = "3.5.4"
  val http4s = "1.0.0-M41"
  val log4cats = "2.7.0"
  val scribe = "3.13.5"
  val circe = "0.14.7"
  val chronoscala = "2.0.10"
  val fs2Data = "1.11.0"
}

object bolaris extends ScalaModule {
  def scalaVersion = "3.4.2"

  def ivyDeps = Agg(
    ivy"org.typelevel::cats-effect:${V.catsEffect}",
    ivy"org.http4s::http4s-ember-server:${V.http4s}",
    ivy"org.http4s::http4s-dsl:${V.http4s}",
    ivy"org.http4s::http4s-circe:${V.http4s}",
    ivy"org.typelevel::log4cats-slf4j:${V.log4cats}",
    ivy"com.outr::scribe:${V.scribe}",
    ivy"com.outr::scribe-slf4j:${V.scribe}",
    ivy"com.outr::scribe-slf4j2:${V.scribe}",
    ivy"com.outr::scribe-cats:${V.scribe}",
    ivy"io.circe::circe-core:${V.circe}",
    ivy"io.circe::circe-parser:${V.circe}",
    ivy"io.github.chronoscala::chronoscala:${V.chronoscala}",
    ivy"org.gnieh::fs2-data-json:${V.fs2Data}",
    ivy"org.gnieh::fs2-data-json-circe:${V.fs2Data}"
  )
}
