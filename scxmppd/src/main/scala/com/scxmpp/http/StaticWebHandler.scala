package com.scxmpp.http

import java.io.{FileNotFoundException, RandomAccessFile, File}
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util._
import java.util.regex.Pattern
import javax.activation.MimetypesFileTypeMap

import java.util.logging.Logger
import com.typesafe.config.Config
import io.netty.channel._
import io.netty.handler.codec.http._
import io.netty.handler.ssl.SslHandler
import io.netty.handler.stream.ChunkedFile

class StaticWebHandler(config: Config) extends UriBasedHandler(config) {

  private val INSECURE_URL = Pattern.compile(".*[<>&\"].*")
  private val HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz"
  private val HTTP_DATE_GMT_TIMEZONE = "GMT"
  private val HTTP_CACHE_SECONDS = 60

  private val logger = Logger.getLogger(getClass.getName)

  def process(ctx: ChannelHandlerContext, request: FullHttpRequest): Unit = {
    if (!request.decoderResult.isSuccess) {
      HttpHelpers.sendError(ctx, HttpResponseStatus.BAD_REQUEST)
      return
    }

    if (request.method != HttpMethod.GET) {
      HttpHelpers.sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED)
      return
    }

    val path = sanitizeUri(request.uri)
    if (path.isEmpty) {
      HttpHelpers.sendError(ctx, HttpResponseStatus.FORBIDDEN)
      return
    }

    val file = new File(path.get)
    if (file.isHidden || !file.exists) {
      HttpHelpers.sendError(ctx, HttpResponseStatus.NOT_FOUND)
      return
    }

    if (!file.isFile) {
      HttpHelpers.sendError(ctx, HttpResponseStatus.FORBIDDEN)
      return
    }

    val raf: Option[RandomAccessFile] = try {
      Some(new RandomAccessFile(file, "r"))
    } catch {
      case e: FileNotFoundException =>
        HttpHelpers.sendError(ctx, HttpResponseStatus.NOT_FOUND)
        return
      case e: Throwable =>
        HttpHelpers.sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR)
        return
    }

    val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)

    HttpUtil.setContentLength(response, raf.get.length)
    setContentTypeHeader(response, file)
    setDateAndCacheHeaders(response, file)

    if (HttpUtil.isKeepAlive(request)) {
      response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
    }

    // Write the initial line and the headers
    ctx.write(response)

    // Write the content.
    val (sendFileFuture, lastContentFuture) = ctx.pipeline().get(classOf[SslHandler]) match {
      case null => (ctx.write(new DefaultFileRegion(raf.get.getChannel, 0, raf.get.length),
        ctx.newProgressivePromise()),
        ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT))
      case _ =>
        val future = ctx.writeAndFlush(new HttpChunkedInput(new ChunkedFile(raf.get, 0, raf.get.length, 8192)),
          ctx.newProgressivePromise())
        (future, future)
    }

    sendFileFuture.addListener(new ChannelProgressiveFutureListener() {
      override def operationProgressed(future: ChannelProgressiveFuture, progress: Long, total: Long) {
        if (total < 0) {
          logger.fine(future.channel + " Transfer progress: " + progress)
        } else {
          logger.fine(future.channel + " Transfer progress: " + progress + " / " + total)
        }
      }

      override def operationComplete(future: ChannelProgressiveFuture) {
        logger.fine(future.channel + " Transfer complete.")
      }
    })

    // Decide whether to close the connection or not.
    if (!HttpUtil.isKeepAlive(request)) {
      lastContentFuture.addListener(ChannelFutureListener.CLOSE)
    }
  }

  def sanitizeUri(uri: String): Option[String] = {
    var decoded = URLDecoder.decode(uri, "UTF-8")

    if (decoded.isEmpty || decoded.charAt(0) != '/')
      return None

    // Remove the root part of the URI
    decoded = decoded.substring(decoded.indexOf("/", 1) match { case -1 => 0; case x => x })

    // Convert file separators.
    decoded = decoded.replace('/', File.separatorChar)

    // Simplistic dumb security check.
    if (decoded.contains(File.separator + '.') || decoded.contains('.' + File.separator) ||
      decoded.charAt(0) == '.' || decoded.charAt(decoded.length() - 1) == '.' || INSECURE_URL.matcher(decoded).matches())
      return None

    // Convert to absolute path.
    Some(config.getString("root") + decoded)
  }

  def setContentTypeHeader(response: HttpResponse, file: File) {
    val mimeTypesMap = new MimetypesFileTypeMap()
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, mimeTypesMap.getContentType(file.getPath))
  }

  def setDateAndCacheHeaders(response: HttpResponse, fileToCache: File) {
    val dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US)
    dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE))

    val time = new GregorianCalendar()
    response.headers().set(HttpHeaderNames.DATE, dateFormatter.format(time.getTime))

    // Add cache headers
    time.add(Calendar.SECOND, HTTP_CACHE_SECONDS)
    response.headers().set(HttpHeaderNames.EXPIRES, dateFormatter.format(time.getTime))
    response.headers().set(HttpHeaderNames.CACHE_CONTROL, "private, max-age=" + HTTP_CACHE_SECONDS)
    response.headers().set(
      HttpHeaderNames.LAST_MODIFIED, dateFormatter.format(new Date(fileToCache.lastModified())))
  }
}
