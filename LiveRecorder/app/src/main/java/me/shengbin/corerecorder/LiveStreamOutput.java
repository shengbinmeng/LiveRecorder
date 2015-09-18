package me.shengbin.corerecorder;

import java.io.IOException;
import java.nio.ByteBuffer;

import android.media.MediaCodec;
import android.util.Log;

public class LiveStreamOutput implements StreamOutput {
	private static final String TAG = "LiveStreamOutput";
	private double mCurrentVideoBitrateKbps = 0, mCurrentAudioBitrateKbps = 0;
	private long mAudioBytesCount = 0, mVideoBytesCount = 0;
	private long mAudioCountBeginTime = 0, mVideoCountBeginTime = 0;

	static{
		System.loadLibrary("rtmp");
		System.loadLibrary("transport");
	}

	public native boolean rtmpInit(String url);
	public native void rtmpClose();
	public native void rtmpSendVideoData(byte[] array, int timestamp);
	public native void rtmpSendAudioData(byte[] array, int timestamp);
	
	@Override
	public void open(String url) throws IOException {
		if (!rtmpInit(url)) {
			Log.e(TAG, "rtmp connect failed");
			throw new IOException("rtmp connect failed");
		}
		mCurrentVideoBitrateKbps = mCurrentAudioBitrateKbps = 0;
		mAudioBytesCount = mVideoBytesCount = 0;
		mAudioCountBeginTime = mVideoCountBeginTime = System.currentTimeMillis();
	}
	
	@Override
	public void encodedFrameReceived(byte[] data, MediaCodec.BufferInfo bufferInfo) {
		Log.d(TAG, "encodedFrameReceived: " + data.length + "bytes.");
		ByteBuffer byteBuf = ByteBuffer.allocate(data.length);
		byteBuf.put(data);
		try {
            byteBuf.clear();
            byteBuf.position(bufferInfo.size);
            byteBuf.flip();
            int pts = (int)(bufferInfo.presentationTimeUs / 1000);
            rtmpSendVideoData(byteBuf.array(), pts);
		} catch (Exception e) {
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
	
	@Override
	public void encodedSamplesReceived(byte[] data, MediaCodec.BufferInfo bufferInfo) {
		Log.d(TAG, "encodedSamplesReceived: " + data.length + "bytes.");
		ByteBuffer byteBuf = ByteBuffer.allocate(data.length);
		byteBuf.put(data);
		try {
            byteBuf.clear();
            byteBuf.position(bufferInfo.size);
            byteBuf.flip();
            int pts = (int)(bufferInfo.presentationTimeUs / 1000);
            rtmpSendAudioData(byteBuf.array(), pts);
		} catch (Exception e) {
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

	@Override
	public void close() {
        rtmpClose();
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
