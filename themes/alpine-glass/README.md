# Alpine Glass

A native home layout built for this personal fork, based on the supplied alpine lock-screen reference: a translucent light clock behind the mountain ridge, a centered date/weather line, a circular Settings shortcut, three frosted viewing cards, and twelve compact dock icons. The profile selector is omitted. It uses a generated dusk landscape; it is not a pixel copy of the photographed TV.

**This requires the Alpine launcher build on `theme/alpine-glass`.** A JSON theme alone cannot add the depth clock, content reader or new dock layout to the upstream launcher. The original UI remains in `ClassicHomeActivity`; the previously installed Tide APK is retained privately for rollback.

The existing Home component and no-filter Google Home redirect are retained. Keep Google TV enabled for its native Settings/app-button handlers, keep the Home helper bound, and retain `key_filter: false` plus `stock_home_redirect`. No system packages are newly disabled and no Home safety guard is changed.

## Data and behavior

- The cards read searchable Watch Next and preview-program rows with `android.permission.READ_TV_LISTINGS`. Each app appears at most once, with a small installed-app icon leading its provider name. Browser search/history rows are excluded. Where published entries are missing, recent launcher opens or installed-app suggestions are clearly labelled; no episode or progress is invented. Only available public rows can be shown, so the services/titles need not match the reference.
- Program posters are loaded over HTTPS or through their publishing content provider. If an image cannot be read, the app's installed artwork remains visible.
- Weather uses Google’s official condition artwork stored locally, with separate clear/partly-cloudy day and night icons and rain, snow and storm icons. Weather comes from [Open-Meteo](https://open-meteo.com/), cached for 30 minutes and refreshed only while Home is visible. Attribution appears in the Apps panel. The selected location lives in a private `alpine/private.json` under the launcher's external-files directory; coordinates are not included in the public theme.
- The landscape retains the original wallpaper’s saturation and warmth. Solar lighting moves smoothly through dawn, warm morning, daylight, golden hour, twilight and night using locally calculated sunrise and sunset at the private weather location. It updates once per minute while Home is visible and recalculates for date/time-zone changes; there are no added network requests. Without a configured location it uses the existing 07:00/18:30 defaults. The scene, mountain occlusion and frosted containers share the same lighting, with a single cached blur. Card and dock border opacity is reduced to 7% while keeping the original 1.25-pixel stroke width. The top Settings shortcut has no border. Focus uses scale only, with no moving outline. The clock uses its own tinted glass fill and a foreground clipping path matched to this particular mountain silhouette. No video, live wallpaper or per-frame blur is used.
- Native Android D-pad focus links move between dock, cards and top Settings in rows and hold at the horizontal ends; system navigation sound effects are enabled. The last dock focus is retained. At the tested 1920×1080 layout, twelve 110-pixel square slots have equal 146-pixel outer margins. The dock panel wraps the icon group with 16 dp of inner padding on each side. The twelve slots are ten installed apps, Settings and All apps. Installed brand icons are used; missing reference apps are not added.

## Local typefaces

The clock was reverted to the earlier light treatment at the user’s request. It loads the private variable SF Pro face with width 47, weight 270.325 and optical size 28; there is no additional horizontal text scaling. The private static Light face is the fallback. The clock tint is roughly 40% opaque, with a softly blurred scene showing through.

Optional typefaces are loaded from `alpine/font-clock.ttf` (or `alpine/font-clock.otf`), `alpine/font-text.otf` and `alpine/font-medium.otf` under the launcher's external-files directory. These are local files, not APK resources. Without them, Android's built-in sans-serif families are used. No Apple font files are included in this repository, the APK or GitHub; their published [licence](https://developer.apple.com/fonts/) still restricts non-Apple use even when private. No system font is replaced.

## Customize

`wallpaper.jpg` is the original 1920×1080 landscape, with sun-timed lighting applied at draw time. The palette and interpolation anchors are in `AlpineSolarLight.java`; sunrise and sunset come from the existing offline `Sun` calculation. The clock's depth clipping follows this image's ridge: changing the wallpaper also needs a matching clipping path in `AlpineArt.java`. The glass style and twelve-icon layout are native code. The app order is the priority list in `MainActivity.buildDock`, filtered to installed apps.

On an already configured TV running this build, apply the appearance with:

```sh
TV_HOST=<tv ip> tools/theme.sh themes/alpine-glass
```

The local session's `work/alpine/build-and-install.sh` and `work/alpine/restore-tide.sh` rebuild/update this configured TV or restore the saved Tide build. Those scripts, device backups, private fonts and weather settings stay local. The standard upstream handover installer still requires direct Home ownership; its guards must remain intact.

Artwork provenance and the exact generation prompt are in [CREDITS.md](CREDITS.md) and [PROMPT.txt](PROMPT.txt). Local screenshots are from the real TV; viewing titles in a screenshot may be private and should be reviewed before publication.
