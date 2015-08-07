package me.shengbin.liverecorder;

import java.nio.ByteBuffer;

import android.media.MediaCodec;
import android.media.MediaFormat;

public class HardwareAudioEncoder implements VideoEncoder {
	private static final String MIMETYPE_AUDIO_AAC = "audio/mp4a-latm"; // This is AAC
    private static final int BIT_RATE = 20000; // 20 kbps
    
    private MediaCodec mEncoder = null;
    private MediaCodec.BufferInfo mBufferInfo = null;
    private LiveStreamOutput mOutput = new LiveStreamOutput();

    @Override
	public void open(int sampleRate, int channelCount) {
		MediaFormat format = MediaFormat.createAudioFormat(MIMETYPE_AUDIO_AAC, sampleRate, channelCount);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        
        mEncoder = MediaCodec.createEncoderByType(MIMETYPE_AUDIO_AAC);
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mEncoder.start();
	}

	@Override
	public void encode(byte[] data, long pts) {
		ByteBuffer[] inBuffers = mEncoder.getInputBuffers();
        ByteBuffer[] outBuffers = mEncoder.getOutputBuffers();
        
        // Feed in samples data
        while (true) {
            int inBufferIndex = mEncoder.dequeueInputBuffer(-1);
            if (inBufferIndex >= 0) {
                ByteBuffer buffer = inBuffers[inBufferIndex];
                buffer.clear();
                buffer.put(data, 0, data.length);
                mEncoder.queueInputBuffer(inBufferIndex, 0, data.length, pts, 0);
            } else if (inBufferIndex < 0) {
            	break;
            }
        }
        
        // Get out stream
        while (true) {
            int outBufferIndex = mEncoder.dequeueOutputBuffer(mBufferInfo, 0);
            if (outBufferIndex >= 0) {
                ByteBuffer buffer = outBuffers[outBufferIndex];
                mOutput.encodedSamplesReceived(buffer.array());
                mEncoder.releaseOutputBuffer(outBufferIndex, false);
            } else if (outBufferIndex < 0) {
                break;
            }
        }
	}

	@Override
	public void close() {
		mEncoder.stop();
		mEncoder.release();
	}
}
