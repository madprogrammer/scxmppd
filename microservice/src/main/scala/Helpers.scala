package main.scala

import java.util.regex.Pattern

object Helpers {
  def makePattern(s: String): Pattern =
    Pattern.compile("^\\Q" + s.replace("?", "\\E.\\Q").replace("*", "\\E.*\\Q") + "\\E$")
}
