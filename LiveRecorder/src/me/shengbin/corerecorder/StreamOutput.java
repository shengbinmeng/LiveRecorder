package me.shengbin.corerecorder;

import android.media.MediaCodec;

public interface StreamOutput {
	boolean open(String url);
	void encodedFrameReceived(byte[] data, MediaCodec.BufferInfo bufferInfo);
	void encodedSamplesReceived(byte[] data, MediaCodec.BufferInfo bufferInfo);
	void close();
}
