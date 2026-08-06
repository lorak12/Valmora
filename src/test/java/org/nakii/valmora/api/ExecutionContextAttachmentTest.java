package org.nakii.valmora.api;

import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.execution.SimpleExecutionContext;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers Phase 1.2 of the refactor (docs/REFACTOR/PROGRESS.md): the generic namespaced
 * key-value store on {@link org.nakii.valmora.api.execution.ExecutionContext} and its
 * parent-child inheritance semantics.
 */
public class ExecutionContextAttachmentTest {

    @Test
    void setThenGetRoundTripsOnTheSameContext() {
        SimpleExecutionContext ctx = new SimpleExecutionContext(null, null, null);
        ctx.set("pet:level", 5);
        assertEquals(5, (Integer) ctx.get("pet:level"));
        assertTrue(ctx.has("pet:level"));
    }

    @Test
    void missingKeyReturnsNullAndDefaultValueFallsThrough() {
        SimpleExecutionContext ctx = new SimpleExecutionContext(null, null, null);
        assertNull(ctx.get("missing:key"));
        assertEquals("fallback", ctx.get("missing:key", "fallback"));
        assertFalse(ctx.has("missing:key"));
    }

    @Test
    void childContextInheritsFromParentOnLocalMiss() {
        SimpleExecutionContext parent = new SimpleExecutionContext(null, null, null);
        parent.set("slayer:target", "revenant_horror");

        SimpleExecutionContext child = new SimpleExecutionContext(null, null, null, null, parent);
        assertEquals("revenant_horror", child.get("slayer:target"));
        assertTrue(child.has("slayer:target"));
    }

    @Test
    void setOnChildNeverWritesThroughToParent() {
        SimpleExecutionContext parent = new SimpleExecutionContext(null, null, null);
        SimpleExecutionContext child = new SimpleExecutionContext(null, null, null, null, parent);

        child.set("prop.key", "child-value");

        assertEquals("child-value", child.get("prop.key"));
        assertNull(parent.get("prop.key"), "set() must only write locally, never to the parent");
    }

    @Test
    void localValueShadowsParentValue() {
        SimpleExecutionContext parent = new SimpleExecutionContext(null, null, null);
        parent.set("shared", "from-parent");
        SimpleExecutionContext child = new SimpleExecutionContext(null, null, null, null, parent);
        child.set("shared", "from-child");

        assertEquals("from-child", child.get("shared"));
        assertEquals("from-parent", parent.get("shared"));
    }

    @Test
    void removeOnlyAffectsLocalContext() {
        SimpleExecutionContext parent = new SimpleExecutionContext(null, null, null);
        parent.set("k", "v");
        SimpleExecutionContext child = new SimpleExecutionContext(null, null, null, null, parent);
        child.set("k", "child-v");

        child.remove("k");

        // local copy removed, so it now falls through to parent's value
        assertEquals("v", child.get("k"));
    }

    @Test
    void keySetReturnsOnlyLocalKeysNotInheritedOnes() {
        SimpleExecutionContext parent = new SimpleExecutionContext(null, null, null);
        parent.set("parent-key", "v");
        SimpleExecutionContext child = new SimpleExecutionContext(null, null, null, null, parent);
        child.set("child-key", "v");

        assertTrue(child.keySet().contains("child-key"));
        assertFalse(child.keySet().contains("parent-key"));
    }
}
