package com.killsleft;

import com.google.inject.Provides;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.InteractingChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.game.SkillIconManager;

@Slf4j
@PluginDescriptor(
	name = "Kills Left",
	description = "Kills left to level from average XP per kill, auto-tracking combat skills gaining XP",
	tags = {"xp", "tracker", "combat", "kills"}
)
public class KillsLeftPlugin extends Plugin
{
	private static final Skill[] COMBAT_SKILLS = {
		Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE,
		Skill.RANGED, Skill.MAGIC, Skill.HITPOINTS, Skill.SLAYER
	};

	@Inject
	private Client client;

	@Inject
	private KillsLeftConfig config;

	@Inject
	private InfoBoxManager infoBoxManager;

	@Inject
	private SkillIconManager skillIconManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ConfigManager configManager;

	private NavigationButton navButton;
	private KillsLeftPanel panel;

	private final Map<Skill, Integer> skillTargets = new EnumMap<>(Skill.class);

	private final Map<Skill, KillsLeftInfoBox> infoBoxes = new EnumMap<>(Skill.class);

	// Per-skill rolling XP-per-kill history
	private final Map<Skill, Deque<Double>> recentKillXp = new EnumMap<>(Skill.class);
	// XP at fight start (set on interact), so 1 kill is enough after XP drops arrive
	private final Map<Skill, Integer> fightStartXp = new EnumMap<>(Skill.class);
	private int killsSeen = 0;
	private int pendingXPTicks = 0;

	// Interacting is usually cleared by the time ActorDeath fires, so remember last target.
	private NPC lastTarget;
	private long lastTargetTimeMs;

	@Override
	protected void startUp() throws Exception
	{
		fightStartXp.clear();
		recentKillXp.clear();
		pendingXPTicks = 0;
		loadTargets();

		panel = new KillsLeftPanel();
		java.awt.image.BufferedImage icon = net.runelite.client.util.ImageUtil.loadImageResource(getClass(), "/killsleft_icon.png");
		if (icon == null)
		{
			try
			{
				icon = skillIconManager.getSkillImage(Skill.ATTACK);
			}
			catch (Exception e)
			{
				icon = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
			}
		}
		navButton = NavigationButton.builder()
			.tooltip("Kills Left")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		refreshPanel();
	}

	@Override
	protected void shutDown() throws Exception
	{
		clientToolbar.removeNavigation(navButton);
		panel = null;
		navButton = null;
		for (KillsLeftInfoBox box : infoBoxes.values())
		{
			infoBoxManager.removeInfoBox(box);
		}
		infoBoxes.clear();
		recentKillXp.clear();
		fightStartXp.clear();
		killsSeen = 0;
		pendingXPTicks = 0;
	}

	@Provides
	KillsLeftConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(KillsLeftConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (!e.getGroup().equals("killsleft"))
		{
			return;
		}
		if (e.getKey().equals("showInfoBoxes") && !config.showInfoBoxes())
		{
			for (KillsLeftInfoBox box : infoBoxes.values())
			{
				infoBoxManager.removeInfoBox(box);
			}
			infoBoxes.clear();
		}
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged e)
	{
		if (e.getSource() == client.getLocalPlayer() && e.getTarget() instanceof NPC)
		{
			lastTarget = (NPC) e.getTarget();
			lastTargetTimeMs = System.currentTimeMillis();
			for (Skill skill : trackedSkills())
			{
				try
				{
					fightStartXp.put(skill, client.getSkillExperience(skill));
				}
				catch (Exception ignored)
				{
				}
			}
			pendingXPTicks = 0;
		}
	}

	@Subscribe
	public void onGameTick(net.runelite.api.events.GameTick t)
	{
		if (pendingXPTicks > 0 && --pendingXPTicks == 0)
		{
			resolvePendingKill();
		}
		else
		{
			refreshPanel();
		}
	}

	private void resolvePendingKill()
	{
		for (Skill skill : trackedSkills())
		{
			Integer start = fightStartXp.get(skill);
			if (start == null)
			{
				continue;
			}
			int currentXp;
			try
			{
				currentXp = client.getSkillExperience(skill);
			}
			catch (Exception ex)
			{
				continue;
			}
			int delta = currentXp - start;
			if (delta >= 1 && delta < 200_000)
			{
				pushKillXp(skill, delta);
			}
			fightStartXp.put(skill, currentXp);
		}
		syncInfoBoxes();
		refreshPanel();
	}

	private void syncInfoBoxes()
	{
		if (!config.showInfoBoxes())
		{
			for (KillsLeftInfoBox box : infoBoxes.values())
			{
				infoBoxManager.removeInfoBox(box);
			}
			infoBoxes.clear();
			return;
		}
		for (Snapshot s : snapshots())
		{
			Skill skill = skillByName(s.skillName);
			if (skill == null)
			{
				continue;
			}
			KillsLeftInfoBox box = infoBoxes.get(skill);
			if (box == null)
			{
				try
				{
					box = new KillsLeftInfoBox(skillIconManager.getSkillImage(skill), this, s.skillName);
					// Add to end so ours sit under existing RuneLite infoboxes
					infoBoxManager.addInfoBox(box);
					infoBoxes.put(skill, box);
				}
				catch (Exception e)
				{
					log.debug("infobox create failed for {}", skill, e);
					continue;
				}
			}
			box.update(s.killsLeft, s.avgXpPerKill, s.level, s.target, s.samples, s.progress);
		}
	}

