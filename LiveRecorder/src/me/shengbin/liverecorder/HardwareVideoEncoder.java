package me.shengbin.liverecorder;

import java.nio.ByteBuffer;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

public class HardwareVideoEncoder implements VideoEncoder {
	private static final String MIMETYPE_VIDEO_AVC = "video/avc"; // H.264 Advanced Video Coding
    private static final int FRAME_RATE = 30; // 30fps
    private static final int IFRAME_INTERVAL = 5; // 5 seconds between I-frames
    private static final int BIT_RATE = 200000; // 200 kbps
    
    private MediaCodec mEncoder = null;
    private int mWidth, mHeight;
    private MediaCodec.BufferInfo mBufferInfo = null;
    private LiveStreamOutput mOutput = new LiveStreamOutput();

    @Override
	public void open(int width, int height) {
		MediaFormat format = MediaFormat.createVideoFormat(MIMETYPE_VIDEO_AVC, width, height);
		format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        
        mEncoder = MediaCodec.createEncoderByType(MIMETYPE_VIDEO_AVC);
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mEncoder.start();
        mWidth = width;
        mHeight = height;
	}

	@Override
	public void encode(byte[] data, long pts) {
		ByteBuffer[] inBuffers = mEncoder.getInputBuffers();
        ByteBuffer[] outBuffers = mEncoder.getOutputBuffers();
        
        // Feed in frame data
        while (true) {
            int inBufferIndex = mEncoder.dequeueInputBuffer(-1);
            if (inBufferIndex >= 0) {
            	byte[] dataConverted = new byte[data.length];
            	YV12toYUV420Planar(data, dataConverted, mWidth, mHeight);
            	
                ByteBuffer buffer = inBuffers[inBufferIndex];
                buffer.clear();
                buffer.put(dataConverted, 0, dataConverted.length);
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
                mOutput.encodedFrameReceived(buffer.array());
                mEncoder.releaseOutputBuffer(outBufferIndex, false);
            } else if (outBufferIndex < 0) {
                break;
            }
        }
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
		
	}

	private static byte[] YV12toYUV420Planar(byte[] input, byte[] output, int width, int height) {
        /*
         * COLOR_FormatYUV420Planar is I420 which is like YV12, but with U and V reversed.
         * So we just have to reverse U and V.
         */
        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y
        System.arraycopy(input, frameSize, output, frameSize + qFrameSize, qFrameSize); // Cr (V)
        System.arraycopy(input, frameSize + qFrameSize, output, frameSize, qFrameSize); // Cb (U)

        return output;
    }

}
