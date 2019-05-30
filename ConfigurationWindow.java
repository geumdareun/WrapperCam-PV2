import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;


public class ConfigurationWindow extends JFrame
{
	JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
	JLabel qualityLabel = new JLabel("QUALITY(0~1, DEFAULT 0.2)");
	JTextField qualityField = new JTextField(5);
	JLabel sizeLabel = new JLabel("SIZE(1~160, DEFAULT 80):");
	JTextField sizeField = new JTextField(5);
	
	MousePanel mousePanel = new MousePanel();
	
	public class Pair
	{
		int x, y;
		
		public Pair(int x, int y)
		{
			this.x = x;
			this.y = y;
		}
	}
	
	public class MousePanel extends JPanel
	{
		
		ArrayList<Pair> pairs = new ArrayList<Pair>();
		ArrayList<String> texts = new ArrayList<String>();
		
		public synchronized void addPoint(int x, int y, String text)
		{
			pairs.add(new Pair(x,y));
			texts.add(text);
			//System.out.println(x + " " + y);
		}
		
		public synchronized void clear()
		{
			pairs.clear();
		}
		
		@Override
		public synchronized void paint(Graphics g)
		{
			super.paint(g);
			g.setColor(Color.WHITE);
			int n = pairs.size();
			for(int i=0;i<n;i++)
			{
				Pair pair = pairs.get(i);
				String text = texts.get(i);
				g.fillOval(pair.x-2, pair.y-2, 5, 5);
				g.drawString(text, pair.x-2, pair.y-2);
			}
		}
	}
	
	public ConfigurationWindow()
	{
		setLayout(new BorderLayout());
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setSize(600,600);
		setResizable(false);
		setLocationRelativeTo(null);
		setAlwaysOnTop(true);
		
		qualityField.setFocusable(false);
		qualityField.setEditable(false);
		sizeField.setFocusable(false);
		sizeField.setEditable(false);
		
		controlPanel.add(qualityLabel);
		controlPanel.add(qualityField);
		controlPanel.add(sizeLabel);
		controlPanel.add(sizeField);
		add(controlPanel, BorderLayout.NORTH);
		
		mousePanel.setBackground(Color.BLACK);
		add(mousePanel, BorderLayout.CENTER);
		
		mousePanel.addMouseListener(new MouseAdapter()
		{
			public void mousePressed(MouseEvent event)
			{	
				if(event.getButton()==1)
				{
					float quality = map(event.getX(), 0, mousePanel.getWidth(), 0, 1);
					int sizeMultiplier = (int) map(mousePanel.getHeight()-event.getY(), 0, mousePanel.getHeight(), 1, 160);
					String text = String.format("(%.2f, %d)",quality, sizeMultiplier);
					mousePanel.addPoint(event.getX(), event.getY(), text);	
				}
				else if(event.getButton()==3)
				{
					mousePanel.clear();
				}
				
				repaint();
			}
		});
		
		mousePanel.addMouseMotionListener(new MouseMotionAdapter()
		{
			public void mouseMoved(MouseEvent event)
			{
				float quality = map(event.getX(), 0, mousePanel.getWidth(), 0, 1);
				int sizeMultiplier = (int) map(mousePanel.getHeight()-event.getY(), 0, mousePanel.getHeight(), 1, 160);
				Configuration.setParameters(quality, sizeMultiplier);
				qualityField.setText(String.format("%.2f",quality));
				sizeField.setText(Integer.toString(sizeMultiplier));
			}
		});
	}
	
	private float map(int from, int fromMin, int fromMax, float low, float high)
	{
		float fromRatio = (float) from / (fromMax - fromMin); 
		float value = low + fromRatio * (high - low);
		if(value<low)
			return low;
		else if(high<value)
			return high;
		else
			return value;
	}
	
	public static void main(String[] args)
	{
		ConfigurationWindow configurationWindow = new ConfigurationWindow();
		configurationWindow.setVisible(true);
	}
	
}
