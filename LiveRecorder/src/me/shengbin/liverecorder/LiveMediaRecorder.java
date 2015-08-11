package me.shengbin.liverecorder;

import java.util.ArrayList;
import java.util.Locale;

import me.shengbin.corerecorder.CoreRecorder;
import android.app.Activity;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;

public class LiveMediaRecorder {
	private static final String TAG = "LiveMediaRecorder";
	private Activity mActivity = null;
	private Camera mCamera = null;
	private CameraPreview mPreview = null;
	private ViewGroup mPreviewHolder = null;
	private ArrayList<Callback> mCallbackList = null;
	
	private AudioRecord mAudioRecord = null;
	private byte[] mAudioBuffer = null;
	private Thread mAudioThread = null;
	
	private boolean mPrepared = false;
	private boolean mRecording = false;
	private CoreRecorder mCoreRecorder = null;
	
	private long mStartTimeMillis = 0;
	private long mFrameCount = 0;
	private long mCountBeginTime = 0;
	
	LiveMediaRecorder(Activity activity, ViewGroup previewHolder) {
		mActivity = activity;
		mPreviewHolder = previewHolder;
		mCallbackList = new ArrayList<Callback>();
	}
	
	public void open() throws Exception {
		// Audio parameters are set here.
		int sampleRate = 44100;
		int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
		int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
		int bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig,
				audioFormat);
		mAudioBuffer = new byte[bufferSize];
		mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT,
				sampleRate, channelConfig, audioFormat, bufferSize);
		if (mAudioRecord == null) {
			throw new Exception("Prepare audio source failed.");
		}
		// Create an instance of Camera.
		mCamera = getCameraInstance();
		if (mCamera == null) {
			throw new Exception("Prepare video source failed. Can not get camera instance.");
		}
		// Create our Preview view and set it as the content of our activity.
		mPreview = new CameraPreview(mActivity, mCamera);
		mPreviewHolder.addView(mPreview);

		// Set display size to the size of our frame layout, i.e. full screen.
		// TODO: Consider the ratio.
		LayoutParams params = (LayoutParams) mPreviewHolder.getLayoutParams();
		mPreview.setDisplaySize(params.width, params.height);
		
		mCoreRecorder = new CoreRecorder();
		mCoreRecorder.configure(sampleRate, 2, 20000, 640, 480, 25, 200000, "rtmp://123.56.150.52/origin/test");
		
		mPrepared = true;
	}
	
	public void start() throws Exception {
		if (!mPrepared) {
			return;
		}
		mCoreRecorder.start();
		
		mStartTimeMillis = System.currentTimeMillis();
		mCountBeginTime = mStartTimeMillis;
		mRecording = true;
		final Size s = mCamera.getParameters().getPreviewSize();
		// Start feeding video frame.
		mCamera.setPreviewCallback(new PreviewCallback() {
			@Override
			public void onPreviewFrame(byte[] data, Camera cam) {
				if (mRecording) {
					long pts = (System.currentTimeMillis() - mStartTimeMillis) * 1000;
					mCoreRecorder.videoFrameReceived(data, pts);
				}
				long currentTime = System.currentTimeMillis();
				mFrameCount += 1;
				// Update FPS every 1000ms (i.e. 1s).
				if (currentTime - mCountBeginTime > 1000) {
					double fps = mFrameCount / ((currentTime - mCountBeginTime)/1000.0);
					String info = String.format(Locale.ENGLISH, mActivity.getResources().getString(R.string.video_size) + ": %dx%d, " + mActivity.getResources().getString(R.string.frame_rate) + ": %.2f FPS", s.width, s.height, fps);
					Log.i(TAG, info);
					mCountBeginTime = currentTime;
					mFrameCount = 0;
					
					for (Callback callback : mCallbackList) {
						callback.statusUpdated(info, LiveMediaRecorder.this);
					}
				}
				
			}
		});

		// Start audio recording.
		mAudioThread = new AudioRecordThread();
		mAudioThread.start();
	}
	
	// Attempt to get a Camera instance.
	private Camera getCameraInstance() {
		Camera c = null;
		try {
			c = Camera.open();
		} catch (Exception e) {
			// Camera is not available (in use or does not exist).
			e.printStackTrace();
		}
		return c;
	}
		
	/**
	 * A new thread will be started for the audio recording.
	 * Because a while loop is needed to continually supply the audio data to recorder.
	 */
	class AudioRecordThread extends Thread {
		public void run() {
			try {
				mAudioRecord.startRecording();

				while (mRecording) {
					int bytesRead = mAudioRecord.read(mAudioBuffer, 0,
							mAudioBuffer.length);
					if (bytesRead < 0) {
						break;
					}
					long pts = (System.currentTimeMillis() - mStartTimeMillis) * 1000;
					mCoreRecorder.audioSamplesReceived(mAudioBuffer, pts);
				}

				mAudioRecord.stop();

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	};
	
	public void stop() {
		mRecording = false;
		try {
			mAudioThread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		mCamera.setPreviewCallback(null);
		mCoreRecorder.stop();
	}
	
	public void releaseCamera() {
		if (mCamera != null) {
			// Release the camera for other applications.
			mCamera.release();
			mCamera = null;
		}
	}
	
	public void recoverCamera() {
		if (mCamera == null && mPreview != null) {
			mCamera = getCameraInstance();
			mPreview.setCamera(mCamera);
		}
	}
	
	public boolean isRecording() {
		return mRecording;
	}
	
	public static interface Callback {
		void statusUpdated(String info, LiveMediaRecorder recorder);
	}
	
	public void addCallback(Callback cb) {
		mCallbackList.add(cb);
	}
	
	public void removeCallback(Callback cb) {
		mCallbackList.remove(cb);
	}
}
