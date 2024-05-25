import mill._, scalalib._

object bolaris extends ScalaModule {
  def scalaVersion = "3.4.2"
  
  def ivyDeps = Agg(
    ivy"org.typelevel::cats-effect:3.5.4",
    ivy"org.http4s::http4s-ember-server:1.0.0-M41",
    ivy"org.http4s::http4s-dsl:1.0.0-M41"
  )
}