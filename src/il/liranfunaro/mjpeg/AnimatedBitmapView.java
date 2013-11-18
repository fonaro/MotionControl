package il.liranfunaro.mjpeg;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.TextView;

public class AnimatedBitmapView extends SurfaceView implements SurfaceHolder.Callback {
	static final String TAG = "AnimatedBitmapView";

	private AnimationTask task = new AnimationTask();
	boolean showFps = false;
	private final AtomicBoolean playing = new AtomicBoolean(false);

	Paint framePaint = new Paint();
	TextView fpsTextView = null;
	
	int dispWidth;
	int dispHeight;
	
	SurfaceHolder holder;
	
	private int frameCounter = 0;
	private long start = 0;
	
	public void setFpsView(TextView textView) {
		this.fpsTextView = textView;
	}
	
	public AnimatedBitmapView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}
	
	public AnimatedBitmapView(Context context) {
		super(context);
		init(context);
	}

	private void init(Context context) {
		holder = getHolder();
		holder.addCallback(this);

		setFocusable(true);
		
		dispWidth = getWidth();
		dispHeight = getHeight();
	}

	public void startPlayback(AnimationStreamProducer producer) {
		if(playing.compareAndSet(false, true)) {
			if(producer != null) {
				task.execute(producer);
			} else {
				task.execute();
			}
		}
	}
	
	public void startPlayback() {
		startPlayback(null);
	}

	public void stopPlayback() {
		if(playing.compareAndSet(true, false)) {
			while (true) {
				try {
					task.get();
					return;
				} catch (Exception e) {
					Log.e(TAG, e.getMessage(), e);
				}
			}
		}		
	}
	
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		startPlayback();
	}
	
	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		stopPlayback();
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int f, int w, int h) {
		synchronized (holder) {
			dispWidth = w;
			dispHeight = h;
		}
	}

	public void setFrame(Bitmap bitmap) {
		synchronized (holder) {
			Canvas canvas = holder.lockCanvas();
			
			try {
				Rect destRect = calculateDestinationRect(bitmap);
				
				//canvas.drawColor(Color.RED);
				canvas.drawBitmap(bitmap, null, destRect, framePaint);
			} finally {
				if (canvas != null) {
					holder.unlockCanvasAndPost(canvas);
				}
			}
		}
	}

	public void showFps(boolean b) {
		showFps = b;
	}

	private Rect calculateDestinationRect(Bitmap bitmap) {
		return calculateDestinationRect(bitmap.getWidth(), bitmap.getHeight());
	}

	private Rect calculateDestinationRect(int bitmapWidth, int bitmapHeight) {
		Rect result = new Rect();
		
		double bitmapAspectRation = (double) bitmapWidth / (double) bitmapHeight;
		
		// Try full width
		bitmapWidth = dispWidth;
		bitmapHeight = (int) ((double)dispWidth / bitmapAspectRation);
		
		if (bitmapHeight < dispHeight) {
			result.left = 0;
			result.right = dispWidth;
			result.top = (dispHeight - bitmapHeight) / 2;
			result.bottom = result.top + bitmapHeight;
		} else {
			bitmapHeight = dispHeight;
			bitmapWidth = (int) ((double)dispHeight * bitmapAspectRation);
			
			result.top = 0;
			result.bottom = bitmapHeight;
			result.left = (dispWidth - bitmapWidth) / 2;
			result.right = result.left + bitmapWidth;
		}
		
		return result;
	}
	
	public interface AnimationStreamProducer {
		public void getAnimationStream(AnimationTask task);
	}
	
	public class AnimationTask extends AsyncTask<AnimationStreamProducer, Integer, Void> {
		public AnimationStreamProducer producer = null;
		
		public void startAnimation(AnimatedBitmap animatedBitmap) throws IOException {
			while (playing.get()) {
				setFrame(animatedBitmap.readNextFrame());
				
				if (showFps) {
					++frameCounter;
					
					if ((System.currentTimeMillis() - start) >= 1000) {
						publishProgress(frameCounter);
						frameCounter = 0;
						start = System.currentTimeMillis();
					}
				}
			}
		}
		
		@Override
		protected void onProgressUpdate(Integer... progress) {
			String fps = String.valueOf(progress[0]) + " fps";

			if(fpsTextView != null) {
				fpsTextView.setText(fps);
			}
	     }
		
		@Override
		protected Void doInBackground(AnimationStreamProducer... producer) {
			if(producer.length == 0 || producer[0] == null) {
				if(this.producer == null) {
					return null;
				}
			} else {
				this.producer = producer[0];
			}
			
			this.producer.getAnimationStream(this);
			
			return null;
		}
	}
}