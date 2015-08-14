package me.shengbin.corerecorder;

import android.util.Log;

public class CoreRecorder {
	public static class EncoderType {
		public static final int HARDWARE_VIDEO = 0;
		public static final int SOFTWARE_VIDEO = 2;
	}
	
	private static final String TAG = "CoreRecorder";
	private VideoEncoder mVideoEncoder = null;
	private AudioEncoder mAudioEncoder = null;
	private StreamOutput mOutput = null;
	private int mSampleRate = 0;
	private int mChannelCount = 0;
	private int mAudioBitrate = 0;
	private int mWidth = 0;
	private int mHeight = 0;
	private int mFrameRate = 0;
	private int mVideoBitrate = 0;
	private String mOutputAddress = null;
	private int mVideoEncoderType = 0;
	
	public CoreRecorder () {
		// These are default values.
		mSampleRate = 44100;
		mChannelCount = 2;
		mAudioBitrate = 20000;
		
		mWidth = 640;
		mHeight = 480;
		mFrameRate = 30;
		mVideoBitrate = 200000;
		
		mOutputAddress = "/";
		mVideoEncoderType = 0;
	}
	
	public void configure(int sampleRate, int channelCount, int audioBitrate, int width, int height, int frameRate, int videoBitrate, int videoEncoderType, String outputAddress) {
		configureAudio(sampleRate, channelCount, audioBitrate);
		configureVideo(width, height, frameRate, videoBitrate, videoEncoderType);
		mOutputAddress = outputAddress;
	}
	
	public void configureAudio(int sampleRate, int channelCount, int bitrate) {
		mSampleRate = sampleRate;
		mChannelCount = channelCount;
		mAudioBitrate = bitrate;
	}
	
	public void configureVideo(int width, int height, int frameRate, int bitrate, int encoderType) {
		mWidth = width;
		mHeight = height;
		mFrameRate = frameRate;
		mVideoBitrate = bitrate;
		mVideoEncoderType = encoderType;
	}
	
	public void start() throws Exception {
		String s = "Configuration:" + mSampleRate + ", " + mChannelCount + ", " + mAudioBitrate + 
				", " + mWidth + ", " + mHeight + ", " + mFrameRate + ", " + mVideoBitrate + ", " + mOutputAddress;
		Log.i(TAG, s);
		
		if (mOutputAddress.startsWith("rtmp://")) {
			mOutput = new LiveStreamOutput();
		} else if (mOutputAddress.startsWith("/")) {
			mOutput = new FileStreamOutput();
		} else {
			throw new Exception("Do not know how to output."); 
		}
		mOutput.open(mOutputAddress);
		
		mAudioEncoder = new HardwareAudioEncoder();
		mAudioEncoder.setOutput(mOutput);

		if (mVideoEncoderType == EncoderType.HARDWARE_VIDEO) {
			mVideoEncoder = new HardwareVideoEncoder();
		} else if (mVideoEncoderType == EncoderType.SOFTWARE_VIDEO) {
			mVideoEncoder = new SoftwareVideoEncoder();
		}
		mVideoEncoder.setOutput(mOutput);
		
		mAudioEncoder.open(mSampleRate, mChannelCount, mAudioBitrate);
		mVideoEncoder.open(mWidth, mHeight, mFrameRate, mVideoBitrate);
	}
	
	public void stop() {
		mAudioEncoder.close();
		mVideoEncoder.close();
		mOutput.close();
	}
		
	public void videoFrameReceived(byte[] pixels, long pts) {
		processFrame(pixels, pts);
	}
	
	public void audioSamplesReceived(byte[] samples, long pts) {
		processSamples(samples, pts);
	}
	
	
	private void processFrame(byte[] data, long pts) {
		// We can do other processing here before encode.
		mVideoEncoder.encode(data, pts);
	}
	
	private void processSamples(byte[] data, long pts) {
		// We can do other processing here before encode.
		mAudioEncoder.encode(data, pts);
	}
	
	public void restartWithBitrate(int bitrate) throws Exception {
		stop();
		mVideoBitrate = bitrate * 8/10;
		mAudioBitrate = bitrate * 1/10;
		start();
		Log.i(TAG, "Restarted with vide bitrate: " + mVideoBitrate + ", and audio bitrate: " + mAudioBitrate);
	}	
}
