package com.longyb.mylive.server.entities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.longyb.mylive.amf.AMF0;
import com.longyb.mylive.server.cfg.MyLiveConfig;
import com.longyb.mylive.server.rtmp.Constants;
import com.longyb.mylive.server.rtmp.messages.AudioMessage;
import com.longyb.mylive.server.rtmp.messages.RtmpMediaMessage;
import com.longyb.mylive.server.rtmp.messages.RtmpMessage;
import com.longyb.mylive.server.rtmp.messages.UserControlMessageEvent;
import com.longyb.mylive.server.rtmp.messages.VideoMessage;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.util.ReferenceCountUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 由流管理器管理
 * 用于接收，存储，广播流数据到订阅者
 */
@Data
@Slf4j
public class Stream {  // 不同的推流对应不同的Stream

	/**
	 * 0x46, 0x4C, 0x56 is the ASCII representation for "FLV", which is the signature of an FLV file.
	 * 0x01 is the version of the FLV file format. In this case, it's version 1.
	 * 0x05 is a flag indicating that the FLV file contains both audio and video data.
	 * 00, 00, 00, 0x09 is the offset where the first previous tag size is stored (9 bytes from the start of the file).
	 */
	static byte[] flvHeader = new byte[] { 0x46, 0x4C, 0x56, 0x01, 0x05, 00, 00, 00, 0x09 };

	Map<String, Object> metadata;

	Channel publisher;

	VideoMessage avcDecoderConfigurationRecord;

	AudioMessage aacAudioSpecificConfig;
	Set<Channel> subscribers;

	List<RtmpMediaMessage> content;  //

	StreamName streamName;

	int videoTimestamp;
	int audioTimestamp;

	int obsTimeStamp;

	FileOutputStream flvout;
	boolean flvHeadAndMetadataWritten = false;

	Set<Channel> httpFLvSubscribers;

	public Stream(StreamName streamName) {
		subscribers = new LinkedHashSet<>();
		httpFLvSubscribers = new LinkedHashSet<>();
		content = new ArrayList<>();
		this.streamName = streamName;
		if (MyLiveConfig.INSTANCE.isSaveFlvFile()) {
			createFileStream();
		}
	}

	public synchronized void addContent(RtmpMediaMessage msg) {

		if (streamName.isObsClient()) {
			handleObsStream(msg);
		} else {
			handleNonObsStream(msg);
		}
		
		if(msg instanceof VideoMessage) {
			VideoMessage vm = (VideoMessage)msg;
			if (vm.isAVCDecoderConfigurationRecord()) {
				log.debug("avcDecoderConfigurationRecord  ok");
				avcDecoderConfigurationRecord = vm;
			}
	
			if (vm.isH264KeyFrame()) {
				log.debug("video key frame in stream :{}", streamName);
				content.clear();
			}
		}
		
		if(msg instanceof AudioMessage) {
			AudioMessage am=(AudioMessage) msg;
			if (am.isAACAudioSpecificConfig()) {
				aacAudioSpecificConfig = am;
			}
		}

		content.add(msg);
		if (MyLiveConfig.INSTANCE.isSaveFlvFile()) {
			writeFlv(msg);
		}
		broadCastToSubscribers(msg);
	}

	private void handleNonObsStream(RtmpMediaMessage msg) {
		if (msg instanceof VideoMessage) {
			VideoMessage vm = (VideoMessage) msg;
			if (vm.getTimestamp() != null) {
				// we may encode as FMT1 ,so we need timestamp delta
				vm.setTimestampDelta(vm.getTimestamp() - videoTimestamp);
				videoTimestamp = vm.getTimestamp();
			} else if (vm.getTimestampDelta() != null) {
				videoTimestamp += vm.getTimestampDelta();
				vm.setTimestamp(videoTimestamp);
			}
		}

		if (msg instanceof AudioMessage) {

			AudioMessage am = (AudioMessage) msg;
			if (am.getTimestamp() != null) {
				am.setTimestampDelta(am.getTimestamp() - audioTimestamp);
				audioTimestamp = am.getTimestamp();
			} else if (am.getTimestampDelta() != null) {
				audioTimestamp += am.getTimestampDelta();
				am.setTimestamp(audioTimestamp);
			}

			
		}
	}

