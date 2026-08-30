package org.nakii.valmora.module.machine;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Id -> {@link MachineDefinition}, case-insensitive (CLAUDE.md §7.2 Registry convention). */
public class MachineRegistry {

    private final Map<String, MachineDefinition> machines = new LinkedHashMap<>();

    public void register(MachineDefinition machine) {
        machines.put(machine.getId().toLowerCase(Locale.ROOT), machine);
    }

    public Optional<MachineDefinition> get(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(machines.get(id.toLowerCase(Locale.ROOT)));
    }

    public Collection<MachineDefinition> values() {
        return machines.values();
    }

    public void clear() {
        machines.clear();
    }
}
