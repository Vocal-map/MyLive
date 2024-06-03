package com.longyb.mylive.server.manager;

import java.util.concurrent.ConcurrentHashMap;
import com.longyb.mylive.server.entities.Stream;
import com.longyb.mylive.server.entities.StreamName;

/**
 * 流管理器为此应用的核心组件，负责管理流。
 * 通过使用ConcurrentHashMap来存储Stream对象，来处理并发请求。
 */
public class StreamManager {
	// ConcurrentHashMap to store Stream objects with StreamName as the key
	private ConcurrentHashMap<StreamName, Stream> streams = new ConcurrentHashMap<>();

	/**
	 * This method is used to add a new stream to the map.
	 * @param streamName The name of the stream, used as the key in the map.
	 * @param s The Stream object to be stored in the map.
	 */
	public void newStream(StreamName streamName, Stream s) {
		streams.put(streamName, s);
	}

	/**
	 * This method checks if a stream with the given name exists in the map.
	 * @param streamName The name of the stream to check.
	 * @return true if the stream exists, false otherwise.
	 */
	public boolean exist(StreamName streamName) {
		return streams.containsKey(streamName);
	}

	/**
	 * This method retrieves a stream from the map using its name.
	 * @param streamName The name of the stream to retrieve.
	 * @return The Stream object if it exists, null otherwise.
	 */
	public Stream getStream(StreamName streamName) {
		return streams.get(streamName);
	}

	/**
	 * This method removes a stream from the map using its name.
	 * @param streamName The name of the stream to remove.
	 */
	public void remove(StreamName streamName) {
		streams.remove(streamName);
	}
}

