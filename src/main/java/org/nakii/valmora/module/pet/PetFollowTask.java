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

    private static final double FOLLOW_DISTANCE = 2.5;
    private static final double TELEPORT_DISTANCE = 12.0;
    private static final double STEP = 0.35;

    private final Valmora plugin;
    private final Map<UUID, Entity> activePets;

    public PetFollowTask(Valmora plugin, Map<UUID, Entity> activePets) {
        this.plugin = plugin;
        this.activePets = activePets;
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
            if (distance <= FOLLOW_DISTANCE) continue;

            if (distance >= TELEPORT_DISTANCE) {
                pet.teleport(ownerLoc.clone().add(1, 0, 0));
                continue;
            }

            org.bukkit.util.Vector direction = ownerLoc.toVector().subtract(petLoc.toVector()).normalize().multiply(STEP);
            Location stepped = petLoc.clone().add(direction);
            stepped.setY(ownerLoc.getY());
            stepped.setDirection(ownerLoc.toVector().subtract(petLoc.toVector()));
            pet.teleport(stepped);
        }
    }
}
