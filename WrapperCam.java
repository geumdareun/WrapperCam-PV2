import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.JFrame;


public class WrapperCam extends JFrame
{

	static byte[] RESAMPLED_IMAGE_BUFFER = new byte[1024*1024*8];
	static byte[] IMAGE_BUFFER = new byte[1024*1024*8];
	static int IMAGE_BUFFER_INDEX;
	static byte[] RECEIVE_BUFFER = new byte[65536];
	static int REMAINING_IMAGE_LENGTH;

	static int HEADER_SIZE = 3;
	static int MAX_FRAGMENT_SIZE = 1400;
	static byte[] FRAGMENT_SEND_BUFFER = new byte[HEADER_SIZE + MAX_FRAGMENT_SIZE];

	public static BufferedImage decodeImage()
	{
		//System.out.println("DECODING IMAGE (SIZE: " + IMAGE_BUFFER_INDEX + ")");
		ByteArrayInputStream bais = new ByteArrayInputStream(IMAGE_BUFFER, 0, IMAGE_BUFFER_INDEX);
		try
		{
			return ImageIO.read(bais);
		}
		catch (IOException e)
		{
			e.printStackTrace();
			return null;
		}
	}

	static SynchronizedHolder<Timestamped<BufferedImage>> rawImageHolder = new SynchronizedHolder<Timestamped<BufferedImage>>();
	static SortedBuffer<Timestamped<BufferedImage>> resampledQueue = new SortedBuffer<Timestamped<BufferedImage>>(5);

	public static void startImageReceiver(final DatagramSocket socket)
	{
		new Thread()
		{
			@Override
			public void run()
			{
				try
				{
					DatagramPacket receivePacket = new DatagramPacket(RECEIVE_BUFFER, RECEIVE_BUFFER.length);
					while(true)
					{
						socket.receive(receivePacket);
						int packetLength = receivePacket.getLength();
						if(
								packetLength==7 &&
								RECEIVE_BUFFER[0] == (byte) 0xFF &&
								RECEIVE_BUFFER[1] == (byte) 0xD8 &&
								RECEIVE_BUFFER[2] == (byte) 0xFF &&
								RECEIVE_BUFFER[3] == (byte) 0xD8)
						{
							REMAINING_IMAGE_LENGTH = (RECEIVE_BUFFER[4] & 0xFF) | (RECEIVE_BUFFER[5] & 0xFF)<<8 | (RECEIVE_BUFFER[6] & 0xFF)<<16;
							IMAGE_BUFFER_INDEX = 0;
							//System.out.println("DECODED IMAGE LENGTH: " + REMAINING_IMAGE_LENGTH);
						}
						else
						{
							for(int i=0;i<packetLength;i++)
							{
								IMAGE_BUFFER[IMAGE_BUFFER_INDEX++] = RECEIVE_BUFFER[i];
								if(IMAGE_BUFFER_INDEX==IMAGE_BUFFER.length) //SAFETY MECHANISM
								{
									System.out.println("MAXIMUM BUFFER SIZE REACHED!");
									IMAGE_BUFFER_INDEX = 0;
								}
							}
							REMAINING_IMAGE_LENGTH -= packetLength;
							if(REMAINING_IMAGE_LENGTH==0)
							{
								long timestamp = System.currentTimeMillis();
								BufferedImage image = decodeImage();
								IMAGE_BUFFER_INDEX = 0;
								if(image==null)
								{
									System.out.println("NULL IMAGE");
									continue;
								}
								rawImageHolder.set(new Timestamped<BufferedImage>(image, timestamp));
							}
						}
					}
				}
				catch(Exception e)
				{
					e.printStackTrace();
					System.exit(1);
				}
			}
		}.start();
	}

