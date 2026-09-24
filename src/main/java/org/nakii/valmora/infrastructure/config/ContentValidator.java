package org.nakii.valmora.infrastructure.config;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.infrastructure.config.diag.ConfigDiagnostic;
import org.nakii.valmora.infrastructure.config.refs.ContentIndex;
import org.nakii.valmora.infrastructure.config.refs.ReferenceIndex;
import org.nakii.valmora.infrastructure.config.refs.ReferenceValidator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code /valmora validate}: a dry run over the content on disk — YAML syntax, every entry through
 * its parser, and every reference those entries make — without changing anything live.
 */
public final class ContentValidator {

    private ContentValidator() {}

    public static List<ConfigDiagnostic> validate(Valmora plugin) {
        List<ConfigDiagnostic> problems = new ArrayList<>();
        Map<String, Set<String>> definedOnDisk = new HashMap<>();
        ReferenceIndex scratch = new ReferenceIndex();
        ReferenceIndex.capturing(scratch, () ->
                problems.addAll(YamlLoader.validateAllDiagnostics(plugin, definedOnDisk)));
        // References to content that exists on disk but isn't loaded yet are fine — the reload
        // that follows will load it.
        new ReferenceValidator(ContentIndex.global(), scratch).runDanglingOnly(problems::add, definedOnDisk);
        return problems;
    }
}
