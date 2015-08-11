package me.shengbin.corerecorder;

public class SoftwareVideoEncoder implements VideoEncoder {

	static {
		System.loadLibrary("x264");
		System.loadLibrary("native_encoder");
	}
	
	private native int native_encoder_open(int width, int height);
	private native int native_encoder_encode(byte[] data, byte[] out, long pts);
	private native int native_encoder_encoding(byte[] out, long pts);
	private native int native_encoder_close();
	
	@Override
	public void open(int width, int height, int frameRate, int bitrate) throws Exception {
		native_encoder_open(width, height);
	}

	@Override
	public void encode(byte[] data, long presentationTimeUs) {
		byte[] out = null;
		int rv = native_encoder_encode(data, out, presentationTimeUs);
		
		if (rv < 0) {
			//error handling
		} else if (rv > 0) {
			//output bitstream.
		}
	}

	@Override
	public void close() {
		int presentationTimeUs = 0;
		byte[] out = null;
		
		while ((native_encoder_encoding(out, presentationTimeUs)) > 0) {
			//output bitstream.
		}
		
		native_encoder_close();
	}

	@Override
	public void setOutput(StreamOutput output) {
		
	}

}
