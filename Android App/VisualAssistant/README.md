# Visual Assistant — On-Device Vision-Language-Speech System (Android)

**Visual Assistant** is a real-time, on-device assistive vision application built for Android. It combines camera frame acquisition (via a remote Raspberry Pi Zero camera), perceptual scene filtering, on-device multimodal AI inference (**FastVLM-0.5B via LiteRT-LM**), semantic redundancy filtering, real-time Text-To-Speech (TTS) with word-level range highlighting, and automated gallery saving.

---

## Key Features

- **On-Device Multimodal AI**: Runs `FastVLM-0.5B.litertlm` locally on the device GPU via `com.google.ai.edge.litertlm:litertlm-android:0.17.1`. Includes `play-services-tflite-gpu` integration for optimal compatibility on both Mali (Google Tensor) and Adreno (Snapdragon) GPUs.
- **Zero Cold-Start Latency**: Engine warm-up on startup pre-compiles GPU delegate shaders into a persistent local cache (`filesDir/litertlm_cache`), ensuring rapid inference starts on subsequent runs.
- **Remote Camera Architecture**: Receives image frames and initiates captures via a networked Raspberry Pi Zero, ensuring lightweight headset design while doing the heavy AI lifting on the phone. Auto-discovers the Pi via Network Service Discovery (NSD) `_http._tcp.`.
- **Motion-Gated Frame Filtering**: 64-bit dHash perceptual image similarity filter compares raw camera frames to eliminate redundant model calls when the scene is static.
- **Semantic Caption Redundancy Filtering**: Post-inference NLP filter strips English stop words and VLM meta-fillers ("image depicts", "shows", etc.) and discards captions with high word overlap.
- **Asynchronous Pipelined Inference**: Uses a 3-stage worker architecture (Watcher -> Thinker -> Speaker) via Kotlin Coroutines and Channels to eliminate idle periods and keep the AI thinking while TTS plays audio.
- **Advanced TTS Customization**: Supports discovering installed TTS engines, filtering local offline English voices, adjusting Speech Rate (0.5x – 2.5x) and Pitch (0.5x – 2.0x), and live word-range highlighting.
- **Comprehensive Analytics & Stats**: Tracks per-stage latency breakdown (Preprocessing, Encoder TTFT, Decoder, Full Pipeline), network fetch time, payload sizes, and peak memory (PSS / JVM Heap).

---

## System Pipeline Architecture

The application uses an asynchronous, overlapping pipeline to maximize GPU and Audio hardware utilization.

1. **The Watcher (Stage 1)**: `CameraController` constantly polls the Raspberry Pi's `/latest_hash_frame` endpoint. `FrameSimilarityFilter` calculates a 64-bit dHash. If motion is detected (and Stage 2 is idle), it fetches a high-res WebP image from the Pi's `/capture` endpoint and pushes it to the `inferenceTaskChannel`.
2. **The Thinker (Stage 2)**: The Inference Worker pulls from the channel. `FastVlmImagePreprocessor` ensures the image is exactly 1024x1024. The GPU-accelerated `FastVlmEngine` generates a caption. `CaptionRedundancyFilter` prevents repetitive captions. The result is pushed to the `speechTaskChannel`.
3. **The Speaker (Stage 3)**: The Speech Worker pulls from the channel. It plays audio via `CaptionSpeaker` and handles word-by-word UI highlighting.
4. **Analytics & Logging**: `SessionStatsCollector` aggregates latency metrics and network payloads for review on the Stats screen.

---

## Setup & Deployment

### Prerequisites
- Android Studio Ladybug (2024.2.1+) or newer.
- Java Development Kit (JDK) 11+.
- Arm64-v8a Android device running API 26+ (Google Pixel 8, Xiaomi Redmi 12 5G, or similar).
- Raspberry Pi Zero W (or newer) running the bundled Python Flask server on port 5000.

### 1. Build and Install the Android App

1. **Clone the Repository:**
   ```bash
   git clone <your-repo-url>
   cd VisualAssistant
   ```
2. **Open in Android Studio:**
   Open the `VisualAssistant` folder in Android Studio and let Gradle sync.
3. **Build the Project:**
   In Android Studio, click **Build > Make Project**, or use the command line:
   ```bash
   ./gradlew assembleDebug
   ```
4. **Install on Device:**
   Connect your Android device via USB or Wi-Fi Debugging and click **Run > Run 'app'** in Android Studio, or use ADB:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

### 2. First Launch & Model Sideloading
On first launch, the app automatically downloads `FastVLM-0.5B.litertlm` (~1.1 GB) from [litert-community/FastVLM-0.5B](https://huggingface.co/litert-community/FastVLM-0.5B) to the device's internal storage.

### 3. Raspberry Pi Configuration
The Android app expects to communicate with a remote camera server.
1. Deploy the Python Flask server to your Raspberry Pi.
2. Ensure the Pi advertises itself via Multicast DNS (mDNS / NSD) as `cameraGlasses.local` on the `_http._tcp.` protocol.
3. Provide the following endpoints:
    * `/latest_hash_frame`: Returns a low-res image or raw bytes for hashing.
    * `/capture`: Returns a high-res (e.g., 1024x1024) WebP compressed image for inference.
    * `/status`: Responds with HTTP 200 OK for heartbeat checks.

---

## Demo Configuration Settings

The **Home Screen** includes configurable settings persisted via `SharedPreferences`:

- **Motion Sensitivity (0 – 64)**: Hamming distance threshold for frame hashing. Higher values require more visual change before triggering AI inference.
- **Redundancy Limit (1 – 10 words)**: Number of overlapping descriptive words allowed between consecutive captions before discarding the new caption.
- **Pi Hostname**: Default is `cameraGlasses.local`. Can be manually overridden if auto-discovery fails.
- **Speech Rate & Pitch**: Controls TTS voice characteristics.
- **Offline Voice Selection**: Dropdown menu allowing choice among installed local English voices.

---

## Debugging & Logcat

Filter logs in Android Studio or via ADB:
```bash
adb logcat -s FastVlmLiteRt SessionViewModel CaptionFilter CameraController NsdHelper
```