	private void handleObsStream(RtmpMediaMessage msg) {
		// OBS rtmp stream is different from FFMPEG
		// it's timestamp_delta is delta of last packet,not same type of last packet

		// flv only require an absolute timestamp
		// but rtmp client like vlc require a timestamp-delta,which is relative to last
		// same media packet.
		if(msg.getTimestamp()!=null) {
			obsTimeStamp=msg.getTimestamp();
		}else		if(msg.getTimestampDelta()!=null) {
			obsTimeStamp += msg.getTimestampDelta();
		}
		msg.setTimestamp(obsTimeStamp);
		if (msg instanceof VideoMessage) {
			msg.setTimestampDelta(obsTimeStamp - videoTimestamp);
			videoTimestamp = obsTimeStamp;
		}
		if (msg instanceof AudioMessage) {
			msg.setTimestampDelta(obsTimeStamp - audioTimestamp);
			audioTimestamp = obsTimeStamp;
		}
	}

	private byte[] encodeMediaAsFlvTagAndPrevTagSize(RtmpMediaMessage msg) {
		int tagType = msg.getMsgType();
		byte[] data = msg.raw();
		int dataSize = data.length;
		int timestamp = msg.getTimestamp() & 0xffffff;
		int timestampExtended = ((msg.getTimestamp() & 0xff000000) >> 24);

		ByteBuf buffer = Unpooled.buffer();
		/*
		FLV Tag = Tag header + Tag data
		Tag header = TagType + DataSize + Timestamp + TimestampExtended + StreamID
		Tag data = tag specific data
		 */
		/*
		Tag Type
		8：audio (0x08)
		9：video (0x09)
		18：script data (0x12)
		 */
		buffer.writeByte(tagType);
		buffer.writeMedium(dataSize);
		buffer.writeMedium(timestamp);
		buffer.writeByte(timestampExtended);// timestampExtended
		buffer.writeMedium(0);// stream_id
		// Tag data
		buffer.writeBytes(data);
		buffer.writeInt(data.length + 11); // prevoursTagSize

		byte[] r = new byte[buffer.readableBytes()];
		buffer.readBytes(r);

		return r;
	}

	private void writeFlv(RtmpMediaMessage msg) {
		if (flvout == null) {
			log.error("no flv file existed for stream : {}", streamName);
			return;
		}
		try {
			if (!flvHeadAndMetadataWritten) {
				writeFlvHeaderAndMetadata();
				flvHeadAndMetadataWritten = true;
			}
			byte[] encodeMediaAsFlv = encodeMediaAsFlvTagAndPrevTagSize(msg);
			flvout.write(encodeMediaAsFlv);
			flvout.flush();

		} catch (IOException e) {
			log.error("writing flv file failed , stream is :{}", streamName, e);
		}
	}

	private byte[] encodeFlvHeaderAndMetadata() {
		ByteBuf encodeMetaData = encodeMetaData();
		ByteBuf buf = Unpooled.buffer();  // 创建一个ByteBuf，用于写入flv header和metadata

		RtmpMediaMessage msg = content.get(0);  // 获取第一个msg
		int timestamp = msg.getTimestamp() & 0xffffff;
		int timestampExtended = ((msg.getTimestamp() & 0xff000000) >> 24);

		/*
		FLV = FLV header + FLV file body
		FLV file body = PreviousTagSize0 + Tag1 + PreviousTagSize1 + Tag2 + ... + PreviousTagSizeN-1 + TagN
		 */
		buf.writeBytes(flvHeader);
		buf.writeInt(0); // previousTagSize0

		/*
		FLV Tag = Tag header + Tag data
		Tag header = TagType + DataSize + Timestamp + TimestampExtended + StreamID
		Tag data = tag specific data
		 */
		/*
		Tag Type
		8：audio (0x08)
		9：video (0x09)
		18：script data (0x12)
		 */
		int readableBytes = encodeMetaData.readableBytes();
		buf.writeByte(0x12); // script
		buf.writeMedium(readableBytes);
		// make the first script tag timestamp same as the keyframe
		buf.writeMedium(timestamp);
		buf.writeByte(timestampExtended);
		// buf.writeInt(0); // timestamp + timestampExtended
		buf.writeMedium(0); // stream_id, 总是0
		// Tag data
		buf.writeBytes(encodeMetaData);  // 写入meta信息
		// readableBytes为meta的长度，11为头的长度
		buf.writeInt(readableBytes + 11);

		byte[] result = new byte[buf.readableBytes()];
		buf.readBytes(result); // 将buf中的数据读取到result中
		return result;
	}

	private void writeFlvHeaderAndMetadata() throws IOException {
		byte[] encodeFlvHeaderAndMetadata = encodeFlvHeaderAndMetadata();
		flvout.write(encodeFlvHeaderAndMetadata);
		flvout.flush();

	}

