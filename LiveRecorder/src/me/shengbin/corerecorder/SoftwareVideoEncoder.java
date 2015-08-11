package me.shengbin.corerecorder;

import android.media.MediaCodec;
import android.util.Log;
import java.io.ByteArrayOutputStream;

public class SoftwareVideoEncoder implements VideoEncoder {

	static {
		System.loadLibrary("x264");
		System.loadLibrary("native_encoder");
	}
	private static final String TAG = "SoftwareVideoEncoder";
	private native int native_encoder_open(int width, int height);
	private native int native_encoder_encode(byte[] data, ByteArrayOutputStream out, long pts);
	private native int native_encoder_encoding();
	private native int native_encoder_close();
	
	private StreamOutput mOutput = null;
	private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
	
	@Override
	public void open(int width, int height, int frameRate, int bitrate) throws Exception {
		int rv = native_encoder_open(width, height);
		
		if (rv < 0) {
			
		}
	}

	@Override
	public void encode(byte[] data, long presentationTimeUs) {
		ByteArrayOutputStream out = new ByteArrayOutputStream(0);
		int rv = native_encoder_encode(data, out, presentationTimeUs);
		
		if (rv < 0) {
			//error handling
			Log.d(TAG, "Software encoder error,rv = " + rv);
			close();
		} else if (rv > 0) {
			//output bitstream.
			byte[] bytes = out.toByteArray();
			mBufferInfo.set(0, rv, presentationTimeUs, 0);//todo: fix set.
			mOutput.encodedFrameReceived(bytes, mBufferInfo);
		}
	}

	@Override
	public void close() {
		int presentationTimeUs = 0;//test
		
		//flush encoder.
		while (native_encoder_encoding() == 1) {
			//output bitstream.
			encode(null, presentationTimeUs);
		}
		
		native_encoder_close();
	}

	@Override
	public void setOutput(StreamOutput output) {
		mOutput = output;
	}

}
