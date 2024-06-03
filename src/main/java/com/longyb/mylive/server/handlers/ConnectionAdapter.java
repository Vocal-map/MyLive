package com.longyb.mylive.server.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

/**
 * ConnectionAdapter 类用于处理连接相关的事件，如连接建立、断开等。
 */
@Slf4j
public class ConnectionAdapter extends ChannelInboundHandlerAdapter {

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		// 当一个新的客户端连接被接受后，这个方法会被调用。
		log.info("channel active:" + ctx.channel().remoteAddress());
		
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		// 连接关闭时调用
		log.info("channel inactive:" + ctx.channel().remoteAddress());
		// 事件传播
		ctx.fireChannelInactive();
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		log.error("channel exceptionCaught:" + ctx.channel().remoteAddress(), cause);

	}
}
