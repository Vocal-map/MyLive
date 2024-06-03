package com.longyb.mylive.server;

import com.longyb.mylive.server.handlers.ChunkDecoder;
import com.longyb.mylive.server.handlers.ChunkEncoder;
import com.longyb.mylive.server.handlers.ConnectionAdapter;
import com.longyb.mylive.server.handlers.HandShakeDecoder;
import com.longyb.mylive.server.handlers.RtmpMessageHandler;
import com.longyb.mylive.server.manager.StreamManager;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import lombok.extern.slf4j.Slf4j;

/**
 * RTMPServer 类用于启动RTMP服务。
 */
@Slf4j
public class RTMPServer {

	private int port;

	ChannelFuture channelFuture;

	EventLoopGroup eventLoopGroup;
	StreamManager streamManager;
	int handlerThreadPoolSize;
	
	
	public RTMPServer(int port, StreamManager sm,int threadPoolSize) {
		this.port = port;
		this.streamManager = sm;
		this.handlerThreadPoolSize = threadPoolSize;
	}

	public void run() throws Exception {
		// 使用NIO处理IO操作
		eventLoopGroup = new NioEventLoopGroup();
		// 服务器基本配置
		ServerBootstrap b = new ServerBootstrap();
		// 线程池
		DefaultEventExecutorGroup executor = new DefaultEventExecutorGroup(handlerThreadPoolSize);
		b.group(eventLoopGroup).channel(NioServerSocketChannel.class)
				.childHandler(new ChannelInitializer<SocketChannel>() {
					@Override
					public void initChannel(SocketChannel ch) throws Exception {
						// 添加一系列处理器
						// ConnectionAdapter：用于处理连接相关的事件，如连接建立、断开等。
						// HandShakeDecoder：用于处理握手过程的解码器，用于解析客户端发送的握手消息。
						// ChunkDecoder：用于处理RTMP协议中的块（chunk）解码，将接收到的数据块转换为可处理的消息对象。
						// ChunkEncoder：用于处理RTMP协议中的块（chunk）编码，将消息对象转换为数据块并发送给客户端。
						// RtmpMessageHandler：用于处理RTMP协议中的各种消息，如连接请求、播放、暂停等。这个处理器需要传入一个streamManager参数，用于管理流媒体资源。
						ch.pipeline()
								.addLast(new ConnectionAdapter())  // in
								.addLast(new HandShakeDecoder())   // in
								.addLast(new ChunkDecoder())       // in
								.addLast(new ChunkEncoder())       // out
								.addLast(executor, new RtmpMessageHandler(streamManager));  // in
					}
				})
				.option(ChannelOption.SO_BACKLOG, 128)
				.childOption(ChannelOption.SO_KEEPALIVE, true);

		channelFuture = b.bind(port).sync();
		
		log.info("RTMP Server start , listen at :{}",port);
	}

	public void close() {
		try {
			channelFuture.channel().closeFuture().sync();
			eventLoopGroup.shutdownGracefully();
		} catch (Exception e) {
			log.error("close rtmp server failed", e);
		}
	}
}
