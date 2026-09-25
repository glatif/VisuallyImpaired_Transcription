# Visual Assistant: Smart Glasses for the Blind

Visual Assistant is an assistive wearable that describes the world out loud. A lightweight camera worn on a pair of glasses streams what the wearer is facing to their Android phone. The phone runs a vision-language model entirely on-device, and speaks a short description of the scene through text-to-speech. No cloud service is required.

## How It Works

The system is split into two parts that talk to each other over the local network.

```
   SMART GLASSES
   Raspberry Pi camera
          |
          |  Wi-Fi: frames + capture commands
          v
   ANDROID PHONE
   1. Detect scene change
   2. Caption with FastVLM
   3. Drop repeated captions
   4. Speak the description
```

1. <b>The glasses (Raspberry Pi)</b> act as a small camera controller. They serve a low-resolution frame for cheap change detection and a high-resolution capture on demand, and advertise themselves on the network so the phone can find them automatically.
2. <b>The phone (Android app)</b> does the heavy lifting. It watches for scene changes, runs the FastVLM-0.5B vision-language model on the device GPU to caption the scene, filters out repetitive descriptions, and reads new ones aloud.

Keeping the AI on the phone means the headset stays light and low-power, and the wearer's camera feed never leaves their devices.

## Key Features

- <b>On-device multimodal AI:</b> FastVLM-0.5B runs locally via LiteRT-LM with GPU acceleration.
- <b>Lightweight headset:</b> the Raspberry Pi only captures and serves images.
- <b>Automatic discovery:</b> the phone finds the glasses on the network via mDNS/NSD.
- <b>Motion-gated inference:</b> a perceptual hash skips the model when the scene hasn't changed.
- <b>Semantic redundancy filtering:</b> near-duplicate captions are discarded so the wearer isn't told the same thing twice.
- <b>Pipelined processing:</b> capture, inference and speech overlap, so the AI keeps thinking while audio plays.
- <b>Customizable speech:</b> choose an offline voice, and adjust speech rate and pitch.
- <b>Built-in analytics:</b> per-stage latency, network payload and memory statistics.

## Repository Structure

| Folder | Purpose |
| --- | --- |
| [`<Programming_raspberry_pi_zero_w>/`](./<Programming_raspberry_pi_zero_w>) | Code and setup instructions for the Raspberry Pi camera controller |
| [`VisualAssistant/Android App`](./VisualAssistant/Android App) | Android app |

Each folder has its own README with complete setup and deployment instructions. Start there for the details of each part.

## Getting Started

1. <b>Set up the glasses.</b> Follow the README in the Raspberry Pi folder to configure the Pi and start its camera server. Once running, it advertises itself as `cameraGlasses.local`.
2. <b>Install the app.</b> Follow the README in the `VisualAssistant` folder to build the Android app and install it on your phone. On first launch it downloads the FastVLM model (about 1.1 GB), so connect to Wi-Fi first.
3. <b>Connect.</b> Put the phone and the Pi on the same network. The app discovers the glasses automatically. If it can't, enter the Pi's hostname manually in the app's settings.
4. <b>Listen.</b> Wear the glasses. As the scene changes, the phone speaks a description.

## Requirements

### Glasses
- Raspberry Pi Zero W (or newer) with a camera module

### Phone
- Arm64-v8a Android device running API 26 or higher (tested targets include Google Pixel 8 and Xiaomi Redmi 12 5G)
- Enough free storage for the ~1.1 GB model

### Network
- Both devices on the same local network

