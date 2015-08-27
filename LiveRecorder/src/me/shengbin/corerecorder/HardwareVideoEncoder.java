package me.shengbin.corerecorder;

import java.nio.ByteBuffer;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.EncoderCapabilities;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Range;

public class HardwareVideoEncoder implements VideoEncoder {
	private static final String TAG = "HardwareVideoEncoder";
			
	private static final String MIMETYPE_VIDEO_AVC = "video/avc"; // H.264 Advanced Video Coding
    private static final int IFRAME_INTERVAL = 1; // seconds between I-frames
    private static final int TIMEOUT_INPUT = 2000000; // 2s
    private static final int TIMEOUT_OUTPUT = 20000; // 20ms
    
    private MediaCodec mEncoder = null;
    private int mWidth = 0, mHeight = 0, mColorFormat = 0;
    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private ByteBuffer[] mInputBuffers = null, mOutputBuffers = null;
    private boolean mEncoding = false;
    private StreamOutput mOutput = null;

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	@Override
	public void open(int width, int height, int frameRate, int bitrate) throws Exception {
    	MediaCodecInfo codecInfo = selectCodec(MIMETYPE_VIDEO_AVC);
    	if (codecInfo == null) {
    		Log.e(TAG, "Couldn't find encoder for " + MIMETYPE_VIDEO_AVC);
    		throw new Exception("Couldn't find encoder");
    	}
    	int colorFormat = selectColorFormat(codecInfo, MIMETYPE_VIDEO_AVC);
    	if (colorFormat < 0) {
    		throw new Exception("Couldn't find a good color format");
    	}
    	
		MediaFormat format = MediaFormat.createVideoFormat(MIMETYPE_VIDEO_AVC, width, height);
		format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        
        mEncoder = MediaCodec.createByCodecName(codecInfo.getName());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        	// These features are available only in APL level 21 and later.
        	CodecCapabilities codecCap = codecInfo.getCapabilitiesForType(MIMETYPE_VIDEO_AVC);
        	EncoderCapabilities encoderCap = codecCap.getEncoderCapabilities();
        	Range<Integer> range = encoderCap.getComplexityRange();
        	int complexity = range.getUpper();
        	format.setInteger(MediaFormat.KEY_COMPLEXITY, complexity);
        	Log.i(TAG, "Encoder complexity: " + complexity + ", in range [" + range.getLower() + ", " + range.getUpper() + "].");
        	format.setInteger(MediaFormat.KEY_BITRATE_MODE, EncoderCapabilities.BITRATE_MODE_VBR);
        }
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mEncoder.start();
        mWidth = width;
        mHeight = height;
        mColorFormat = colorFormat;
        mEncoding = true;
	}
    
    /**
     * Returns the first codec capable of encoding the specified MIME type, or null if no
     * match was found.
     */
    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }
    
    /**
     * Returns a color format that is supported by the codec and this app.
     */
    private static int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        int selected = 0;
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (!isRecognizedFormat(colorFormat)) {
            	continue;
            }
            if (colorFormat > selected) {
            	selected = colorFormat;
            }
        }
        if (selected == Integer.MAX_VALUE) {
        	Log.e(TAG, "Couldn't find a good color format for " + codecInfo.getName() + " / " + mimeType);
            return -1;
        } else {
        	Log.i(TAG, "Color format " + selected + " for codec " + codecInfo.getName() + " / " + mimeType);
        	return selected;
        }
    }
    
    /**
     * Returns true if this is a color format that we understand (i.e. we know how
     * to convert frames to this format).
     */
    private static boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            // These are the formats we know how to handle.
            case CodecCapabilities.COLOR_FormatYUV420Planar: // 19
            case CodecCapabilities.COLOR_FormatYUV420PackedPlanar: // 20
            case CodecCapabilities.COLOR_FormatYUV420SemiPlanar: // 21
            case CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar: // 39
            case CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar: // 2130706688
            case CodecCapabilities.COLOR_QCOM_FormatYUV420SemiPlanar: // 2141391872
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Returns true if the specified color format is semi-planar YUV (NV12).  Throws an exception
     * if the color format is not recognized (e.g. not YUV).
     */
    private static boolean isSemiPlanarYUV(int colorFormat) {
        switch (colorFormat) {
            case CodecCapabilities.COLOR_FormatYUV420Planar:
            case CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
                return false;
            case CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
            case CodecCapabilities.COLOR_QCOM_FormatYUV420SemiPlanar:
                return true;
            default:
                throw new RuntimeException("unknown format " + colorFormat);
        }
    }

	@Override
	public void encode(byte[] data, long pts) {
		if (!mEncoding) {
			return;
		}
		mInputBuffers = mEncoder.getInputBuffers();
		mOutputBuffers = mEncoder.getOutputBuffers();
        // Feed in frame data.
        while (true) {
            int inBufferIndex = mEncoder.dequeueInputBuffer(TIMEOUT_INPUT);
            if (inBufferIndex >= 0) {
            	byte[] dataConverted = new byte[data.length];
            	if (isSemiPlanarYUV(mColorFormat)) {
                    YV12toYUV420PackedSemiPlanar(data, dataConverted, mWidth, mHeight);
                } else {
                    YV12toYUV420Planar(data, dataConverted, mWidth, mHeight);
                }
            	
                ByteBuffer buffer = mInputBuffers[inBufferIndex];
                buffer.clear();
                buffer.put(dataConverted, 0, dataConverted.length);
                mEncoder.queueInputBuffer(inBufferIndex, 0, dataConverted.length, pts, 0);
                break;
            } else if (inBufferIndex < 0) {
            	continue;
            }
        }
        
        // Get the stream out.
        while (true) {
            int outBufferIndex = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_OUTPUT);
            if (outBufferIndex >= 0) {
                ByteBuffer buffer = mOutputBuffers[outBufferIndex];
                byte[] bytes = new byte[mBufferInfo.size];
                buffer.get(bytes);
                mOutput.encodedFrameReceived(bytes, mBufferInfo);
                mEncoder.releaseOutputBuffer(outBufferIndex, false);
            } else if (outBufferIndex < 0) {
            	Log.d(TAG, "outBufferIndex negative: " + outBufferIndex);
                break;
            }
        }
	}

	@Override
	public void close() {
		if (!mEncoding) {
			return;
		}
		mEncoding = false;
		
		mInputBuffers = mEncoder.getInputBuffers();
		mOutputBuffers = mEncoder.getOutputBuffers();
		int inBufferIndex = mEncoder.dequeueInputBuffer(TIMEOUT_INPUT);
		mEncoder.queueInputBuffer(inBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
		while (true) {
            int outBufferIndex = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_OUTPUT);
            if (outBufferIndex >= 0) {
                ByteBuffer buffer = mOutputBuffers[outBufferIndex];
                byte[] bytes = new byte[mBufferInfo.size];
                buffer.get(bytes);
                mOutput.encodedFrameReceived(bytes, mBufferInfo);
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

	// Color format conversion; @see http://stackoverflow.com/questions/15739684/mediacodec-and-camera-color-space-incorrect.
	private static byte[] YV12toYUV420PackedSemiPlanar(final byte[] input, final byte[] output, final int width, final int height) {
        /*
         * COLOR_TI_FormatYUV420PackedSemiPlanar is NV12
         * We convert by putting the corresponding U and V bytes together (interleaved).
         */
        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y

        for (int i = 0; i < qFrameSize; i++) {
            output[frameSize + i * 2] = input[frameSize + i + qFrameSize]; // Cb (U)
            output[frameSize + i * 2 + 1] = input[frameSize + i]; // Cr (V)
        }
        return output;
    }
	
	public static byte[] YV12toYUV420Planar(byte[] input, byte[] output, int width, int height) {
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

	@Override
	public void setOutput(StreamOutput output) {
		mOutput = output;
	}

	@TargetApi(Build.VERSION_CODES.KITKAT)
	@Override
	public boolean updateBitrate(int bitrate) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			// In API level 19 and later, we can set video bitrate on the fly.
			Bundle params = new Bundle();
		    params.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, bitrate);
		    mEncoder.setParameters(params);
		    return true;
		} else {
			return false;
		}
	}

}
