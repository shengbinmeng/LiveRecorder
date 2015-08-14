package me.shengbin.corerecorder;

import android.media.MediaCodec;

public interface StreamOutput {
	void open(String url) throws Exception;
	void encodedFrameReceived(byte[] data, MediaCodec.BufferInfo bufferInfo);
	void encodedSamplesReceived(byte[] data, MediaCodec.BufferInfo bufferInfo);
	void close();
}
