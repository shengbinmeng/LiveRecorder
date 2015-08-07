package me.shengbin.liverecorder;

public interface VideoEncoder {
	void open(int width, int height);
	void encode(byte[] data, long presentationTimeUs);
	void close();
}
