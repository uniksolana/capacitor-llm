package ee.forgr.capgo_llm;

import java.io.File;

final class ModelDownloadFiles {
    private ModelDownloadFiles() {}

    static void cleanupStaleTemps(File parentDirectory) {
        if (parentDirectory == null || !parentDirectory.isDirectory()) {
            return;
        }

        File[] entries = parentDirectory.listFiles();
        if (entries == null) return;

        for (File entry : entries) {
            String name = entry.getName();
            if (!entry.isFile() || !name.startsWith("agathia-") || !name.endsWith(".part")) {
                continue;
            }
            if (!entry.delete()) {
                android.util.Log.w("CapgoLLM", "Could not remove stale model download temporary file: " + entry.getAbsolutePath());
            }
        }
    }
}
