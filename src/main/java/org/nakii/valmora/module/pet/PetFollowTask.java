package org.nakii.valmora.module.pet;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.nakii.valmora.Valmora;

import java.util.Map;
import java.util.UUID;

/**
 * Basic owner-follow behavior. Pet entities are spawned with {@code setAI(false)} (see
 * {@link PetModule#toggleSummon}) so vanilla goal selectors — and with them, the vanilla
 * pathfinder — never run; this task instead steps each pet a short distance toward its owner
 * every few ticks, teleporting to catch up if it falls too far behind (e.g. after the owner
 * teleports/warps). Deliberately simple — not a full pathfinding follow (that would require AI
 * enabled, which would also re-enable vanilla wandering/targeting we don't want).
 */
public class PetFollowTask implements Runnable {

    // HC-230: follow-feel tuning, read once per task construction (module re-created per reload).
    private final double followDistance;
    private final double teleportDistance;
    private final double step;

    private final Valmora plugin;
    private final Map<UUID, Entity> activePets;

    public PetFollowTask(Valmora plugin, Map<UUID, Entity> activePets) {
        this.plugin = plugin;
        this.activePets = activePets;
        this.followDistance = plugin.getConfig().getDouble("pets.follow.follow-distance", 2.5);
        this.teleportDistance = plugin.getConfig().getDouble("pets.follow.teleport-distance", 12.0);
        this.step = plugin.getConfig().getDouble("pets.follow.step", 0.35);
    }

    @Override
    public void run() {
        for (Map.Entry<UUID, Entity> entry : activePets.entrySet()) {
            Player owner = Bukkit.getPlayer(entry.getKey());
            Entity pet = entry.getValue();
            if (owner == null || !owner.isOnline() || pet == null || !pet.isValid()) continue;

            Location ownerLoc = owner.getLocation();
            Location petLoc = pet.getLocation();
            if (!ownerLoc.getWorld().equals(petLoc.getWorld())) {
                pet.teleport(ownerLoc.clone().add(1, 0, 0));
                continue;
            }

            double distance = ownerLoc.distance(petLoc);
            if (distance <= followDistance) continue;

            if (distance >= teleportDistance) {
                pet.teleport(ownerLoc.clone().add(1, 0, 0));
                continue;
            }

            org.bukkit.util.Vector direction = ownerLoc.toVector().subtract(petLoc.toVector()).normalize().multiply(step);
            Location stepped = petLoc.clone().add(direction);
            stepped.setY(ownerLoc.getY());
            stepped.setDirection(ownerLoc.toVector().subtract(petLoc.toVector()));
            pet.teleport(stepped);
        }
    }
}