	private Skill skillByName(String name)
	{
		for (Skill s : COMBAT_SKILLS)
		{
			if (s.getName().equals(name))
			{
				return s;
			}
		}
		return null;
	}

	@Subscribe
	public void onActorDeath(ActorDeath death)
	{
		Actor actor = death.getActor();
		if (!(actor instanceof NPC))
		{
			return;
		}
		if (!wasMyKill((NPC) actor))
		{
			return;
		}
		killsSeen++;
		pendingXPTicks = 2;
	}

	private boolean wasMyKill(NPC npc)
	{
		Player me = client.getLocalPlayer();
		if (me == null)
		{
			return false;
		}
		if (me.getInteracting() == npc || npc.getInteracting() == me)
		{
			return true;
		}
		return npc == lastTarget && System.currentTimeMillis() - lastTargetTimeMs < 15_000;
	}

	private List<Skill> trackedSkills()
	{
		List<Skill> out = new ArrayList<>(7);
		for (Skill s : COMBAT_SKILLS)
		{
			if (s == Skill.SLAYER && !config.showSlayer())
			{
				continue;
			}
			out.add(s);
		}
		return out;
	}

	private void pushKillXp(Skill skill, double xp)
	{
		int window = Math.max(1, Math.min(100, config.avgKills()));
		Deque<Double> q = recentKillXp.computeIfAbsent(skill, k -> new ArrayDeque<>());
		q.addLast(xp);
		while (q.size() > window)
		{
			q.pollFirst();
		}
	}

	List<Snapshot> snapshots()
	{
		List<Snapshot> out = new ArrayList<>();
		for (Skill skill : trackedSkills())
		{
			Deque<Double> q = recentKillXp.get(skill);
			if (q == null || q.isEmpty())
			{
				continue;
			}
			double avg = 0;
			for (double d : q)
			{
				avg += d;
			}
			avg /= q.size();

			int xp;
			try
			{
				xp = client.getSkillExperience(skill);
			}
			catch (Exception e)
			{
				continue;
			}

			int level = Experience.getLevelForXp(xp);
			int target = getSkillTarget(skill, level);
			long killsLeft;
			double progress;
			if (level >= target)
			{
				killsLeft = -1;
				progress = 1.0;
			}
			else
			{
				int curLevelXp = Experience.getXpForLevel(level);
				int targetXp = Experience.getXpForLevel(target);
				int remaining = Math.max(0, targetXp - xp);
				killsLeft = avg > 0 ? (long) Math.ceil(remaining / avg) : 0;
				progress = targetXp > curLevelXp
					? (double) (xp - curLevelXp) / (targetXp - curLevelXp)
					: 0.0;
			}
			out.add(new Snapshot(skill.getName(), level, target, avg, killsLeft, q.size(), progress));
		}
		return out;
	}

	int getKillsSeen()
	{
		return killsSeen;
	}

	private int getSkillTarget(Skill skill, int level)
	{
		Integer t = skillTargets.get(skill);
		if (t == null)
		{
			return Math.min(99, level + 1);
		}
		return Math.min(99, Math.max(level + 1, t));
	}

	private void loadTargets()
	{
		skillTargets.clear();
		for (Skill s : COMBAT_SKILLS)
		{
			try
			{
				String v = configManager.getConfiguration("killsleft", "target_" + s.name());
				if (v != null)
				{
					skillTargets.put(s, Math.min(99, Math.max(2, Integer.parseInt(v))));
				}
			}
			catch (Exception ignored)
			{
			}
		}
	}

	private void refreshPanel()
	{
		if (panel == null)
		{
			return;
		}
		List<Snapshot> snaps = snapshots();
		// Default each newly-seen skill to next level until user changes it
		for (Snapshot s : snaps)
		{
			Skill skill = skillByName(s.skillName);
			if (skill != null && !skillTargets.containsKey(skill))
			{
				skillTargets.put(skill, Math.min(99, s.level + 1));
			}
		}
		javax.swing.SwingUtilities.invokeLater(() ->
			panel.rebuild(snaps, skillTargets, skillIconManager, (skill, target) ->
			{
				skillTargets.put(skill, Math.min(99, Math.max(2, target)));
				configManager.setConfiguration("killsleft", "target_" + skill.name(), String.valueOf(target));
				syncInfoBoxes();
				refreshPanel();
			},
			skill ->
			{
				resetSkill(skill);
			},
			() ->
			{
				resetAll();
			}));
	}

	private void resetSkill(Skill skill)
	{
		recentKillXp.remove(skill);
		fightStartXp.remove(skill);
		KillsLeftInfoBox box = infoBoxes.remove(skill);
		if (box != null)
		{
			infoBoxManager.removeInfoBox(box);
		}
		refreshPanel();
	}

	private void resetAll()
	{
		recentKillXp.clear();
		fightStartXp.clear();
		for (KillsLeftInfoBox box : infoBoxes.values())
		{
			infoBoxManager.removeInfoBox(box);
		}
		infoBoxes.clear();
		killsSeen = 0;
		pendingXPTicks = 0;
		refreshPanel();
	}

	static class Snapshot
	{
		final String skillName;
		final int level;
		final int target;
		final double avgXpPerKill;
		final long killsLeft;
		final int samples;
		final double progress;

		Snapshot(String skillName, int level, int target, double avgXpPerKill, long killsLeft, int samples, double progress)
		{
			this.skillName = skillName;
			this.level = level;
			this.target = target;
			this.avgXpPerKill = avgXpPerKill;
			this.killsLeft = killsLeft;
			this.samples = samples;
			this.progress = progress;
		}
	}
}
