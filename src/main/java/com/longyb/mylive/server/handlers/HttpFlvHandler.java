package com.longyb.mylive.server.handlers;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;

import java.util.List;

import com.google.common.base.Splitter;
import com.longyb.mylive.server.entities.Stream;
import com.longyb.mylive.server.entities.StreamName;
import com.longyb.mylive.server.manager.StreamManager;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import lombok.extern.slf4j.Slf4j;

import static io.netty.handler.codec.http.HttpHeaderNames.*;

/**
 * 用于处理http-flv协议的请求，将其订阅在对应的stream上
 **/
@Slf4j
public class HttpFlvHandler extends SimpleChannelInboundHandler<HttpObject> {

	StreamManager streamManager;

	public HttpFlvHandler(StreamManager streamManager) {
		this.streamManager = streamManager;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
		if (msg instanceof HttpRequest) {
			HttpRequest req = (HttpRequest) msg;

			String uri = req.uri();
			// 根据分隔符获取所需访问的资源
			List<String> appAndStreamName = Splitter.on("/").omitEmptyStrings().splitToList(uri);
			if (appAndStreamName.size() != 2) {
				httpResponseStreamNotExist(ctx, uri);
				return;
			}

			
			String app=appAndStreamName.get(0);
			String streamName= appAndStreamName.get(1);
			if(streamName.endsWith(".flv")) {
				streamName=streamName.substring(0, streamName.length()-4);
			}
			StreamName sn = new StreamName(app, streamName,false);
			log.info("http stream :{} requested",sn);
			// 获取当前的拉流请求对应的stream
			Stream stream = streamManager.getStream(sn); // 从streamManager中获取stream

			if (stream == null) {
				httpResponseStreamNotExist(ctx, uri);
				return;
			}
			DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
			response.headers().set(CONTENT_TYPE, "video/x-flv");
			response.headers().set(TRANSFER_ENCODING, "chunked");
			//we may need a cross domain configuration in future 
			response.headers().set(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
			response.headers().set(ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, PUT, DELETE");
		    response.headers().set(ACCESS_CONTROL_ALLOW_HEADERS, "Origin, X-Requested-With, Content-Type, Accept");
          
			ctx.writeAndFlush(response); // 先响应http协议

			// 将当前channel加入到stream的httpflv订阅者列表中
			// 由stream来管理当前的channel
			stream.addHttpFlvSubscriber(ctx.channel());

		}

		if (msg instanceof HttpContent) {

		}

	}

	private void httpResponseStreamNotExist(ChannelHandlerContext ctx, String uri) {
		ByteBuf body = Unpooled.wrappedBuffer(("stream [" + uri + "] not exist").getBytes());
		DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
				HttpResponseStatus.NOT_FOUND, body);
		response.headers().set(CONTENT_TYPE, "text/plain");
		ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
	}

}
