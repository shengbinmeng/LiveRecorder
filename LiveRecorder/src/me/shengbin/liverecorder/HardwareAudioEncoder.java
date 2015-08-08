package me.shengbin.liverecorder;

import java.nio.ByteBuffer;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;

public class HardwareAudioEncoder implements AudioEncoder {
	private static final String TAG = "HardwareAudioEncoder";
	
	private static final String MIMETYPE_AUDIO_AAC = "audio/mp4a-latm"; // This is AAC
    private static final int BIT_RATE = 20000; // 20 kbps
    private static final int TIMEOUT_INPUT = 2000000; // 2s
    private static final int TIMEOUT_OUTPUT = 20000; // 20ms
    
    private MediaCodec mEncoder = null;
    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private ByteBuffer[] mInputBuffers = null, mOutputBuffers = null;

    private StreamOutput mOutput = null;

    @Override
	public void open(int sampleRate, int channelCount) {
		MediaFormat format = MediaFormat.createAudioFormat(MIMETYPE_AUDIO_AAC, sampleRate, channelCount);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
		int bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_STEREO,
        		AudioFormat.ENCODING_PCM_16BIT);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize);
        
        mEncoder = MediaCodec.createEncoderByType(MIMETYPE_AUDIO_AAC);
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mEncoder.start();
	}

	@Override
	public void encode(byte[] data, long pts) {
		mInputBuffers = mEncoder.getInputBuffers();
        mOutputBuffers = mEncoder.getOutputBuffers();
        
        // Feed in samples data
        while (true) {
            int inBufferIndex = mEncoder.dequeueInputBuffer(TIMEOUT_INPUT);
            if (inBufferIndex >= 0) {
                ByteBuffer buffer = mInputBuffers[inBufferIndex];
                buffer.clear();
                buffer.put(data, 0, data.length);
                mEncoder.queueInputBuffer(inBufferIndex, 0, data.length, pts, 0);
                break;
            } else if (inBufferIndex < 0) {
            	continue;
            }
        }
        
        // Get out stream
        while (true) {
            int outBufferIndex = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_OUTPUT);
            if (outBufferIndex >= 0) {
                ByteBuffer buffer = mOutputBuffers[outBufferIndex];
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                mOutput.encodedSamplesReceived(bytes);
                mEncoder.releaseOutputBuffer(outBufferIndex, false);
            } else if (outBufferIndex < 0) {
            	Log.d(TAG, "outBufferIndex negative: " + outBufferIndex);
                break;
            }
        }
	}

	@Override
	public void close() {
		int inBufferIndex = mEncoder.dequeueInputBuffer(TIMEOUT_INPUT);
		mEncoder.queueInputBuffer(inBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
		while (true) {
            int outBufferIndex = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_OUTPUT);
            if (outBufferIndex >= 0) {
                ByteBuffer buffer = mOutputBuffers[outBufferIndex];
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                mOutput.encodedSamplesReceived(bytes);
                mEncoder.releaseOutputBuffer(outBufferIndex, false);
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                	Log.i(TAG, "End of stream.");
                	break;
                }
            } else if (outBufferIndex < 0) {
            	Log.d(TAG, "outBufferIndex negative: " + outBufferIndex);
                continue;
            }
        }
		mEncoder.stop();
		mEncoder.release();
	}

	@Override
	public void setOutput(StreamOutput output) {
		mOutput = output;
	}
}
