package cn.liaozhonghao.www.shadowsocks.common.codec;

import cn.liaozhonghao.www.shadowsocks.common.common.SSAddrRequest;
import cn.liaozhonghao.www.shadowsocks.common.common.SSCommon;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.List;

/**
 * https://www.shadowsocks.org/en/spec/Protocol.html
 * [1-byte type][variable-length host][2-byte port]
 * The following address types are defined:
 * <p>
 * 0x01: host is a 4-byte IPv4 address.
 * 0x03: host is a variable length string, starting with a 1-byte length, followed by up to 255-byte domain name.
 * 0x04: host is a 16-byte IPv6 address.
 * The port number is a 2-byte big-endian unsigned integer.
 **/
public class SSProtocolCodec extends MessageToMessageCodec<Object, Object> {
    private static InternalLogger logger = InternalLoggerFactory.getInstance(SSProtocolCodec.class);
    private boolean isSSLocal = false; // 是本地端还是远程端，默认为远程端
    private boolean isNeedTcpAddressed = true; // 底层协议如果是tcp，则通过该变量控制第一次与远程代理端建立连接时需要传入请求远程资源地址

    public SSProtocolCodec() {
        this(false);
    }

    public SSProtocolCodec(boolean isSSLocal) {
        super();
        this.isSSLocal = isSSLocal;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {

        ByteBuf buf;
        if (msg instanceof DatagramPacket) { // udp包
            buf = ((DatagramPacket) msg).content();
        } else if (msg instanceof ByteBuf) {
            buf = (ByteBuf) msg;
        } else {
            throw new Exception("unsupported msg type:" + msg.getClass());
        }

        logger.debug("encode {}", buf.readableBytes());
        //组装ss协议
        //udp [target address][payload]
        //tcp only [payload]
        boolean isUdp = ctx.channel().attr(SSCommon.IS_UDP).get(); // 底层协议是udp还是tcp

        SSAddrRequest ssAddr = null;
        if (isUdp) {
            // 如果是本地端，则使用远程资源地址，如果是服务端，则使用
            ssAddr = ctx.channel().attr(
                    !isSSLocal ? SSCommon.REMOTE_SRC : SSCommon.REMOTE_DES
            ).get();
            logger.debug("addr {}", ssAddr);
        } else if (isSSLocal && isNeedTcpAddressed) { // tcp
            ssAddr = ctx.channel().attr(SSCommon.REMOTE_DES).get();
            isNeedTcpAddressed = false; // 接下来数据传输不需要包头
        }

        if (ssAddr == null) {
            buf.retain(); // 增加引用计数
        } else {

            ByteBuf addrBuff = Unpooled.buffer(128);
            ssAddr.encodeAsByteBuf(addrBuff);

            buf = Unpooled.wrappedBuffer(addrBuff, buf.retain());
            logger.debug("encode {}", buf.readableBytes());
        }

        if (msg instanceof DatagramPacket) {
            msg = ((DatagramPacket) msg).replace(buf);
        } else { // tcp
            msg = buf;
        }

        out.add(msg);
        logger.debug("encode done");
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
        ByteBuf buf;
        if (msg instanceof DatagramPacket) {
            buf = ((DatagramPacket) msg).content();
        } else if (msg instanceof ByteBuf) {
            buf = (ByteBuf) msg;
        } else {
            throw new Exception("unsupported msg type:" + msg.getClass());
        }

        if (buf.readableBytes() < 1 + 1 + 2) {// [1-byte type][variable-length host][2-byte port]
            return;
        }

        Boolean isUdp = ctx.channel().attr(SSCommon.IS_UDP).get();

        logger.debug("dataBuff readableBytes: {}", buf.readableBytes());

        if (isUdp || (!isSSLocal && isNeedTcpAddressed)) { // 服务端第一次接收tcp包
            SSAddrRequest addrRequest = SSAddrRequest.getAddrRequest(buf); // 解析地址
            if (addrRequest == null) {
                logger.error("fail to get address request from {},pls check client's cipher setting", ctx.channel().remoteAddress());
                if (!ctx.channel().attr(SSCommon.IS_UDP).get()) {
                    ctx.close();
                }
                return;
            }
            logger.debug(ctx.channel().id().toString() + " addressType = " + addrRequest.addressType() + ",host = " + addrRequest.host() + ",port = " + addrRequest.port() + ",dataBuff = "
                    + buf.readableBytes());

            // 如果是服务端，则设置为远程资源地址，如果是本地端，则设置为
            ctx.channel().attr(
                    !isSSLocal ? SSCommon.REMOTE_DES : SSCommon.REMOTE_SRC
            ).set(addrRequest);

            if (!isUdp && isNeedTcpAddressed) {
                isNeedTcpAddressed = false;
            }
        }
        out.add(buf.retain());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        logger.error("client {},error :{}", ctx.channel().remoteAddress(), cause.getMessage());
    }
}
