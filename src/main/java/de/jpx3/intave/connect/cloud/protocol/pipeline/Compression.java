package de.jpx3.intave.connect.cloud.protocol.pipeline;

import de.jpx3.intave.codec.VarInt;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.util.zip.Deflater;

public final class Compression extends MessageToByteEncoder<ByteBuf> {
  private final byte[] buffer = new byte[8192];
  private final Deflater deflater;
  private final int threshold;

  public Compression(int threshold) {
    this.deflater = new Deflater();
    this.threshold = threshold;
  }

  @Override
  protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) {
    int i = msg.readableBytes();
    if (i < this.threshold) {
      VarInt.writeTo(out, 0);
      out.writeBytes(msg);
    } else {
      byte[] bytes = new byte[i];
      msg.readBytes(bytes);
      VarInt.writeTo(out, bytes.length);
      deflater.setInput(bytes, 0, i);
      deflater.finish();
      while (!deflater.finished()) {
        int compressedSize = deflater.deflate(buffer);
        out.writeBytes(buffer, 0, compressedSize);
      }
      deflater.reset();
    }
  }
}
