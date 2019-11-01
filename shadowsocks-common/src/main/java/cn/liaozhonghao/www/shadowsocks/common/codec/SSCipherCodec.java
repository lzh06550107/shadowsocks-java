package cn.liaozhonghao.www.shadowsocks.common.codec;

import cn.liaozhonghao.www.shadowsocks.common.cipher.AbstractCipher;
import cn.liaozhonghao.www.shadowsocks.common.common.SSCommon;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.List;

public class SSCipherCodec extends MessageToMessageCodec<Object, Object> {

    private static InternalLogger logger = InternalLoggerFactory.getInstance(SSCipherCodec.class);
    private AbstractCipher cipher;

    public SSCipherCodec(AbstractCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
        ByteBuf buf;
        if (msg instanceof DatagramPacket) {
            buf = ((DatagramPacket) msg).content();
        } else if (msg instanceof ByteBuf) {
            buf = (ByteBuf) msg;
        } else {
            throw new Exception("unsupported msg type:" + msg.getClass());
        }

        logger.debug("encode msg size:" + buf.readableBytes());
        // 获取加密方式对象
        byte[] originBytes = new byte[buf.readableBytes()];
        buf.readBytes(originBytes);
        // 加密数据
        byte[] encryptedData = cipher.encodeBytes(originBytes);
        if (encryptedData == null || encryptedData.length == 0) {
            return;
        }
//        logger.debug("encode after encryptedData size:{}",encryptedData.length);
        buf.retain().clear().writeBytes(encryptedData); // 清空并写入加密数据
        out.add(msg);
        logger.debug("encode done:");
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
        logger.debug("decode msg size:" + buf.readableBytes());
        byte[] data = cipher.decodeBytes(buf);
        if (data == null || data.length == 0) {
            return;
        }
        logger.debug((ctx.channel().attr(SSCommon.IS_UDP).get() ? "(UDP)" : "(TCP)") + " decode after:" + data.length);
//        logger.debug("channel id:{}  decode text:{}", ctx.channel().id(), new String(data, Charset.forName("gbk")));
        buf.retain().clear().writeBytes(data);
        out.add(buf);//
    }
}
