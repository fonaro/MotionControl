package il.liranfunaro.animatedbitmap;

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
import android.support.v4.view.GestureDetectorCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;

public class AnimatedBitmapView extends SurfaceView implements
		SurfaceHolder.Callback {
	static final String TAG = "AnimatedBitmapView";

	// Parameter: background color
	protected int backgroundColor = Color.DKGRAY;
	
	// Parameter: holds the frame-per-second text box
	protected TextView fpsTextView = null;
	
	// Parameter: will the frame-per-second text box is drawn
	protected boolean showFps = false;
	
	// An AsyncTask that render the animation frame-by-frame
	protected AnimationTask animationTask = new AnimationTask();
	
	// True if no frame had been drawn yet
	protected AtomicBoolean isFirstFrame = new AtomicBoolean(true);
	
	// Indicates if the animation is running 
	protected final AtomicBoolean playing = new AtomicBoolean(false);

	// The currently displayed bitmap
	protected AtomicReference<Bitmap> currentBitmap = new AtomicReference<Bitmap>(null);
	
	// Used to draw the animation frame
	protected final Paint framePaint = new Paint();

	// The transformation matrix for the video square
	protected Matrix transformation = new Matrix();
	
	// Indicate the display width
	protected int dispWidth;
	
	// Indicate the diaply height
	protected int dispHeight;
	
	// SurfaceHolder holder
	protected final SurfaceHolder holder = getHolder();
	
	/**
	 * Detectors for gestures
	 */
	protected ScaleGestureDetector scaleGestureDetector;
	protected GestureDetectorCompat gustureDetector; 
	
	// Used to animate the square when it had been moved out of bound
	protected final static AccelerateDecelerateInterpolator animationInterpolator = new AccelerateDecelerateInterpolator();
	
	public AnimatedBitmapView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}
	
	public AnimatedBitmapView(Context context) {
		super(context);
		init(context);
	}
	
	public void setFpsView(TextView textView) {
		this.fpsTextView = textView;
		applyFpsBoxVisibility();
	}
	
	public void setBackgroundColor(int backgroundColor) {
		this.backgroundColor = backgroundColor;
	}
	
	public void showFps(boolean showFps) {
		this.showFps = showFps;
		applyFpsBoxVisibility();
	}
	
	protected void applyFpsBoxVisibility() {
		if(fpsTextView != null) {
			fpsTextView.setVisibility(showFps ? VISIBLE : GONE);
		}
	}

	/**
	 * Initiate the view (called by C'tors)
	 * 
	 * @param context
	 *            the context of the view
	 */
	private void init(Context context) {
		holder.addCallback(this);

		setFocusable(true);
		
		dispWidth = getWidth();
		dispHeight = getHeight();
		
		scaleGestureDetector = new ScaleGestureDetector(context, new  ScaleGestureDetector.SimpleOnScaleGestureListener() {
		    @Override
		    public boolean onScale(ScaleGestureDetector detector) {
		    	float scale = detector.getScaleFactor();
		    	// Don't let the object get too small or too large.
		    	scale = Math.max(0.1f, Math.min(scale, 5.0f));
		    	
		    	float focusX = detector.getFocusX();
		    	float focusY = detector.getFocusY();
		    	
		    	synchronized (transformation) {
		    		transformation.postTranslate(-focusX, -focusY);
		    		transformation.postScale(scale, scale);
		    		transformation.postTranslate(focusX, focusY);
				}
		    	
		        redraw();
		        return true;
		    }
		});
		
		// Instantiate the gesture detector with the
        // application context and an implementation of
        // GestureDetector.OnGestureListener
        gustureDetector = new GestureDetectorCompat(getContext(),new SimpleOnGestureListener() {
        	@Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
                    float distanceY) {
        		synchronized (transformation) {
					transformation.postTranslate(-distanceX, -distanceY);
				}
				redraw();
                return true;
            }
        });
        
        // Set the gesture detector as the double tap
        // listener.
        gustureDetector.setOnDoubleTapListener(new SimpleOnGestureListener() {
        	@Override
        	public boolean onDoubleTap(MotionEvent e) {
        		calculateBestFitTransformation();
        		redraw();
        		return true;
        	}
        });
		
		setOnTouchListener(new OnTouchListener() {
			
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				boolean isDectected = scaleGestureDetector.onTouchEvent(event);
				isDectected |= gustureDetector.onTouchEvent(event);
				
				if(event.getAction() == MotionEvent.ACTION_UP) {
					adjustOffset();
					return true;
				}
				
				return isDectected;
			}
		});
	}
	
	public void startPlayback(AnimationStreamProducer producer) {
		if(playing.compareAndSet(false, true)) {
			if(producer != null) {
				animationTask.execute(producer);
			} else {
				animationTask.execute();
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
					animationTask.get();
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
		dispWidth = w;
		dispHeight = h;
		
		redraw();
	}

	/**
	 * Sets new frame and redraw the view
	 * 
	 * @param bitmap
	 *            the new frame
	 */
	protected void setFrame(Bitmap bitmap) {
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
		synchronized (transformation) {
			canvas.drawBitmap(bitmap, transformation, framePaint);
		}
	}
	
	/**
	 * Redraw the view
	 */
	public void redraw() {
		final Bitmap bitmap = currentBitmap.get();
		if(bitmap == null) {
			return;
		}
		
		Canvas canvas = holder.lockCanvas();
		if(canvas == null) {
			return;
		}

		try {
			render(canvas, bitmap);
		} finally {
			if (canvas != null) {
				holder.unlockCanvasAndPost(canvas);
			}
		}
	}

	/**
	 * Adjust the square when it is out of bound
	 */
	private void adjustOffset() {
		float dx = 0.0f, dy = 0.0f;
		boolean noChangeX = false;
		boolean noChangeY = false;
		
		final Bitmap bitmap = currentBitmap.get();
		if(bitmap == null) {
			return;
		}
		
		RectF desinationRect = new RectF(0,0,bitmap.getWidth(),bitmap.getHeight());
		synchronized (transformation) {
			transformation.mapRect(desinationRect);
		}
		
		if(desinationRect.left > 0 && desinationRect.right > dispWidth) {
			dx -= Math.min(desinationRect.left, desinationRect.right - dispWidth);
		} else if(desinationRect.left < 0 && desinationRect.right < dispWidth) {
			dx += Math.min(-desinationRect.left, dispWidth - desinationRect.right);
		} else {
			noChangeX = true;
		}
		
		if(desinationRect.top > 0 && desinationRect.bottom > dispHeight) {
			dy -= Math.min(desinationRect.top, desinationRect.bottom - dispHeight);
		} else if(desinationRect.top < 0 && desinationRect.bottom < dispHeight) {
			dy += Math.min(-desinationRect.top, dispHeight - desinationRect.bottom);
		} else {
			noChangeY = true;
		}
		
		if(noChangeX && noChangeY) {
			return;
		}
		
		final long durationMS = 300;
		final long intervalsMS = 30;
		
		final Matrix origMat = new Matrix();
		synchronized (transformation) {
			origMat.set(transformation);
		}
		
		final float fdx = dx, fdy = dy;
		
		final Timer timer = new Timer(true);
		timer.scheduleAtFixedRate(new TimerTask() {
			long start = System.currentTimeMillis();
			long end = start + durationMS;
			
			@Override
			public void run() {
				long cur = Math.min(end, System.currentTimeMillis());
				float  t = animationInterpolator.getInterpolation((float)(cur - start)/(float)durationMS);
				
				synchronized (transformation) {
					transformation.set(origMat);
					transformation.postTranslate(fdx*t, fdy*t);
				}
				redraw();
				
				if(cur == end) {
					cancel();
					timer.cancel();
					timer.purge();
				}
			}
		}, 0, intervalsMS);
	}
	
	private void calculateBestFitTransformation() {
		final Bitmap bitmap = currentBitmap.get();
		if(bitmap != null) {
			calculateBestFitTransformation(bitmap);
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
		
		synchronized (transformation) {
			transformation.setScale(scale, scale);
			transformation.postTranslate((dispWidth - scale*bitmapWidth) / 2, (dispHeight - scale*bitmapHeight) / 2);
		}
	}
	
	public class AnimationTask extends AsyncTask<AnimationStreamProducer, Integer, Void> implements AnimatedBitmapTask {
		public AnimationStreamProducer producer = null;
		protected long startTime = 0;
		protected int frameCounter = 0;
		
		@Override
		public void startAnimation(AnimatedBitmap animatedBitmap) throws IOException {
			while (playing.get()) {
				setFrame(animatedBitmap.readNextFrame());
				
				if (showFps) {
					++frameCounter;
					
					if ((System.currentTimeMillis() - startTime) >= 1000) {
						publishProgress(frameCounter);
						frameCounter = 0;
						startTime = System.currentTimeMillis();
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