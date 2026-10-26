# Tide

A quiet aerial coastline: petrol-blue water, sea-green shallows and a pale curve of sand. The wallpaper carries the color; a translucent dock and compact controls keep the app artwork clear. The clock and date retain the launcher's Outfit typography, with the greeting removed to reduce competing text.

Focus keeps a small scale lift and a light white halo. `focus_overshoot: false` uses the existing critically damped highlight spring and decelerating tile zoom, so focus glides into place without swinging past its target. Animations stay on; the faster, nearly immediate `reduced_motion` mode is not needed for this look. App-colored ambient glows are off so moving between apps does not recolor the sea.

This is a theme only. It preserves the launcher's installed dock apps and their order, uses five columns, and requires no APK rebuild, new permissions, network access, blur or video. The static 1920×1080 JPEG is under 300 KB. True-color wallpaper decoding avoids color banding on the tested Philips TA5; it uses about 8 MB for the decoded image and suits TVs with at least 2 GB RAM.

Apply from the repository root:

```sh
TV_HOST=<tv ip> tools/theme.sh themes/tide
```

Undo the theme and return to the launcher's built-in configuration:

```sh
TV_HOST=<tv ip> tools/theme.sh --reset
```

`wallpaper_dim` controls the shade over the photograph. `glass_style` can be `halo`, `rim` or `glass`; `focus_overshoot: false` is the setting responsible for the calmer focus movement. Changing this theme does not change the TV's system animation scales.

Artwork provenance and the exact generation prompt are in [CREDITS.md](CREDITS.md) and [PROMPT.txt](PROMPT.txt). `screenshot.jpg` is captured from the real Philips Google TV TA5 after applying the theme.

Verified on Philips Google TV TA5 (PH1M_WW_9972), Android 14, at its 1920×1080 UI resolution. The TV reports about 2.8 GB RAM. The dock, five-column grid, top-bar navigation and Inputs dropdown were checked on the device. The owner confirmed that the smoother focus glide feels right. White controls over the refined wallpaper sampled about 9.4–10.3:1 contrast at the tested background points; this is a spot check rather than a complete accessibility audit.

Repository validation: 1,837 Python checks completed successfully, with 23 platform-specific checks skipped. The theme uses only existing configuration keys; the launcher APK was not changed for Tide.
