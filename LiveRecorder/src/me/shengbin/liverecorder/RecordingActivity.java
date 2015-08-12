package me.shengbin.liverecorder;

import android.app.Activity;
import android.app.AlertDialog;
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
		
		mInfoText = (TextView) findViewById(R.id.text_info);
		mInfoText.setText("");
		mInfoText.setTextSize(32);
		mControlButton = (Button) findViewById(R.id.button_control);
		mControlButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				if (mRecorder.isRecording()) {
					// Only show the dialog when user clicks the Stop button;
					// When stop in other case (e.g. Home or Back or interrupt), user won't be able to see it.
					mProgressDlg = ProgressDialog.show(RecordingActivity.this, getResources().getString(R.string.information), getResources().getString(R.string.please_wait));
					stopRecording();
				} else {
					startRecording();
				}
			}
		});

		FrameLayout cameraPreview = (FrameLayout) findViewById(R.id.camera_preview);
		mRecorder = new LiveMediaRecorder(this, cameraPreview);
		mRecorder.addCallback(this);
		String options = this.getIntent().getStringExtra("me.shengbin.livrecorder.Options");
		String address = this.getIntent().getStringExtra("me.shengbin.livrecorder.Address");
		try {
			mRecorder.open(options, address);
		} catch (Exception e) {
			e.printStackTrace();
			showAlert(getResources().getString(R.string.error), getResources().getString(R.string.open_failed) + e.getLocalizedMessage());
		}
		
		// Text and button will be covered by the preview, need to bring them to front.
		RelativeLayout layoutText = (RelativeLayout) findViewById(R.id.layout_text);
		layoutText.bringToFront();
		RelativeLayout layoutButton = (RelativeLayout) findViewById(R.id.layout_button);
		layoutButton.bringToFront();
	}
	
	private void showAlert(String title, String message) {
		new AlertDialog.Builder(this).setTitle(title).setMessage(message)
		.setCancelable(false)
		.setPositiveButton(android.R.string.ok, null).show();
		mControlButton.setEnabled(false);
	}
	
	private void startRecording() {
		try {
			mRecorder.start();
		} catch (Exception e) {
			e.printStackTrace();
			showAlert(getResources().getString(R.string.error), getResources().getString(R.string.start_failed) + e.getLocalizedMessage());
		}
		if (mRecorder.isRecording()) {
			mControlButton.setText(R.string.stop);
		}
	}
	
	private void stopRecording() {
		// We need to start a new thread to do this, because:
		// during closing, it will take a while to flush the delayed frames,
		// if it's executed in the main UI thread, the progress dialog won't show.
		new Thread(){
			public void run(){
				mRecorder.stop();
				try {
					// Make the dialog be seen.
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
							mInfoText.setText("");
							mControlButton.setText(R.string.start);
					    }
					});
				}
			}
		}.start();
		
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
		// Release the camera immediately on pause event, so other apps can use it.
		mRecorder.releaseCamera();
	}

	@Override
	public void statusUpdated(String info, LiveMediaRecorder recorder) {
		mInfoText.setText(info);
	}
}
