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
package cn.liaozhonghao.www.shadowsocks.common.common;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

public final class RelayHandler extends ChannelInboundHandlerAdapter {

    private static InternalLogger logger = InternalLoggerFactory.getInstance(RelayHandler.class);

    private final Channel relayChannel;

    public RelayHandler(Channel relayChannel) {
        this.relayChannel = relayChannel;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (relayChannel.isActive()) { // 如果中继管道建立成功，则直接把数据写入该管道
            relayChannel.write(msg);
        } else { // 如果中继管道异常，则释放该消息缓存
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        relayChannel.flush();
    }

    // 在连接断开时都会触发 channelInactive 方法
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (relayChannel.isActive()) { // 如果当前通道断开，则中继通道也需要关闭
            SSAddrRequest addr = relayChannel.attr(SSCommon.REMOTE_DES).get();
            if(addr != null) {
                logger.info("id:{} remote connection {} closed!", ctx.channel().id(),addr.host() + ":" + addr.port());
            }
            relayChannel.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close(); // 关闭通道
    }
}
