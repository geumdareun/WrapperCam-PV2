import java.util.ArrayList;
import java.util.Collections;

public class SortedBuffer<T extends Comparable<T>>
{
	
	final private int bufferSize;
	final private ArrayList<T> buffer = new ArrayList<T>();
	
	public SortedBuffer(int bufferSize)
	{
		this.bufferSize = bufferSize;
	}
	
	public synchronized void add(T t)
	{
		if(buffer.size()==(bufferSize-1) && t.compareTo(buffer.get(0))<0)
			return;
		if(bufferSize==buffer.size())
			buffer.remove(0);
		buffer.add(t);
		Collections.sort(buffer);
		//System.out.println(buffer);
	}
	
	public synchronized T poll()
	{
		if(buffer.size()<bufferSize)
			return null;
		return buffer.remove(0);
	}
}
