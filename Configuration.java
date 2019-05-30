
public class Configuration
{
	private static float quality = 1f;
	private static int sizeMultiplier = 40; //80 is Default
	
	public synchronized static void setParameters(float quality, int sizeMultiplier)
	{
		Configuration.quality = quality;
		Configuration.sizeMultiplier = sizeMultiplier;
	}
	
	public synchronized static float getQuality()
	{
		return quality;
	}
	
	public synchronized static int getSizeMultiplier()
	{
		return sizeMultiplier;
	}
	
}
