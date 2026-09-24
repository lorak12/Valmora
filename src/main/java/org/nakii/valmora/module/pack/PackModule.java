package org.nakii.valmora.module.pack;

import org.nakii.valmora.Valmora;
import org.nakii.valmora.api.ReloadableModule;
import org.nakii.valmora.database.DataStore;
import org.nakii.valmora.module.pack.validate.PackReferenceCheckerRegistry;

/**
 * The content pack manager (docs/modules/design/pack.md). Registered last in the module order — it
 * only orchestrates other modules' existing reload machinery ({@code ModuleManager.reloadModules})
 * and must never be a dependency of anything else.
 *
 * <p><b>Namespacing hook lifetime — deliberately NOT owned by this module's onEnable/onDisable:</b>
 * {@link PackNamespacer}'s {@code YamlLoader} hook must already be active before <em>any</em> other
 * module's {@code onEnable()} runs (every content loader needs it while parsing), but this module is
 * registered — and therefore enabled — <em>last</em>. Tying the hook to this module's own lifecycle
 * would mean every earlier module loads unnamespaced on first boot, and {@code /valmora reload} would
 * repeat that bug every time (module disable/enable both happen in a single pass, and this module's
 * onDisable() still runs before every other module's onEnable() within that pass). So {@code Valmora}
 * installs the hook once, directly, immediately after the database is ready and before
 * {@code moduleManager.enableModules()} — and uninstalls it once, at actual plugin shutdown — passing
 * the same {@link PackFileIndex} instance into this module's constructor. This module's own
 * onEnable()/onDisable() only manage its own DB-backed bookkeeping ({@link PackManager}'s installed-
 * pack cache), exactly like every other module's registry-clear-then-reload pattern.
 *
 * <p><b>Phase status:</b> install/uninstall/rollback against a locally-staged pack directory work
 * (Phase 3). Remote download (Phase 4) and the {@code /valmora pack ...} command surface are not
 * wired up yet.
 */
public class PackModule implements ReloadableModule {

    private final Valmora plugin;
    private final DataStore dataStore;
    private final PackFileIndex fileIndex;
    private final PackReferenceCheckerRegistry referenceCheckerRegistry = new PackReferenceCheckerRegistry();
    private PackManager packManager;

    public PackModule(Valmora plugin, DataStore dataStore, PackFileIndex fileIndex) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.fileIndex = fileIndex;
    }

    @Override
    public void onEnable() {
        referenceCheckerRegistry.register(new org.nakii.valmora.module.pack.validate.IndexedPackReferenceChecker(fileIndex));
        packManager = new PackManager(plugin, dataStore, fileIndex);
        packManager.loadInstalledPacks();
    }

    @Override
    public void onDisable() {
        referenceCheckerRegistry.clear();
        packManager = null;
    }

    @Override
    public String getId() {
        return "pack";
    }

    @Override
    public String getName() {
        return "Content Pack Manager";
    }

    public PackFileIndex getFileIndex() {
        return fileIndex;
    }

    public PackReferenceCheckerRegistry getReferenceCheckerRegistry() {
        return referenceCheckerRegistry;
    }

    public PackManager getPackManager() {
        return packManager;
    }
}
