package cn.liaozhonghao.www.shadowsocks.server;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import cn.liaozhonghao.www.shadowsocks.common.cipher.CipherProvider;
import cn.liaozhonghao.www.shadowsocks.common.codec.SSCipherCodec;
import cn.liaozhonghao.www.shadowsocks.common.codec.SSProtocolCodec;
import cn.liaozhonghao.www.shadowsocks.common.common.SSCommon;
import cn.liaozhonghao.www.shadowsocks.common.obfs.ObfsFactory;
import cn.liaozhonghao.www.shadowsocks.server.chananelHandler.ServerConnectHandler;
import cn.liaozhonghao.www.shadowsocks.server.config.ServerConfig;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.apache.commons.cli.*;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.util.List;

/**
 * server start
 */
public class SSServer {
    /**
     * static logger
     */
    private static InternalLogger logger = InternalLoggerFactory.getInstance(SSServer.class);

    /**
     * boosLoopGroup
     */
    private static EventLoopGroup bossLoopGroup = new NioEventLoopGroup(ServerConfig.serverConfig.getBossThreadNumber());
    /**
     * worksLoopGroup
     */
    private static EventLoopGroup worksLoopGroup = new NioEventLoopGroup(ServerConfig.serverConfig.getWorkersThreadNumber());
    /**
     * serverBootstrap
     */
    private static ServerBootstrap serverBootstrap = new ServerBootstrap();

    public static void main(String[] args) throws InterruptedException {
        initCliArgs(args);
        startupTCP();
    }

    public static void startupTCP() throws InterruptedException {
        serverBootstrap.group(bossLoopGroup, worksLoopGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<Channel>() {
                    protected void initChannel(Channel ch) throws Exception {
                        ch.attr(SSCommon.IS_UDP).set(false);

                        //obfs pugin，混淆插件
//                        List<ChannelHandler> obfsHandlers = ObfsFactory.getObfsHandler(ServerConfig.serverConfig.getObfs());
//                        if (obfsHandlers != null) {
//                            for (ChannelHandler obfsHandler : obfsHandlers) {
//                                ch.pipeline().addLast(obfsHandler);
//                            }
//                        }

                        ch.pipeline()
                                .addLast(new SSCipherCodec(CipherProvider.getByName(ServerConfig.serverConfig.getMethod(), ServerConfig.serverConfig.getPassword())))
                                .addLast(new SSProtocolCodec(false))
                                .addLast(ServerConnectHandler.INSTANCE);
                    }
                });

        String localIp = ServerConfig.serverConfig.getListen_host();
        int localPort = ServerConfig.serverConfig.getListen_port();

        InetSocketAddress localAddress = "0.0.0.0".equals(localIp) || "::0".equals(localIp) ? new InetSocketAddress(localPort) : new InetSocketAddress(localIp, localPort);

        ChannelFuture channelFuture = serverBootstrap.bind(localAddress).sync();
        logger.info("shadowsocks server [tcp] running at {}:{}", localAddress.getHostName(), localAddress.getPort());
        channelFuture.channel().closeFuture().sync();
    }


    private static Options OPTIONS = new Options();
    private static CommandLine commandLine;
    private static String HELP_STRING = null;
    /**
     * init args
     *
     * @param args args
     */
    private static void initCliArgs(String[] args) {
        // validate args
        {
            CommandLineParser commandLineParser = new DefaultParser();
            // help
            OPTIONS.addOption("help","usage help");
            // address
            OPTIONS.addOption(Option.builder("d").longOpt("listen_address").argName("ip").required(false).type(String.class).desc("address bind").build());
            // port
            OPTIONS.addOption(Option.builder("P").longOpt("listen_port").hasArg(true).type(Integer.class).desc("port bind").build());
            // password
            OPTIONS.addOption(Option.builder("p").longOpt("password").required().hasArg(true).type(String.class).desc("password of ssserver").build());
            // method
            OPTIONS.addOption(Option.builder("m").longOpt("method").required().hasArg(true).type(String.class).desc("encrypt method").build());

            // number of boss thread
            OPTIONS.addOption(Option.builder("bn").longOpt("boss_number").hasArg(true).type(Integer.class).desc("boss thread number").build());
            // number of workers thread
            OPTIONS.addOption(Option.builder("wn").longOpt("workers_number").hasArg(true).type(Integer.class).desc("workers thread number").build());

            // set log level
            OPTIONS.addOption(Option.builder("level").longOpt("log_level").hasArg(true).type(String.class).desc("log level").build());
            try {
                commandLine = commandLineParser.parse(OPTIONS, args);
            } catch (ParseException e) {
                logger.error(e.getMessage() + "\n" + getHelpString());
                System.exit(0);
            }
        }

        // init serverConfigure
        {
            if(commandLine.hasOption("help")){
                logger.error("\n" + getHelpString());
                System.exit(1);
            }

            // address
            String listen_address = commandLine.getOptionValue("d") == null || "".equals(commandLine.getOptionValue("d")) ? "0.0.0.0" : commandLine.getOptionValue("d");
            ServerConfig.serverConfig.setListen_host(listen_address);
            // port
            String portOptionValue = commandLine.getOptionValue("P");
            int listen_port = portOptionValue == null || "".equals(portOptionValue) ? 1080 : Integer.parseInt(portOptionValue);
            ServerConfig.serverConfig.setListen_port(listen_port);
            // password
            ServerConfig.serverConfig.setPassword(commandLine.getOptionValue("p"));
            // method
            ServerConfig.serverConfig.setMethod(commandLine.getOptionValue("m"));

            // boss thread number
            String bossThreadNumber = commandLine.getOptionValue("bn") == null || "".equals(commandLine.getOptionValue("bn")) ? String.valueOf(Runtime.getRuntime().availableProcessors() * 2) : commandLine.getOptionValue("bn");
            ServerConfig.serverConfig.setBossThreadNumber(Integer.parseInt(bossThreadNumber));
            // workers thread number
            String workersThreadNumber = commandLine.getOptionValue("wn") == null || "".equals(commandLine.getOptionValue("wn")) ? String.valueOf(Runtime.getRuntime().availableProcessors() * 2) : commandLine.getOptionValue("wn");
            ServerConfig.serverConfig.setWorkersThreadNumber(Integer.parseInt(workersThreadNumber));

            String levelName = commandLine.getOptionValue("level");
            if(levelName != null && !"".equals(levelName)){
                Level level = Level.toLevel(levelName,Level.INFO);
                logger.info("set log level to " + level.toString());

                LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
                List<ch.qos.logback.classic.Logger> loggerList = loggerContext.getLoggerList();
                for (ch.qos.logback.classic.Logger logger1 : loggerList) {
                    logger1.setLevel(level);
                }
            }
        }

    }

    /**
     * get string of help usage
     *
     * @return help string
     */
    private static String getHelpString() {
        if (HELP_STRING == null) {
            HelpFormatter helpFormatter = new HelpFormatter();

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            PrintWriter printWriter = new PrintWriter(byteArrayOutputStream);
            helpFormatter.printHelp(printWriter, HelpFormatter.DEFAULT_WIDTH, "java -jar shadowsocks-server-xxx.jar -help", null,
                    OPTIONS, HelpFormatter.DEFAULT_LEFT_PAD, HelpFormatter.DEFAULT_DESC_PAD, null);
            printWriter.flush();
            HELP_STRING = new String(byteArrayOutputStream.toByteArray());
            printWriter.close();
        }
        return HELP_STRING;
    }
}
