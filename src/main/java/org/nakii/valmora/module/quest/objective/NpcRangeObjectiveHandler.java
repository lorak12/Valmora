package org.nakii.valmora.module.quest.objective;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.quest.ObjectiveHandler;
import org.nakii.valmora.module.npc.NpcManager;
import org.nakii.valmora.module.quest.QuestDefinition;
import org.nakii.valmora.module.quest.QuestManager;
import org.nakii.valmora.module.quest.QuestObjective;
import org.nakii.valmora.module.quest.QuestObjectiveTypes;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;

import java.util.Map;

/**
 * Handles npcrange objectives by polling every second.
 *
 * Target format: {@code "<action>:<npcId>"}
 *   action = enter | leave | inside | outside
 * Required: range in blocks (integer)
 *
 * State per player+objective is stored in profile variables:
 *   {@code npcrange.state.<questId>.<objKey> = "inside" | "outside"}
 */
public class NpcRangeObjectiveHandler implements ObjectiveHandler {

    private final Valmora plugin;
    private final QuestManager questManager;
    private BukkitTask task;

    public NpcRangeObjectiveHandler(Valmora plugin, QuestManager questManager) {
        this.plugin = plugin;
        this.questManager = questManager;
    }

    @Override
    public String getTypeId() { return QuestObjectiveTypes.NPCRANGE; }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    private void tick() {
        NpcManager npcManager = ValmoraAPI.getInstance().getNpcManager();
        if (npcManager == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!questManager.hasActiveObjectiveType(player, QuestObjectiveTypes.NPCRANGE)) continue;

            ValmoraPlayer vp = ValmoraAPI.getInstance().getPlayerManager().getSession(player.getUniqueId());
            if (vp == null || vp.getActiveProfile() == null) continue;
            ValmoraProfile profile = vp.getActiveProfile();

            for (QuestDefinition quest : questManager.getRegistry().values()) {
                if (!questManager.getStatus(profile, quest.getId()).equals(QuestManager.STATUS_IN_PROGRESS)) continue;

                for (QuestObjective obj : quest.getObjectives()) {
                    if (!obj.getType().equalsIgnoreCase(QuestObjectiveTypes.NPCRANGE)) continue;

                    String target = obj.getTarget();
                    int colonIdx = target.indexOf(':');
                    if (colonIdx < 0) continue;

                    String action = target.substring(0, colonIdx).toLowerCase();
                    String npcId  = target.substring(colonIdx + 1);
                    int range     = obj.getRequired();

                    Location npcLoc = npcManager.getSpawnedLocation(npcId);
                    if (npcLoc == null || !npcLoc.getWorld().equals(player.getWorld())) continue;

                    boolean inRange = player.getLocation().distanceSquared(npcLoc) <= (double) range * range;

                    String key      = obj.getId() != null ? obj.getId() : "npcrange";
                    String stateVar = "npcrange.state." + quest.getId() + "." + key;
                    String prevState = profile.getVariables().getOrDefault(stateVar, "outside").toString();
                    boolean wasInside = "inside".equals(prevState);

                    profile.getVariables().put(stateVar, inRange ? "inside" : "outside");

                    switch (action) {
                        case "inside" -> {
                            if (inRange)
                                questManager.trigger(player, QuestObjectiveTypes.NPCRANGE, target, 1);
                        }
                        case "outside" -> {
                            if (!inRange)
                                questManager.trigger(player, QuestObjectiveTypes.NPCRANGE, target, 1);
                        }
                        case "enter" -> {
                            if (inRange && !wasInside)
                                questManager.trigger(player, QuestObjectiveTypes.NPCRANGE, target, 1);
                        }
                        case "leave" -> {
                            if (!inRange && wasInside)
                                questManager.trigger(player, QuestObjectiveTypes.NPCRANGE, target, 1);
                        }
                    }
                }
            }
        }
    }
}
