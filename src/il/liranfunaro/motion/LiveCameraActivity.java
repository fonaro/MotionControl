package il.liranfunaro.motion;

import il.liranfunaro.animatedbitmap.AnimatedBitmapTask;
import il.liranfunaro.animatedbitmap.AnimatedBitmapView;
import il.liranfunaro.animatedbitmap.AnimationStreamProducer;
import il.liranfunaro.mjpeg.android.AnimatedJpeg;
import il.liranfunaro.motion.client.MotionHostClient.RequestSuccessCallback;

import java.io.IOException;
import java.io.InputStream;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

public class LiveCameraActivity extends GenericCameraActivity implements AnimationStreamProducer {
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.live_camera_layout);

		AnimatedBitmapView mv = (AnimatedBitmapView) findViewById(R.id.animatedBitmapView);
		TextView fpsTextView = (TextView) findViewById(R.id.fpsTextView);

		mv.setFpsView(fpsTextView);
		mv.showFps(true);
		mv.setBackgroundColor(Color.DKGRAY);
		mv.startPlayback(this);
	}
	
	/*
	 * (non-Javadoc)
	 * @see il.liranfunaro.mjpeg.AnimatedBitmapView.AnimationStreamProducer#getAnimationStream(il.liranfunaro.mjpeg.AnimatedBitmapView.AnimationTask)
	 */
	@Override
	public void getAnimationStream(final AnimatedBitmapTask task) {
		cameraClient.getLiveStream(new RequestSuccessCallback() {
			
			@Override
			public Object onSuccess(InputStream resultStream) throws IOException {
				task.startAnimation(new AnimatedJpeg(resultStream));
				return null;
			}
		});
	}
	
	@Override
	protected void requestWindowFeatures() {
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
	}

	public void onPause() {
		super.onPause();
		
		AnimatedBitmapView mv = (AnimatedBitmapView) findViewById(R.id.animatedBitmapView);
		mv.stopPlayback(true);
	}
}
