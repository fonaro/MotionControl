package il.liranfunaro.mjpeg;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;

public class AnimatedBitmapView extends SurfaceView implements SurfaceHolder.Callback {
	static final String TAG = "AnimatedBitmapView";

	private AnimationTask task = new AnimationTask();
	boolean showFps = false;
	
	// Indicates if the animation is running 
	private final AtomicBoolean playing = new AtomicBoolean(false);

	Paint framePaint = new Paint();
	TextView fpsTextView = null;
	
	int dispWidth;
	int dispHeight;
	
	SurfaceHolder holder;
	AtomicReference<Bitmap> currentBitmap = new AtomicReference<Bitmap>(null);
	protected AtomicBoolean isFirstFrame = new AtomicBoolean(true);
	protected Matrix transformation = new Matrix();
	protected ScaleGestureDetector scaleGestureDetector = null;  
	
	protected int frameCounter = 0;
	protected long start = 0;
	
	protected int backgroundColor = Color.DKGRAY;
	
	protected final AccelerateDecelerateInterpolator interpolator = new AccelerateDecelerateInterpolator();
	
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
		
		scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
		
		setOnTouchListener(new OnTouchListener() {
			boolean startOnBitmap = true;
			float startX,startY;
			
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				scaleGestureDetector.onTouchEvent(event);
				
				switch(event.getAction()) {
				case MotionEvent.ACTION_DOWN:
					startX = event.getX();
					startY = event.getY();
					//startOnBitmap = desinationRect.contains((int)startX, (int)startY);
					return true;
				case MotionEvent.ACTION_MOVE:
					if(!startOnBitmap) return false;
					
					transformation.postTranslate((int) (event.getX() - startX), (int) (event.getY() - startY));
					startX = event.getX();
					startY = event.getY();
					redraw();
					return true;
				case MotionEvent.ACTION_UP:
					if(!startOnBitmap) return false;
					adjustOffset();
					redraw();
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
		
		redraw();
	}

	public void setFrame(Bitmap bitmap) {
		if(bitmap != null) {
			currentBitmap.set(bitmap);
			redraw();
		}
	}
	
	protected void render(Canvas canvas, Bitmap bitmap) {
		if(isFirstFrame.compareAndSet(true, false)) {
			calculateBestFitTransformation(bitmap);
		}
		
		canvas.drawColor(backgroundColor);
		canvas.drawBitmap(bitmap, transformation, framePaint);
	}
	
	public void redraw() {
		final Bitmap bitmap = currentBitmap.get();
		if(bitmap == null) {
			return;
		}
		
		Canvas canvas = holder.lockCanvas();

		try {
			render(canvas, bitmap);
		} finally {
			if (canvas != null) {
				holder.unlockCanvasAndPost(canvas);
			}
		}
	}

	public void showFps(boolean b) {
		showFps = b;
	}
	
	private void adjustOffset() {
		float dx = 0.0f, dy = 0.0f;
		
		final Bitmap bitmap = currentBitmap.get();
		if(bitmap == null) {
			return;
		}
		
		RectF desinationRect = new RectF(0,0,bitmap.getWidth(),bitmap.getHeight());
		transformation.mapRect(desinationRect);
		
		if(desinationRect.left > 0 && desinationRect.right > dispWidth) {
			dx -= Math.min(desinationRect.left, desinationRect.right - dispWidth);
		} else if(desinationRect.left < 0 && desinationRect.right < dispWidth) {
			dx += Math.min(-desinationRect.left, dispWidth - desinationRect.right);
		}
		
		if(desinationRect.top > 0 && desinationRect.bottom > dispHeight) {
			dy -= Math.min(desinationRect.top, desinationRect.bottom - dispHeight);
		} else if(desinationRect.top < 0 && desinationRect.bottom < dispHeight) {
			dy += Math.min(-desinationRect.top, dispHeight - desinationRect.bottom);
		}
		
		new MatrixTranslateAnimator(dx, dy, 300, 30).start();
	}
	
	private class MatrixTranslateAnimator {
		final private Matrix origMat;
		final private float dx;
		final private float dy;
		final private long durationMS;
		final private long intervalsMS;
		
		public MatrixTranslateAnimator(float dx, float dy, long durationMS, long intervalsMS) {
			super();
			this.origMat = new Matrix(transformation);
			this.dx = dx;
			this.dy = dy;
			this.durationMS = durationMS;
			this.intervalsMS = intervalsMS;
		}
		
		void start() {
			
			final Timer timer = new Timer(true);
			timer.scheduleAtFixedRate(new TimerTask() {
				long start = System.currentTimeMillis();
				long end = start + durationMS;
				
				@Override
				public void run() {
					long cur = Math.min(end, System.currentTimeMillis());
					float  t = interpolator.getInterpolation((float)(cur - start)/(float)durationMS);
					transformation.set(origMat);
					transformation.postTranslate(dx*t, dy*t);
					redraw();
					
					if(cur == end) {
						cancel();
						timer.cancel();
						timer.purge();
					}
				}
			}, 0, intervalsMS);
		}
	}
	
	private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
	    @Override
	    public boolean onScale(ScaleGestureDetector detector) {
	    	if(!detector.isInProgress()) {
	    		return false;
	    	}
	    	
	    	float scale = detector.getScaleFactor();
	    	// Don't let the object get too small or too large.
	    	scale = Math.max(0.1f, Math.min(scale, 5.0f));
	    	
	    	float focusX = detector.getFocusX();
	    	float focusY = detector.getFocusY();
	    	
	    	transformation.postTranslate(-focusX, -focusY);
	    	transformation.postScale(scale, scale);
	    	transformation.postTranslate(focusX, focusY);
	    	
	        redraw();
	        return true;
	    }
	}
	
	private void calculateBestFitTransformation(Bitmap bitmap) {
		calculateBestFitTransformation(bitmap.getWidth(), bitmap.getHeight());
	}
	
	private void calculateBestFitTransformation(int bitmapWidth, int bitmapHeight) {
		// Try full width
		float scale = (float)dispWidth / (float)bitmapWidth;
		if(bitmapHeight * scale > dispHeight) {
			scale = (float)dispHeight / (float)bitmapHeight;
		}
		
		transformation.postScale(scale, scale);
		transformation.postTranslate((dispWidth - scale*bitmapWidth) / 2, (dispHeight - scale*bitmapHeight) / 2);
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