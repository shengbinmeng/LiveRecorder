package me.shengbin.liverecorder;

import java.util.Locale;

import android.app.Activity;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;

public class LiveMediaRecorder {
	private static final String TAG = "LiveMediaRecorder";
	private Activity mActivity = null;
	
	private Camera mCamera = null;
	private CameraPreview mPreview = null;
	
	private AudioRecord mAudioRecord = null;
	private byte[] mAudioBuffer = null;
	private Thread mAudioThread = null;
	
	private boolean mPrepared = false;
	private boolean mRecording = false;
	
	private VideoEncoder mVideoEncoder = null;
	private AudioEncoder mAudioEncoder = null;
	private StreamOutput mOutput = null;
	
	private long mStartTimeMillis = 0;
	private long mFrameCount = 0;
	private long mCountBeginTime = 0;
	
	LiveMediaRecorder(Activity activity) {
		mActivity = activity;
	}
	
	public void open() {
		// audio parameters are set here
		int sampleRate = 44100;
		int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
		int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
		int bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig,
				audioFormat);
		//mAudioBuffer = new byte[Math.min(4096, bufferSize)];
		mAudioBuffer = new byte[bufferSize];
		mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT,
				sampleRate, channelConfig, audioFormat, bufferSize);
		
		// Create an instance of Camera
		mCamera = getCameraInstance();
		if (mCamera == null) {
			return;
		}
		// Create our Preview view and set it as the content of our activity.
		mPreview = new CameraPreview(mActivity, mCamera);
		FrameLayout preview = (FrameLayout) mActivity.findViewById(R.id.camera_preview);
		preview.addView(mPreview);

		// set display size to the size of our frame layout, i.e. full screen
		// (better to consider the ratio)
		LayoutParams params = (LayoutParams) preview.getLayoutParams();
		mPreview.setDisplaySize(params.width, params.height);
				
		mPrepared = true;
	}
	
	public void start() {
		if (!mPrepared) {
			return;
		}
		
		// open encoders
		mAudioEncoder = new HardwareAudioEncoder();
		mAudioEncoder.open(mAudioRecord.getSampleRate(), mAudioRecord.getChannelCount());
		final Size s = mCamera.getParameters().getPreviewSize();
		mVideoEncoder = new HardwareVideoEncoder();
		try {
			mVideoEncoder.open(s.width, s.height);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
		
		mOutput = new FileStreamOutput();
		mOutput.open();
		mAudioEncoder.setOutput(mOutput);
		mVideoEncoder.setOutput(mOutput);
		
		mStartTimeMillis = System.currentTimeMillis();
		mCountBeginTime = mStartTimeMillis;
		
		mRecording = true;

		// start feeding video frame
		mCamera.setPreviewCallback(new PreviewCallback() {
			@Override
			public void onPreviewFrame(byte[] data, Camera cam) {
				if (mRecording) {
					videoFrameReceived(data);
				}
				long currentTime = System.currentTimeMillis();
				mFrameCount += 1;
				// update FPS every 1000ms (i.e. 1s)
				if (currentTime - mCountBeginTime > 1000) {
					double fps = mFrameCount / ((currentTime - mCountBeginTime)/1000.0);
					String info = String.format(Locale.ENGLISH, "Video size: %dx%d, FPS: %.2f", s.width, s.height, fps);
					((RecordingActivity)mActivity).updateInfoText(info);
					Log.i(TAG, info);
					mCountBeginTime = currentTime;
					mFrameCount = 0;
				}
			}
		});

		// start audio recording
		mAudioThread = new AudioRecordThread();
		mAudioThread.start();
	}
	
	// attempt to get a Camera instance
	private Camera getCameraInstance() {
		Camera c = null;
		try {
			c = Camera.open();
		} catch (Exception e) {
			// Camera is not available (in use or does not exist)
			e.printStackTrace();
		}
		return c;
	}
		
	/**
	 * A new thread will be started for the audio recording.
	 * Because a while loop is needed to continually supply the audio data to recorder
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
					audioSamplesReceived(mAudioBuffer);
				}

				mAudioRecord.stop();

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	};
		
	public void videoFrameReceived(byte[] pixels) {
		long pts = (System.currentTimeMillis() - mStartTimeMillis) * 1000;
		mVideoEncoder.encode(pixels, pts);
	}
	
	public void audioSamplesReceived(byte[] samples) {
		long pts = (System.currentTimeMillis() - mStartTimeMillis) * 1000;
		mAudioEncoder.encode(samples, pts);
	}
	
	public void stop() {
		mRecording = false;
		// wait the audio thread to end before close native recorder
		try {
			mAudioThread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		mCamera.setPreviewCallback(null);
		mAudioEncoder.close();
		mVideoEncoder.close();
		mOutput.close();
	}
	
	public void releaseCamera() {
		if (mCamera != null) {
			mCamera.release(); // release the camera for other applications
			mCamera = null;
		}
	}
	
	public void recoverCamera() {
		if (mCamera == null && mPreview != null) {
			// Create an instance of Camera
			mCamera = getCameraInstance();
			mPreview.setCamera(mCamera);
		}
	}
	
	public boolean isRecording() {
		return mRecording;
	}
}
