package me.shengbin.liverecorder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import android.os.Environment;
import android.util.Log;

public class FileStreamOutput implements StreamOutput {
	private static final String TAG = "FileStreamOutput";
	private OutputStream mVideoOutStream = null, mAudioOutStream = null;
	public void open() {
		String sdcardPath = Environment.getExternalStorageDirectory().getPath();
		try {
			mVideoOutStream = new FileOutputStream(new File(sdcardPath + "/video.avc"));
			mAudioOutStream = new FileOutputStream(new File(sdcardPath + "/audio.aac"));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Override
	public void encodedFrameReceived(byte[] data) {
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
	
	public void encodedSamplesReceived(byte[] data) {
		Log.d(TAG, "encodedSampleReceived: " + data.length + "bytes.");
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
