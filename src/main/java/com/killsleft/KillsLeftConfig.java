package com.killsleft;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("killsleft")
public interface KillsLeftConfig extends Config
{
	@ConfigItem(
		keyName = "showInfoBoxes",
		name = "Show infoboxes",
		description = "Show per-skill infoboxes with kills left."
	)
	default boolean showInfoBoxes()
	{
		return true;
	}

	@ConfigItem(
		keyName = "avgKills",
		name = "Kills to average",
		description = "Rolling average over this many recent kills. Higher = smoother."
	)
	default int avgKills()
	{
		return 10;
	}

	@ConfigItem(
		keyName = "showSlayer",
		name = "Show Slayer",
		description = "Include Slayer when gaining Slayer XP."
	)
	default boolean showSlayer()
	{
		return true;
	}
}
