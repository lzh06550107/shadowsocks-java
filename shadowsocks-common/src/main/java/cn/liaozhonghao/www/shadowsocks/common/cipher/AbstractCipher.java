package cn.liaozhonghao.www.shadowsocks.common.cipher;

import cn.liaozhonghao.www.shadowsocks.common.codec.SSCipherCodec;
import cn.liaozhonghao.www.shadowsocks.common.util.ShadowsocksUtils;
import io.netty.buffer.ByteBuf;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.security.SecureRandom;

/**
 * 解密/加密工具
 */
public abstract class AbstractCipher {

    private static InternalLogger logger = InternalLoggerFactory.getInstance(AbstractCipher.class);

    /**
     * cipher name
     */
    private String cipherName;
    /**
     * key
     */
    private byte[] key = null;

    public AbstractCipher(String cipherName, String password) {
        this.cipherName = cipherName;
        key = ShadowsocksUtils.getShadowsocksKey(password, getKeyLength());
    }

    /**
     * 解密
     *
     * @param secretByteBuf 密文
     * @return 明文
     */
    public abstract byte[] decodeBytes(ByteBuf secretByteBuf);

    /**
     * 解密
     *
     * @param secretBytes 密文
     * @return 明文
     */
    public abstract byte[] decodeBytes(byte[] secretBytes);

    /**
     * 加密
     *
     * @param originBytes 明文
     * @return 密文
     */
    public abstract byte[] encodeBytes(byte[] originBytes);

    /**
     * 获取密钥长度
     *
     * @return 密钥长度
     */
    public abstract int getKeyLength();

    /**
     * 生成随机数 byte
     *
     * @param size 位数
     * @return random of bytes
     */
    protected byte[] getRandomBytes(int size) {
        byte[] bytes = new byte[size];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    public byte[] getKey() {
        return key;
    }

    public String getCipherName() {
        return cipherName;
    }
}
