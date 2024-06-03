package com.longyb.mylive.server;

import java.io.File;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.longyb.mylive.server.cfg.MyLiveConfig;
import com.longyb.mylive.server.manager.StreamManager;

import lombok.extern.slf4j.Slf4j;

/**
 * @author vocal
 **/
@Slf4j
public class MyLiveServer {
	static {
		readConfig();
	}
	public static void main(String[] args) throws Exception {

		StreamManager streamManager = new StreamManager();

		// 获得配置文件中的rtmp端口号
		int rtmpPort = MyLiveConfig.INSTANCE.getRtmpPort();
		// 获得配置文件中的handler线程池大小
		int handlerThreadPoolSize = MyLiveConfig.INSTANCE.getHandlerThreadPoolSize();
		// 开启RTMP服务
		RTMPServer rtmpServer = new RTMPServer(rtmpPort, streamManager, handlerThreadPoolSize);
		rtmpServer.run();

		if (!MyLiveConfig.INSTANCE.isEnableHttpFlv()) {
			return;
		}
		// 开启HTTP-FLV服务
		int httpPort = MyLiveConfig.INSTANCE.getHttpFlvPort();
		HttpFlvServer httpFlvServer = new HttpFlvServer(httpPort, streamManager, handlerThreadPoolSize);
		httpFlvServer.run();
	}
	private static void readConfig() {
		ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
		try {
			File file = new File("./mylive.yaml");

			MyLiveConfig cfg = mapper.readValue(file, MyLiveConfig.class);
			log.info("MyLive read configuration as : {}", cfg);

			MyLiveConfig.INSTANCE = cfg;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}
}
