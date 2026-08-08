package org.nakii.valmora.module.script.condition;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nakii.valmora.api.ValmoraAPI;
import org.nakii.valmora.api.execution.ExecutionContext;
import org.nakii.valmora.api.scripting.Condition;
import org.nakii.valmora.api.scripting.TagService;
import org.nakii.valmora.api.scripting.VariableResolver;
import org.nakii.valmora.module.profile.PlayerManager;
import org.nakii.valmora.module.profile.PlayerState;
import org.nakii.valmora.module.profile.ValmoraPlayer;
import org.nakii.valmora.module.profile.ValmoraProfile;
import org.nakii.valmora.module.quest.QuestManager;
import org.nakii.valmora.module.script.expression.ExpressionParser;
import org.nakii.valmora.module.stat.StatRegistry;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers the 11 {@link Condition} implementations under module/script/condition, plus
 * {@link ConditionParser}'s dispatch/negation logic — previously entirely untested
 * (docs/IMPLEMENTATION_BACKLOG.md "Add unit tests for untested modules" / script item).
 */
public class ConditionTest {

    private final ValmoraAPI api = mock(ValmoraAPI.class);
    private final PlayerManager playerManager = mock(PlayerManager.class);
    private final QuestManager questManager = mock(QuestManager.class);
    private final VariableResolver variableResolver = mock(VariableResolver.class);

    private final UUID uuid = UUID.randomUUID();
    private Player player;
    private ValmoraPlayer valmoraPlayer;
    private ValmoraProfile profile;

    @BeforeEach
    void setUp() {
        ValmoraAPI.setProvider(api);
        when(api.getPlayerManager()).thenReturn(playerManager);
        when(api.getQuestManager()).thenReturn(questManager);
        when(api.getStatRegistry()).thenReturn(new StatRegistry());

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);

