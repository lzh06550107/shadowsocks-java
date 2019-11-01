package cn.liaozhonghao.www.shadowsocks.common.common;


import cn.liaozhonghao.www.shadowsocks.common.util.SocksIpUtils;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.socks.SocksAddressType;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;

import java.net.IDN;

/**
 * shadowsock协议地址头
 */
public final class SSAddrRequest {
    private final SocksAddressType addressType;
    private final String host;
    private final int port;

    public SSAddrRequest(SocksAddressType addressType, String host, int port) {
         if (addressType == null) {
            throw new NullPointerException("addressType");
        } else if (host == null) {
            throw new NullPointerException("host");
        } else {
            switch(addressType) {
                case IPv4:
                    if (!NetUtil.isValidIpV4Address(host)) {
                        throw new IllegalArgumentException(host + " is not a valid IPv4 address");
                    }
                    break;
                case DOMAIN:
                    if (IDN.toASCII(host).length() > 255) {
                        throw new IllegalArgumentException(host + " IDN: " + IDN.toASCII(host) + " exceeds 255 char limit");
                    }
                    break;
                case IPv6:
                    if (!NetUtil.isValidIpV6Address(host)) {
                        throw new IllegalArgumentException(host + " is not a valid IPv6 address");
                    }
                case UNKNOWN:
            }

            if (port > 0 && port < 65536) {
                this.addressType = addressType;
                this.host = IDN.toASCII(host);
                this.port = port;
            } else {
                throw new IllegalArgumentException(port + " is not in bounds 0 < x < 65536");
            }
        }
    }


    public SocksAddressType addressType() {
        return this.addressType;
    }

    public String host() {
        return IDN.toUnicode(this.host);
    }

    public int port() {
        return this.port;
    }

    /**
     * 把对象编码为shadowsock协议地址格式
     * @param byteBuf 存储编码后的数据
     */
    public void encodeAsByteBuf(ByteBuf byteBuf) {
        byteBuf.writeByte(this.addressType.byteValue());
        switch(this.addressType) {
            case IPv4:
            case IPv6:
                byteBuf.writeBytes(NetUtil.createByteArrayFromIpAddressString(this.host));
                byteBuf.writeShort(this.port);
                break;
            case DOMAIN:
                byteBuf.writeByte(this.host.length());
                byteBuf.writeBytes(this.host.getBytes(CharsetUtil.US_ASCII));
                byteBuf.writeShort(this.port);
                break;
        }
    }

    /**
     * 解码shadowsocks协议中的地址为对象
     * @param byteBuf 传入包含地址信息的字节码
     * @return
     */
    public static SSAddrRequest getAddrRequest(ByteBuf byteBuf) {
        SSAddrRequest request = null;
        SocksAddressType addressType = SocksAddressType.valueOf(byteBuf.readByte());
        String host;
        int port;
        switch (addressType) {
            case IPv4: {
                host = SocksIpUtils.intToIp(byteBuf.readInt());
                port = byteBuf.readUnsignedShort();
                request = new SSAddrRequest( addressType, host, port);
                break;
            }
            case DOMAIN: {
                int fieldLength = byteBuf.readByte();
                host = SocksIpUtils.readUsAscii(byteBuf, fieldLength);
                port = byteBuf.readUnsignedShort();
                request = new SSAddrRequest( addressType, host, port);
                break;
            }
            case IPv6: {
                byte[] bytes = new byte[16];
                byteBuf.readBytes(bytes);
                host = SocksIpUtils.ipv6toStr(bytes);
                port = byteBuf.readUnsignedShort();
                request = new SSAddrRequest(addressType, host, port);
                break;
            }
            case UNKNOWN:
                break;
        }
        return request;
    }
}
