package com.longyb.mylive.server;

import com.longyb.mylive.server.handlers.HttpFlvHandler;
import com.longyb.mylive.server.manager.StreamManager;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import lombok.extern.slf4j.Slf4j;

/**
 * HttpFlvServer 类用于启动HTTP-FLV服务。
 */
@Slf4j
public class HttpFlvServer {

	private int port;

	ChannelFuture channelFuture;

	EventLoopGroup eventLoopGroup;
	StreamManager streamManager;
	//not used currently
	int handlerThreadPoolSize;
	

	public HttpFlvServer(int port, StreamManager sm, int threadPoolSize) {
		this.port = port;
		this.streamManager = sm;
		this.handlerThreadPoolSize = threadPoolSize;
	}
	
	
	public void run() throws Exception {
		eventLoopGroup = new NioEventLoopGroup();

		ServerBootstrap b = new ServerBootstrap();
 
		b.group(eventLoopGroup).channel(NioServerSocketChannel.class)
				.childHandler(new ChannelInitializer<SocketChannel>() {
					@Override
					public void initChannel(SocketChannel ch) throws Exception {
						ch.pipeline().addLast(new HttpRequestDecoder());  // in
						ch.pipeline().addLast(new HttpResponseEncoder());  // out
						ch.pipeline().addLast(new HttpFlvHandler(streamManager));  // in
					}
				}).option(ChannelOption.SO_BACKLOG, 128).childOption(ChannelOption.SO_KEEPALIVE, true);

		// 绑定全端口
		channelFuture = b.bind(port).sync();
		log.info("HttpFlv server start , listen at :{}",port);

	}
}

