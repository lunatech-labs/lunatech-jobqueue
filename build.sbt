name := "jobqueue-web"

libraryDependencies ++= Seq(
  "org.scalaz" %% "scalaz-core" % "7.0.6",
  "com.lunatech" %% "dirqueue" % "0.1"
)     

resolvers += "Lunatech Public Releases" at "http://artifactory.lunatech.com/artifactory/releases-public"

play.Project.playScalaSettings
