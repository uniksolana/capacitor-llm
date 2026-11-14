package ee.forgr.capgo_llm;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "CapgoLLM")
public class LLMPlugin extends Plugin {

    private final String pluginVersion = "8.2.1-agathia.7";

    private LLM llm;

    @Override
    public void load() {
        super.load();
        llm = LLM.getInstance(getContext());
    }

    @PluginMethod
    public void createChat(PluginCall call) {
        try {
            String chatId = llm.createChat();
            JSObject ret = new JSObject();
            ret.put("id", chatId);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to create chat", e);
        }
    }

    @PluginMethod
    public void closeChat(PluginCall call) {
        String chatId = call.getString("chatId");
        if (chatId == null || chatId.isBlank()) {
            call.reject("chatId is required");
            return;
        }
        try {
            llm.closeChat(chatId, new LLM.CloseCallback() {
                @Override
                public void onSuccess() {
                    call.resolve();
                }

                @Override
                public void onError(String error) {
                    call.reject("Failed to close chat", error);
                }
            });
        } catch (Exception exception) {
            call.reject("Failed to close chat", exception);
        }
    }

    @PluginMethod
    public void sendMessage(PluginCall call) {
        String chatId = call.getString("chatId");
        String message = call.getString("message");

        if (chatId == null || message == null) {
            call.reject("chatId and message are required");
            return;
        }

        try {
            llm.sendMessage(
                chatId,
                message,
                new LLM.MessageCallback() {
                    @Override
                    public void onTextReceived(String chatId, String text, boolean isChunk) {
                        JSObject event = new JSObject();
                        event.put("text", text);
                        event.put("chatId", chatId);
                        event.put("isChunk", isChunk);
                        notifyListeners("textFromAi", event);
                    }

                    @Override
                    public void onComplete(String chatId) {
                        JSObject event = new JSObject();
                        event.put("chatId", chatId);
                        notifyListeners("aiFinished", event);
                    }

                    @Override
                    public void onError(String error) {
                        JSObject event = new JSObject();
                        event.put("chatId", chatId);
                        event.put("error", error);
                        notifyListeners("generationError", event);
                    }
                }
            );

            call.resolve();
        } catch (IllegalStateException exception) {
            call.reject(exception.getMessage());
        } catch (RuntimeException exception) {
            android.util.Log.e("CapgoLLM", "Unexpected failure while starting generation", exception);
            call.reject("Failed to start local generation", exception);
        }
    }

    @PluginMethod
    public void getReadiness(PluginCall call) {
        String readiness = llm.getReadiness();
        JSObject ret = new JSObject();
        ret.put("readiness", readiness);
        call.resolve(ret);

        // Also notify listeners
        JSObject readinessData = new JSObject();
        readinessData.put("readiness", readiness);
        notifyListeners("readinessChange", readinessData);
    }

    @PluginMethod
    public void setModel(PluginCall call) {
        String path = call.getString("path");
        String expectedSha256 = call.getString("expectedSha256");

        if (path == null) {
            call.reject("path is required");
            return;
        }
        if (expectedSha256 != null) {
            try {
                ModelFileIntegrity.parseExpectedSha256(expectedSha256);
            } catch (IllegalArgumentException exception) {
                call.reject(exception.getMessage(), exception);
                return;
            }
        }

        // Extract model parameters
        Integer maxTokens = call.getInt("maxTokens", 2048);
        Integer topk = call.getInt("topk", 40);
        Float temperature = call.getFloat("temperature", 0.8f);
        Integer randomSeed = call.getInt("randomSeed", 0);
        String modelType = call.getString("modelType");
        String backend = call.getString("backend");
        String androidGeminiBackend = call.getString("androidGeminiBackend");

        try {
            llm.setModel(
                path,
                expectedSha256,
                modelType,
                maxTokens,
                topk,
                temperature,
                randomSeed,
                backend,
                androidGeminiBackend,
                new LLM.ModelLoadCallback() {
                    @Override
                    public void onSuccess() {
                        // Notify readiness change
                        JSObject readinessData = new JSObject();
                        readinessData.put("readiness", "ready");
                        notifyListeners("readinessChange", readinessData);
                        call.resolve();
                    }

                    @Override
                    public void onError(String error) {
                        // Notify readiness change
                        JSObject readinessData = new JSObject();
                        readinessData.put("readiness", "Failed to load model: " + error);
                        notifyListeners("readinessChange", readinessData);
                        call.reject("Failed to load model", error);
                    }
                }
            );
        } catch (RuntimeException exception) {
            android.util.Log.e("CapgoLLM", "Unexpected failure while setting local model", exception);
            call.reject("Failed to initialize local model", exception);
        }
    }

