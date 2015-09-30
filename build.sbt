lazy val root = (project in file("."))
      .dependsOn(microservice)
      .settings(Settings.root: _*)

lazy val microservice = (project in file("microservice"))
      .configs(Configs.all: _*)
      .settings(Settings.microservice: _*)

