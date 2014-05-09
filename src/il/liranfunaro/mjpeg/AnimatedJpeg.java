package il.liranfunaro.mjpeg;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

public class AnimatedJpeg extends MjpegInputStream implements AnimatedBitmap {
	public final static int DEFAULT_TEMP_STORAGE_SIZE = 1 << 14;
	
	protected final RestartableBufferedInputStream bufferd;
	protected final byte[] tempStorage = new byte[DEFAULT_TEMP_STORAGE_SIZE];
	
	public static class RestartableBufferedInputStream extends BufferedInputStream {

		public RestartableBufferedInputStream(InputStream in, int size) {
			super(in, size);
		}
		
		public void respawn() {
			markpos = -1;
			count = marklimit = pos = 0;
		}
		
	}
	
	protected BitmapFactory.Options cachedOptions = new BitmapFactory.Options();

	public AnimatedJpeg(InputStream in) {
		super(in);
		bufferd = new RestartableBufferedInputStream(this, (1<<22));
		
		cachedOptions.inMutable = true;
		cachedOptions.inPreferQualityOverSpeed = false;
		cachedOptions.inTempStorage = tempStorage;
		cachedOptions.inSampleSize = 1;
		cachedOptions.inInputShareable = true;
		cachedOptions.inScaled = false;
		cachedOptions.inPreferredConfig = Bitmap.Config.RGB_565;
	}
	
	int c  = 0;
	int cf = 0;
	
	@Override
	public Bitmap readNextFrame() throws IOException {
		bufferd.respawn();

		Bitmap bitmap = null;
		
		c++;
		
		while(bitmap == null && !isEndTransittion()) {
			try {
				bitmap = BitmapFactory.decodeStream(bufferd, null, cachedOptions);
				if(bitmap == null) {
					cf++;
					Log.i("BitmapFactoryError", "bitmap - null - " + cf + "/" + (c + cf));
				}
			} catch (IllegalArgumentException e) {
				cf++;
				Log.i("BitmapFactoryError", e.getMessage() + cf + "/" + (c + cf));
				cachedOptions.inBitmap = null;
			}
		}
		
		if(bitmap != null && bitmap.isMutable()) {
			cachedOptions.inBitmap = bitmap;
		}
		
		return bitmap;
	}

}
