package com.scxmpp.util

import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.{GregorianCalendar, TimeZone, Locale}
import java.util.regex.Pattern

object Helpers {
  private val XML_DATE_FORMAT: String = "yyyy-MM-dd'T'HH:mm:ss"

  def getXmlTimestamp: String = {
    val dateFormatter = new SimpleDateFormat(XML_DATE_FORMAT, Locale.US)
    dateFormatter.setTimeZone(TimeZone.getTimeZone("GMT"))
    val time = new GregorianCalendar()
    dateFormatter.format(time.getTime)
  }

  def makePattern(s: String): Pattern =
    Pattern.compile("^\\Q" + s.replace("?", "\\E.\\Q").replace("*", "\\E.*\\Q") + "\\E$")

  def urlEncode(s: String) = URLEncoder.encode(s, "UTF-8")

  def unixTimestamp: Long = System.currentTimeMillis / 1000
}