    @PluginMethod
    public void downloadModel(PluginCall call) {
        String url = call.getString("url");
        String companionUrl = call.getString("companionUrl");
        String filename = call.getString("filename");
        String expectedSha256 = call.getString("expectedSha256");

        if (url == null || url.isBlank()) {
            call.reject("url is required");
            return;
        }
        final byte[] expectedDigest;
        try {
            expectedDigest = expectedSha256 == null ? null : ModelFileIntegrity.parseExpectedSha256(expectedSha256);
        } catch (IllegalArgumentException exception) {
            call.reject(exception.getMessage(), exception);
            return;
        }

        final String finalUrl = url;
        final String finalCompanionUrl = companionUrl;
        final String requestedFilename = filename;

        new Thread(() -> {
            try {
                java.io.File filesDir = getContext().getFilesDir();
                if (filesDir == null || !filesDir.isDirectory()) {
                    throw new java.io.IOException("Private files directory is unavailable");
                }

                String resolvedFilename = requestedFilename;
                if (resolvedFilename == null || resolvedFilename.isBlank()) {
                    java.net.URL parsedUrl = new java.net.URL(finalUrl);
                    String path = parsedUrl.getPath();
                    resolvedFilename = path.substring(path.lastIndexOf('/') + 1);
                }
                java.io.File modelFile = resolvePrivateDownloadFile(filesDir, resolvedFilename);

                downloadFile(finalUrl, modelFile, expectedDigest, (downloadedBytes, totalBytes, percentage) -> {
                    JSObject event = new JSObject();
                    event.put("progress", percentage);
                    event.put("downloadedBytes", downloadedBytes);
                    event.put("totalBytes", totalBytes);
                    notifyListeners("downloadProgress", event);
                });

                JSObject ret = new JSObject();
                ret.put("path", modelFile.getAbsolutePath());

                if (finalCompanionUrl != null && !finalCompanionUrl.isBlank()) {
                    java.net.URL companionParsedUrl = new java.net.URL(finalCompanionUrl);
                    String companionPath = companionParsedUrl.getPath();
                    String companionFilename = companionPath.substring(companionPath.lastIndexOf('/') + 1);
                    java.io.File companionFile = resolvePrivateDownloadFile(filesDir, companionFilename);

                    downloadFile(finalCompanionUrl, companionFile, null, (downloadedBytes, totalBytes, percentage) -> {
                        JSObject event = new JSObject();
                        event.put("progress", percentage);
                        event.put("downloadedBytes", downloadedBytes);
                        event.put("totalBytes", totalBytes);
                        notifyListeners("downloadProgress", event);
                    });
                    ret.put("companionPath", companionFile.getAbsolutePath());
                }

                call.resolve(ret);
            } catch (Exception e) {
                android.util.Log.e("CapgoLLM", "Model download failed", e);
                call.reject("Failed to download model", e);
            }
        }, "Agathia-Model-Download").start();
    }

    private java.io.File resolvePrivateDownloadFile(java.io.File filesDir, String requestedName) throws java.io.IOException {
        if (requestedName == null) {
            throw new java.io.IOException("Download filename is missing");
        }

        String safeName = requestedName.replace('\\', '/');
        safeName = safeName.substring(safeName.lastIndexOf('/') + 1);
        safeName = safeName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safeName.isBlank() || ".".equals(safeName) || "..".equals(safeName)) {
            throw new java.io.IOException("Download filename is invalid");
        }

