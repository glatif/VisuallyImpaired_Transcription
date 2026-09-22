# Visual Assistant — Technical Architecture Guide

This document provides a deep-dive into the internal architecture, sequential pipeline execution, memory management, and data flow of **Visual Assistant**.

---

## 1. Concurrency Model & Sequential Pipeline

The core execution loop in `SessionViewModel.kt` uses a clean, sequential coroutine execution loop (`runSequentialLoop`) executing within `viewModelScope`.

### Pipeline Execution Diagram

```
┌─────────────────────────────────────────────────────────────┐
│ 1. Motion Gate (150ms dHash Polling)                        │
│    └─ Evaluate Y-Plane Hamming distance >= threshold        │
├─────────────────────────────────────────────────────────────┤
│ 2. Camera Capture & Preprocess                              │
│    └─ Capture frame -> Scale/Pad to 1024x1024 -> Recycle    │
├─────────────────────────────────────────────────────────────┤
│ 3. FastVLM GPU Inference                                    │
│    └─ LiteRT-LM Encoder TTFT + Decoder token generation     │
├─────────────────────────────────────────────────────────────┤
│ 4. Semantic Redundancy Filter                               │
│    └─ Tokenize -> Strip stop/filler words -> Overlap check  │
├─────────────────────────────────────────────────────────────┤
│ 5. UI Update & Async Gallery Saving                         │
│    └─ Set phase = SPEAKING, queue async image burn-in/save │
├─────────────────────────────────────────────────────────────┤
│ 6. Text-To-Speech Synthesis                                 │
│    └─ Speak caption via TTS -> Emit word highlight events   │
├─────────────────────────────────────────────────────────────┤
│ 7. Telemetry & Analytics                                    │
│    └─ Record frame metrics -> Set phase = WAITING_CAPTION   │
└──────────────────────────────┬──────────────────────────────┘
                               │ Loop to Step 1
                               ▼
```

- **Sequential Execution**: A single, clean coroutine loop handles the complete lifecycle per frame. This avoids channel deadlocks, race conditions, or stale frame backlogs.
- **Controlled Timing**: The camera captures and infers the next frame only after motion is detected and the previous caption has finished speaking, guaranteeing that every spoken description aligns with the live camera scene.

---

## 2. On-Device VLM Acceleration (`FastVlmEngine`)

### LiteRT-LM GPU Configuration
- **Model**: `FastVLM-0.5B.litertlm` (Apple FastVLM architecture translated to LiteRT-LM).
- **Backend**: `Backend.GPU()` for both main text decoder and vision encoder backends.
- **Persistent GPU Cache**: Shaders and delegate binaries are cached in `appContext.filesDir/litertlm_cache`. On cold boot, `warmUp()` triggers a dummy inference to compile GPU shaders upfront. On warm boot, shader compilation is skipped.
- **Conversation Scope**: To prevent Key-Value (KV) cache accumulation and cross-frame context bleed, a fresh `Conversation` instance is created per frame and closed upon completion.

---

## 3. Image & Memory Lifecycle Management

Large 12MP camera frames (4080x3072) can quickly exhaust JVM heap memory if not recycled carefully.

### Bitmap Allocation Strategy
1. **CameraX Capture**: `CameraController.takePictureBitmap()` retrieves the JPEG buffer from `ImageCapture` and decodes it into an in-memory ARGB_8888 bitmap.
2. **Immediate Resizing**: `FastVlmImagePreprocessor.preprocess()` pads the image to square and scales it to `1024x1024` (the FastVLM tensor dimension).
3. **Early Recycling**: The original 12MP `fullResBitmap` is recycled immediately after scaling.
4. **Gallery Copying**: `CaptionedImageSaver` makes a lightweight 1024x1024 copy for asynchronous disk burn-in and saving, after which the proxy bitmap is recycled.

---

## 4. Dual Filtering System

### A. Pre-Inference Motion Gate (`FrameSimilarityFilter`)
- Uses Difference Hashing (dHash) calculated across the 8-bit Y-Plane (luminance) buffer.
- Compares current 64-bit hash against `lastProcessedHash` using bitwise XOR and `Long.bitCount`.
- If Hamming distance $< \text{similarityThreshold}$, the camera frame is skipped, avoiding unnecessary GPU power consumption.

### B. Post-Inference Semantic Redundancy Filter (`CaptionRedundancyFilter`)
- Tokenizes generated caption into lowercase words.
- Filters out non-descriptive English stop words (`the`, `and`, `with`, `is`) and VLM meta-fillers (`image`, `shows`, `depicts`, `captures`, `view`).
- Computes intersection between the current word set and `lastMeaningfulWords`.
- If overlap $\ge \text{overlapThreshold}$, the caption is dropped, preventing repetitive speech.

---

## 5. State Management & Persistence

`SessionUiState` encapsulates all UI state properties in an immutable Kotlin `data class`.

### Persistent Settings
`SessionViewModel` uses `SharedPreferences` (`demo_settings`) to persist user choices across app restarts:
- `similarity_threshold`: Integer (0 – 64)
- `overlap_threshold`: Integer (1 – 10)
- `speech_rate`: Float (0.5f – 2.5f)
- `speech_pitch`: Float (0.5f – 2.0f)
- `selected_voice`: String (TTS Voice Name)
- `selected_engine`: String (TTS Package Name)
