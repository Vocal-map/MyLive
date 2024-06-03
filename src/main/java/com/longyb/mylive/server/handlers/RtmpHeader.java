package com.longyb.mylive.server.handlers;

import lombok.Data;

@Data
public class RtmpHeader {
	int csid;  // Chunk Stream ID
	int fmt;
	int timestamp;  // 时间戳

	int messageLength;   // 消息长度
	short messageTypeId;   // 消息ID
	int messageStreamId;  // 消息流ID

	int timestampDelta;

	long extendedTimestamp;
	
	//used when response an ack
	int headerLength;  // 头长度
	
	public boolean mayHaveExtendedTimestamp() {
		return  (fmt==0 && timestamp ==  0xFFFFFF) || ( (fmt==1 || fmt==2) && timestampDelta ==  0xFFFFFF); 
	}
}
