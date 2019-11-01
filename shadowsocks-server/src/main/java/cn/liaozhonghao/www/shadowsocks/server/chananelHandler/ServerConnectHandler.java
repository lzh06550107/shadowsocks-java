/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package cn.liaozhonghao.www.shadowsocks.server.chananelHandler;

import cn.liaozhonghao.www.shadowsocks.common.common.RelayHandler;
import cn.liaozhonghao.www.shadowsocks.common.common.SSAddrRequest;
import cn.liaozhonghao.www.shadowsocks.common.common.SSCommon;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

@ChannelHandler.Sharable
public final class ServerConnectHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private static InternalLogger logger = InternalLoggerFactory.getInstance(ServerConnectHandler.class);

    public static final ServerConnectHandler INSTANCE = new ServerConnectHandler();

    private ServerConnectHandler() {
    }

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, final ByteBuf message) throws Exception {

        // 客户端与 Socks 服务器之间建立的 Channel 称为 InboundChannel
        Bootstrap remoteBootstrap = new Bootstrap();
        final Channel inboundChannel = ctx.channel();
        remoteBootstrap.group(inboundChannel.eventLoop())
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel outboundChannel) throws Exception {

                        // 当远程连接建立成功后，则清除该处理器实例
                        ctx.pipeline().remove(ServerConnectHandler.this);

                        // 本地通道数据写入到远程连接通道
                        ctx.pipeline().addLast(new RelayHandler(outboundChannel));
                        // 远程连接通道数据写入到本地通道
                        outboundChannel.pipeline().addLast(new RelayHandler(ctx.channel()));
                    }
                });

        SSAddrRequest remote = ctx.channel().attr(SSCommon.REMOTE_DES).get();
        message.retain(); // 这里需要增加引用计数，保证方法调用结束时，message还能使用
        // 连接到指定的 IPV4 地址和端口
        remoteBootstrap.connect(remote.host(), remote.port()).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {

                    logger.info("client {} connction remote {} success!", ctx.channel().remoteAddress(), remote.host() + ":" + remote.port());

                    // 把第一次请求包剩余数据写入到中继通道
                    future.channel().writeAndFlush(message.retain());
                } else {
                    // Close the connection if the connection attempt has failed.
                    ctx.channel().close();
                }
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.channel().close();
    }
}
