package cn.liaozhonghao.www.shadowsocks.client.channelHandler.inbound;

import cn.liaozhonghao.www.shadowsocks.client.channelHandler.outbound.Socks5DecryptOutbound;
import cn.liaozhonghao.www.shadowsocks.client.channelHandler.outbound.Socks5ServerEncoder;
import cn.liaozhonghao.www.shadowsocks.client.config.ClientConfig;
import cn.liaozhonghao.www.shadowsocks.client.config.ClientContextConstant;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socksx.v5.*;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * socks5建立连接
 */
public class Socks5ServerDoorHandler extends ChannelInboundHandlerAdapter {
    /**
     * static logger
     */
    private static Logger logger = LoggerFactory.getLogger(Socks5ServerDoorHandler.class);

    enum ACCSTATE {UN_INIT, UN_CONNECT, FINISHED}

    private ACCSTATE state;

    private Bootstrap remoteBootstrap;

    private Channel remoteChannel; // 本地客户端到远程服务端通道

    private Channel clientChannel; // 浏览器到本地客户端的连接通道

    public Socks5ServerDoorHandler() {
        this.state = ACCSTATE.UN_INIT;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        this.clientChannel = ctx.channel();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        ctx.pipeline()
                // Socks5InitialRequestDecoder负责服务器和客户端协商鉴权方式，返回服务器端支持的鉴权方式，会将消息解码为DefaultSocks5InitialRequest对象
                .addFirst("socks5-init", new Socks5InitialRequestDecoder())//init socks5 request
                .addLast(new Socks5ServerEncoder());//socks5 response
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        switch (state) {
            case UN_INIT: // 连接未初始化状态
                Socks5InitialResponse response;
                if (msg instanceof Socks5InitialRequest) { //服务器和客户端协商鉴权
                    response = new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH); // 不需要鉴权
                    ctx.channel().writeAndFlush(response);

                    ctx.pipeline().addBefore(ctx.name(), "socks5-command", new Socks5CommandRequestDecoder());
                    this.state = ACCSTATE.UN_CONNECT;

                    if (logger.isDebugEnabled()) {
                        logger.debug("{} init success", ctx.channel().id());
                    }
                } else { // 不是 socks5 初始化连接请求
                    response = new DefaultSocks5InitialResponse(Socks5AuthMethod.UNACCEPTED);
                    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
                    logger.error("{} init is not socks5InitRequest", ctx.channel().id());
                }
                ReferenceCountUtil.release(msg);
                break;
            case UN_CONNECT:
                if (msg instanceof DefaultSocks5CommandRequest) { //
                    DefaultSocks5CommandRequest commandRequest = (DefaultSocks5CommandRequest) msg;
                    InetSocketAddress dstAddress = new InetSocketAddress(commandRequest.dstAddr(), commandRequest.dstPort());

                    ctx.channel().attr(ClientContextConstant.DST_ADDRESS).setIfAbsent(dstAddress);
                    buildRemoteConnect();
                    this.state = ACCSTATE.FINISHED;
                } else { // 不是 socks5 命令请求
                    ctx.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, Socks5AddressType.IPv4))
                            .addListener(ChannelFutureListener.CLOSE);
                    logger.error("{} is not a commanderRequest", ctx.channel().id());
                }

                ReferenceCountUtil.release(msg);
                break;
            case FINISHED:
                ctx.fireChannelRead(msg);
                break;
        }
    }

    /**
     * 连接远程代理服务端
     */
    private void buildRemoteConnect() {
        if (remoteBootstrap == null) {
            remoteBootstrap = new Bootstrap();
            remoteBootstrap.group(new NioEventLoopGroup(1))
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.pipeline()
                                    .addLast(new IdleStateHandler(0, 0, 120, TimeUnit.SECONDS)) // 心跳
                                    .addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                                        @Override
                                        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
                                            if(clientChannel.pipeline().get(Socks5DecryptOutbound.class) == null){
                                                // 增加解密处理器到本地通道
                                                clientChannel.pipeline().addLast("decryptRemote",new Socks5DecryptOutbound());
                                            }
                                            // 把远程加密响应数据写入本地通道
                                            clientChannel.writeAndFlush(msg.retain());
                                        }

                                        @Override
                                        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                            logger.debug("remoteId {} is inactive", ctx.channel().id());
                                            closeChannel();
                                        }

                                        @Override
                                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                            logger.error("channelId: {}, cause : {}", ctx.channel().id(), cause.getMessage());
                                            closeChannel();
                                        }
                                    });
                        }
                    });

            //init remote attr and getProxy
            InetSocketAddress proxyAddress = getProxyAddress();
            remoteBootstrap.connect(proxyAddress).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) { // 连接成功
                    this.remoteChannel = future.channel(); // 获取远程通道
                    // 建立本地通道和远程通道关联
                    this.clientChannel.attr(ClientContextConstant.REMOTE_CHANNEL).setIfAbsent(this.remoteChannel);
                    // 本地通道响应给浏览器连接建立成功
                    clientChannel.writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS, Socks5AddressType.IPv4));
                    // 本地通道添加数据变换(加密和解密)处理器
                    clientChannel.pipeline().addLast("socks5-transfer", new TransferFlowHandler());

                    if (logger.isDebugEnabled()) {
                        logger.debug("host: [{}:{}] connect success, client channelId is [{}],  remote channelId is [{}]", remoteChannel.id());
                    }
                } else {
                    logger.error("channelId: {}, cause : {}", future.channel().id(), future.cause().getMessage());
                    closeChannel();
                }
            });
        }
    }
    /**
     * close channel
     */
    private void closeChannel() {
        if (remoteChannel != null) {
            remoteChannel.close();
            remoteChannel = null;
        }

        if (clientChannel != null) {
            clientChannel.close();
            clientChannel = null;
        }

        if (remoteBootstrap != null) {
            remoteBootstrap = null;
        }
    }

    /**
     * get proxyAddress from config
     *
     * @return inetSocketAddress
     */
    private InetSocketAddress getProxyAddress() {
        return new InetSocketAddress(ClientConfig.clientConfig.getServer(),ClientConfig. clientConfig.getServer_port());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            ctx.channel().close();
            return;
        }
        super.userEventTriggered(ctx, evt);
    }
}
