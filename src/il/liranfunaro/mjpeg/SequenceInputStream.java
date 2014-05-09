package il.liranfunaro.mjpeg;

import java.io.IOException;
import java.io.InputStream;

public class SequenceInputStream extends InputStream {
	protected final InputStream in;
	
	protected final int[] startSequance;
	protected final int[] stopSequance;
    
	private boolean foundStart;
	
	private int startSequanceReturned;
	private int stopSequanceMatchCount;
	
	private boolean endTransmittion = false;;
	
	public SequenceInputStream(InputStream in, int[] startSequance, int[] stopSequance) throws IllegalArgumentException {
		validateInput(in, startSequance, stopSequance);
		
		this.in = in;
		this.startSequance = startSequance;
		this.stopSequance = stopSequance;
		
		restart();
	}
	
	public boolean isEndTransittion() {
		return endTransmittion;
	}
	
	@Override
	public int available() throws IOException {
		return in.available();
	}
	
	public void restart() {
		this.foundStart = false;
		this.startSequanceReturned = 0;
		this.stopSequanceMatchCount = 0;
	}
	
	private void validateInput(InputStream in, int[] startSequance, int[] stopSequance) throws IllegalArgumentException {
		if(in == null || startSequance == null || stopSequance == null) {
			throw new IllegalArgumentException("Arguments must not be null");
		}
		
		if(startSequance.length == 0 || stopSequance.length == 0) {
			throw new IllegalArgumentException("start and stop sequanece must be longer then 0");
		}
	}
	
	private boolean lookForStartSequance() throws IOException {
		int sequancePos = 0;
		
		int c = -1;
		while(sequancePos < startSequance.length && (c = in.read()) != -1) {
			if(startSequance[sequancePos] == c) {
				++sequancePos;
			} else {
				sequancePos = 0;
			}
		}
		
		return sequancePos == startSequance.length;
	}
	
	private int readUntilEndSequance() throws IOException {
		if(stopSequanceMatchCount == stopSequance.length) {
			// Found stop sequence => end of stream
			restart();
			return -1;
		}
		
		int c = in.read();
		
		if(stopSequance[stopSequanceMatchCount] == c) {
			++stopSequanceMatchCount;
		} else {
			stopSequanceMatchCount = 0;
		}
		
		if(c == -1) {
			setEndTransmittion();
		}
		
		return c;
	}
	
	private void setEndTransmittion() throws IOException {
		restart();
		in.close();
		close();
		endTransmittion = true;
	}
	
	@Override
	public int read() throws IOException {
		if(!foundStart) {
			if(!lookForStartSequance()) {
				setEndTransmittion();
				return -1;
			} else {
				foundStart = true;
			}
		}
		
		if(startSequanceReturned < startSequance.length) {
			return startSequance[startSequanceReturned++];
		}
		
		return readUntilEndSequance();
	}
}