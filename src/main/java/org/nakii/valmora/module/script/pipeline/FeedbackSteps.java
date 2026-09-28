package org.nakii.valmora.module.script.pipeline;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.nakii.valmora.api.pipeline.flow.BuiltinStepFactory;
import org.nakii.valmora.api.pipeline.flow.FlowState;
import org.nakii.valmora.api.pipeline.flow.StepOutcome;
import org.nakii.valmora.infrastructure.config.read.ConfigReader;

import java.util.List;
import java.util.Locale;

/**
 * Generic {@code play_sound}/{@code spawn_particle} built-ins for "feedback" flows (the fishing
 * bite, resource mine/deny/regen cues) — what used to be hardcoded {@code world.playSound(...)}/
 * {@code world.spawnParticle(...)} calls, now a list of steps an admin can retune, remove or extend.
 *
 * <p>Sounds and particles are resolved on first use and cached (they need a running server); when
 * a server is present they're also validated at load time so a typo shows up in the load report.
 */
public final class FeedbackSteps {

    private FeedbackSteps() {}

    /** A flow state that has a place (and, for block particles, a material) to play feedback at. */
    public interface Located extends FlowState {
        Location feedbackLocation();

        /** The block material for {@code block-data: true} particles; null if there's none. */
        default Material feedbackMaterial() {
            return null;
        }
    }

    /** {@code play_sound} — params: {@code sound} (namespaced key, e.g. {@code block.stone.break}), {@code volume}, {@code pitch}. */
    public static <S extends Located> BuiltinStepFactory<S> playSound() {
        return BuiltinStepFactory.of("play_sound", List.of("sound", "volume", "pitch"), params -> {
            ConfigReader reader = params.reader();
            String key = reader.requireString("sound");
            float volume = (float) reader.doubleRange("volume", 1.0, 0.0, 10.0);
            float pitch = (float) reader.doubleRange("pitch", 1.0, 0.0, 2.0);
            SoundRef sound = new SoundRef(key);
            if (key != null && serverRunning() && sound.get() == null) {
                reader.error("sound", "unknown sound '" + key + "' (use the namespaced form, e.g. block.stone.break)");
            }
            return (ctx, state) -> {
                Location loc = state.feedbackLocation();
                Sound resolved = sound.get();
                if (loc == null || resolved == null) return StepOutcome.CONTINUE;
                World world = loc.getWorld();
                if (world != null) world.playSound(loc, resolved, volume, pitch);
                return StepOutcome.CONTINUE;
            };
        });
    }

    /**
     * {@code spawn_particle} — params: {@code particle} (Particle name), {@code count},
     * {@code offset} ([x, y, z] added to the location), {@code spread} ([dx, dy, dz]),
     * {@code speed}, {@code block-data} (true = use the state's block material, for BLOCK-type
     * particles).
     */
    public static <S extends Located> BuiltinStepFactory<S> spawnParticle() {
        return BuiltinStepFactory.of("spawn_particle", List.of("particle", "count", "offset", "spread", "speed", "block-data"), params -> {
            ConfigReader reader = params.reader();
            String name = reader.requireString("particle");
            int count = reader.intRange("count", 1, 0, 10_000);
            double[] offset = vector(reader, "offset");
            double[] spread = vector(reader, "spread");
            double speed = reader.doubleValue("speed", 1.0);
            boolean blockData = reader.bool("block-data", false);
            ParticleRef particle = new ParticleRef(name);
            if (name != null && serverRunning()) {
                Particle resolved = particle.get();
                if (resolved == null) {
                    reader.error("particle", "unknown particle '" + name + "'");
                } else if (resolved.getDataType() == BlockData.class && !blockData) {
                    reader.warn("particle", "particle '" + name + "' needs block data — add 'block-data: true'");
                }
            }
            return (ctx, state) -> {
                Location base = state.feedbackLocation();
                Particle resolved = particle.get();
                if (base == null || resolved == null || base.getWorld() == null) return StepOutcome.CONTINUE;
                Location loc = base.clone().add(offset[0], offset[1], offset[2]);
                if (blockData) {
                    Material material = state.feedbackMaterial();
                    if (material == null || !material.isBlock()) return StepOutcome.CONTINUE;
                    loc.getWorld().spawnParticle(resolved, loc, count, spread[0], spread[1], spread[2], speed, material.createBlockData());
                } else {
                    loc.getWorld().spawnParticle(resolved, loc, count, spread[0], spread[1], spread[2], speed);
                }
                return StepOutcome.CONTINUE;
            };
        });
    }

    private static double[] vector(ConfigReader reader, String key) {
        ConfigurationSection section = reader.section();
        if (section == null || !section.contains(key)) return new double[]{0, 0, 0};
        List<?> raw = section.getList(key);
        if (raw == null || raw.size() != 3) {
            reader.warn(key, "expected a list of 3 numbers [x, y, z] — using [0, 0, 0]");
            return new double[]{0, 0, 0};
        }
        double[] out = new double[3];
        for (int i = 0; i < 3; i++) {
            out[i] = FlowState.asDouble(raw.get(i), 0);
        }
        return out;
    }

    private static boolean serverRunning() {
        try {
            return Bukkit.getServer() != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Lazily-resolved sound (the registry needs a server). */
    private static final class SoundRef {
        private final String key;
        private volatile Sound sound;
        private volatile boolean resolved;

        SoundRef(String key) {
            this.key = key;
        }

        Sound get() {
            if (!resolved) {
                if (key != null) {
                    NamespacedKey nk = NamespacedKey.fromString(key.toLowerCase(Locale.ROOT));
                    sound = nk == null ? null : Registry.SOUNDS.get(nk);
                }
                resolved = true;
            }
            return sound;
        }
    }

    /** Lazily-resolved particle. */
    private static final class ParticleRef {
        private final String name;
        private volatile Particle particle;
        private volatile boolean resolved;

        ParticleRef(String name) {
            this.name = name;
        }

        Particle get() {
            if (!resolved) {
                particle = name == null ? null : ConfigReader.parseEnum(name, Particle.class);
                resolved = true;
            }
            return particle;
        }
    }
}
