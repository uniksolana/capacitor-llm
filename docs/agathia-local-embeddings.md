# Agathia local embeddings (Android)

The Android plugin exposes a separate LiteRT-LM embedding engine. It does not replace, reconfigure, or share resources with the chat model.

## Runtime

The implementation uses `com.google.ai.edge.litertlm:litertlm-android:0.18.0`, which provides the Kotlin `EmbeddingEngine` API. The plugin buildscript pins Kotlin Gradle Plugin 2.4.0 for compatibility with the current LiteRT-LM dependency line.

## Model

The intended candidate is the text-only EmbeddingGemma 2 270M LiteRT-LM bundle from the official `litert-community/embeddinggemma-2-text-270m-litert-lm` Hugging Face repository. Before enabling downloads, pin an immutable repository revision and independently confirm the exact artifact SHA-256. Do not use a floating `main` artifact or invent a checksum.

## Lifecycle

Download the model with the existing private downloader, then load it:

```ts
const downloaded = await CapgoLLM.downloadModel({
  url: EMBEDDING_MODEL_URL,
  filename: 'embeddinggemma-2-text-270m.litertlm',
  expectedSha256: EMBEDDING_MODEL_SHA256,
});

await CapgoLLM.loadEmbeddingModel({
  path: downloaded.path,
  expectedSha256: EMBEDDING_MODEL_SHA256,
  maxInputLength: 512,
});
```

The SHA-256 is mandatory and rechecked immediately before native initialization. The loader requires a readable `.litertlm` file inside the application's private files directory. Loading and inference run on a dedicated serial background executor using CPU (two threads). The embedding engine has an independent lifecycle and can be closed without affecting chat.

## Retrieval embeddings

`embedTexts` accepts batches of 1–16 strings, with a maximum of 8,000 characters per input. The API applies EmbeddingGemma retrieval prefixes:

- `taskType: 'query'`: `task: search result | query: ...`
- `taskType: 'document'`: `title: ... | text: ...` (one title per chunk)

Output dimensions are 128, 256, 512 or 768; default 512. Vectors are checked for finite values and normalized. Keep one dimension and model revision per index. Rebuild when either changes.

The native API does not persist vectors: the consuming application owns document/page/chunk metadata and vector persistence. The web adapter rejects these calls explicitly; it never silently routes private text to a cloud model.

## Current limitations

- Android native runtime only; web and iOS are not implemented.
- No automatic model download or activation is performed by this plugin API.
- Exact model revision and SHA-256 still need verification.
- Device performance, thermal behavior, memory use and retrieval quality require testing on the target Android device.
