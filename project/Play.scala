import sbt.Keys._
import sbt._

object Play {
  val playLower = "2.5.0"
  val playUpper = "2.7.1"

  val playVer = Def.setting[String] {
    sys.env.get("PLAY_VERSION").getOrElse {
      if (scalaVersion.value startsWith "2.11.") playLower
      else playUpper
    }
  }

  val playDir = Def.setting[String] {
    if (playVer.value startsWith "2.5") "play-upto2.5"
    else "play-2.6+"
  }
}