        profile = new ValmoraProfile("test");
        valmoraPlayer = new ValmoraPlayer(uuid);
        valmoraPlayer.addProfile(profile);
        when(playerManager.getSession(uuid)).thenReturn(valmoraPlayer);
    }

    private ExecutionContext contextFor(Player caster) {
        return new StubExecutionContext(caster, variableResolver);
    }

    // --- TagCondition ---

    @Test
    void tagCondition_matchesWhenTagPresent() {
        profile.getTags().add("vip");
        assertTrue(new TagCondition("vip").evaluate(contextFor(player)));
        assertFalse(new TagCondition("nope").evaluate(contextFor(player)));
    }

    @Test
    void tagCondition_falseWithNoCaster() {
        assertFalse(new TagCondition("vip").evaluate(contextFor(null)));
    }

    // --- HealthCondition ---

    @Test
    void healthCondition_comparesAgainstCurrentHealth() {
        when(player.getHealth()).thenReturn(15.0);
        assertTrue(new HealthCondition(10.0).evaluate(contextFor(player)));
        assertFalse(new HealthCondition(20.0).evaluate(contextFor(player)));
    }

    // --- HungerCondition ---

    @Test
    void hungerCondition_comparesAgainstFoodLevel() {
        when(player.getFoodLevel()).thenReturn(12);
        assertTrue(new HungerCondition(10).evaluate(contextFor(player)));
        assertFalse(new HungerCondition(15).evaluate(contextFor(player)));
    }

    // --- LocationCondition ---

    @Test
    void locationCondition_parseValidString() {
        LocationCondition lc = LocationCondition.parse("1.0;2.0;3.0;world", 5.0);
        assertNotNull(lc);
        assertEquals("world", lc.worldName());
        assertEquals(1.0, lc.x());
        assertEquals(5.0, lc.radius());
    }

    @Test
    void locationCondition_parseInvalidStringReturnsNull() {
        assertNull(LocationCondition.parse("1.0;2.0", 5.0));
        assertNull(LocationCondition.parse("a;b;c;world", 5.0));
    }

    // --- ObjectiveActiveCondition ---

    @Test
    void objectiveActiveCondition_delegatesToQuestManager() {
        when(questManager.isObjectiveActive(profile, "kill_zombies")).thenReturn(true);
        assertTrue(new ObjectiveActiveCondition("kill_zombies").evaluate(contextFor(player)));

        when(questManager.isObjectiveActive(profile, "other")).thenReturn(false);
        assertFalse(new ObjectiveActiveCondition("other").evaluate(contextFor(player)));
    }

    // --- QuestStatusCondition ---

    @Test
    void questStatusCondition_matchesStatusCaseInsensitively() {
        when(questManager.getStatus(profile, "intro")).thenReturn("COMPLETED");
        assertTrue(new QuestStatusCondition("intro", "completed").evaluate(contextFor(player)));
        assertFalse(new QuestStatusCondition("intro", "in_progress").evaluate(contextFor(player)));
    }

    // --- VariableCondition ---

    @Test
    void variableCondition_numericComparison() {
        when(variableResolver.resolve(eq("$player.stat.HEALTH$"), any())).thenReturn(50.0);
        VariableCondition cond = new VariableCondition("player.stat.HEALTH", ">", "10");
        assertTrue(cond.evaluate(contextFor(player)));

        VariableCondition condFalse = new VariableCondition("player.stat.HEALTH", "<", "10");
        assertFalse(condFalse.evaluate(contextFor(player)));
    }

    @Test
    void variableCondition_stringFallbackWhenNotNumeric() {
        when(variableResolver.resolve(eq("$player.name$"), any())).thenReturn("Steve");
        assertTrue(new VariableCondition("player.name", "==", "Steve").evaluate(contextFor(player)));
        assertTrue(new VariableCondition("player.name", "!=", "Alex").evaluate(contextFor(player)));
        // Unsupported operator for string comparison falls through to false.
        assertFalse(new VariableCondition("player.name", ">", "Alex").evaluate(contextFor(player)));
    }

    @Test
    void variableCondition_nullResolvesToLiteralNullString() {
        when(variableResolver.resolve(eq("$missing.path$"), any())).thenReturn(null);
        assertTrue(new VariableCondition("missing.path", "==", "null").evaluate(contextFor(player)));
    }

    // --- ZoneCondition ---

    @Test
    void zoneCondition_matchesCurrentZoneCaseInsensitively() {
        PlayerState state = profile.getPlayerState();
        state.setCurrentZoneId("hub");
        assertTrue(new ZoneCondition("HUB").evaluate(contextFor(player)));
        assertFalse(new ZoneCondition("mine").evaluate(contextFor(player)));
    }

    // --- ExpressionCondition ---

    @Test
    void expressionCondition_trueOnlyForBooleanTrueResult() {
        assertTrue(new ExpressionCondition(ctx -> Boolean.TRUE).evaluate(contextFor(player)));
        assertFalse(new ExpressionCondition(ctx -> Boolean.FALSE).evaluate(contextFor(player)));
        assertFalse(new ExpressionCondition(ctx -> "not a boolean").evaluate(contextFor(player)));
        assertFalse(new ExpressionCondition(ctx -> null).evaluate(contextFor(player)));
    }

    // --- ConditionGroup ---

    @Test
    void conditionGroup_emptyIsVacuouslyTrue() {
        assertTrue(new ConditionGroup(java.util.List.of()).evaluate(contextFor(player)));
    }

    @Test
    void conditionGroup_isAndOfAllConditions() {
        Condition alwaysTrue = ctx -> true;
        Condition alwaysFalse = ctx -> false;
        assertTrue(new ConditionGroup(java.util.List.of(alwaysTrue, alwaysTrue)).evaluate(contextFor(player)));
        assertFalse(new ConditionGroup(java.util.List.of(alwaysTrue, alwaysFalse)).evaluate(contextFor(player)));
    }

    // --- ConditionParser ---

    private final ConditionParser parser = new ConditionParser(new ExpressionParser());

    @Test
    void parser_dispatchesKeywordPrefixes() {
        assertInstanceOf(TagCondition.class, parser.parse("tag vip"));
        assertInstanceOf(HealthCondition.class, parser.parse("health 10"));
        assertInstanceOf(HungerCondition.class, parser.parse("hunger 5"));
        assertInstanceOf(ZoneCondition.class, parser.parse("zone hub"));
        assertInstanceOf(VariableCondition.class, parser.parse("variable player.stat.HEALTH > 10"));
        assertInstanceOf(ObjectiveActiveCondition.class, parser.parse("objective kill_zombies"));
        assertInstanceOf(QuestStatusCondition.class, parser.parse("quest intro completed"));
    }

    @Test
    void parser_fallsBackToExpressionForUnknownPrefix() {
        assertInstanceOf(ExpressionCondition.class, parser.parse("10 > 5"));
    }

    @Test
    void parser_malformedKeywordFallsBackToExpression() {
        // "health abc" fails to parse as a double -> falls through to ExpressionCondition.
        assertInstanceOf(ExpressionCondition.class, parser.parse("health abc"));
    }

    @Test
    void parser_negationWrapsInnerCondition() {
        when(player.getHealth()).thenReturn(5.0);
        Condition negated = parser.parse("!health 10");
        assertTrue(negated.evaluate(contextFor(player)));

        when(player.getHealth()).thenReturn(15.0);
        assertFalse(negated.evaluate(contextFor(player)));
    }

    @Test
    void parser_emptyStringYieldsVacuouslyTrueGroup() {
        Condition cond = parser.parse("");
        assertTrue(cond.evaluate(contextFor(player)));
        assertInstanceOf(ConditionGroup.class, cond);
    }

    @Test
    void parser_parseListAndsAllConditions() {
        when(player.getHealth()).thenReturn(20.0);
        when(player.getFoodLevel()).thenReturn(20);
        ConditionGroup group = parser.parseList(java.util.List.of("health 10", "hunger 10"));
        assertTrue(group.evaluate(contextFor(player)));

        when(player.getFoodLevel()).thenReturn(0);
        assertFalse(group.evaluate(contextFor(player)));
    }

    @Test
    void parser_parseInlineListSplitsOnCommaAndSupportsNegation() {
        when(player.getHealth()).thenReturn(20.0);
        when(player.getFoodLevel()).thenReturn(0);
        // "health 10" true AND "!hunger 10" true (food level 0 < 10)
        ConditionGroup group = parser.parseInlineList("health 10, !hunger 10");
        assertTrue(group.evaluate(contextFor(player)));
    }

    /** Minimal ExecutionContext stub — only the members the conditions above actually read. */
    private static class StubExecutionContext implements ExecutionContext {
        private final Player caster;
        private final VariableResolver resolver;

        StubExecutionContext(Player caster, VariableResolver resolver) {
            this.caster = caster;
            this.resolver = resolver;
        }

        @Override public org.bukkit.entity.LivingEntity getCaster() { return caster; }
        @Override public Optional<org.bukkit.entity.LivingEntity> getTarget() { return Optional.empty(); }
        @Override public org.bukkit.Location getLocation() { return null; }
        @Override public VariableResolver getVariableResolver() { return resolver; }
        @Override public TagService getTagService() { return null; }
        @Override public org.bukkit.configuration.ConfigurationSection getParams() { return null; }
    }
}
