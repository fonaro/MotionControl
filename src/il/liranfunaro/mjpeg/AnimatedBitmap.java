package il.liranfunaro.mjpeg;

import java.io.IOException;

import android.graphics.Bitmap;
import android.graphics.Rect;

public interface AnimatedBitmap {
	public Bitmap readNextFrame(Rect rect) throws IOException;
}