        java.io.File base = filesDir.getCanonicalFile();
        java.io.File destination = new java.io.File(base, safeName).getCanonicalFile();
        if (!base.equals(destination.getParentFile())) {
            throw new java.io.IOException("Download destination is outside private storage");
        }
        return destination;
    }

    private void downloadFile(String urlString, java.io.File outputFile, byte[] expectedDigest, ProgressCallback callback) throws Exception {
        java.net.URL url = new java.net.URL(urlString);
        String protocol = url.getProtocol();
        if (!"https".equalsIgnoreCase(protocol) && !"http".equalsIgnoreCase(protocol)) {
            throw new java.io.IOException("Only HTTP and HTTPS download URLs are supported");
        }

        java.net.HttpURLConnection connection = null;
        java.io.File temporaryFile = null;
        try {
            int statusCode;
            int redirectCount = 0;
            while (true) {
                connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(20_000);
                connection.setReadTimeout(90_000);
                connection.setInstanceFollowRedirects(false);
                connection.setUseCaches(false);
                connection.setRequestMethod("GET");
                connection.connect();
                statusCode = connection.getResponseCode();

                boolean redirect = statusCode == java.net.HttpURLConnection.HTTP_MOVED_PERM
                    || statusCode == java.net.HttpURLConnection.HTTP_MOVED_TEMP
                    || statusCode == java.net.HttpURLConnection.HTTP_SEE_OTHER
                    || statusCode == 307 || statusCode == 308;
                if (!redirect) break;

                String location = connection.getHeaderField("Location");
                connection.disconnect();
                connection = null;
                if (location == null || location.trim().isEmpty()) {
                    throw new java.io.IOException("Model server returned a redirect without a Location header");
                }
                if (++redirectCount > 5) {
                    throw new java.io.IOException("Model download exceeded the maximum of 5 redirects");
                }

                java.net.URL redirectedUrl = new java.net.URL(url, location);
                String redirectedProtocol = redirectedUrl.getProtocol();
                if (!"https".equalsIgnoreCase(redirectedProtocol) && !"http".equalsIgnoreCase(redirectedProtocol)) {
                    throw new java.io.IOException("Model download redirect used an unsupported protocol");
                }
                if ("https".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(redirectedProtocol)) {
                    throw new java.io.IOException("Refusing an insecure HTTPS-to-HTTP download redirect");
                }
                url = redirectedUrl;
            }

            if (statusCode < 200 || statusCode >= 300) {
                java.io.InputStream errorStream = connection.getErrorStream();
                if (errorStream != null) {
                    try {
                        errorStream.close();
                    } catch (java.io.IOException ignored) {
                        android.util.Log.w("CapgoLLM", "Could not close HTTP error stream", ignored);
                    }
                }
                throw new java.io.IOException("Model server returned HTTP " + statusCode);
            }

            long expectedLength = connection.getContentLengthLong();
            java.io.File parent = outputFile.getParentFile();
            if (parent == null || !parent.isDirectory()) {
                throw new java.io.IOException("Download destination directory is unavailable");
            }
            temporaryFile = java.io.File.createTempFile("agathia-" + outputFile.getName() + ".", ".part", parent);

            long totalBytes = 0L;
            java.security.MessageDigest digest = expectedDigest == null ? null : ModelFileIntegrity.newSha256Digest();
            byte[] buffer = new byte[64 * 1024];
            try (
                java.io.InputStream input = new java.io.BufferedInputStream(connection.getInputStream());
                java.io.FileOutputStream output = new java.io.FileOutputStream(temporaryFile)
            ) {
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    if (digest != null) digest.update(buffer, 0, count);
                    totalBytes += (long) count;
                    if (callback != null) {
                        int percentage = expectedLength > 0L
                            ? (int) Math.min(100L, (long) ((double) totalBytes * 100.0d / (double) expectedLength))
                            : -1;
                        callback.onProgress(totalBytes, expectedLength, percentage);
                    }
                }
                output.flush();
                output.getFD().sync();
            }

            if (expectedLength >= 0L && totalBytes != expectedLength) {
                throw new java.io.IOException(
                    "Incomplete download: received " + totalBytes + " of " + expectedLength + " bytes"
                );
            }

            if (digest != null && !ModelFileIntegrity.matches(digest.digest(), expectedDigest)) {
                throw new java.io.IOException("MODEL_CHECKSUM_MISMATCH: downloaded model SHA-256 does not match the pinned artifact");
            }

            // Same-directory rename is atomic on Android/Linux and preserves the previous file on failure.
            android.system.Os.rename(temporaryFile.getAbsolutePath(), outputFile.getAbsolutePath());
            temporaryFile = null;
        } finally {
            if (temporaryFile != null && temporaryFile.exists() && !temporaryFile.delete()) {
                android.util.Log.w("CapgoLLM", "Could not remove partial download: " + temporaryFile.getAbsolutePath());
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    interface ProgressCallback {
        void onProgress(long downloadedBytes, long totalBytes, int percentage);
    }

    @PluginMethod
    public void getPluginVersion(final PluginCall call) {
        try {
            final JSObject ret = new JSObject();
            ret.put("version", this.pluginVersion);
            call.resolve(ret);
        } catch (final Exception e) {
            call.reject("Could not get plugin version", e);
        }
    }
}
