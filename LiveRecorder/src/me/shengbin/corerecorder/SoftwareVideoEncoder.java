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
	private native int native_encoder_open(int width, int height, int bitrate);
	private native int native_encoder_encode(byte[] data, ByteArrayOutputStream out, long pts, long[] frameEncapsulation);
	private native int native_encoder_encoding();
	private native int native_encoder_close();
	
	private StreamOutput mOutput = null;
	private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
	
	private int BUFFER_FLAG_KEY_FRAME = 1;
	
	@Override
	public void open(int width, int height, int frameRate, int bitrate) throws Exception {
		int rv = native_encoder_open(width, height, bitrate);
		if (rv < 0) {
			
		}
	}

	@Override
	public void encode(byte[] data, long presentationTimeUs) {
		ByteArrayOutputStream out = new ByteArrayOutputStream(0);
		long[] frameEncapsulation = new long[3];
		int rv = native_encoder_encode(data, out, presentationTimeUs, frameEncapsulation);
		if (rv < 0) {
			// Error handling.
			Log.e(TAG, "Software encoder error, rv = " + rv);
			close();
		} else if (rv > 0) {
			// Output bitstream.
			byte[] outBytes = out.toByteArray();
			long pts = frameEncapsulation[0];
			int boolKeyFrame = (int) frameEncapsulation[1];
			int flag = 0;
			
			if (boolKeyFrame == 1) {
				flag |= BUFFER_FLAG_KEY_FRAME;
			}
			mBufferInfo.set(0, rv, pts, flag);
			//TODO: Decide and set BUFFER_FLAG_END_OF_STREAM.
			mOutput.encodedFrameReceived(outBytes, mBufferInfo);
		}
	}

	@Override
	public void close() {
		int presentationTimeUs = 0;
		// Flush encoder.
		while (native_encoder_encoding() == 1) {
			encode(null, presentationTimeUs);
		}
		native_encoder_close();
	}

	@Override
	public void setOutput(StreamOutput output) {
		mOutput = output;
	}

}
