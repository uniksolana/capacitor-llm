package ee.forgr.capgo_llm;

import java.io.File;

final class ModelDownloadFiles {
    private ModelDownloadFiles() {}

    static void cleanupStaleTemps(File parentDirectory, String targetFilename) {
        if (parentDirectory == null || !parentDirectory.isDirectory()
            || targetFilename == null || targetFilename.isBlank()) {
            return;
        }

        File[] entries = parentDirectory.listFiles();
        if (entries == null) return;

        String prefix = "agathia-" + targetFilename + ".";
        for (File entry : entries) {
            String name = entry.getName();
            if (!entry.isFile() || !name.startsWith(prefix) || !name.endsWith(".part")) {
                continue;
            }
            if (!entry.delete()) {
                android.util.Log.w("CapgoLLM", "Could not remove stale model download temporary file: " + entry.getAbsolutePath());
            }
        }
    }
}
