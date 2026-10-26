package com.gathin.tvhome;

import android.app.*;
import android.content.*;
import android.database.ContentObserver;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.tv.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * Personal Alpine Glass home. Original Tide is preserved in ClassicHomeActivity and the rollback
 * APK.
 */
public class MainActivity extends Activity {
  static final String ACTION_LAUNCHER_SETTINGS = "com.gathin.tvhome.action.LAUNCHER_SETTINGS";
  static final String ACTION_VIEW_INPUTS = "com.android.tv.action.VIEW_INPUTS";
  static int COLUMNS = 5;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final ExecutorService data = Executors.newSingleThreadExecutor();
  private final ExecutorService images = Executors.newFixedThreadPool(2);
  private FrameLayout root;
  private AlpineArt art;
  private TextView date, label;
  private FrameLayout settings;
  private LinearLayout shelf, dock;
  private final ArrayList<View> dockTiles = new ArrayList<>(), cards = new ArrayList<>();
  private List<AlpineData.App> apps = new ArrayList<>();
  private int width, height, lastDock;
  private boolean visible, loading, weatherLoading;
  private long weatherAt;
  private String weather = "";
  private String displayedRows = "";
  private final android.util.LruCache<String, Bitmap> artworkCache =
      new android.util.LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
          return bitmap.getAllocationByteCount();
        }
      };
  private Dialog library;
  private final Runnable minute =
      new Runnable() {
        @Override
        public void run() {
          if (!visible) return;
          updateDate();
          art.tick();
          dock.invalidate();
          settings.invalidate();
          for (View card : cards) card.invalidate();
          refreshWeather();
          handler.postDelayed(this, 60_000 - System.currentTimeMillis() % 60_000);
        }
      };
  private final ContentObserver programs =
      new ContentObserver(handler) {
        @Override
        public void onChange(boolean self) {
          if (visible) refreshPrograms();
        }
      };

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow()
        .setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
    root = new FrameLayout(this);
    root.setBackgroundColor(0xff100e18);
    root.setClipChildren(false);
    setContentView(root);
    art = new AlpineArt(this);
    root.addView(art, new FrameLayout.LayoutParams(-1, -1));
    date = text("", 15, art.textFont);
    date.setGravity(Gravity.CENTER);
    date.setShadowLayer(2, 0, 1, 0x40000000);
    root.addView(date);
    settings = button("Settings", 4, null, this::openSettings);
    art.frost(settings, dp(22), .24f, false);
    root.addView(settings);
    label = text("Keep watching", 13, art.textFont);
    label.setTextColor(0xe6ffffff);
    root.addView(label);
    shelf = new LinearLayout(this);
    shelf.setOrientation(LinearLayout.HORIZONTAL);
    shelf.setClipChildren(false);
    root.addView(shelf);
    dock = new LinearLayout(this);
    dock.setOrientation(LinearLayout.HORIZONTAL);
    dock.setGravity(Gravity.CENTER);
    dock.setClipChildren(false);
    art.frost(dock, dp(20), .42f);
    root.addView(dock);
    apps = AlpineData.apps(this);
    buildDock();
    showRecentApps();
    root.addOnLayoutChangeListener(
        (v, l, t, r, b, ol, ot, or, ob) -> {
          if (width != r - l || height != b - t) {
            width = r - l;
            height = b - t;
            geometry();
          }
        });
    weather = getPreferences(MODE_PRIVATE).getString("weather", "");
    weatherAt = getPreferences(MODE_PRIVATE).getLong("weather_at", 0);
    updateDate();

    lastDock =
        Math.max(
            0,
            Math.min(dockTiles.size() - 1, getPreferences(MODE_PRIVATE).getInt("dock_index", 0)));
    root.post(
        () -> {
          dockTiles.get(lastDock).requestFocus();
          handle(getIntent());
        });
  }

  private void geometry() {
    place(date, 0, height * .103f, width, height * .052f);
    float settingsSize = height * .074f;
    place(
        settings,
        width * .928f - settingsSize / 2,
        height * .118f - settingsSize / 2,
        settingsSize,
        settingsSize);
    place(label, width * .04f, height * .614f, width * .7f, height * .035f);
    place(shelf, width * .04f, height * .658f, width * .92f, height * .125f);
    int gap = dp(14);
    int side = Math.min(dp(58), (Math.round(width * .92f) - dp(64) - gap * 11) / 12);
    int innerPadding = dp(16);
    int dockWidth = 12 * side + 11 * gap + 2 * innerPadding;
    place(dock, (width - dockWidth) / 2f, height * .824f, dockWidth, height * .139f);
    dock.setPadding(innerPadding, 0, innerPadding, 0);
    for (int i = 0; i < dockTiles.size(); i++) {
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(side, side);
      params.gravity = Gravity.CENTER_VERTICAL;
      if (i > 0) params.leftMargin = gap;
      dockTiles.get(i).setLayoutParams(params);
    }
    dock.post(
        () -> {
          if (dockTiles.isEmpty()) return;
          int[] first = new int[2], last = new int[2], origin = new int[2];
          dockTiles.get(0).getLocationOnScreen(first);
          View end = dockTiles.get(dockTiles.size() - 1);
          end.getLocationOnScreen(last);
          root.getLocationOnScreen(origin);
          android.util.Log.i(
              "AlpineHome",
              "Dock margins L/R: "
                  + (first[0] - origin[0])
                  + "/"
                  + (width - (last[0] - origin[0] + end.getWidth()))
                  + " px; square slots "
                  + side);
        });
    for (View view : cards) {
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
      params.rightMargin = view == cards.get(cards.size() - 1) ? 0 : dp(10);
      view.setLayoutParams(params);
    }
  }

  private static void place(View view, float x, float y, float w, float h) {
    FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(Math.round(w), Math.round(h));
    p.leftMargin = Math.round(x);
    p.topMargin = Math.round(y);
    view.setLayoutParams(p);
  }

  private int dp(float value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private TextView text(String value, float size, Typeface font) {
    TextView t = new TextView(this);
    t.setText(value);
    t.setTextColor(Color.WHITE);
    t.setTextSize(size);
    t.setTypeface(font);
    t.setIncludeFontPadding(false);
    t.setSingleLine(true);
    t.setEllipsize(TextUtils.TruncateAt.END);
    return t;
  }

  private FrameLayout button(String title, int kind, AlpineData.App app, Runnable click) {
    FrameLayout tile = new FrameLayout(this);
    tile.setId(View.generateViewId());
    tile.setFocusable(true);
    tile.setSoundEffectsEnabled(true);
    tile.setDefaultFocusHighlightEnabled(false);
    tile.setClickable(true);
    tile.setContentDescription(title);
    Icon icon = new Icon(kind, app);
    tile.addView(icon, new FrameLayout.LayoutParams(-1, -1));
    tile.setOnClickListener(v -> click.run());
    tile.setOnFocusChangeListener(
        (v, focus) -> {
          icon.animate()
              .scaleX(focus ? 1.065f : 1)
              .scaleY(focus ? 1.065f : 1)
              .setDuration(180)
              .setInterpolator(new DecelerateInterpolator())
              .start();
        });
    return tile;
  }

  private void buildDock() {
    List<String> priority =
        Arrays.asList(
            "com.google.android.youtube.tv",
            "com.disney.disneyplus",
            "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom",
            "com.plexapp.android",
            "com.spotify.tv.android",
            "com.google.android.youtube.tvmusic",
            "tv.twitch.android.app",
            "de.prosiebensat1digital.seventv",
            "de.exaring.waipu");
    List<AlpineData.App> selected = new ArrayList<>();
    for (String pkg : priority)
      for (AlpineData.App a : apps) if (a.pkg.equals(pkg) && selected.size() < 10) selected.add(a);
    for (AlpineData.App a : apps)
      if (selected.size() < 10 && !selected.contains(a)) selected.add(a);
    for (AlpineData.App a : selected) {
      FrameLayout tile = button(a.label, 0, a, () -> launch(a.launch, a.pkg));
      dock.addView(tile);
      dockTiles.add(tile);
    }
    while (dockTiles.size() < 10) {
      FrameLayout tile = button("Apps", 3, null, this::showLibrary);
      dock.addView(tile);
      dockTiles.add(tile);
    }
    FrameLayout gear = button("Settings", 2, null, this::openSettings);
    dock.addView(gear);
    dockTiles.add(gear);
    FrameLayout more = button("All apps", 3, null, this::showLibrary);
    dock.addView(more);
    dockTiles.add(more);
    for (int i = 0; i < dockTiles.size(); i++) {
      final int at = i;
      View tile = dockTiles.get(i);
      View.OnFocusChangeListener animation = tile.getOnFocusChangeListener();
      tile.setOnFocusChangeListener(
          (v, f) -> {
            animation.onFocusChange(v, f);
            if (f) {
              lastDock = at;
              linkNativeFocus();
              getPreferences(MODE_PRIVATE).edit().putInt("dock_index", at).apply();
            }
          });
    }
  }

  /** Explicit links, with native Android focus search, key repeat and navigation audio. */
  private void linkNativeFocus() {
    if (settings == null || dockTiles.isEmpty()) return;
    int settingsId = settings.getId();
    settings.setNextFocusLeftId(settingsId);
    settings.setNextFocusRightId(settingsId);
    settings.setNextFocusUpId(settingsId);
    settings.setNextFocusDownId(
        cards.isEmpty() ? dockTiles.get(lastDock).getId() : cards.get(cards.size() - 1).getId());
    for (int index = 0; index < dockTiles.size(); index++) {
      View tile = dockTiles.get(index);
      tile.setNextFocusLeftId(dockTiles.get(Math.max(0, index - 1)).getId());
      tile.setNextFocusRightId(dockTiles.get(Math.min(dockTiles.size() - 1, index + 1)).getId());
      tile.setNextFocusDownId(tile.getId());
      tile.setNextFocusUpId(
          cards.isEmpty()
              ? settingsId
              : cards
                  .get(Math.min(cards.size() - 1, index * cards.size() / dockTiles.size()))
                  .getId());
    }
    for (int index = 0; index < cards.size(); index++) {
      View card = cards.get(index);
      card.setNextFocusLeftId(cards.get(Math.max(0, index - 1)).getId());
      card.setNextFocusRightId(cards.get(Math.min(cards.size() - 1, index + 1)).getId());
      card.setNextFocusUpId(settingsId);
      card.setNextFocusDownId(dockTiles.get(lastDock).getId());
    }
  }

  private void showRecentApps() {
    List<AlpineData.App> recent = new ArrayList<>(apps);
    recent.sort((a, b) -> Long.compare(LastOpened.get(this, b.pkg), LastOpened.get(this, a.pkg)));
    List<AlpineData.Program> rows = new ArrayList<>();
    for (AlpineData.App a : recent) {
      long when = LastOpened.get(this, a.pkg);
      if (when > 0)
        rows.add(
            new AlpineData.Program(
                a,
                a.label,
                "Last opened " + LastOpened.describe(this, when),
                "",
                a.launch,
                when,
                0));
      if (rows.size() == 3) break;
    }
    if (rows.isEmpty())
      for (AlpineData.App a : recent) {
        rows.add(new AlpineData.Program(a, a.label, "Open app", "", a.launch, 0, 0));
        if (rows.size() == 3) break;
      }
    showPrograms(
        rows,
        rows.isEmpty() ? "Your apps" : rows.get(0).when > 0 ? "Recently opened" : "Your apps");
  }

  private void showPrograms(List<AlpineData.Program> rows, String heading) {
    StringBuilder signature = new StringBuilder(heading);
    Set<String> signatureApps = new HashSet<>();
    for (AlpineData.Program row : rows) {
      if (!signatureApps.add(row.app.pkg)) continue;
      signature
          .append('|')
          .append(row.app.pkg)
          .append('|')
          .append(row.title)
          .append('|')
          .append(row.subtitle)
          .append('|')
          .append(row.art);
      if (signatureApps.size() == 3) break;
    }
    if (signature.toString().equals(displayedRows)) return;
    displayedRows = signature.toString();
    boolean hadCard = cards.contains(getCurrentFocus());
    shelf.removeAllViews();
    cards.clear();
    label.setText(heading);
    Set<String> unique = new HashSet<>();
    for (AlpineData.Program row : rows) {
      if (!unique.add(row.app.pkg)) continue;
      if (cards.size() == 3) break;
      FrameLayout card = new FrameLayout(this);
      card.setId(View.generateViewId());
      card.setFocusable(true);
      card.setSoundEffectsEnabled(true);
      card.setDefaultFocusHighlightEnabled(false);
      card.setClickable(true);
      card.setContentDescription(row.app.label + ", " + row.title + ", " + row.subtitle);
      art.frost(card, dp(14), .48f);
      ImageView poster = new ImageView(this);
      poster.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
      poster.setImageDrawable(row.app.banner == null ? row.app.icon : row.app.banner);
      GradientDrawable clip = new GradientDrawable();
      clip.setColor(0xff24222c);
      clip.setCornerRadius(dp(9));
      poster.setBackground(clip);
      poster.setClipToOutline(true);
      int previewWidth = dp(88);
      FrameLayout.LayoutParams image = new FrameLayout.LayoutParams(previewWidth, -1);
      image.setMargins(dp(8), dp(8), 0, dp(8));
      card.addView(poster, image);
      LinearLayout copy = new LinearLayout(this);
      copy.setOrientation(LinearLayout.VERTICAL);
      copy.setGravity(Gravity.CENTER_VERTICAL);
      LinearLayout brandLine = new LinearLayout(this);
      brandLine.setOrientation(LinearLayout.HORIZONTAL);
      brandLine.setGravity(Gravity.CENTER_VERTICAL);
      ImageView brandIcon = new ImageView(this);
      Drawable.ConstantState iconState = row.app.icon.getConstantState();
      brandIcon.setImageDrawable(
          iconState == null ? row.app.icon : iconState.newDrawable(getResources()).mutate());
      brandIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
      brandIcon.setOutlineProvider(
          new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
              outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(2));
            }
          });
      brandIcon.setClipToOutline(true);
      brandIcon.setFocusable(false);
      brandIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      brandLine.addView(brandIcon, new LinearLayout.LayoutParams(dp(10), dp(10)));
      TextView service = text(row.app.label.toUpperCase(Locale.ROOT), 9, art.mediumFont);
      service.setTextColor(0xbfffffff);
      service.setLetterSpacing(.06f);
      LinearLayout.LayoutParams brandText = new LinearLayout.LayoutParams(0, -2, 1);
      brandText.leftMargin = dp(4);
      brandLine.addView(service, brandText);
      copy.addView(brandLine, new LinearLayout.LayoutParams(-1, -2));
      TextView title = text(row.title, 15, art.mediumFont);
      LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
      tp.topMargin = dp(4);
      copy.addView(title, tp);
      TextView sub = text(row.subtitle, 10, art.textFont);
      sub.setTextColor(0xbfffffff);
      LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
      sp.topMargin = dp(3);
      copy.addView(sub, sp);
      FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-1, -1);
      cp.setMargins(dp(8) + previewWidth + dp(10), dp(6), dp(12), dp(8));
      card.addView(copy, cp);
      card.setOnClickListener(v -> launch(row.intent, row.app.pkg));
      card.setOnFocusChangeListener(
          (v, f) -> {
            card.animate()
                .scaleX(f ? 1.025f : 1f)
                .scaleY(f ? 1.025f : 1f)
                .setDuration(180)
                .setInterpolator(new DecelerateInterpolator())
                .start();
          });

      shelf.addView(card);
      cards.add(card);
      Bitmap cached = artworkCache.get(row.art);
      if (cached != null) {
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setImageBitmap(cached);
      } else if (!row.art.isEmpty())
        images.execute(
            () -> {
              Bitmap bitmap = AlpineData.artwork(this, row.art);
              if (bitmap != null) artworkCache.put(row.art, bitmap);
              if (bitmap != null)
                handler.post(
                    () -> {
                      if (!isDestroyed() && poster.isAttachedToWindow()) {
                        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        poster.setImageBitmap(bitmap);
                      }
                    });
            });
    }
    linkNativeFocus();
    if (width > 0) geometry();
    if (hadCard && !cards.isEmpty()) cards.get(0).requestFocus();
  }

  private void refreshPrograms() {
    if (loading || !visible) return;
    loading = true;
    data.execute(
        () -> {
          List<AlpineData.Program> rows = AlpineData.programs(this, apps, false);
          boolean watch = !rows.isEmpty();
          android.util.Log.i("AlpineHome", "Watch Next rows: " + rows.size());
          if (rows.isEmpty()) rows = AlpineData.programs(this, apps, true);
          Map<String, Integer> counts = new LinkedHashMap<>();
          for (AlpineData.Program row : rows)
            counts.put(row.app.pkg, counts.getOrDefault(row.app.pkg, 0) + 1);
          android.util.Log.i("AlpineHome", "Visible content sources: " + counts);
          List<AlpineData.Program> distinct = new ArrayList<>();
          Set<String> seen = new HashSet<>();
          for (AlpineData.Program row : rows)
            if (!row.app.pkg.toLowerCase(Locale.ROOT).contains("browse") && seen.add(row.app.pkg))
              distinct.add(row);
          if (distinct.size() < 3) {
            List<AlpineData.Program> preview = AlpineData.programs(this, apps, true);
            android.util.Log.i("AlpineHome", "Preview rows: " + preview.size());
            for (AlpineData.Program row : preview)
              if (!row.app.pkg.toLowerCase(Locale.ROOT).contains("browse") && seen.add(row.app.pkg))
                distinct.add(row);
          }
          List<AlpineData.App> recent = new ArrayList<>(apps);
          recent.sort(
              (a, b) -> Long.compare(LastOpened.get(this, b.pkg), LastOpened.get(this, a.pkg)));
          for (AlpineData.App a : recent) {
            if (distinct.size() >= 3) break;
            long when = LastOpened.get(this, a.pkg);
            if (when > 0 && seen.add(a.pkg))
              distinct.add(
                  new AlpineData.Program(
                      a,
                      a.label,
                      "Last opened " + LastOpened.describe(this, when),
                      "",
                      a.launch,
                      when,
                      0));
          }
          if (distinct.size() < 3) {
            for (String pkg :
                Arrays.asList(
                    "com.netflix.ninja",
                    "com.amazon.amazonvideo.livingroom",
                    "com.google.android.youtube.tv"))
              for (AlpineData.App app : apps)
                if (distinct.size() < 3 && app.pkg.equals(pkg) && seen.add(app.pkg))
                  distinct.add(
                      new AlpineData.Program(app, app.label, "Open app", "", app.launch, 0, 0));
          }
          if (!distinct.isEmpty()) rows = distinct;
          List<AlpineData.Program> ready = rows;
          handler.post(
              () -> {
                loading = false;
                if (isDestroyed() || !visible) return;
                if (!ready.isEmpty()) showPrograms(ready, watch ? "Keep watching" : "Recommended");
                else showRecentApps();
              });
        });
  }

  private void updateDate() {
    String day = new SimpleDateFormat("EEEE d MMMM", Locale.ENGLISH).format(new Date());
    if (weather.isEmpty()) {
      date.setText(day);
      return;
    }
    String line = day + "   " + weather;
    android.text.SpannableString styled = new android.text.SpannableString(line);
    int symbolEnd = weather.indexOf(' ');
    if (symbolEnd > 0) {
      Drawable glyph = AlpineWeatherGlyph.load(this, weather);
      glyph.setBounds(0, 0, dp(16), dp(16));
      styled.setSpan(
          new android.text.style.ImageSpan(glyph, android.text.style.ImageSpan.ALIGN_BASELINE),
          day.length() + 3,
          day.length() + 3 + symbolEnd,
          android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    date.setText(styled);
    date.setContentDescription(day + ", " + weather.substring(Math.max(0, symbolEnd + 1)));
  }

  private void refreshWeather() {
    if (weatherLoading || System.currentTimeMillis() - weatherAt < 30 * 60_000L) return;
    weatherLoading = true;
    data.execute(
        () -> {
          String next = "";
          try {
            next = AlpineData.weather(this);
          } catch (Exception ignored) {
          }
          String value = next;
          handler.post(
              () -> {
                weatherLoading = false;
                if (isDestroyed()) return;
                weatherAt = System.currentTimeMillis();
                if (!value.isEmpty()) {
                  weather = value;
                  getPreferences(MODE_PRIVATE)
                      .edit()
                      .putString("weather", weather)
                      .putLong("weather_at", weatherAt)
                      .apply();
                  updateDate();
                }
              });
        });
  }

  private void launch(Intent intent, String pkg) {
    try {
      LastOpened.record(this, pkg);
      startActivity(intent);
    } catch (RuntimeException error) {
      Intent fallback = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
      if (fallback != null) startActivity(fallback);
      else Toast.makeText(this, "App unavailable", Toast.LENGTH_SHORT).show();
    }
  }

  private void openSettings() {
    startActivity(
        SystemSettings.intent(this, Settings.ACTION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  }

  private void showLibrary() {
    if (library != null) return;
    library = new Dialog(this);
    FrameLayout panel = new FrameLayout(this);
    art.frost(panel, dp(25), .72f);
    LinearLayout body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(dp(28), dp(22), dp(28), dp(20));
    panel.addView(body, new FrameLayout.LayoutParams(-1, -1));
    TextView title = text("Apps", 25, art.mediumFont);
    body.addView(title);
    ScrollView scroll = new ScrollView(this);
    body.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    GridLayout grid = new GridLayout(this);
    grid.setColumnCount(7);
    scroll.addView(grid);
    for (AlpineData.App a : apps) {
      LinearLayout cell = new LinearLayout(this);
      cell.setOrientation(LinearLayout.VERTICAL);
      cell.setGravity(Gravity.CENTER);
      FrameLayout tile =
          button(
              a.label,
              0,
              a,
              () -> {
                library.dismiss();
                launch(a.launch, a.pkg);
              });
      cell.addView(tile, new LinearLayout.LayoutParams(dp(66), dp(66)));
      TextView name = text(a.label, 11, art.textFont);
      name.setGravity(Gravity.CENTER);
      LinearLayout.LayoutParams text = new LinearLayout.LayoutParams(dp(90), dp(26));
      text.topMargin = dp(6);
      cell.addView(name, text);
      GridLayout.LayoutParams g = new GridLayout.LayoutParams();
      g.width = dp(104);
      g.height = dp(112);
      grid.addView(cell, g);
    }
    TextView credit =
        text("Weather by Open-Meteo  ·  Cached for 30 minutes  ·  Back to close", 11, art.textFont);
    credit.setTextColor(0x99ffffff);
    body.addView(credit);
    library.setContentView(panel);
    Window window = library.getWindow();
    if (window != null) {
      window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
      window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
      WindowManager.LayoutParams attr = window.getAttributes();
      attr.dimAmount = .35f;
      window.setAttributes(attr);
    }
    library.setOnDismissListener(
        d -> {
          library = null;
          dockTiles.get(lastDock).requestFocus();
        });
    library.show();
    if (window != null) window.setLayout((int) (width * .88f), (int) (height * .78f));
    grid.post(
        () -> {
          if (grid.getChildCount() > 0)
            ((ViewGroup) grid.getChildAt(0)).getChildAt(0).requestFocus();
        });
  }

  private void inputs() {
    TvInputManager manager = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
    if (manager == null) return;
    List<TvInputInfo> list = new ArrayList<>();
    List<String> names = new ArrayList<>();
    for (TvInputInfo input : manager.getTvInputList())
      if (input.isPassthroughInput() && !input.isHidden(this)) {
        list.add(input);
        names.add(input.loadLabel(this).toString());
      }
    names.add("Live TV");
    new AlertDialog.Builder(this)
        .setTitle("Inputs")
        .setItems(
            names.toArray(new String[0]),
            (d, at) -> {
              if (at < list.size())
                startActivity(
                    new Intent(
                        Intent.ACTION_VIEW,
                        TvContract.buildChannelUriForPassthroughInput(list.get(at).getId())));
              else {
                Intent live =
                    getPackageManager().getLeanbackLaunchIntentForPackage("org.droidtv.playtv");
                if (live != null) startActivity(live);
              }
            })
        .show();
  }

  private void handle(Intent intent) {
    if (intent == null) return;
    if (ACTION_VIEW_INPUTS.equals(intent.getAction())) inputs();
    else if (ACTION_LAUNCHER_SETTINGS.equals(intent.getAction())) openSettings();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    if (library != null) library.dismiss();
    handle(intent);
    dockTiles.get(lastDock).requestFocus();
  }

  @Override
  protected void onResume() {
    super.onResume();
    visible = true;
    handler.removeCallbacks(minute);
    handler.post(minute);
    refreshPrograms();
    try {
      getContentResolver()
          .registerContentObserver(Uri.parse("content://android.media.tv"), true, programs);
    } catch (RuntimeException ignored) {
    }
  }

  @Override
  protected void onPause() {
    visible = false;
    handler.removeCallbacks(minute);
    getContentResolver().unregisterContentObserver(programs);
    if (library != null) library.dismiss();
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    handler.removeCallbacksAndMessages(null);
    data.shutdownNow();
    images.shutdownNow();
    super.onDestroy();
  }

  @Override
  public void onBackPressed() {
    if (library != null) library.dismiss();
    else dockTiles.get(lastDock).requestFocus();
  }

  private final class Icon extends View {
    final int kind;
    final AlpineData.App app;
    final Paint p = new Paint(3);

    Icon(int kind, AlpineData.App app) {
      super(MainActivity.this);
      this.kind = kind;
      this.app = app;
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onDraw(Canvas c) {
      float w = getWidth(),
          h = getHeight(),
          side = Math.min(w, h),
          x = (w - side) / 2,
          y = (h - side) / 2,
          r = side * .20f;
      RectF box = new RectF(x, y, x + side, y + side);
      int save = c.save();
      Path clip = new Path();
      clip.addRoundRect(box, r, r, Path.Direction.CW);
      c.clipPath(clip);
      if (kind == 0 && app != null) {
        if (app.pkg.equals("com.netflix.ninja")) {
          p.setColor(0xfffaf9fc);
          c.drawRoundRect(box, r, r, p);
          Drawable logo = app.banner == null ? app.icon : app.banner;
          logo.setBounds(
              (int) (x + side * .08f),
              (int) (y + side * .25f),
              (int) (x + side * .92f),
              (int) (y + side * .75f));
          logo.draw(c);
        } else if (app.pkg.equals("com.google.android.youtube.tv")) {
          p.setColor(0xffefedf2);
          c.drawRoundRect(box, r, r, p);
          p.setColor(0xffff0038);
          RectF play =
              new RectF(x + side * .20f, y + side * .31f, x + side * .80f, y + side * .69f);
          c.drawRoundRect(play, side * .08f, side * .08f, p);
          p.setColor(Color.WHITE);
          Path triangle = new Path();
          triangle.moveTo(x + side * .44f, y + side * .40f);
          triangle.lineTo(x + side * .44f, y + side * .60f);
          triangle.lineTo(x + side * .60f, y + side * .50f);
          triangle.close();
          c.drawPath(triangle, p);
        } else {
          p.setColor(0xff211d2e);
          c.drawRoundRect(box, r, r, p);
          float inset = app.pkg.equals("com.spotify.tv.android") ? side * .12f : 0;
          app.icon.setBounds(
              (int) (x + inset),
              (int) (y + inset),
              (int) (x + side - inset),
              (int) (y + side - inset));
          app.icon.draw(c);
        }
      } else {
        p.setColor(kind == 2 ? 0x5e272232 : 0x5e38303f);
        if (kind != 4) c.drawRoundRect(box, r, r, p);
        if (kind == 3) {
          p.setColor(0xffe8e4ef);
          for (int i = -1; i <= 1; i++)
            c.drawCircle(x + side * .5f + i * side * .16f, y + side * .5f, side * .038f, p);
        } else {
          p.setColor(0xffeeeaf3);
          p.setStyle(Paint.Style.STROKE);
          p.setStrokeWidth(side * .023f);
          p.setStrokeJoin(Paint.Join.ROUND);
          Path gear = new Path();
          float cx = x + side / 2, cy = y + side / 2;
          for (int tooth = 0; tooth < 32; tooth++) {
            double angle = Math.PI * 2 * tooth / 32 - Math.PI / 2;
            float radius = side * ((tooth % 4 == 1 || tooth % 4 == 2) ? .215f : .174f);
            float gx = cx + (float) Math.cos(angle) * radius;
            float gy = cy + (float) Math.sin(angle) * radius;
            if (tooth == 0) gear.moveTo(gx, gy);
            else gear.lineTo(gx, gy);
          }
          gear.close();
          c.drawPath(gear, p);
          c.drawCircle(cx, cy, side * .071f, p);
          p.setStyle(Paint.Style.FILL);
        }
      }
      c.restoreToCount(save);
    }
  }
}
