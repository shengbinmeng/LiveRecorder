package me.shengbin.liverecorder;

import android.util.Log;

public class LiveStreamOutput implements StreamOutput {
	private static final String TAG = "LiveStreamOutput";
	
	@Override
	public void open() {
		
	}
	
	@Override
	public void encodedFrameReceived(byte[] data) {
		Log.d(TAG, "encodedFrameReceived: " + data.length + "bytes.");
	}
	
	@Override
	public void encodedSamplesReceived(byte[] data) {
		Log.d(TAG, "encodedSampleReceived: " + data.length + "bytes.");
	}

	@Override
	public void close() {
		
	}
}
