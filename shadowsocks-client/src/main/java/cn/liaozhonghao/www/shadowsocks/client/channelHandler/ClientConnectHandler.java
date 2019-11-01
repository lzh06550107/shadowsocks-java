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
package cn.liaozhonghao.www.shadowsocks.client.channelHandler;

import cn.liaozhonghao.www.shadowsocks.client.config.ClientConfig;
import cn.liaozhonghao.www.shadowsocks.common.cipher.AbstractCipher;
import cn.liaozhonghao.www.shadowsocks.common.cipher.CipherProvider;
import cn.liaozhonghao.www.shadowsocks.common.codec.SSCipherCodec;
import cn.liaozhonghao.www.shadowsocks.common.codec.SSProtocolCodec;
import cn.liaozhonghao.www.shadowsocks.common.common.RelayHandler;
import cn.liaozhonghao.www.shadowsocks.common.common.SSAddrRequest;
import cn.liaozhonghao.www.shadowsocks.common.common.SSCommon;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socks.SocksAddressType;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

@ChannelHandler.Sharable
public final class ClientConnectHandler extends SimpleChannelInboundHandler<SocksMessage> {
    private static InternalLogger logger = InternalLoggerFactory.getInstance(ClientConnectHandler.class);

    public static final ClientConnectHandler INSTANCE = new ClientConnectHandler();

    private ClientConnectHandler() {}

    @Override
    public void channelRead0(final ChannelHandlerContext ctx, final SocksMessage message) throws Exception {

        if (message instanceof Socks5CommandRequest) {
            final Socks5CommandRequest request = (Socks5CommandRequest) message;

            // 请求远程资源地址
            SSAddrRequest addr = new SSAddrRequest(SocksAddressType.valueOf(request.dstAddrType().byteValue()), request.dstAddr(), request.dstPort());
            logger.info("client {} connection remote {}", ctx.channel().remoteAddress(), addr.host()+ ":" + addr.port());

            // 客户端与 Socks 服务器之间建立的 Channel 称为 InboundChannel
            final Channel inboundChannel = ctx.channel();
            Bootstrap remoteBootstrap = new Bootstrap();
            remoteBootstrap.group(inboundChannel.eventLoop())
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {

                        @Override
                        protected void initChannel(SocketChannel outboundChannel) throws Exception {
                            // 当远程连接建立成功后，则清除该处理器实例
                            ctx.pipeline().remove(ClientConnectHandler.this);

                            outboundChannel.attr(SSCommon.IS_UDP).setIfAbsent(false);
                            // 把目的地址加入频道属性
                            outboundChannel.attr(SSCommon.REMOTE_DES).setIfAbsent(addr);

                            AbstractCipher cipher = CipherProvider.getByName(ClientConfig.clientConfig.getMethod(), ClientConfig.clientConfig.getPassword());

                            // 本地通道数据写入到远程连接通道
                            ctx.pipeline().addLast(new RelayHandler(outboundChannel));

                            // 远程连接通道数据写入到本地通道
                            outboundChannel.pipeline()
                                    .addLast(new SSCipherCodec(cipher))
                                    .addLast(new SSProtocolCodec(true))
                                    .addLast(new RelayHandler(ctx.channel()));
                        }
                    }); // 发出远程连接，连接成功则回调channelActive

            // 连接到指定的 IPV4 地址和端口
            remoteBootstrap.connect(ClientConfig.clientConfig.getServer_host(), ClientConfig.clientConfig.getServer_port()).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) { // 如果是连接建立成功
                        // 向客户端写出远程连接建立成功的响应，返回 responseFuture
                        ctx.channel().writeAndFlush(new DefaultSocks5CommandResponse(
                                        Socks5CommandStatus.SUCCESS, // 表示命令执行成功
                                        request.dstAddrType(),
                                        request.dstAddr(),
                                        request.dstPort()));

                    } else {
                        // 连接失败时向客户端写出失败响应
                        ctx.channel().writeAndFlush(new DefaultSocks5CommandResponse(
                                Socks5CommandStatus.FAILURE, request.dstAddrType()));
                        // 关闭通道
                        ctx.channel().close();
                    }
                }
            });
        } else {
            ctx.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.channel().close();
    }
}
