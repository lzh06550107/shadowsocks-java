package cn.liaozhonghao.www.shadowsocks.client.config;

import cn.liaozhonghao.www.shadowsocks.common.config.Config;

/**
 * client config
 */
public class ClientConfig extends Config {
    public static ClientConfig clientConfig = new ClientConfig();

    // 远程服务器域名
    private String server_host;

    // 远程服务器端口
    private int server_port;

    private ClientConfig() {
    }

    public String getServer_host() {
        return server_host;
    }

    public void setServer_host(String server_host) {
        this.server_host = server_host;
    }

    public int getServer_port() {
        return server_port;
    }

    public void setServer_port(int server_port) {
        this.server_port = server_port;
    }

}
