package cn.liaozhonghao.www.shadowsocks.client.channelHandler.outbound;

import cn.liaozhonghao.www.shadowsocks.client.config.ClientContextConstant;
import cn.liaozhonghao.www.shadowsocks.common.cipher.AbstractCipher;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * decrypt remote rec secret message to clear text
 */
public class Socks5DecryptOutbound extends MessageToByteEncoder<ByteBuf> {
    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
        AbstractCipher cipher = ctx.channel().attr(ClientContextConstant.SOCKS5_CLIENT_CIPHER).get();
        out.writeBytes(cipher.decodeBytes(msg));
    }
}
