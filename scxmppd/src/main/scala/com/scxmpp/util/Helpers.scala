package com.scxmpp.util

import java.net.URLEncoder
import java.util.regex.Pattern

object Helpers {
  def makePattern(s: String): Pattern =
    Pattern.compile("^\\Q" + s.replace("?", "\\E.\\Q").replace("*", "\\E.*\\Q") + "\\E$")
  def urlEncode(s: String) = URLEncoder.encode(s, "UTF-8")
}
