package com.killsleft;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class KillsLeftPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(KillsLeftPlugin.class);
		RuneLite.main(args);
	}
}
