package il.liranfunaro.mjpeg;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
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
	AtomicReference<Bitmap> currentBitmap = new AtomicReference<Bitmap>(null);
	Point offset = new Point(0,0);
	Rect desinationRect = new Rect();
	
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
		setOnTouchListener(new OnTouchListener() {
			float startX,startY;
			Point lastOffset = new Point();
			
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				switch(event.getAction()) {
				case MotionEvent.ACTION_DOWN:
					startX = event.getX();
					startY = event.getY();
					lastOffset.set(offset.x, offset.y);
					return true;
				case MotionEvent.ACTION_MOVE:
					synchronized (offset) {
						offset.x = (int) (event.getX() - startX);
						offset.y = (int) (event.getY() - startY);
						offset.offset(lastOffset.x, lastOffset.y);
					}
					return true;
				}
				return false;
			}
		});
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

	public void stopPlayback(boolean wait) {
		if(playing.compareAndSet(true, false)) {
			while (wait) {
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
		stopPlayback(true);
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int f, int w, int h) {
		synchronized (holder) {
			dispWidth = w;
			dispHeight = h;
		}
	}

	public void setFrame(Bitmap bitmap) {
		if(bitmap != null) {
			currentBitmap.set(bitmap);
			redraw();
		}
	}
	
	protected void render(Canvas canvas, Bitmap bitmap) {
		calculateDestinationRect(bitmap);
		
		canvas.drawColor(Color.RED);
		canvas.drawBitmap(bitmap, null, desinationRect, framePaint);
	}
	
	public void redraw() {
		final Bitmap bitmap = currentBitmap.get();
		if(bitmap == null) {
			return;
		}
		
		synchronized (holder) {
			Canvas canvas = holder.lockCanvas();

			try {
				render(canvas, bitmap);
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

	private void calculateDestinationRect(Bitmap bitmap) {
		calculateDestinationRect(bitmap.getWidth(), bitmap.getHeight());
	}

	private void calculateDestinationRect(int bitmapWidth, int bitmapHeight) {
		double bitmapAspectRation = (double) bitmapWidth / (double) bitmapHeight;
		
		// Try full width
		bitmapWidth = dispWidth;
		bitmapHeight = (int) ((double)dispWidth / bitmapAspectRation);
		
		if (bitmapHeight < dispHeight) {
			desinationRect.left = 0;
			desinationRect.right = dispWidth;
			desinationRect.top = (dispHeight - bitmapHeight) / 2;
			desinationRect.bottom = desinationRect.top + bitmapHeight;
		} else {
			bitmapHeight = dispHeight;
			bitmapWidth = (int) ((double)dispHeight * bitmapAspectRation);
			
			desinationRect.top = 0;
			desinationRect.bottom = bitmapHeight;
			desinationRect.left = (dispWidth - bitmapWidth) / 2;
			desinationRect.right = desinationRect.left + bitmapWidth;
		}
		
		desinationRect.offset(offset.x, offset.y);
	}
	
	public interface AnimationStreamProducer {
		public void getAnimationStream(AnimationTask task);
	}
	
	public class AnimationTask extends AsyncTask<AnimationStreamProducer, Integer, Void> {
		public AnimationStreamProducer producer = null;
		
		public void startAnimation(AnimatedBitmap animatedBitmap) throws IOException {
			while (playing.get()) {
				setFrame(animatedBitmap.readNextFrame(desinationRect));
				
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
			if(fpsTextView == null) {
				return;
			}
			
			String fps;
			if(playing.get() && progress[0] > 0 ) {
				fps = String.valueOf(progress[0]) + " fps";
			} else {
				fps = "Stopped";
			}

			fpsTextView.setText(fps);
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