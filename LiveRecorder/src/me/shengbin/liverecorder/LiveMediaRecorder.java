package me.shengbin.liverecorder;

public class LiveMediaRecorder {
	VideoEncoder mVideoEncoder = null; 
	private long mStartTimeMillis = 0;
	
	public void open(int width, int height) {
		mVideoEncoder = new HardwareVideoEncoder();
		mVideoEncoder.open(width, height);
		mStartTimeMillis = System.currentTimeMillis();
	}
	
	public void videoFrameReceived(byte[] pixels) {
		long pts = (System.currentTimeMillis() - mStartTimeMillis) * 1000;
		mVideoEncoder.encode(pixels, pts);
	}
	
	public void audioSamplesReceived(byte[] samples) {
		
	}
	
	public void close() {
		mVideoEncoder.close();
	}
}
