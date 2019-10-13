package cn.liaozhonghao.www.shadowsocks.server.chananelHandler.outbound;

import cn.liaozhonghao.www.shadowsocks.common.cipher.AbstractCipher;
import cn.liaozhonghao.www.shadowsocks.server.config.ServerContextConstant;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;

/**
 * encode laws text
 */
public class EncodeCipherStreamOutHandler extends MessageToMessageEncoder<ByteBuf> {
    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
        AbstractCipher cipher = ctx.channel().attr(ServerContextConstant.SERVER_CIPHER).get();

        byte[] realData = new byte[msg.readableBytes()];
        msg.getBytes(0, realData);

        byte[] resultData = cipher.encodeBytes(realData);
        out.add(Unpooled.buffer().writeBytes(resultData));
    }
}
