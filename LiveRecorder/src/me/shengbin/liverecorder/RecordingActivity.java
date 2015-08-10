package me.shengbin.liverecorder;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class RecordingActivity extends Activity implements LiveMediaRecorder.Callback {

	TextView mInfoText = null;
	Button mControlButton = null;
	
	private ProgressDialog mProgressDlg = null;
	LiveMediaRecorder mRecorder = null;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

		setContentView(R.layout.activity_recording);
		FrameLayout cameraPreview = (FrameLayout) findViewById(R.id.camera_preview);
		mRecorder = new LiveMediaRecorder(this, cameraPreview);
		mRecorder.addCallback(this);
		mRecorder.open();
		
		setupControl();
	}
	
	private void setupControl() {
		RelativeLayout layoutText = (RelativeLayout) findViewById(R.id.layout_text);
		layoutText.bringToFront();
		RelativeLayout layoutButton = (RelativeLayout) findViewById(R.id.layout_button);
		layoutButton.bringToFront();
		mInfoText = (TextView) findViewById(R.id.text_info);
		mInfoText.setText("");
		mInfoText.setTextSize(32);
		mControlButton = (Button) findViewById(R.id.button_control);
		
		mControlButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				if (mRecorder.isRecording()) {
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
		// start the recorder
		mRecorder.start();
		if (mRecorder.isRecording()) {
			mControlButton.setText(R.string.stop);
		}
	}
	
	private void stopRecording() {
		// stop the recorder
		// we need to start a new thread to do this, because:
		// during closing, it will take a while to flush the delayed frames,
		// if it's executed in the main UI thread, the progress dialog won't show
		new Thread(){
			public void run(){
				mRecorder.stop();
				try {
					// Make the dialog be seen
					sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
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
		
		mInfoText.setText("");
		mControlButton.setText(R.string.start);
	}
	
	public void updateInfoText(String info) {
		mInfoText.setText(info);
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		mRecorder.recoverCamera();
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (mRecorder.isRecording()) {
			mRecorder.stop();
			mInfoText.setText("");
			mControlButton.setText(R.string.start);
		}
		// release the camera immediately on pause event, so other apps can use it
		mRecorder.releaseCamera();
	}

	@Override
	public void statusUpdated(String info, LiveMediaRecorder recorder) {
		mInfoText.setText(info);
	}
}
