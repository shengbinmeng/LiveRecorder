package me.shengbin.liverecorder;

import android.media.MediaCodec;

public interface StreamOutput {
	void open(String url);
	void encodedFrameReceived(byte[] data, MediaCodec.BufferInfo bufferInfo);
	void encodedSamplesReceived(byte[] data, MediaCodec.BufferInfo bufferInfo);
	void close();
}
