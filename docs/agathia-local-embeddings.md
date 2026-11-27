# Agathia local embeddings (Android)

The Android plugin exposes a separate LiteRT-LM embedding engine. It does not replace, reconfigure, or share resources with the chat model.

## Runtime

The implementation uses `com.google.ai.edge.litertlm:litertlm-android:0.18.0`, which provides the Kotlin `EmbeddingEngine` API. The plugin buildscript pins Kotlin Gradle Plugin 2.4.0 for compatibility with the current LiteRT-LM dependency line.

## Model

The selected candidate is the text-only EmbeddingGemma 2 270M LiteRT-LM bundle from the official `litert-community/embeddinggemma-2-text-270m-litert-lm` Hugging Face repository. The model card describes support for 100+ languages, 270M parameters and a 165 MB CPU/GPU bundle. The artifact is pinned to the immutable repository commit that uploaded this file; its Git LFS pointer provides the exact size and SHA-256:

- Revision: `8c9f652b5233a80313d5c848f3656e492cf0397f`
- File: `embeddinggemma-2-text-270m.litertlm`
- Exact size: `164626432` bytes
- SHA-256: `2d079ee2f6f066b1f368e8d7c819f55214eaef1d0513b312321901f30ab286fb`
- Immutable download URL: `https://huggingface.co/litert-community/embeddinggemma-2-text-270m-litert-lm/resolve/8c9f652b5233a80313d5c848f3656e492cf0397f/embeddinggemma-2-text-270m.litertlm`

Do not replace this URL with a floating `main` URL without re-verifying the artifact and updating the pinned checksum.

## Lifecycle

On startup, first look for a complete private copy. If it is absent or has the wrong size, download the pinned artifact; the native loader always verifies SHA-256 before use:

```ts
const existing = await CapgoLLM.findExistingModel({
  filename: 'embeddinggemma-2-text-270m.litertlm',
  expectedSize: 164626432,
});

const modelPath = existing.exists
  ? existing.path!
  : (await CapgoLLM.downloadModel({
      url: EMBEDDING_MODEL_URL,
      filename: 'embeddinggemma-2-text-270m.litertlm',
      expectedSha256: EMBEDDING_MODEL_SHA256,
      maxBytes: 200000000,
    })).path;

await CapgoLLM.loadEmbeddingModel({
  path: modelPath,
  expectedSha256: EMBEDDING_MODEL_SHA256,
  maxInputLength: 512,
});
```

The lookup only checks that a regular readable file with the exact expected size exists in app-private storage. It is not an integrity check; the native loader verifies the SHA-256 before initializing the engine.

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
- The pinned artifact has not yet been downloaded or exercised on the target phone.
- Device performance, thermal behavior, memory use and retrieval quality require testing on the target Android device.
