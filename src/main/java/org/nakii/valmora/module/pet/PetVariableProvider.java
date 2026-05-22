package org.nakii.valmora.module.pet;

import org.bukkit.entity.Player;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.module.script.variable.VariableProvider;

public class PetVariableProvider implements VariableProvider {

    private final PetModule module;

    public PetVariableProvider(PetModule module) {
        this.module = module;
    }

    @Override
    public String getNamespace() { return "pet"; }

    @Override
    public Object resolve(String[] path, ExecutionContext context) {
        if (path.length == 0) return null;
        Player player = context.getPlayerCaster().orElse(null);
        if (player == null) return null;

        return switch (path[0].toLowerCase()) {
            case "id" -> {
                var def = module.getActivePetDefinition(player);
                yield def != null ? def.getId() : "none";
            }
            case "name" -> {
                var def = module.getActivePetDefinition(player);
                yield def != null ? def.getName() : "None";
            }
            case "level" -> module.getActivePetLevel(player);
            case "xp" -> module.getActivePetXp(player);
            case "max_xp" -> PetDefinition.xpForLevel(module.getActivePetLevel(player));
            case "active" -> module.hasPetActive(player);
            default -> null;
        };
    }
}
