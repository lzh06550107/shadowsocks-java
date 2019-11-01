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

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.v5.*;

@ChannelHandler.Sharable
public final class SocksServerHandler extends SimpleChannelInboundHandler<SocksMessage> {

    public static final SocksServerHandler INSTANCE = new SocksServerHandler();

    private SocksServerHandler() { }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, SocksMessage socksRequest) throws Exception {
        switch (socksRequest.version()) {
            case SOCKS5:
                // 处理socks协议初始连接请求
                if (socksRequest instanceof Socks5InitialRequest) {
                    // auth support example
                    //ctx.pipeline().addFirst(new Socks5PasswordAuthRequestDecoder()); // 添加密码授权请求解码器
                    //ctx.write(new DefaultSocks5AuthMethodResponse(Socks5AuthMethod.PASSWORD)); // 告诉客户端用密码授权
                    // 在处理链前面添加命令请求解码器
                    ctx.pipeline().addFirst(new Socks5CommandRequestDecoder());
                    // 告诉客户端不用授权
                    ctx.write(new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH));
                } else if (socksRequest instanceof Socks5PasswordAuthRequest) { // 处理socks协议密码授权请求
                    // 添加命令请求解码器
                    ctx.pipeline().addFirst(new Socks5CommandRequestDecoder());
                    // 告诉客户端授权认证成功
                    ctx.write(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS));
                } else if (socksRequest instanceof Socks5CommandRequest) { // 处理socks协议命令请求
                    Socks5CommandRequest socks5CmdRequest = (Socks5CommandRequest) socksRequest;
                    // 处理连接命令
                    if (socks5CmdRequest.type() == Socks5CommandType.CONNECT) {
                        // 添加连接命令处理器，建立与远程目的服务器连接
                        ctx.pipeline().addLast(ClientConnectHandler.INSTANCE);
                        // 移除当前处理器
                        ctx.pipeline().remove(this);
                        // 调用下一个处理器的channelRead()方法
                        ctx.fireChannelRead(socksRequest);
                    } else {
                        ctx.close();
                    }
                } else {
                    ctx.close();
                }
                break;
            default:
                ctx.close();
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable throwable) {
        throwable.printStackTrace();
        ctx.channel().close();
    }
}
