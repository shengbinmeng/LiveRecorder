package me.shengbin.corerecorder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import android.media.MediaCodec;
import android.util.Log;

public class FileStreamOutput implements StreamOutput {
	private static final String TAG = "FileStreamOutput";
	private OutputStream mVideoOutStream = null, mAudioOutStream = null;
	private double mCurrentVideoBitrateKbps = 0, mCurrentAudioBitrateKbps = 0;
	private long mAudioBytesCount = 0, mVideoBytesCount = 0;
	private long mAudioCountBeginTime = 0, mVideoCountBeginTime = 0;
	
	public void open(String path) throws FileNotFoundException {
			File pathFile = new File(path);
			pathFile.mkdirs();
			mVideoOutStream = new FileOutputStream(new File(path + "/video.avc"));
			mAudioOutStream = new FileOutputStream(new File(path + "/audio.aac"));
			mCurrentVideoBitrateKbps = mCurrentAudioBitrateKbps = 0;
			mAudioBytesCount = mVideoBytesCount = 0;
			mAudioCountBeginTime = mVideoCountBeginTime = System.currentTimeMillis();
	}
	
	@Override
	public void encodedFrameReceived(byte[] data, MediaCodec.BufferInfo bufferInfo) {
		Log.d(TAG, "encodedFrameReceived: " + data.length + "bytes.");
		try {
			if (mVideoOutStream != null) {
				mVideoOutStream.write(data);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		mVideoBytesCount += data.length;
		long currentTime = System.currentTimeMillis();
		if (currentTime - mVideoCountBeginTime > 1000) {
			mCurrentVideoBitrateKbps = mVideoBytesCount * 8 / 1000.0;
			mVideoBytesCount = 0;
			mVideoCountBeginTime = currentTime;
		}
	}
	
	public void encodedSamplesReceived(byte[] data, MediaCodec.BufferInfo bufferInfo) {
		Log.d(TAG, "encodedSamplesReceived: " + data.length + "bytes.");
		try {
			if (mAudioOutStream != null) {
				mAudioOutStream.write(data);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		mAudioBytesCount += data.length;
		long currentTime = System.currentTimeMillis();
		if (currentTime - mAudioCountBeginTime > 1000) {
			mCurrentAudioBitrateKbps = mAudioBytesCount * 8 / 1000.0;
			mAudioBytesCount = 0;
			mAudioCountBeginTime = currentTime;
		}
	}
	
	public void close() {
		try {
			if (mVideoOutStream != null) {
				mVideoOutStream.close();
			}
			if (mAudioOutStream != null) {
				mAudioOutStream.close();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public double getCurrentAudioBitrateKbps() {
		return mCurrentAudioBitrateKbps;
	}

	@Override
	public double getCurrentVideoBitrateKbps() {
		return mCurrentVideoBitrateKbps;
	}
}
