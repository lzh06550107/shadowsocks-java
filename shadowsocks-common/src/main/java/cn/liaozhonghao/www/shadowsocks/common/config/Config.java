package cn.liaozhonghao.www.shadowsocks.common.config;

public abstract class Config {

    private String listen_host;
    // 本地监听端口
    private int listen_port;

    // 加密密码
    private String password;

    // 加密方法
    private String method;

    // 混淆方式
    private String obfs;

    // boss线程数
    private int bossThreadNumber;

    // worker线程数
    private int workersThreadNumber;

    public String getObfs() {
        return obfs;
    }

    public void setObfs(String obfs) {
        this.obfs = obfs;
    }

    public int getListen_port() {
        return listen_port;
    }

    public String getListen_host() {
        return listen_host;
    }

    public void setListen_host(String listen_host) {
        this.listen_host = listen_host;
    }

    public void setListen_port(int listen_port) {
        this.listen_port = listen_port;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public int getBossThreadNumber() {
        return bossThreadNumber;
    }

    public void setBossThreadNumber(int bossThreadNumber) {
        this.bossThreadNumber = bossThreadNumber;
    }

    public int getWorkersThreadNumber() {
        return workersThreadNumber;
    }

    public void setWorkersThreadNumber(int workersThreadNumber) {
        this.workersThreadNumber = workersThreadNumber;
    }

}
