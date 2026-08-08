package org.nakii.valmora.module.script.event;

import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.execution.SimpleExecutionContext;
import org.nakii.valmora.module.script.event.impl.InterruptEventFactory;

import static org.junit.jupiter.api.Assertions.*;

class InterruptEventFactoryTest {

    private final InterruptEventFactory factory = new InterruptEventFactory();

    @Test
    void getName_returnsInterrupt() {
        assertEquals("interrupt", factory.getName());
    }

    @Test
    void execute_setsPipelineInterruptedAttachment() {
        SimpleExecutionContext ctx = new SimpleExecutionContext(null, null, null);
        assertNull(ctx.get("pipeline:interrupted"));

        factory.compile(new String[0], EventOptions.DEFAULT).execute(ctx);

        assertEquals(Boolean.TRUE, ctx.get("pipeline:interrupted"));
    }

    @Test
    void execute_ignoresArgs() {
        SimpleExecutionContext ctx = new SimpleExecutionContext(null, null, null);
        factory.compile(new String[]{"ignored", "args"}, EventOptions.DEFAULT).execute(ctx);
        assertEquals(Boolean.TRUE, ctx.get("pipeline:interrupted"));
    }
}
