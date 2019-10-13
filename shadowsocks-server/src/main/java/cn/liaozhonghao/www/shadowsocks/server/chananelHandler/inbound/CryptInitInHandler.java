package cn.liaozhonghao.www.shadowsocks.server.chananelHandler.inbound;

import cn.liaozhonghao.www.shadowsocks.server.config.ServerConfig;
import cn.liaozhonghao.www.shadowsocks.server.config.ServerContextConstant;
import cn.liaozhonghao.www.shadowsocks.common.cipher.AbstractCipher;
import cn.liaozhonghao.www.shadowsocks.common.cipher.CipherProvider;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * init crypt
 */
public class CryptInitInHandler extends ChannelInboundHandlerAdapter {
    /**
     * static logger
     */
    private static Logger logger = LoggerFactory.getLogger(CryptInitInHandler.class);

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (ctx.channel().attr(ServerContextConstant.SERVER_CIPHER).get() == null) {
            initAttribute(ctx);
        }

        super.channelRead(ctx, msg);
    }


    /**
     * init client attribute
     *
     * @param ctx client context
     */
    private void initAttribute(ChannelHandlerContext ctx) {
        // cipher
        AbstractCipher cipher = CipherProvider.getByName(ServerConfig.serverConfig.getMethod(), ServerConfig.serverConfig.getPassword());
        if (cipher == null) {
            ctx.channel().close();
            throw new IllegalArgumentException("un support server method: " + ServerConfig.serverConfig.getMethod());
        } else {
            ctx.channel().attr(ServerContextConstant.SERVER_CIPHER).set(cipher);
        }
    }
}
