package cn.liaozhonghao.www.shadowsocks.server.chananelHandler.inbound;

import cn.liaozhonghao.www.shadowsocks.common.cipher.AbstractCipher;
import cn.liaozhonghao.www.shadowsocks.common.util.ShadowsocksUtils;
import cn.liaozhonghao.www.shadowsocks.server.config.ServerContextConstant;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * decode secret bytes
 */
public class DecodeCipherStreamInHandler extends MessageToMessageDecoder<ByteBuf> {
    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
        AbstractCipher cipher = ctx.channel().attr(ServerContextConstant.SERVER_CIPHER).get();
        byte[] realBytes = cipher.decodeBytes(msg); // 解密消息

        msg.clear().writeBytes(realBytes);
        if (ctx.channel().attr(ServerContextConstant.REMOTE_INET_SOCKET_ADDRESS).get() == null) {
            // 从shadowsocks协议获取远程请求的ip地址
            InetSocketAddress inetSocketAddress = ShadowsocksUtils.getIp(msg);
            if (inetSocketAddress == null) {
                ctx.channel().close();
                return;
            }
            // 保持远程请求ip地址到通道中
            ctx.channel().attr(ServerContextConstant.REMOTE_INET_SOCKET_ADDRESS).set(inetSocketAddress);
        }
        out.add(msg.retain());
    }
}
