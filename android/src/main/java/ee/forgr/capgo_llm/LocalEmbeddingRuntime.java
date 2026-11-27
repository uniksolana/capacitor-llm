package ee.forgr.capgo_llm;

import android.content.Context;
import android.util.Log;
import com.google.ai.edge.litertlm.Backend;
import com.google.ai.edge.litertlm.EmbeddingEngine;
import com.google.ai.edge.litertlm.EmbeddingEngineConfig;
import com.google.ai.edge.litertlm.EmbeddingOptions;
import com.google.ai.edge.litertlm.EmbeddingResponse;
import com.google.ai.edge.litertlm.InputData;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Owns an independent on-device embedding engine. It never shares or replaces
 * the chat engine, and all model work is serialized away from the UI thread.
 */
final class LocalEmbeddingRuntime {
    interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception error);
    }

    private static final String TAG = "AgathiaEmbeddings";
    private static final int MAX_BATCH_SIZE = 16;
    private static final int MAX_TEXT_CHARS = 8000;
    private static final int DEFAULT_OUTPUT_SIZE = 512;
    private static final int MAX_OUTPUT_SIZE = 768;

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile EmbeddingEngine engine;

    LocalEmbeddingRuntime(Context context) {
        this.context = context.getApplicationContext();
    }

    void load(String modelPath, String expectedSha256, int maxInputLength, Callback<Void> callback) {
        executor.execute(() -> {
            EmbeddingEngine candidate = null;
            try {
                if (modelPath == null || modelPath.isBlank()) {
                    throw new IllegalArgumentException("La ruta del modelo de embeddings es obligatoria.");
                }
                if (maxInputLength != 128 && maxInputLength != 256 && maxInputLength != 512 && maxInputLength != 1024 && maxInputLength != 2048) {
                    throw new IllegalArgumentException("maxInputLength debe ser uno de los tamaños admitidos por EmbeddingGemma 2.");
                }
                File model = new File(modelPath).getCanonicalFile();
                File privateRoot = context.getFilesDir().getCanonicalFile();
                String privatePrefix = privateRoot.getPath() + File.separator;
                if (!model.getPath().startsWith(privatePrefix) || !model.isFile() || !model.canRead() || model.length() <= 0L) {
                    throw new IllegalArgumentException("El modelo debe ser un archivo legible dentro del almacenamiento privado de la aplicación.");
                }
                if (expectedSha256 != null) {
                    ModelFileIntegrity.verify(model, expectedSha256);
                }

                EmbeddingEngineConfig config = new EmbeddingEngineConfig(
                    model.getAbsolutePath(),
                    new Backend.CPU(2, null),
                    null,
                    null,
                    context.getCacheDir().getAbsolutePath(),
                    null,
                    maxInputLength,
                    null,
                    null
                );
                candidate = new EmbeddingEngine(config);
                candidate.initialize();

                EmbeddingEngine previous = engine;
                if (previous != null && previous.isInitialized()) {
                    try {
                        previous.close();
                    } catch (Exception closeError) {
                        throw new IllegalStateException("No se pudo sustituir de forma segura el modelo de embeddings anterior.", closeError);
                    }
                }
                engine = candidate;
                candidate = null;
                callback.onSuccess(null);
            } catch (Exception error) {
                closeQuietly(candidate);
                callback.onError(error);
            }
        });
    }

    void embedTexts(List<String> texts, List<String> titles, String taskType, int outputSize, Callback<List<float[]>> callback) {
        executor.execute(() -> {
            try {
                EmbeddingEngine active = engine;
                if (active == null || !active.isInitialized()) {
                    throw new IllegalStateException("El modelo de embeddings no está cargado.");
                }
                if (texts == null || texts.isEmpty() || texts.size() > MAX_BATCH_SIZE) {
                    throw new IllegalArgumentException("El lote debe contener entre 1 y " + MAX_BATCH_SIZE + " textos.");
                }
                if (outputSize != 128 && outputSize != 256 && outputSize != 512 && outputSize != 768) {
                    throw new IllegalArgumentException("outputSize debe ser 128, 256, 512 o 768.");
                }
                if (!"query".equals(taskType) && !"document".equals(taskType)) {
                    throw new IllegalArgumentException("taskType debe ser query o document.");
                }
                if ("document".equals(taskType) && (titles == null || titles.size() != texts.size())) {
                    throw new IllegalArgumentException("Se necesita un título por cada fragmento documental.");
                }

                List<List<InputData>> batch = new ArrayList<>(texts.size());
                for (String text : texts) {
                    if (text == null || text.isBlank() || text.length() > MAX_TEXT_CHARS) {
                        throw new IllegalArgumentException("Cada texto debe contener entre 1 y " + MAX_TEXT_CHARS + " caracteres.");
                    }
                    String prefixedText;
                    if ("query".equals(taskType)) {
                        prefixedText = "task: search result | query: " + text;
                    } else {
                        String title = titles.get(batch.size());
                        if (title == null || title.isBlank()) title = "none";
                        if (title.length() > 512) title = title.substring(0, 512);
                        prefixedText = "title: " + title + " | text: " + text;
                    }
                    batch.add(Collections.singletonList(new InputData.Text(prefixedText)));
                }

                List<EmbeddingResponse> responses = active.computeEmbeddingBatch(
                    batch,
                    new EmbeddingOptions(true, true, outputSize, null)
                );
                if (responses.size() != texts.size()) {
                    throw new IllegalStateException("El motor devolvió un número inesperado de vectores.");
                }

                List<float[]> vectors = new ArrayList<>(responses.size());
                for (EmbeddingResponse response : responses) {
                    float[] vector = response.getEmbedding();
                    if (vector == null || vector.length != outputSize) {
                        throw new IllegalStateException("El motor devolvió un vector con dimensiones no válidas.");
                    }
                    double squaredNorm = 0d;
                    for (float value : vector) {
                        if (Float.isNaN(value) || Float.isInfinite(value)) {
                            throw new IllegalStateException("El motor devolvió un vector con valores no finitos.");
                        }
                        squaredNorm += (double) value * (double) value;
                    }
                    double norm = Math.sqrt(squaredNorm);
                    if (!Double.isFinite(norm) || norm <= 1e-12d) {
                        throw new IllegalStateException("El motor devolvió un vector sin norma válida.");
                    }
                    float[] normalized = vector.clone();
                    for (int i = 0; i < normalized.length; i++) normalized[i] = (float) (normalized[i] / norm);
                    vectors.add(normalized);
                }
                callback.onSuccess(vectors);
            } catch (Exception error) {
                callback.onError(error);
            }
        });
    }

    void close(Callback<Void> callback) {
        executor.execute(() -> {
            EmbeddingEngine previous = engine;
            try {
                if (previous != null && previous.isInitialized()) previous.close();
                engine = null;
                callback.onSuccess(null);
            } catch (Exception error) {
                Log.e(TAG, "No se pudo liberar el motor de embeddings", error);
                callback.onError(error);
            }
        });
    }

    private void closeQuietly(EmbeddingEngine target) {
        if (target == null) return;
        try {
            if (target.isInitialized()) target.close();
        } catch (Exception error) {
            Log.w(TAG, "No se pudo liberar un motor de embeddings sustituido", error);
        }
    }
}
