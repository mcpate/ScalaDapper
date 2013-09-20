name := "LogActorTest"

version := "0.1"

scalaVersion := "2.10.2"

libraryDependencies ++= Seq(
          "com.typesafe.akka" % "akka-actor_2.10" % "2.2.1",
	  "com.typesafe.akka" % "akka-testkit_2.10" % "2.2.1",
	  "org.scalatest" % "scalatest_2.10" % "1.9.2"
          )
