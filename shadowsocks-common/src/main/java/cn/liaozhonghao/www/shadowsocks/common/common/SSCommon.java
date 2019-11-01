package cn.liaozhonghao.www.shadowsocks.common.common;

import cn.liaozhonghao.www.shadowsocks.common.cipher.AbstractCipher;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.util.AttributeKey;

import java.net.InetSocketAddress;

public class SSCommon {
    public static AttributeKey<AbstractCipher> SOCKS5_CLIENT_CIPHER = AttributeKey.valueOf("channelHandler client cipher");
    public static final AttributeKey<Boolean> IS_UDP = AttributeKey.valueOf("ssIsUdp");
    public static final AttributeKey<InetSocketAddress> CLIENT_Addr = AttributeKey.valueOf("ssclient"); // 客户端地址
    public static final AttributeKey<SSAddrRequest> REMOTE_DES = AttributeKey.valueOf("ssremotedes"); // 远程请求资源地址
    public static final AttributeKey<SSAddrRequest> REMOTE_SRC = AttributeKey.valueOf("ssremotesrc"); // 远程代理服务端地址
    public static final AttributeKey<Socks5CommandRequest> REMOTE_DES_SOCKS5 = AttributeKey.valueOf("socks5remotedes");

}
