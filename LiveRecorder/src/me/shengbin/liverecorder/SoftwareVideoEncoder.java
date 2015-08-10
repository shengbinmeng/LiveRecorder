package me.shengbin.liverecorder;

public class SoftwareVideoEncoder implements VideoEncoder {

	static {
		System.loadLibrary("x264");
		System.loadLibrary("native_encoder");
	}
	
	private native int native_encoder_open(int width, int height);
	private native int native_encoder_encode(byte[] data, long pts);
	private native int native_encoder_close();
	
	@Override
	public void open(int width, int height) throws Exception {
		native_encoder_open(width, height);
	}

	@Override
	public void encode(byte[] data, long presentationTimeUs) {
		native_encoder_encode(data, presentationTimeUs);
	}

	@Override
	public void close() {
		native_encoder_close();
	}

	@Override
	public void setOutput(StreamOutput output) {
		
	}

}
