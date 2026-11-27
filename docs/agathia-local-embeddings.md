# Agathia local embeddings (Android)

The Android plugin exposes a separate LiteRT-LM embedding engine. It does not replace, reconfigure, or share resources with the chat model.

## Model

Use the text-only EmbeddingGemma 2 270M LiteRT-LM bundle from the official
`litert-community/embeddinggemma-2-text-270m-litert-lm` Hugging Face repository.
The bundle is approximately 165 MB and includes its tokenizer. Pin a reviewed
repository revision and the artifact SHA-256 in the consuming app; do not use a
floating `main` artifact in production.

LiteRT-LM 0.17.0 provides the Android `EmbeddingEngine` API used by this plugin.

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

The loader requires the model to reside inside the application's private files
directory. When a digest is supplied it is rechecked immediately before the
native engine initializes. Loading and inference run on a dedicated serial
background executor using the CPU backend (two threads). The embedding engine
has its own lifecycle and can be closed without affecting chat:

```ts
await CapgoLLM.closeEmbeddingModel();
```

## Retrieval embeddings

`embedTexts` accepts batches of 1–16 strings, with a maximum of 8,000
characters per input. The API applies EmbeddingGemma 2's asymmetric retrieval
prefixes itself:

- `taskType: 'query'`: `task: search result | query: ...`
- `taskType: 'document'`: `title: ... | text: ...` (one title per text)

```ts
const query = await CapgoLLM.embedTexts({
  taskType: 'query',
  texts: ['¿Qué acordamos sobre el presupuesto?'],
  outputSize: 512,
});

const passages = await CapgoLLM.embedTexts({
  taskType: 'document',
  texts: ['El presupuesto aprobado para el proyecto es ...'],
  titles: ['Acta de reunión.pdf · página 3'],
  outputSize: 512,
});
```

Supported output dimensions are 128, 256, 512 and 768. The default is 512.
Vectors are checked for finite values and normalized after truncation. Keep the
same dimension and model revision for every vector in one index. Rebuild the
index when the model revision or embedding profile changes.

The native API does not persist vectors: the consuming application owns
document/page/chunk metadata and vector persistence. The web adapter rejects
these calls explicitly; it never silently routes private text to a cloud model.

## Current limitations

- Android native runtime only; web and iOS are not implemented.
- No automatic model download or activation is performed by this plugin API.
- Device performance, thermal behavior, memory use and retrieval quality still
  require testing on the target Android device.
