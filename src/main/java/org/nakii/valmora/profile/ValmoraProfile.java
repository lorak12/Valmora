package org.nakii.valmora;

import java.util.UUID;

public class ValmoraProfile {
    private final UUID id;
    private final String name;

    public ValmoraProfile(String name) {
        this.id = UUID.randomUUID();
        this.name = name;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
}
