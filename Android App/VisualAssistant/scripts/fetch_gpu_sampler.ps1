# Optional: bundle GPU Top-K sampler for faster decode (LiteRT-LM#2211).
#
# The Maven AAR ships only liblitertlm_jni.so. Runtime dlopen's
# libLiteRtTopKOpenClSampler.so; if missing, it tries a static OpenCL sampler
# baked into liblitertlm_jni (check logcat for "Statically linked LiteRtTopKOpenClSampler").
#
# To try the dynamic sampler (flutter_gemma-style fix):
#   1. Install patchelf (WSL: sudo apt install patchelf)
#   2. Run: .\scripts\fetch_gpu_sampler.ps1
#   3. Rebuild APK
#
# Patches DT_NEEDED to liblitertlm_jni.so. May fail on some devices (Tensor G2).

param(
    [string]$Tag = "v0.13.0",
    [string]$OutDir = "$PSScriptRoot\..\app\src\main\jniLibs\arm64-v8a"
)

$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$url = "https://github.com/google-ai-edge/LiteRT-LM/raw/$Tag/prebuilt/android_arm64/libLiteRtTopKOpenClSampler.so"
$dest = Join-Path $OutDir "libLiteRtTopKOpenClSampler.so"
Write-Host "Downloading $url ..."
Invoke-WebRequest -Uri $url -OutFile $dest -UseBasicParsing

$patchelf = Get-Command patchelf -ErrorAction SilentlyContinue
if ($patchelf) {
    & patchelf --add-needed liblitertlm_jni.so $dest
    Write-Host "patchelf OK -> $dest"
} else {
    $wsl = Get-Command wsl -ErrorAction SilentlyContinue
    if ($wsl) {
        $wslDest = wsl wslpath -a $dest
        wsl patchelf --add-needed liblitertlm_jni.so $wslDest
        Write-Host "patchelf via WSL OK -> $dest"
    } else {
        Write-Warning "patchelf not found. Copied unpatched .so — likely dlopen fail on device. Install patchelf or use WSL."
    }
}

Write-Host "Done. Rebuild the app."
