package me.shengbin.liverecorder;

import android.util.Log;

public class LiveStreamOutput {
	private static final String TAG = "LiveStreamOutput";
	
	public void open() {
		
	}
	
	public void encodedFrameReceived(byte[] data) {
		Log.d(TAG, "encodedFrameReceived: " + data.length + "bytes.");
	}
	
	public void encodedSamplesReceived(byte[] data) {
		Log.d(TAG, "encodedSampleReceived: " + data.length + "bytes.");
	}
}
