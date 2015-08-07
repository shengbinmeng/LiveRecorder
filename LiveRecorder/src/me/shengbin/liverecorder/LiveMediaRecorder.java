package me.shengbin.liverecorder;

public class LiveMediaRecorder {
	VideoEncoder mVideoEncoder = null; 
	
	public void open() {
		mVideoEncoder = new HardwareVideoEncoder();
		mVideoEncoder.open();
	}
	
	public void videoFrameReceived(byte[] pixels) {
		mVideoEncoder.encode();
	}
	
	public void audioSamplesReceived(byte[] samples) {
		
	}
	
	public void close() {
		mVideoEncoder.close();
	}
}
