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
package cn.liaozhonghao.www.shadowsocks.client;

import cn.liaozhonghao.www.shadowsocks.client.config.ClientConfig;
import cn.liaozhonghao.www.shadowsocks.client.channelHandler.SocksServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

public final class SSLocal {

    private static Logger logger = LoggerFactory.getLogger(SSLocal.class);

    private static Options OPTIONS = new Options();
    private static CommandLine commandLine;
    private static String HELP_STRING = null;

    public static void main(String[] args) throws InterruptedException {
        initCliArgs(args);
        startupTCP();
    }

    private static void initCliArgs(String[] args) {
        // validate args
        {
            CommandLineParser commandLineParser = new DefaultParser();
            // help
            OPTIONS.addOption("help","usage help");
            // remote ip
            OPTIONS.addOption(Option.builder("s").longOpt("server").required().hasArg(true).type(String.class).desc("remote ip").build());
            // remote port
            OPTIONS.addOption(Option.builder("P").longOpt("port").hasArg(true).type(Integer.class).desc("remote port").build());
            // remote password
            OPTIONS.addOption(Option.builder("p").longOpt("password").required().hasArg(true).type(String.class).desc("remote secret key").build());
            // remote encrypt method
            OPTIONS.addOption(Option.builder("m").longOpt("method").required().hasArg(true).type(String.class).desc("encrypt method").build());
            // local port
            OPTIONS.addOption(Option.builder("l").longOpt("local_port").required().hasArg(true).type(String.class).desc("local expose port").build());
            try {
                commandLine = commandLineParser.parse(OPTIONS, args);
            } catch (ParseException e) {
                logger.error(e.getMessage() + "\n" + getHelpString());
                System.exit(0);
            }
        }

        // init clientConfigure
        {
            if(commandLine.hasOption("help")){
                logger.error("\n" + getHelpString());
                System.exit(1);
            }

            // remote ip
            ClientConfig.clientConfig.setServer(commandLine.getOptionValue("s"));
            // remote port
            ClientConfig.clientConfig.setServer_port(Integer.parseInt(commandLine.getOptionValue("P")));
            // remote secret key
            ClientConfig.clientConfig.setPassword(commandLine.getOptionValue("p"));
            // encrypt key
            ClientConfig.clientConfig.setMethod(commandLine.getOptionValue("m"));
            // method
            ClientConfig.clientConfig.setLocal_port(Integer.parseInt(commandLine.getOptionValue("l")));
        }

    }

    private static String getHelpString() {
        if (HELP_STRING == null) {
            HelpFormatter helpFormatter = new HelpFormatter();

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            PrintWriter printWriter = new PrintWriter(byteArrayOutputStream);
            helpFormatter.printHelp(printWriter, HelpFormatter.DEFAULT_WIDTH, "java -jar shadowsocks-client-xxx.jar -help", null,
                    OPTIONS, HelpFormatter.DEFAULT_LEFT_PAD, HelpFormatter.DEFAULT_DESC_PAD, null);
            printWriter.flush();
            HELP_STRING = new String(byteArrayOutputStream.toByteArray());
            printWriter.close();
        }
        return HELP_STRING;
    }

    public static void startupTCP() throws InterruptedException {
        // 使用单线程来处理请求连接
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        // 使用多线程来处理读写IO
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
             //.handler(new LoggingHandler(LogLevel.INFO)) // 记录连接信息
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 public void initChannel(SocketChannel ch) {
                     ch.pipeline().addLast(
                             new LoggingHandler(LogLevel.INFO), // IO读写日志
                             new SocksPortUnificationServerHandler(), // socks统一服务处理器，检查协议版本并注册对应版本的初始化处理器
                             SocksServerHandler.INSTANCE); // 单例模式的处理器，传入前面解码的Socks5InitialRequest对象
                 }
             }); // socks服务器初始化处理器
            int port = ClientConfig.clientConfig.getLocal_port();
            ChannelFuture channelFuture = b.bind(port).sync();
            logger.info("shadowsocks channelHandler client [TCP] running at {}", port);
            channelFuture.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
