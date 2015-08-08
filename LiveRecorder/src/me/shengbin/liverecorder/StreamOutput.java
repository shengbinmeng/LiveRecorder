package me.shengbin.liverecorder;

public interface StreamOutput {
	void open();
	void encodedFrameReceived(byte[] data);
	void encodedSamplesReceived(byte[] data);
	void close();
}
