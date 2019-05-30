public class SynchronizedHolder<T>
{
	volatile T t;

	public synchronized T get()
	{
		T value = t;
		t = null;
		return value;
	}

	public void set(T t)
	{
		this.t = t;
	}
}