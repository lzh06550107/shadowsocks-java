package cn.liaozhonghao.www.shadowsocks.common.obfs;

import cn.liaozhonghao.www.shadowsocks.common.obfs.impl.HttpSimpleHandler;
import io.netty.channel.ChannelHandler;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.List;

public class ObfsFactory {

    private static InternalLogger logger = InternalLoggerFactory.getInstance(ObfsFactory.class);

    public static List<ChannelHandler> getObfsHandler(String obfs) {
        switch (obfs) {
            case HttpSimpleHandler.OBFS_NAME:
                return HttpSimpleHandler.getHandlers();
        }
        return null;
    }
}
