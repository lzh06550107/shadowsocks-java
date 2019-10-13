package cn.liaozhonghao.www.shadowsocks.client.channelHandler.inbound;

import cn.liaozhonghao.www.shadowsocks.client.config.ClientConfig;
import cn.liaozhonghao.www.shadowsocks.client.config.ClientContextConstant;
import cn.liaozhonghao.www.shadowsocks.common.cipher.AbstractCipher;
import cn.liaozhonghao.www.shadowsocks.common.cipher.CipherProvider;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * init crypto
 */
public class CryptInitInHandler extends ChannelInboundHandlerAdapter {
    /**
     * static logger
     */
    private static Logger logger = LoggerFactory.getLogger(CryptInitInHandler.class);

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (ctx.channel().attr(ClientContextConstant.SOCKS5_CLIENT_CIPHER).get() == null) {
            initAttribute(ctx);
        }

        super.channelRead(ctx, msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("channelId:{}, cause:{}", ctx.channel().id(), cause.getMessage(), cause);
        ctx.channel().close();
    }

    /**
     * init client attribute
     *
     * @param ctx client context
     */
    private void initAttribute(ChannelHandlerContext ctx) {
        // 初始化cipher对象
        AbstractCipher cipher = CipherProvider.getByName(ClientConfig.clientConfig.getMethod(), ClientConfig.clientConfig.getPassword());
        if (cipher == null) {
            ctx.channel().close();
            throw new IllegalArgumentException("un support server method: " + ClientConfig.clientConfig.getMethod());
        } else {
            ctx.channel().attr(ClientContextConstant.SOCKS5_CLIENT_CIPHER).set(cipher);
        }
    }
}
