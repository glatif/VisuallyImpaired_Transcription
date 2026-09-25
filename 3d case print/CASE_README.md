# Smart Glasses Case — 3D Print Files

`glasses_case.scad` is a parametric OpenSCAD model of the case that holds the ESP32-S3 board, battery and switch, and clips onto the glasses' temple arm behind the hinge.

## Opening It

1. Install [OpenSCAD](https://openscad.org/) (free).
2. Open `glasses_case.scad`.
3. Press **F5** for a quick preview, or **F6** for a full render.
4. Press **F7** to export as STL once you're happy with it.

## Before You Print

All the adjustable numbers are at the top of the file, grouped by part (board, camera, battery, switch, arm clip). Several are still placeholders — check the comment next to each value marked "MEASURE" and update it to match your actual parts:

- Camera lens diameter and ribbon width
- Switch size
- USB-C port footprint and margin
- Glasses arm thickness/height where the clip grips

Change a value, re-render (F6), and check the fit in the preview before exporting.

## Layout

- Board and battery sit **side by side** (not front-to-back), so the case is a bit wider but much shorter.
- The board is **back-aligned**, so its vertical USB-C port opens through the top/back of the case.
- The camera sits in a pod at the front; the flexible ribbon cable bridges the gap from the board to the pod.
- Two snap clips underneath grip the arm; adjust `clip_y0`/`clip_len`/`lip` if the fit is loose or tight.

## Printing

- `part = "both"` (default) generates the base and the lid together.
- Print the base as modeled (flat side down) and the lid as modeled (flat).
- PETG is recommended for the arm clip — it flexes without snapping. PLA works for the rest.
- Leave the default 0.4 mm clearance as-is unless your printer runs tight or loose.
