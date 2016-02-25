lazy val root = (project in file("."))
      .dependsOn(scxmppd)
      .settings(Settings.root: _*)

lazy val scxmppd  = (project in file("scxmppd"))
      .configs(Configs.all: _*)
      .settings(Settings.scxmppd: _*)

