# Task 0 — FastVLM GGUF packaging check (BLOCKER)

Date: 2026-08-04  
File probed: `gguf-org/fastvlm-gguf` → `fastvlm-0.5b-q8_0.gguf` (~1.18 GB)  
Method: HTTP Range download of first 32 MB + custom GGUF header parser  
(`build/gguf_probe/probe_gguf_header.py`)

## Findings

### 1. Architecture is proprietary `pig`, not llama.cpp-native

| Key | Value |
|-----|-------|
| `general.architecture` | **`pig`** |
| `general.quantization_version` | `2` |
| `general.file_type` | `7` (Q8_0) |
| KV count | only **3** keys (no clip/vision metadata) |

Mainline llama.cpp `mtmd`/`clip.cpp` expects either:
- a text GGUF with a known arch (`qwen2`, `llama`, …) **plus** a separate `mmproj-*.gguf`, or
- (rarely) properly namespaced `clip.*` tensors with `clip.has_vision_encoder` etc.

`pig` is **not** a recognized llama.cpp architecture.

### 2. Vision tensors are embedded, but with HuggingFace-style names

- **924** tensors total
- Prefixes: `model` (923), `lm_head` (1)
- **633** vision-related tensors under names like:
  - `model.vision_tower.vision_tower.model.network.*` (FastViT)
  - `model.mm_projector.0.bias`, `model.mm_projector.2.bias`
- **No** `clip.*` / `mmproj.*` / `v.*` tensor-key prefixes that llama.cpp’s CLIP loader looks for

So: vision weights *are* in the single file, but **not** in the packaging mainline llama.cpp understands.

### 3. `gguf-connector` does **not** run these via llama.cpp

Inspected installed `gguf-connector` **3.6.8**:

- `f6.py` (the FastVLM connector advertised on the HF README):
  1. Dequantizes GGUF → safetensors (`quant3.convert_gguf_to_safetensors`)
  2. Launches a **PyTorch / transformers** FastVLM app (`f4.launch_fastvlm_app`)
  3. Uses HF cache model `callgg/fastvlm-0.5b-bf16` for the processor / architecture glue
- That path needs **torch**, not `llama-cpp-python` / `libmtmd`
- Other modes (`ggc cpp` etc.) do use llama.cpp, but **not** for these FastVLM `pig` files

Conclusion from the prompt’s pre-research (“these GGUFs are built for llama.cpp’s vision path”) is **incorrect for this repo**. The “pig” tag is not just a catalog label — it marks a custom single-file HF-weight dump for gguf-connector’s PyTorch FastVLM path.

### 4. Fallback “split out mmproj with llama.cpp tooling” also fails

llama.cpp’s supported multimodal arches (Gemma 3, SmolVLM, Qwen2-VL, InternVL, LLaVA, …) do **not** include Apple FastVLM / FastViT.  
You cannot produce a working `mmproj` for FastViT with current `convert_hf_to_gguf.py --mmproj` or tensor-splitting tools without **adding FastViT support to llama.cpp** (out of prototype scope).

### 5. llama.cpp version note

N/A for loading this file. There is no pinned `llama-cpp-python` version in gguf-connector that makes `pig` FastVLM work under mainline `mtmd` — because that code path never goes through llama.cpp.

## Implications for Tasks 1–4

**Do not proceed** with downloading `gguf-org/fastvlm-gguf` into a llama.cpp Android JNI bridge — load will fail (unknown arch / missing CLIP metadata).

## Recommended ways forward (pick one)

| Option | What changes | Pros | Cons |
|--------|----------------|------|------|
| **A. Swap model to a llama.cpp-supported VLM** (e.g. `ggml-org/SmolVLM-256M-Instruct-GGUF` or `SmolVLM-500M`) | Same Android UX + llama.cpp JNI; download `model.gguf` + `mmproj-*.gguf` | Achieves Tasks 1–4 as written; reliable mtmd path | Not FastVLM; different captions/quality |
| **B. Keep FastVLM, fix MNN** | Stay on MNN; port Alibaba’s status-reset / multimodal stepping from `MnnLlmChat` | Keeps FastVLM-0.5B | Doesn’t deliver llama.cpp migration |
| **C. Keep FastVLM via ExecuTorch / custom FastViT in llama.cpp** | Large native work | True FastVLM on-device | Weeks of work; not prototype-scope |

**Recommendation for this prototype:** **Option A** (SmolVLM via llama.cpp) if the goal is “reliable on-device VLM + overlap TTS + timing logs”; **Option B** if the goal is specifically “FastVLM-0.5B captions.”
