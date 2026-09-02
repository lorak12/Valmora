package org.nakii.valmora.module.pack.validate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Holds the registered {@link PackReferenceChecker}s (see its class doc) and runs them all for one pack. */
public class PackReferenceCheckerRegistry {

    private final List<PackReferenceChecker> checkers = new ArrayList<>();

    public void register(PackReferenceChecker checker) {
        checkers.add(checker);
    }

    public List<PackReferenceChecker> getCheckers() {
        return Collections.unmodifiableList(checkers);
    }

    /** Runs every registered checker against {@code packId} and returns all their findings combined. */
    public List<String> checkAll(String packId) {
        List<String> findings = new ArrayList<>();
        for (PackReferenceChecker checker : checkers) {
            findings.addAll(checker.check(packId));
        }
        return findings;
    }

    public void clear() {
        checkers.clear();
    }
}
