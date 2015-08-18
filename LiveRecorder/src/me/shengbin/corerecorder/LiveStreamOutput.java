package me.shengbin.corerecorder;

import java.io.IOException;
import java.nio.ByteBuffer;

import android.media.MediaCodec;
import android.util.Log;

public class LiveStreamOutput implements StreamOutput {
	private static final String TAG = "LiveStreamOutput";
	private RtmpFlv muxer;
	private double mCurrentVideoBitrateKbps = 0, mCurrentAudioBitrateKbps = 0;
	private long mAudioBytesCount = 0, mVideoBytesCount = 0;
	private long mAudioCountBeginTime = 0, mVideoCountBeginTime = 0;
	
	@Override
	public void open(String url) throws IOException {
		muxer = new RtmpFlv(url, RtmpFlv.OutputFormat.MUXER_OUTPUT_RTMP);
		if (!muxer.RtmpConnect()) {
			Log.e(TAG, "rtmp connect failed");
			throw new IOException("rtmp connect failed");
		}
		muxer.start();
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
			muxer.writeSampleData(1, byteBuf, bufferInfo);
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
			muxer.writeSampleData(0, byteBuf, bufferInfo);
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
		if (muxer != null) {
			muxer.stop();
			muxer.release();
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
