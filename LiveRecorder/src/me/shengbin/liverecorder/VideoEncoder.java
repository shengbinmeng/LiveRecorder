package me.shengbin.liverecorder;

public interface VideoEncoder {
	void open();
	void encode();
	void close();
}
