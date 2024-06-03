package com.longyb.mylive.server.rtmp;

import java.util.Random;

/**
 * 用于在握手时生成随机数据
 */
public class Tools {
	private static Random random = new Random();;
	public static byte[] generateRandomData(int size) {
		byte[] bytes = new byte[size];
		random.nextBytes(bytes);
		return bytes;
	}
}

