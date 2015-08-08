package me.shengbin.liverecorder;

public interface VideoEncoder {
	void open(int width, int height) throws Exception;
	void encode(byte[] data, long presentationTimeUs);
	void close();
	void setOutput(StreamOutput output);
}
