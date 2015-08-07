package me.shengbin.liverecorder;

import android.app.Activity;
import android.app.ProgressDialog;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class RecordingActivity extends Activity {

	private Camera mCamera = null;
	private CameraPreview mPreview = null;
	boolean mRecording = false;
	boolean mAudioPrepared = false;
	boolean mVideoPrepared = false;
	TextView mInfoText = null;
	Button mControlButton = null;
	
	AudioRecord mAudioRecord = null;
	byte[] mAudioBuffer = null;
	Thread mAudioThread = null;
	
	private long mStartTime = 0;
	private long mFrameCount = 0;
	private ProgressDialog mProgressDlg = null;
	LiveMediaRecorder mRecorder = null;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

		setContentView(R.layout.activity_recording);
		mRecorder = new LiveMediaRecorder();
		prepareAudio();
		prepareVideo();
		setupControl();
	}
	
	
	private void prepareAudio() {
		// audio parameters are set here
		int sampleRate = 44100;
		int channels = AudioFormat.CHANNEL_IN_STEREO;
		int format = AudioFormat.ENCODING_PCM_16BIT;
		int bufferSize = AudioRecord.getMinBufferSize(sampleRate, channels,
				format);
		mAudioBuffer = new byte[bufferSize];
		mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT,
				sampleRate, channels, format, bufferSize);
		mAudioPrepared = true;
	}
	
	private void prepareVideo() {
		// Create an instance of Camera
		mCamera = getCameraInstance();
		if (mCamera == null) {
			return;
		}
		// Create our Preview view and set it as the content of our activity.
		mPreview = new CameraPreview(this, mCamera);
		FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
		preview.addView(mPreview);

		// set display size to the size of our frame layout, i.e. full screen
		// (better to consider the ratio)
		LayoutParams params = (LayoutParams) preview.getLayoutParams();
		mPreview.setDisplaySize(params.width, params.height);
		mVideoPrepared = true;
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
	
	private void setupControl() {
		RelativeLayout layoutText = (RelativeLayout) findViewById(R.id.layout_text);
		layoutText.bringToFront();
		RelativeLayout layoutButton = (RelativeLayout) findViewById(R.id.layout_button);
		layoutButton.bringToFront();
		mInfoText = (TextView) findViewById(R.id.text_info);
		mInfoText.setText("");
		mInfoText.setTextSize(48);
		mControlButton = (Button) findViewById(R.id.button_control);
		
		mControlButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				if (mRecording) {
					// only show the dialog when user clicks the Stop button;
					// when stop in other case (e.g. Home or Back or interrupt), user won't be able to see it
					mProgressDlg = ProgressDialog.show(RecordingActivity.this, "Please wait", "Stopping......");
					stopRecording();
				} else {
					startRecording();
				}
			}

		});
	}
	
	private void startRecording() {
		if (!mAudioPrepared || !mVideoPrepared) {
			return;
		}

		// open the recorder
		mRecorder.open();	
		Camera.Parameters p = mCamera.getParameters();
		Size s = p.getPreviewSize();
		// encode every video frame
		mCamera.setPreviewCallback(new PreviewCallback() {
			@Override
			public void onPreviewFrame(byte[] data, Camera cam) {
				mRecorder.videoFrameReceived(data);
				long currentTime = System.currentTimeMillis();
				mFrameCount += 1;
				// update FPS every 1000ms (i.e. 1s)
				if (currentTime - mStartTime > 1000) {
					double fps = mFrameCount / ((currentTime - mStartTime)/1000.0);
					Camera.Parameters p = mCamera.getParameters();
					Size s = p.getPreviewSize();
					mInfoText.setText(String.format("Recording... video size: %dx%d, FPS: %.2f", s.width, s.height, fps));
					mStartTime = currentTime;
					mFrameCount = 0;
				}
			}
		});

		// start audio recording
		mAudioThread = new AudioRecordThread();
		mAudioThread.start();

		mRecording = true;

		mInfoText.setText(String.format("Recording... video size: %dx%d, FPS: waiting", s.width, s.height));
		mControlButton.setText(R.string.stop);

	}
	
	private void stopRecording() {
		if (!mAudioPrepared || !mVideoPrepared) {
			return;
		}
		mRecording = false;
		// wait the audio thread to end before close native recorder
		try {
			mAudioThread.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// close the recorder
		// we need to start a new thread to do this, because:
		// during closing, it will take a while to flush the delayed frames,
		// if it's executed in the main UI thread, the progress dialog won't show
		new Thread(){
			public void run(){
				mRecorder.close();
				
				if (mProgressDlg != null) {
					runOnUiThread(new Runnable() {
					    @Override
					    public void run() {
							mProgressDlg.dismiss();
							mProgressDlg = null;
					    }
					});
				}
			}
		}.start();
		
		mCamera.setPreviewCallback(null);
		mInfoText.setText("");
		mControlButton.setText(R.string.start);
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
					mRecorder.audioSamplesReceived(mAudioBuffer);
				}

				mAudioRecord.stop();

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	};
}
