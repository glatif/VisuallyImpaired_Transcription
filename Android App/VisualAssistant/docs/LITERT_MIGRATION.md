# LiteRT-LM migration notes (FastVLM-0.5B)

## Why LiteRT-LM

MNN path had a bad end-state after one multimodal response (custom JNI).  
llama.cpp / `gguf-org/fastvlm-gguf` was ruled out (pig arch / FastViT unsupported).  
Official Google conversion: `litert-community/FastVLM-0.5B` → `FastVLM-0.5B.litertlm`.

## Task 0 — Version pin

- Gradle: `com.google.ai.edge.litertlm:litertlm-android:**0.15.0**` (exact).
- **Why not 0.8.0:** Current HF `FastVLM-0.5B.litertlm` declares `LlmModelType::kFastVlm`.
  Conversation’s data-processor factory only handles FastVLM from **0.11.0+**. On 0.8.0,
  `Engine.createConversation()` fails immediately with
  `INVALID_ARGUMENT: Unsupported model type` (engine init still succeeds).
- **#1829 note:** Older reports of garbage/`<start_of_turn>` loops on 0.9–0.11 often
  matched wrong chat templates in the model file. Our packaged model logs the
  FastVLM Jinja (`<|im_start|>` + `<image_soft_token>`). Re-check caption quality
  after upgrade; do not use `+` / `latest.release`.
- API (0.15): `Backend.GPU()`, `Engine.setNativeMinLogSeverity`, `Message.user(Contents.of(...))`.

## Task 0b — Image resolution

- **Model target: 1024×1024** (`FastVlmDataProcessorConfig`, Apple `preprocessor_config.json`).
- App: `FastVlmImagePreprocessor` pads-to-square + scales before JPEG (avoids 12MP decode in LiteRT).
- Do **not** go below 1024 — that is not the trained operating point.

## GPU Top-K sampler (decode)

Log `OpenCL sampler not available... library not found` is expected if `libLiteRtTopKOpenClSampler.so`
is not in the APK. Runtime then tries **static** OpenCL sampler inside `liblitertlm_jni.so` — look for
`Statically linked LiteRtTopKOpenClSampler C API` vs `GPU sampler unavailable. Falling back to CPU`.

Optional: `scripts/fetch_gpu_sampler.ps1` (patchelf + jniLibs). See [LiteRT-LM#2211](https://github.com/google-ai-edge/LiteRT-LM/issues/2211).

## Backend (Pixel 8 / Tensor G3)

- File: `FastVLM-0.5B.litertlm` (GPU/CPU package) — **not** `.qualcomm.sm*.litertlm`.
- `EngineConfig(backend = Backend.GPU(), visionBackend = Backend.GPU())`.
- Manifest declares `libOpenCL.so` + `libvndksupport.so` (`required=false`).
- On load, log tag `FastVlmLiteRt` prints `GPU_DELEGATE_CHECK`. Inspect logcat for
  LiteRT OpenCL/GPU init; silent CPU fallback would look like “no speedup”.

## Per-frame Conversation reset

`Engine` lives for the session; each frame calls `createConversation()` and closes it.
Avoids KV growth and cross-frame contamination (MNN-era failure category).

## Empty / error handling

No silent Engine reload. Empty stream or exceptions → log + failed-frame caption string.

## Latency logging

Always-on `Log.d("FastVlmLiteRt", "ttft=…ms decode=…ms total=…ms tokens=…")`
where TTFT ≈ vision encode + prefill, decode = after first token.

## Overlap pipeline

After frame N caption is ready → TTS starts **and** capture+infer for N+1 begins
immediately (single Engine, serialized inference). Channel capacity 1 holds the next
caption until TTS finishes.

## MNN end-state bug

Structurally avoided: no custom MNN JNI session; LiteRT `Conversation` discarded each
frame; Engine not left in a finished-step status across independent image turns.
