
public class Timestamped<T> implements Comparable<Timestamped<T>>
{
	static long INIT_TIME = System.currentTimeMillis();
	
	private final T object;
	private final long time;
	
	public Timestamped(T t)
	{
		this.object = t;
		this.time = System.currentTimeMillis() - INIT_TIME;
	}
	
	public Timestamped(T t, long time)
	{
		this.object = t;
		this.time = time - INIT_TIME;
	}
	
	public T getItem()
	{
		return object;
	}
	
	public long getTime()
	{
		return time;
	}
	
	@Override
	public String toString()
	{
		return String.format("(%s,%d)", object, time);
	}

	public int compareTo(Timestamped<T> timestamped)
	{
		return Long.compare(time, timestamped.time);
	}	
}
