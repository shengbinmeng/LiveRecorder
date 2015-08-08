package me.shengbin.liverecorder;

public interface AudioEncoder {
	void open(int sampleRate, int channelCount);
	void encode(byte[] data, long presentationTimeUs);
	void close();
	void setOutput(StreamOutput output);
}
