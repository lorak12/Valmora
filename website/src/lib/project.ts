import fs from "node:fs";
import path from "node:path";

export const REPO_URL = "https://github.com/lorak12/Valmora";

/** The plugin version, read from the repo's build.gradle at build time so the docs badge can't drift. */
export const PLUGIN_VERSION: string = (() => {
  try {
    const gradle = fs.readFileSync(path.join(process.cwd(), "..", "build.gradle"), "utf8");
    return /^version\s*=\s*['"]([^'"]+)['"]/m.exec(gradle)?.[1] ?? "unknown";
  } catch {
    return "unknown";
  }
})();
