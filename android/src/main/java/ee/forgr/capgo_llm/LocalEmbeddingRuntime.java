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
    private static final int MIN_OUTPUT_SIZE = 32;
    private static final int MAX_OUTPUT_SIZE = 4096;

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
                if (maxInputLength < 32 || maxInputLength > 4096) {
                    throw new IllegalArgumentException("maxInputLength debe estar entre 32 y 4096.");
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
                engine = candidate;
                candidate = null;
                closeQuietly(previous);
                callback.onSuccess(null);
            } catch (Exception error) {
                closeQuietly(candidate);
                callback.onError(error);
            }
        });
    }

    void embedTexts(List<String> texts, int outputSize, Callback<List<float[]>> callback) {
        executor.execute(() -> {
            try {
                EmbeddingEngine active = engine;
                if (active == null || !active.isInitialized()) {
                    throw new IllegalStateException("El modelo de embeddings no está cargado.");
                }
                if (texts == null || texts.isEmpty() || texts.size() > MAX_BATCH_SIZE) {
                    throw new IllegalArgumentException("El lote debe contener entre 1 y " + MAX_BATCH_SIZE + " textos.");
                }
                if (outputSize < MIN_OUTPUT_SIZE || outputSize > MAX_OUTPUT_SIZE) {
                    throw new IllegalArgumentException("outputSize debe estar entre 32 y 4096.");
                }

                List<List<InputData>> batch = new ArrayList<>(texts.size());
                for (String text : texts) {
                    if (text == null || text.isBlank() || text.length() > MAX_TEXT_CHARS) {
                        throw new IllegalArgumentException("Cada texto debe contener entre 1 y " + MAX_TEXT_CHARS + " caracteres.");
                    }
                    batch.add(Collections.singletonList(new InputData.Text(text)));
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
                    if (vector == null || vector.length < MIN_OUTPUT_SIZE || vector.length > MAX_OUTPUT_SIZE) {
                        throw new IllegalStateException("El motor devolvió un vector con dimensiones no válidas.");
                    }
                    for (float value : vector) {
                        if (Float.isNaN(value) || Float.isInfinite(value)) {
                            throw new IllegalStateException("El motor devolvió un vector con valores no finitos.");
                        }
                    }
                    vectors.add(vector);
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
            engine = null;
            try {
                if (previous != null && previous.isInitialized()) previous.close();
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
