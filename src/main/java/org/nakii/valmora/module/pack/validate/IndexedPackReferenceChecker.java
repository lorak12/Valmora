package org.nakii.valmora.module.pack.validate;

import org.nakii.valmora.infrastructure.config.diag.ConfigDiagnostic;
import org.nakii.valmora.infrastructure.config.refs.ContentIndex;
import org.nakii.valmora.infrastructure.config.refs.ContentRef;
import org.nakii.valmora.infrastructure.config.refs.ReferenceIndex;
import org.nakii.valmora.infrastructure.config.refs.ReferenceValidator;
import org.nakii.valmora.module.pack.PackFileIndex;

import java.util.ArrayList;
import java.util.List;

/**
 * The generic {@link PackReferenceChecker}: every reference the pack's own files made while
 * loading (item ids, GUIs, mobs, warps, ... — whatever the loaders and script events recorded in
 * the {@link ReferenceIndex}) must resolve, either in the pack's namespace or in base content.
 * One checker covers every content type, so none of them needs pack-specific code.
 */
public final class IndexedPackReferenceChecker implements PackReferenceChecker {

    private final PackFileIndex fileIndex;

    public IndexedPackReferenceChecker(PackFileIndex fileIndex) {
        this.fileIndex = fileIndex;
    }

    @Override
    public List<String> check(String packId) {
        ReferenceIndex packRefs = new ReferenceIndex();
        for (ContentRef ref : ReferenceIndex.global().all()) {
            String file = ref.source().file();
            if (file != null && fileIndex.ownerOf(file).map(packId::equalsIgnoreCase).orElse(false)) {
                packRefs.record(ref.kind(), ref.id(), ref.source());
            }
        }
        List<ConfigDiagnostic> found = new ArrayList<>();
        new ReferenceValidator(ContentIndex.global(), packRefs).runDanglingOnly(found::add, null);
        List<String> out = new ArrayList<>();
        for (ConfigDiagnostic d : found) out.add(d.format());
        return out;
    }
}
