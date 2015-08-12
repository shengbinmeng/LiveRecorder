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
	
	public void open(String options, String address) throws Exception {
		
		int videoBitrate = 200000, width = 640, height = 480, frameRate = 25;
		int sampleRate = 44100, channelCount = 2, audioBitrate = 20000;
		int videoEncoderType = CoreRecorder.EncoderType.HARDWARE_VIDEO;
		String[] parts = options.split(" ");
		for(int i = 0; i < parts.length; i++) {
			String part = parts[i];
			String[] pair = part.split(":");
			String name = pair[0], value = pair[1];
			if (name.equalsIgnoreCase("videoBitrate")) {
				videoBitrate = Integer.parseInt(value) * 1000;
			} else if (name.equalsIgnoreCase("audioBitrate")) {
				audioBitrate = Integer.parseInt(value) * 1000;
			} else if (name.equalsIgnoreCase("videoSize")) {
				String size[] = value.split("x");
				width = Integer.parseInt(size[0]);
				height = Integer.parseInt(size[1]);
			} else if (name.equalsIgnoreCase("videoEncoder")) {
				if (value.equalsIgnoreCase("hardware")) {
					videoEncoderType = CoreRecorder.EncoderType.HARDWARE_VIDEO;
				} else {
					videoEncoderType = CoreRecorder.EncoderType.SOFTWARE_VIDEO;
				}
			}
		}
		// Audio parameters are set here.
		int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
		int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
		if (channelCount == 1) {
			channelConfig = AudioFormat.CHANNEL_IN_MONO;
		}
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
		mPreview.preferPreviewFps(frameRate);
		Camera.Size size = mPreview.preferPreviewSize(width, height);
		width = size.width;
		height = size.height;
		mPreviewHolder.addView(mPreview);
		// Set display size to the size of our frame layout, i.e. full screen.
		LayoutParams params = (LayoutParams) mPreviewHolder.getLayoutParams();
		double ratio = 1.0 * width / height;
		int displayWidth = params.width;
		int displayHeight = (int) (displayWidth * ratio);
		if (displayHeight > params.height) {
			displayHeight = params.height;
			displayWidth = (int) (displayHeight / ratio);
		}

		mPreview.setDisplaySize(displayWidth, displayHeight);
		
		mCoreRecorder = new CoreRecorder();
		mCoreRecorder.configure(sampleRate, 2, audioBitrate, width, height, frameRate, videoBitrate, videoEncoderType, address);
		
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
		// Start feeding video frame.
		mCamera.setPreviewCallback(new PreviewCallback() {
			@Override
			public void onPreviewFrame(byte[] data, Camera cam) {
				Size s = mCamera.getParameters().getPreviewSize();
				int width = s.width, height = s.height;
				if (mRecording) {
					long pts = (System.currentTimeMillis() - mStartTimeMillis) * 1000;
					int yuvSize = width * height * 3 / 2;
					if (data.length > yuvSize) {
						// For YV12, the image buffer that is received is not necessarily tightly packed.
						// See https://developer.android.com/reference/android/hardware/Camera.Parameters.html#setPreviewFormat(int).
						byte[] tightData = new byte[yuvSize];
						int yStride   = (int) Math.ceil(width / 16.0) * 16;
						int uvStride  = (int) Math.ceil( (yStride / 2) / 16.0) * 16;
						// Copy Y
						for (int r = 0; r < height; r++) {
							System.arraycopy(data, r * yStride, tightData, r * width, width);
						}
						// Copy UV
						for (int r = 0; r < height/2; r++) {
							System.arraycopy(data, yStride * height + r * uvStride, tightData, width * height + r * width/2, width/2);
							System.arraycopy(data, yStride * height + uvStride * height/2 + r * uvStride, tightData, width * height + width/2 * height/2 + r * width/2, width/2);
						}
						
						data = tightData;
					}
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
