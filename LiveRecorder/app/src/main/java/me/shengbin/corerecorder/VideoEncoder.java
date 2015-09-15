package me.shengbin.corerecorder;

public interface VideoEncoder {
	void open(int width, int height, int frameRate, int bitrate) throws Exception;
	void encode(byte[] data, long presentationTimeUs);
	void close();
	void setOutput(StreamOutput output);
	boolean updateBitrate(int bitrate);
}
