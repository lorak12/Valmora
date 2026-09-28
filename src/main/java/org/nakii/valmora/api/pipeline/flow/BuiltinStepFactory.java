package org.nakii.valmora.api.pipeline.flow;

import java.util.List;
import java.util.function.Function;

/**
 * A built-in step type a flow can use ({@code - builtin: <id>}). Registered by the owning domain
 * on its {@link FlowSpec}; {@link #compile} runs once per load with the step's {@code params:}.
 */
public interface BuiltinStepFactory<S extends FlowState> {

    String id();

    /**
     * A required step can't be removed or disabled — the flow fails validation and the last good
     * (or bundled default) flow is used instead. Reserved for steps without which the feature is
     * broken rather than merely different (e.g. applying the damage after zeroing vanilla's).
     */
    default boolean required() {
        return false;
    }

    /** The keys this step accepts under {@code params:} (anything else is warned about). */
    default List<String> paramKeys() {
        return List.of();
    }

    BuiltinStep<S> compile(StepParams params);

    /** A step with no params. */
    static <S extends FlowState> BuiltinStepFactory<S> of(String id, BuiltinStep<S> step) {
        return new Simple<>(id, false, List.of(), params -> step);
    }

    /** A required step with no params. */
    static <S extends FlowState> BuiltinStepFactory<S> required(String id, BuiltinStep<S> step) {
        return new Simple<>(id, true, List.of(), params -> step);
    }

    /** A step with params. */
    static <S extends FlowState> BuiltinStepFactory<S> of(String id, List<String> paramKeys,
                                                          Function<StepParams, BuiltinStep<S>> compiler) {
        return new Simple<>(id, false, paramKeys, compiler);
    }

    /** A required step with params. */
    static <S extends FlowState> BuiltinStepFactory<S> required(String id, List<String> paramKeys,
                                                                Function<StepParams, BuiltinStep<S>> compiler) {
        return new Simple<>(id, true, paramKeys, compiler);
    }

    record Simple<S extends FlowState>(String id, boolean required, List<String> paramKeys,
                                       Function<StepParams, BuiltinStep<S>> compiler) implements BuiltinStepFactory<S> {
        @Override
        public BuiltinStep<S> compile(StepParams params) {
            return compiler.apply(params);
        }
    }
}
