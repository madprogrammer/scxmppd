import java.util.List

import javax.xml.stream.events.XMLEvent

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder

import java.util.logging.Logger

import com.fasterxml.aalto.AsyncXMLStreamReader
import com.fasterxml.aalto.evt.EventAllocatorImpl
import com.fasterxml.aalto.stax.InputFactoryImpl

class XmlFrameDecoder extends ByteToMessageDecoder {

  val factory = new InputFactoryImpl()
  val allocator = EventAllocatorImpl.getDefaultInstance
  val reader = factory.createAsyncForByteBuffer
  val logger = Logger.getLogger(getClass.getName)

  @throws[Exception]
  override def decode(ctx: ChannelHandlerContext, buffer: ByteBuf, out: List[Object]) {
    reader.getInputFeeder.feedInput(buffer.nioBuffer)
    buffer.skipBytes(buffer.readableBytes)

    while (reader.hasNext && reader.next != AsyncXMLStreamReader.EVENT_INCOMPLETE) {
      out.add(allocator.allocate(reader))
    }
  }

}