	private ByteBuf encodeMetaData() {
		ByteBuf buffer = Unpooled.buffer();
		List<Object> meta = new ArrayList<>();
		meta.add("onMetaData");
		meta.add(metadata);
		log.debug("Metadata:{}", metadata);
		AMF0.encode(buffer, meta);

		return buffer;
	}

	private void createFileStream() {
		File f = new File(
				MyLiveConfig.INSTANCE.getSaveFlVFilePath() + 
						"/" + streamName.getApp() + "_" + streamName.getName() + ".flv");
		// 创建文件夹
		if (!f.getParentFile().exists()) {
			f.getParentFile().mkdirs();
		}
		try {
            flvout = new FileOutputStream(f);
		} catch (IOException e) {
			log.error("create file: {} failed", e);
		}

	}

	public synchronized void addSubscriber(Channel channel) {
		subscribers.add(channel);
		log.info("subscriber : {} is added to stream :{}", channel, streamName);
		avcDecoderConfigurationRecord.setTimestamp(content.get(0).getTimestamp());
		log.info("avcDecoderConfigurationRecord:{}", avcDecoderConfigurationRecord);
		channel.writeAndFlush(avcDecoderConfigurationRecord);

		for (RtmpMessage msg : content) {
			channel.writeAndFlush(msg);
		}
	}

	public synchronized void addHttpFlvSubscriber(Channel channel) {
		httpFLvSubscribers.add(channel);
		log.info("http flv subscriber : {} is added to stream :{}", channel, streamName);

		// 1. write flv header and metaData (第一个tag)
		byte[] meta = encodeFlvHeaderAndMetadata();
		channel.writeAndFlush(Unpooled.wrappedBuffer(meta));
		// 2 send config
		// 2.1 write avcDecoderConfigurationRecord
		if (avcDecoderConfigurationRecord != null) {
			avcDecoderConfigurationRecord.setTimestamp(content.get(0).getTimestamp());
			byte[] avc = encodeMediaAsFlvTagAndPrevTagSize(avcDecoderConfigurationRecord);
			channel.writeAndFlush(Unpooled.wrappedBuffer(avc));
		}

		// 2.2 write aacAudioSpecificConfig
		if (aacAudioSpecificConfig != null) {
			aacAudioSpecificConfig.setTimestamp(content.get(0).getTimestamp());
			byte[] aac = encodeMediaAsFlvTagAndPrevTagSize(aacAudioSpecificConfig);
			channel.writeAndFlush(Unpooled.wrappedBuffer(aac));
		}
		// 3. write content
		for (RtmpMediaMessage msg : content) {
			channel.writeAndFlush(Unpooled.wrappedBuffer(encodeMediaAsFlvTagAndPrevTagSize(msg)));
		}

	}

	// 向所有的订阅者推送消息
	private synchronized void broadCastToSubscribers(RtmpMediaMessage msg) {
		Iterator<Channel> iterator = subscribers.iterator();
		while (iterator.hasNext()) {
			Channel next = iterator.next();
			if (next.isActive()) {
				next.writeAndFlush(msg);
			} else {
				iterator.remove();
			}
		}

		if (!httpFLvSubscribers.isEmpty()) {
			byte[] encoded = encodeMediaAsFlvTagAndPrevTagSize(msg);

			Iterator<Channel> httpIte = httpFLvSubscribers.iterator();
			while (httpIte.hasNext()) {
				Channel next = httpIte.next();
				ByteBuf wrappedBuffer = Unpooled.wrappedBuffer(encoded);
				if (next.isActive()) {
					next.writeAndFlush(wrappedBuffer);
				} else {
					log.info("http channel :{} is not active remove", next);
					httpIte.remove();
				}

			}
		}

	}

	// 推送结束标识
	public synchronized void sendEofToAllSubscriberAndClose() {
		if (MyLiveConfig.INSTANCE.isSaveFlvFile() && flvout != null) {
			try {
				flvout.flush();
				flvout.close();
			} catch (IOException e) {
				log.error("close file:{} failed", flvout);
			}
		}
		for (Channel sc : subscribers) {
			sc.writeAndFlush(UserControlMessageEvent.streamEOF(Constants.DEFAULT_STREAM_ID))
					.addListener(ChannelFutureListener.CLOSE);

		}

		for (Channel sc : httpFLvSubscribers) {
			sc.writeAndFlush(DefaultLastHttpContent.EMPTY_LAST_CONTENT).addListener(ChannelFutureListener.CLOSE);

		}

	}

}
