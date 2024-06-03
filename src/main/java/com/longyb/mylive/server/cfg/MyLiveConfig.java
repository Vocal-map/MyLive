package com.longyb.mylive.server.cfg;

import lombok.Data;

/**
 * MyLiveConfig 类用于配置直播相关的参数。
 */
@Data
public class MyLiveConfig {
	/**
	 * 单例模式的实例。
	 */
	public static MyLiveConfig INSTANCE = null;

	/**
	 * RTMP协议的端口号。
	 */
	int rtmpPort;

	/**
	 * HTTP FLV协议的端口号。
	 */
	int httpFlvPort;

	/**
	 * 是否保存FLV文件到本地。
	 */
	boolean saveFlvFile;

	/**
	 * 保存FLV文件的本地路径。
	 */
	String saveFlVFilePath;

	/**
	 * 处理线程池的大小。
	 */
	int handlerThreadPoolSize;

	/**
	 * 是否启用HTTP FLV协议。
	 */
	boolean enableHttpFlv;
}