	private static void delay(int millis)
	{
		try
		{
			Thread.sleep(millis);
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
	}

	private static void activateResampler(final int resamplerIndex)
	{
		Resampler resampler = new Resampler();
		while(true)
		{
			Timestamped<BufferedImage> timestampedRawImage = rawImageHolder.get();
			if(timestampedRawImage==null)
			{
				delay(1);
				continue;
			}
			BufferedImage rawImage = timestampedRawImage.getItem();
			BufferedImage resampledImage = resampler.resample(rawImage);
			resampledQueue.add(new Timestamped<BufferedImage>(resampledImage, timestampedRawImage.getTime()));
			//System.out.println("RESAMPLED BY RESAMPLER:" + resamplerIndex);
		}	
	}

	public static void startResampling(int threadSize)
	{
		for(int i=0;i<threadSize;i++)
		{
			final int index = i;
			new Thread()
			{
				public void run()
				{
					activateResampler(index);
				}
			}.start();
		}
	}

	private static byte[] dequeResampledImageAsBytes()
	{
		try
		{
			Timestamped<BufferedImage> timestampedImage;
			while((timestampedImage = resampledQueue.poll())==null)
				delay(1);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ImageIO.write(timestampedImage.getItem(), "JPEG", baos);
			return baos.toByteArray();
		}
		catch(Exception e)
		{
			e.printStackTrace();
			return null;
		}
	}
	
	static int imageIndex = 0;

	private static void startDequeueingAndSending(final DatagramSocket socket)
	{
		new Thread()
		{
			@Override
			public void run()
			{
				byte[] resampledImageBytes;
				while(true)
				{
					try
					{
						if((resampledImageBytes = dequeResampledImageAsBytes())==null)
							continue;

						if(SEND_PASS)
							continue;
						
						boolean noRemainder = resampledImageBytes.length % MAX_FRAGMENT_SIZE == 0;
						int fragmentCount   = resampledImageBytes.length / MAX_FRAGMENT_SIZE + (noRemainder ? 0 : 1);
						int tailSize        = resampledImageBytes.length - (fragmentCount-1) * MAX_FRAGMENT_SIZE;
						int imageByteIndex = 0;
						
						System.out.println("IMAGE INDEX                = " + imageIndex);
						System.out.println("RESAMPLED IMAGE BYTE SIZE  = " + resampledImageBytes.length);
						System.out.println("FRAGMENT COUNT             = " + fragmentCount);
						System.out.println("TAIL SIZE                  = " + tailSize);
						
						for(int fragmentIndex=0;fragmentIndex<fragmentCount;fragmentIndex++)
						{
							int fragmentSize = noRemainder ? MAX_FRAGMENT_SIZE : fragmentIndex==fragmentCount-1 ? tailSize : MAX_FRAGMENT_SIZE;
							
							FRAGMENT_SEND_BUFFER[0] = (byte) imageIndex;
							FRAGMENT_SEND_BUFFER[1] = (byte) fragmentCount;
							FRAGMENT_SEND_BUFFER[2] = (byte) fragmentIndex;
							//FRAGMENT_SEND_BUFFER[3] = (byte) ( fragmentSize            & 0xFF);
							//FRAGMENT_SEND_BUFFER[4] = (byte) ((fragmentSize >> 8)      & 0xFF);
							//FRAGMENT_SEND_BUFFER[5] = (byte) ( MAX_FRAGMENT_SIZE       & 0xFF);
							//FRAGMENT_SEND_BUFFER[6] = (byte) ((MAX_FRAGMENT_SIZE >> 8) & 0xFF);
							
							for(int i=0;i<fragmentSize;i++)
								FRAGMENT_SEND_BUFFER[HEADER_SIZE + i] = resampledImageBytes[imageByteIndex++];
								
							DatagramPacket fragmentPacket = new DatagramPacket(FRAGMENT_SEND_BUFFER, HEADER_SIZE + fragmentSize);
							fragmentPacket.setAddress(InetAddress.getByName(ip));
							fragmentPacket.setPort(port);
							//fragmentPacket.setLength(HEADER_SIZE + fragmentSize);
							//fragmentPacket.setData(FRAGMENT_SEND_BUFFER);
							socket.send(fragmentPacket);
						}
						
						imageIndex++;
						if(imageIndex>31)
							imageIndex = 0;
					}
					catch(Exception e)
					{
						e.printStackTrace();
					}
				}

			}
		}.start();
	}

	volatile static boolean SEND_PASS;

	private static void processLine(final String line)
	{
		System.out.println("INCOMING REQUEST: [" + line + "]");
		switch(line)
		{
		case "OFF":
			SEND_PASS = true;
			return;
		case "LOW":
			Configuration.setParameters(1f, 40);
			break;
		case "MID":
			Configuration.setParameters(1f, 80);
			break;
		case "HIGH":
			Configuration.setParameters(1f, 160);
			break;
		}
		SEND_PASS = false;
	}

	private static void close(Closeable closeable)
	{
		try
		{
			closeable.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	private static void handleRequest(final Socket socket)
	{
		new Thread()
		{
			@Override
			public void run()
			{
				try
				{
					socket.setSoTimeout(5000);
					byte[] buffer = new byte[1024];
					int length = socket.getInputStream().read(buffer);
					processLine(new String(buffer, 0, length).trim());
				}
				catch(Exception e)
				{
					e.printStackTrace();
				}
				close(socket);
			}
		}.start();
	}

	private static void runServer() throws IOException
	{
		ServerSocket server = new ServerSocket(13579);
		while(true)
		{
			handleRequest(server.accept());
		}
	}

	private static void startServer()
	{
		new Thread()
		{
			@Override
			public void run()
			{
				try
				{
					runServer();
				}
				catch (IOException e)
				{
					e.printStackTrace();
					System.exit(0);
				}
			}
		}.start();
	}

	static String ip;
	static int port;

	public static void main(String[] args) throws IOException
	{

		//ip = args[0];
		//port = Integer.parseInt(args[1]);

		ip = "192.168.10.62";
		port = 12345;

		DatagramSocket socket = new DatagramSocket(13579);

		startImageReceiver(socket);
		startResampling(3);
		startDequeueingAndSending(socket);

		//ConfigurationWindow configurationWindow = new ConfigurationWindow();
		//configurationWindow.setVisible(true);
		startServer();
	}
}
