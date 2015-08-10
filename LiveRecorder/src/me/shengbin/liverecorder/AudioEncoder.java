package me.shengbin.liverecorder;

public interface AudioEncoder {
	void open(int sampleRate, int channelCount, int bitrate);
	void encode(byte[] data, long presentationTimeUs);
	void close();
	void setOutput(StreamOutput output);
}
