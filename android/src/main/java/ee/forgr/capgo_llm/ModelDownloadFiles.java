package ee.forgr.capgo_llm;

import java.io.File;
import java.io.IOException;

final class ModelDownloadFiles {
    private ModelDownloadFiles() {}

    static boolean isAllowedRedirectProtocol(String currentProtocol, String nextProtocol) {
        boolean currentHttp = "http".equalsIgnoreCase(currentProtocol);
        boolean currentHttps = "https".equalsIgnoreCase(currentProtocol);
        boolean nextHttp = "http".equalsIgnoreCase(nextProtocol);
        boolean nextHttps = "https".equalsIgnoreCase(nextProtocol);
        if ((!currentHttp && !currentHttps) || (!nextHttp && !nextHttps)) return false;
        return !currentHttps || nextHttps;
    }

    static void cleanupStaleTemps(File parentDirectory) throws IOException {
        if (parentDirectory == null || !parentDirectory.isDirectory()) {
            return;
        }

        File[] entries = parentDirectory.listFiles();
        if (entries == null) {
            throw new IOException("Could not inspect private storage for stale model download files");
        }

        for (File entry : entries) {
            String name = entry.getName();
            if (!entry.isFile() || !name.startsWith("agathia-") || !name.endsWith(".part")) {
                continue;
            }
            if (!entry.delete() && entry.exists()) {
                throw new IOException("Could not remove stale model download temporary file: " + entry.getName());
            }
        }
    }
}
