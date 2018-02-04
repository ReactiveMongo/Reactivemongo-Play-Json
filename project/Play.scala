import sbt.Keys._
import sbt._

object Play {
  val playLower = "2.5.0"
  val playUpper = "2.6.2"

  val playVer = Def.setting[String] {
    sys.env.get("PLAY_VERSION").getOrElse {
      if (scalaVersion.value startsWith "2.11.") playLower
      else playUpper
    }
  }

  val playDir = Def.setting[String] {
    if (playVer.value startsWith "2.6") "play-2.6"
    else "play-upto2.5"
  }
}
