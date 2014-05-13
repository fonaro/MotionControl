package il.liranfunaro.mjpeg;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class AnimatedJpeg extends MjpegInputStream implements AnimatedBitmap {
	public final static int DEFAULT_TEMP_STORAGE_SIZE = 1 << 22;
	
	protected final RestartableBufferedInputStream bufferd;
	protected final byte[] tempStorage = new byte[DEFAULT_TEMP_STORAGE_SIZE];
	
	public static class RestartableBufferedInputStream extends BufferedInputStream {

		public RestartableBufferedInputStream(InputStream in, int size) {
			super(in, size);
		}
		
		public void restartBuffer() {
			markpos = -1;
			count = marklimit = pos = 0;
		}
	}
	
	protected BitmapFactory.Options options = new BitmapFactory.Options();

	public AnimatedJpeg(InputStream in) {
		super(in);
		bufferd = new RestartableBufferedInputStream(this, DEFAULT_TEMP_STORAGE_SIZE);
		
		options.inMutable = true;
		options.inPreferQualityOverSpeed = false;
		options.inTempStorage = tempStorage;
		options.inSampleSize = 0;
		options.inInputShareable = true;
		options.inScaled = true;
		options.inPreferredConfig = Bitmap.Config.RGB_565;
	}
	
//	int c  = 0;
//	int cf = 0;
	@Override
	public Bitmap readNextFrame() throws IOException {
		bufferd.restartBuffer();

		Bitmap bitmap = null;
		
		do {
			try {
				bitmap = BitmapFactory.decodeStream(bufferd, null, options);
			} catch (IllegalArgumentException e) {
				options.inBitmap = null;
			}
			
//			c++;
//			if(bitmap == null)  cf++;
		} while(bitmap == null && !isEndTransittion());
		
		if(options.inBitmap == null && bitmap != null && bitmap.isMutable()) {
			options.inBitmap = bitmap;
		}
		
//		Log.i("BitmapFactoryStatus", "Success Rate: " + cf + "/" + (c + cf));
		
		return bitmap;
	}

}
