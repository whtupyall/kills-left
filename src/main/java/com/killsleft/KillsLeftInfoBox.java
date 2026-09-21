package com.killsleft;

import java.awt.Color;
import java.awt.image.BufferedImage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBox;

public class KillsLeftInfoBox extends InfoBox
{
	private static final Color ALMOST = new Color(0x66, 0xBB, 0x6A); // green
	private static final Color SOON = new Color(0xFF, 0xCA, 0x28); // amber
	private static final Color GRIND = Color.WHITE;

	private final String skillName;
	private volatile String text = "?";
	private volatile Color textColor = GRIND;
	private volatile String tooltip = "Kills left";

	public KillsLeftInfoBox(BufferedImage image, Plugin plugin, String skillName)
	{
		super(image, plugin);
		this.skillName = skillName;
	}

	public void update(long killsLeft, double avgXp, int level, int target, int samples, double progress)
	{
		if (killsLeft < 0)
		{
			this.text = "done";
			this.textColor = GRIND;
		}
		else
		{
			this.text = compact(killsLeft);
			this.textColor = killsLeft <= 25 ? ALMOST : killsLeft <= 150 ? SOON : GRIND;
		}
		double p = Math.min(1.0, Math.max(0.0, progress));
		int pct = (int) Math.round(p * 100);
		this.tooltip = skillName + " " + level + " to " + target
			+ "<br>" + pct + "% to " + target
			+ "<br>Avg " + (long) avgXp + " XP/kill"
			+ "<br>" + (killsLeft < 0 ? "done" : Long.toString(killsLeft)) + " kills left";
	}

	@Override
	public String getText()
	{
		return text;
	}

	@Override
	public Color getTextColor()
	{
		return textColor;
	}

	@Override
	public String getTooltip()
	{
		return tooltip;
	}

	private static String compact(long n)
	{
		if (n >= 1_000_000)
		{
			return trim1(n / 1_000_000.0) + "M";
		}
		if (n >= 1000)
		{
			return trim1(n / 1000.0) + "K";
		}
		return Long.toString(n);
	}

	private static String trim1(double v)
	{
		String s = String.format(java.util.Locale.US, "%.1f", v);
		if (s.endsWith(".0"))
		{
			s = s.substring(0, s.length() - 2);
		}
		return s;
	}
}
