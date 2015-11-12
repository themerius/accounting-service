name := """accounting"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  specs2 % Test
)

libraryDependencies += "org.apache.geronimo.specs" % "geronimo-jms_1.1_spec" % "1.1.1"
libraryDependencies += "org.fusesource.stompjms" % "stompjms-client" % "1.19"

libraryDependencies += "com.github.nitram509" % "jmacaroons" % "0.3.1"

libraryDependencies += "com.hierynomus" % "sshj" % "0.13.0"

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator
