import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageOutputStream;

public class Resampler
	{
		JPEGImageWriteParam jpegParams = new JPEGImageWriteParam(null);
		ByteArrayOutputStream baos = new ByteArrayOutputStream(1024*1024);
		ImageWriter imageWriter;

		public Resampler()
		{
			imageWriter = (ImageWriter) ImageIO.getImageWritersByFormatName("jpg").next();
		}

		public BufferedImage resample(BufferedImage image)
		{
			try
			{
				long GLOBAL_INIT_TIME, INIT_TIME;
				GLOBAL_INIT_TIME = INIT_TIME = System.currentTimeMillis();

				float rawImageSizeMultiplier = image.getWidth()/4;
				float resampledImageSizeMultiplier = Configuration.getSizeMultiplier();
				float sizeRatio = resampledImageSizeMultiplier/rawImageSizeMultiplier;
				
				BufferedImage resizedImage = new BufferedImage((int)(image.getWidth()*sizeRatio), (int)(image.getHeight()*sizeRatio), BufferedImage.TYPE_INT_RGB);
				Graphics2D g = (Graphics2D) resizedImage.getGraphics();
				
				//System.out.println("RATIO:" + sizeRatio);
				g.scale(sizeRatio, sizeRatio);
				g.drawImage(image, 0, 0, null);
				g.dispose();
				//System.out.println("RESIZE TIME:" + (System.currentTimeMillis() - INIT_TIME));

				baos.reset();
				jpegParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
				jpegParams.setCompressionQuality(Configuration.getQuality());
				ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
				imageWriter.setOutput(ios);

				INIT_TIME = System.currentTimeMillis();
				imageWriter.write(null, new IIOImage(resizedImage, null, null), jpegParams);
				//System.out.println("WRITE TIME:" + (System.currentTimeMillis() - INIT_TIME));

				byte[] resampledImageBytes = baos.toByteArray();

				INIT_TIME = System.currentTimeMillis();
				BufferedImage resampledImage = ImageIO.read(new ByteArrayInputStream(resampledImageBytes, 0, resampledImageBytes.length));
				//System.out.println("READ TIME:" + (System.currentTimeMillis() - INIT_TIME));
				//System.out.println("TOTAL TIME:" + (System.currentTimeMillis() - GLOBAL_INIT_TIME));
				return resampledImage;
			}
			catch (IOException e)
			{
				System.out.println("RESAMPLING EXCEPTION");
				e.printStackTrace();
			}
			return null;
		}
	}