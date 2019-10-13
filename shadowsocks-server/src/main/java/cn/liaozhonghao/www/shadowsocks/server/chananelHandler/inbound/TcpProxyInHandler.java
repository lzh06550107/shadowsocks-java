package cn.liaozhonghao.www.shadowsocks.server.chananelHandler.inbound;

import cn.liaozhonghao.www.shadowsocks.common.util.ShadowsocksUtils;
import cn.liaozhonghao.www.shadowsocks.server.config.ServerConfig;
import cn.liaozhonghao.www.shadowsocks.server.config.ServerContextConstant;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * proxy handler
 */
public class TcpProxyInHandler extends SimpleChannelInboundHandler<ByteBuf> {
    /**
     * static logger
     */
    private static Logger logger = LoggerFactory.getLogger(TcpProxyInHandler.class);
    /**
     * bootstrap
     */
    private Bootstrap bootstrap;
    /**
     * 客户端channel
     */
    private Channel clientChannel; // 本地代理客户端到远程代理服务端通道
    /**
     * channelFuture
     */
    private Channel remoteChannel; // 远程代理服务端到远程资源通道

    private List<ByteBuf> clientBuffs = new ArrayList<>();

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        if (clientChannel == null) {
            clientChannel = ctx.channel(); // 初始化通道
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        proxyMsg(ctx, msg);
    }

    private void proxyMsg(ChannelHandlerContext clientCtx, ByteBuf msg) {
        if (bootstrap == null) {
            bootstrap = new Bootstrap();

            // 获取访问远程资源地址
            InetSocketAddress remoteAddress = clientCtx.channel().attr(ServerContextConstant.REMOTE_INET_SOCKET_ADDRESS).get();

            bootstrap.group(clientCtx.channel().eventLoop())
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline()
                                    .addLast("timeout", new IdleStateHandler(ServerConfig.serverConfig.getRriTime(), ServerConfig.serverConfig.getRwiTime(), ServerConfig.serverConfig.getRaiTime(), TimeUnit.SECONDS))
                                    .addLast("transfer", new SimpleChannelInboundHandler<ByteBuf>() {
                                        @Override
                                        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                                            // 把远程资源通道响应数据写入远程代理服务通道
                                            clientCtx.channel().writeAndFlush(msg.retain());
                                        }

                                        // 触发了一定是连接不再可用了
                                        @Override
                                        public void channelInactive(ChannelHandlerContext ctx) {
                                            if (clientChannel != null && clientChannel.isOpen()) {
                                                // 当建立远程代理通道时，开始建立远程资源通道
                                                reconnectRemote();
                                            } else {
                                                if (logger.isDebugEnabled()) {
                                                    logger.debug("remote [{}] [{}:{}]  is inactive", remoteChannel.id(), remoteAddress.getHostName(), remoteAddress.getPort());
                                                }
                                                remoteChannel = null;
                                            }
                                        }

                                        @Override
                                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                            logger.error("remote channel [{}], cause:{}", ctx.channel().id(), cause.getMessage());
                                            closeRemoteChannel();
                                            closeClientChannel();
                                        }

                                        @Override
                                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                                            if (evt instanceof IdleStateEvent) {
                                                if (logger.isDebugEnabled()) {
                                                    logger.debug("remote [{}] [{}] state:{} happened", remoteChannel.id(), remoteAddress.toString(), ((IdleStateEvent) evt).state().name());
                                                }
                                                closeClientChannel();
                                                closeRemoteChannel();
                                                return;
                                            }
                                            super.userEventTriggered(ctx, evt);
                                        }
                                    });
                        }
                    });

            // 建立与远程资源的通道
            bootstrap.connect(remoteAddress).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {// 远程通道建立成功
                    remoteChannel = future.channel();

                    logger.info("host: [{}:{}] connect success, client channelId is [{}],  remote channelId is [{}]", remoteAddress.getHostName(), remoteAddress.getPort(), clientChannel.id(), remoteChannel.id());
                    // 写入远程代理服务端通道数据到远程资源通道
                    clientBuffs.add(msg.retain());
                    writeAndFlushByteBufList();
                } else {
                    logger.error(remoteAddress.getHostName() + ":" + remoteAddress.getPort() + " connection fail");
                    ReferenceCountUtil.release(msg);
                    closeClientChannel();
                }
            });
        }

        clientBuffs.add(msg.retain());
        writeAndFlushByteBufList();
    }

    private void closeClientChannel() {
        if (clientChannel != null) {
            clientChannel.close();
        }
        dropBufList();
    }

    private void closeRemoteChannel() {
        if (remoteChannel != null) {
            remoteChannel.close();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx){
        if (logger.isDebugEnabled()) {
            logger.debug("client [{}] is inactive", ctx.channel().id());
        }

        clientChannel = null;
    }

    /**
     * print ByteBufList to remote channel
     */
    private void writeAndFlushByteBufList() {
        if (remoteChannel != null && !clientBuffs.isEmpty()) {

            ByteBuf willWriteMsg = PooledByteBufAllocator.DEFAULT.heapBuffer();
            for (ByteBuf messageBuf : clientBuffs) {
                willWriteMsg.writeBytes(ShadowsocksUtils.readRealBytes(messageBuf));
                ReferenceCountUtil.release(messageBuf);
            }
            clientBuffs.clear();

            if (logger.isDebugEnabled()) {
                logger.debug("write to remote channel [{}] {} bytes", remoteChannel.id().toString(), willWriteMsg.readableBytes());
            }
            // 写入远程代理服务端通道数据到远程资源通道
            remoteChannel.writeAndFlush(willWriteMsg);
        }
    }

    /**
     * releaseBufList
     */
    private void dropBufList(){
        if(!clientBuffs.isEmpty()){
            for (ByteBuf clientBuff : clientBuffs) {
                if(clientBuff.refCnt() != 0){
                    clientBuff.retain(clientBuff.refCnt());
                    ReferenceCountUtil.release(clientBuff);
                }
            }
            clientBuffs.clear();
        }
    }

    /**
     * 重新连接远程
     */
    private void reconnectRemote() {
        InetSocketAddress remoteAddress = clientChannel.attr(ServerContextConstant.REMOTE_INET_SOCKET_ADDRESS).get();
        bootstrap.connect(remoteAddress).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                remoteChannel = future.channel();
                if (logger.isDebugEnabled()) {
                    logger.debug("host: {}:{} reconnect success, remote channelId is {}", remoteAddress.getHostName(), remoteAddress.getPort(), remoteChannel.id());
                }
                writeAndFlushByteBufList();
            } else {
                logger.error(remoteAddress.getHostName() + ":" + remoteAddress.getPort() + " reconnection fail");
                closeClientChannel();
                closeRemoteChannel();
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause){
        logger.error("channelId:{}, cause:{}", ctx.channel().id(), cause.getMessage());
        ctx.channel().close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            if (logger.isDebugEnabled()) {
                logger.debug("client [{}] idleStateEvent happened [{}]", ctx.channel().id(), ((IdleStateEvent) evt).state().name());
            }
            closeClientChannel();
            closeRemoteChannel();
            return;
        }
        super.userEventTriggered(ctx, evt);
    }
}
