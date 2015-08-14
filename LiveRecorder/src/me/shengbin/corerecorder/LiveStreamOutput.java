package me.shengbin.corerecorder;

import java.io.IOException;
import java.nio.ByteBuffer;

import android.media.MediaCodec;
import android.util.Log;

public class LiveStreamOutput implements StreamOutput {
	private static final String TAG = "LiveStreamOutput";
	private RtmpFlv muxer;
	
	@Override
	public void open(String url) throws IOException {
		muxer = new RtmpFlv(url, RtmpFlv.OutputFormat.MUXER_OUTPUT_RTMP);
		if (!muxer.RtmpConnect()) {
			Log.e(TAG, "rtmp connect failed");
			throw new IOException("rtmp connect failed");
		}
		muxer.start();
	}
	
	@Override
	public void encodedFrameReceived(byte[] data, MediaCodec.BufferInfo bufferInfo) {
		Log.d(TAG, "encodedFrameReceived: " + data.length + "bytes.");
		ByteBuffer byteBuf = ByteBuffer.allocate(data.length);
		byteBuf.put(data);
		try {
			muxer.writeSampleData(1, byteBuf, bufferInfo);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Override
	public void encodedSamplesReceived(byte[] data, MediaCodec.BufferInfo bufferInfo) {
		Log.d(TAG, "encodedSamplesReceived: " + data.length + "bytes.");
		ByteBuffer byteBuf = ByteBuffer.allocate(data.length);
		byteBuf.put(data);
		try {
			muxer.writeSampleData(0, byteBuf, bufferInfo);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void close() {
		if (muxer != null) {
			muxer.stop();
			muxer.release();
		}
	}
}
