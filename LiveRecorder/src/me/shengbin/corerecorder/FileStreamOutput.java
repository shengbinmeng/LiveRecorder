package me.shengbin.corerecorder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import android.media.MediaCodec;
import android.util.Log;

public class FileStreamOutput implements StreamOutput {
	private static final String TAG = "FileStreamOutput";
	private OutputStream mVideoOutStream = null, mAudioOutStream = null;
	public void open(String path) throws FileNotFoundException {
			File pathFile = new File(path);
			pathFile.mkdirs();
			mVideoOutStream = new FileOutputStream(new File(path + "/video.avc"));
			mAudioOutStream = new FileOutputStream(new File(path + "/audio.aac"));
	}
	
	@Override
	public void encodedFrameReceived(byte[] data, MediaCodec.BufferInfo bufferInfo) {
		Log.d(TAG, "encodedFrameReceived: " + data.length + "bytes.");
		try {
			if (mVideoOutStream != null) {
				mVideoOutStream.write(data);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void encodedSamplesReceived(byte[] data, MediaCodec.BufferInfo bufferInfo) {
		Log.d(TAG, "encodedSamplesReceived: " + data.length + "bytes.");
		try {
			if (mAudioOutStream != null) {
				mAudioOutStream.write(data);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void close() {
		try {
			if (mVideoOutStream != null) {
				mVideoOutStream.close();
			}
			if (mAudioOutStream != null) {
				mAudioOutStream.close();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
