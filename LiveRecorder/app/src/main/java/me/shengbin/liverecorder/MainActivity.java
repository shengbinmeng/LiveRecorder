package me.shengbin.liverecorder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

import org.json.JSONException;
import org.json.JSONObject;


public class MainActivity extends Activity {

	private EditText mEditOptions = null;
	private EditText mEditAddress = null;
	private final static String DEFAULT_OPTIONS = "videoBitrate:500 audioBitrate:20 videoSize:640x480 videoFps:15 videoEncoder:software";
	private final static String DEFAULT_SERVER = "rtmp://rtmpserver1.test.strongene.com/origin/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button launchButton = (Button) this.findViewById(R.id.button_launch);
        launchButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				launchRecording();
			}
        });
        mEditOptions = (EditText) this.findViewById(R.id.edit_options);
        mEditOptions.setText(DEFAULT_OPTIONS);
        mEditAddress = (EditText) this.findViewById(R.id.edit_address);
        String serial = "test";
        try {
            serial = android.os.Build.class.getField("SERIAL").get(null).toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        String address = DEFAULT_SERVER + serial;
        mEditAddress.setText(address);

        // We can get the publish address configuration from remote, e.g. using JSON.
        // See http://kb.qiniu.com/%E4%B8%83%E7%89%9B%E7%9B%B4%E6%92%AD%E6%8A%80%E6%9C%AF%E6%96%87%E6%A1%A3&FAQ
        // For test, we just hardcode a json string here. In practice, it should be obtained from the server.
        String streamJsonStrFromServer = "{\"id\":\"z1.meipai.shijun\",\"createdAt\":\"2015-11-10T11:41:27.091+08:00\",\"updatedAt\":\"2015-11-10T11:41:27.091+08:00\",\"title\":\"shijun\",\"hub\":\"meipai\",\"disabled\":false,\"publishKey\":\"test\",\"publishSecurity\":\"static\",\"hosts\":{\"publish\":{\"rtmp\":\"publish.1iptv.com\"},\"live\":{\"hdl\":\"live-hdl.1iptv.com\",\"hls\":\"live-hls.1iptv.com\",\"http\":\"live-hls.1iptv.com\",\"rtmp\":\"live-rtmp.1iptv.com\"},\"playback\":{\"hls\":\"playback.1iptv.com\",\"http\":\"playback.1iptv.com\"}}}";
        try {
            JSONObject jsonObject = new JSONObject(streamJsonStrFromServer);
            if (jsonObject != null) {
                String rtmpPublishHost = jsonObject.getJSONObject("hosts").getJSONObject("publish").getString("rtmp");
                String hub = jsonObject.getString("hub");
                String title = jsonObject.getString("title");
                String publishKey = jsonObject.getString("publishKey");
                address = "rtmp://" + rtmpPublishHost + "/" + hub + "/" + title + "?key=" + publishKey;
                mEditAddress.setText(address);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    
    private void launchRecording() {
    	String options = mEditOptions.getText().toString();
    	String address = mEditAddress.getText().toString();
    	Intent i = new Intent(this, RecordingActivity.class);
    	i.putExtra("me.shengbin.livrecorder.Options", options);
    	i.putExtra("me.shengbin.livrecorder.Address", address);
    	startActivity(i);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_about) {
        	AlertDialog dialog = new AlertDialog.Builder(this).setMessage(this.getString(R.string.about_message)).setTitle(this.getString(R.string.about_title))
   			.setCancelable(false)
   			.setPositiveButton(android.R.string.ok, null)
   			.create();
       		dialog.show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
