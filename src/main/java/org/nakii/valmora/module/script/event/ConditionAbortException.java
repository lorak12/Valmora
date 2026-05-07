package org.nakii.valmora.module.script.event;

public class ConditionAbortException extends RuntimeException {
    public ConditionAbortException() {
        super(null, null, true, false); // suppress stack trace for performance
    }
}
