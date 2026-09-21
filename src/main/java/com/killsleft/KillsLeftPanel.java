package com.killsleft;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

public class KillsLeftPanel extends PluginPanel
{
	private final JPanel rows = new JPanel(new GridLayout(0, 1, 0, 6));
	private final JLabel hint = new JLabel("Kill monsters to begin tracking.", SwingConstants.CENTER);
	private final JButton resetAll = new JButton("Reset All");

	public KillsLeftPanel()
	{
		setLayout(new BorderLayout(0, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(10, 10, 10, 10));

		JLabel title = new JLabel("Kills Left", SwingConstants.CENTER);
		add(title, BorderLayout.NORTH);

		rows.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(rows, BorderLayout.CENTER);
		add(resetAll, BorderLayout.SOUTH);
	}

	void rebuild(List<KillsLeftPlugin.Snapshot> snaps, Map<Skill, Integer> targets,
		SkillIconManager icons, java.util.function.BiConsumer<Skill, Integer> onTargetChanged,
		java.util.function.Consumer<Skill> onResetSkill, Runnable onResetAll)
	{
		rows.removeAll();

		if (snaps.isEmpty())
		{
			rows.add(hint);
		}

		for (KillsLeftPlugin.Snapshot s : snaps)
		{
			Skill skill = skillByName(s.skillName);
			if (skill == null)
			{
				continue;
			}
			rows.add(row(s, targets.getOrDefault(skill, s.level + 1), icons, onTargetChanged, onResetSkill));
		}

		for (java.awt.event.ActionListener al : resetAll.getActionListeners())
		{
			resetAll.removeActionListener(al);
		}
		resetAll.addActionListener(e -> onResetAll.run());
		resetAll.setEnabled(!snaps.isEmpty());

		revalidate();
		repaint();
	}

	private JPanel row(KillsLeftPlugin.Snapshot s, int target, SkillIconManager icons,
		java.util.function.BiConsumer<Skill, Integer> onTargetChanged,
		java.util.function.Consumer<Skill> onResetSkill)
	{
		Skill skill = skillByName(s.skillName);
		JPanel p = new JPanel(new BorderLayout(6, 4));
		p.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		p.setBorder(new EmptyBorder(6, 6, 6, 6));

		BufferedImage img = null;
		try
		{
			img = icons.getSkillImage(skill);
		}
		catch (Exception ignored)
		{
		}
		JLabel icon = img == null ? new JLabel() : new JLabel(new ImageIcon(img));
		p.add(icon, BorderLayout.WEST);

		JPanel mid = new JPanel(new GridLayout(0, 1));
		mid.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		String kills = s.killsLeft < 0 ? "done" : s.killsLeft + " kills";
		mid.add(new JLabel(s.skillName + " " + s.level + " → " + s.target));
		mid.add(new JLabel(kills + "  (" + (int) Math.round(s.progress * 100) + "%)"));
		JButton reset = new JButton("Reset");
		reset.addActionListener(e -> onResetSkill.accept(skill));
		mid.add(reset);
		p.add(mid, BorderLayout.CENTER);

		JSpinner spinner = new JSpinner(new SpinnerNumberModel(
			Math.min(99, Math.max(s.level + 1, target)), s.level + 1, 99, 1));
		spinner.addChangeListener((ChangeEvent e) ->
			onTargetChanged.accept(skill, (Integer) spinner.getValue()));
		p.add(spinner, BorderLayout.EAST);

		return p;
	}

	private Skill skillByName(String name)
	{
		for (Skill s : Skill.values())
		{
			if (s.getName().equals(name))
			{
				return s;
			}
		}
		return null;
	}
}
