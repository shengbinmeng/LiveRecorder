package me.shengbin.corerecorder;

public interface AudioEncoder {
	void open(int sampleRate, int channelCount, int bitrate) throws Exception;
	void encode(byte[] data, long presentationTimeUs);
	void close();
	void setOutput(StreamOutput output);
	boolean updateBitrate(int bitrate);
}
