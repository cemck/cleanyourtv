package com.gathin.tvhome;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;
import java.io.File;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Locale;
import org.json.JSONObject;

/** Static scenery and a real occlusion layer, with a cached, low-resolution backdrop blur. */
final class AlpineArt extends View {
  final Bitmap image;
  final Bitmap frost;
  final Typeface clockFont, textFont, mediumFont;
  private final Paint paint = new Paint(3);
  private final Paint sceneryPaint = new Paint(3);
  private ColorMatrixColorFilter sceneryFilter;
  private double latitude = Double.NaN, longitude = Double.NaN;
  private LocalDate solarDate;
  private ZoneId solarZone;
  private int sunrise = WallpaperMood.DEFAULT_SUNRISE, sunset = WallpaperMood.DEFAULT_SUNSET;
  private final Path terrain = new Path();
  private String time = "";
  private Path digits = new Path();
  private int clockWidth;
  private static final float[][] RIDGE = {
    {0, .70f},
    {.30f, .65f},
    {.311f, .628f},
    {.330f, .596f},
    {.344f, .586f},
    {.358f, .574f},
    {.368f, .580f},
    {.381f, .548f},
    {.389f, .519f},
    {.395f, .508f},
    {.402f, .518f},
    {.411f, .504f},
    {.416f, .494f},
    {.422f, .504f},
    {.428f, .482f},
    {.437f, .456f},
    {.441f, .437f},
    {.448f, .427f},
    {.453f, .438f},
    {.457f, .433f},
    {.464f, .451f},
    {.466f, .437f},
    {.471f, .444f},
    {.475f, .457f},
    {.484f, .437f},
    {.489f, .427f},
    {.495f, .435f},
    {.500f, .450f},
    {.508f, .469f},
    {.511f, .465f},
    {.514f, .488f},
    {.523f, .493f},
    {.528f, .510f},
    {.535f, .502f},
    {.544f, .520f},
    {.550f, .503f},
    {.554f, .498f},
    {.558f, .504f},
    {.563f, .505f},
    {.566f, .523f},
    {.571f, .535f},
    {.581f, .554f},
    {.584f, .553f},
    {.592f, .559f},
    {.595f, .554f},
    {.602f, .564f},
    {.608f, .562f},
    {.614f, .574f},
    {.618f, .596f},
    {.636f, .621f},
    {.657f, .634f},
    {.687f, .649f},
    {.710f, .670f},
    {.740f, .691f},
    {1, .72f}
  };

  AlpineArt(Context context) {
    super(context);
    File files = Config.dir(context);
    Bitmap source = BitmapFactory.decodeFile(new File(files, "wallpaper.jpg").getPath());
    // Preserve the original source colors. Solar lighting is applied consistently at draw time
    // to the scenery, mountain occlusion and cached glass, without allocating another full bitmap.
    try {
      JSONObject config =
          new JSONObject(
              new String(
                  Files.readAllBytes(new File(files, "alpine/private.json").toPath()),
                  java.nio.charset.StandardCharsets.UTF_8));
      double lat = config.optDouble("latitude", Double.NaN);
      double lon = config.optDouble("longitude", Double.NaN);
      if (Double.isFinite(lat)
          && Double.isFinite(lon)
          && Math.abs(lat) <= 90
          && Math.abs(lon) <= 180) {
        latitude = lat;
        longitude = lon;
      }
    } catch (Exception unavailable) {
      // A launcher without a configured place uses the same documented solar-time defaults.
    }
    updateSolarLight();
    image = source;
    frost = source == null ? null : blur(source);
    clockFont = clockFont(files);
    textFont = font(files, "font-text.otf", "sans-serif");
    mediumFont = font(files, "font-medium.otf", "sans-serif-medium");
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    setFocusable(false);
  }

  private static Typeface clockFont(File root) {
    File variable = new File(root, "alpine/font-clock.ttf");
    if (variable.isFile()) {
      try {
        Typeface face =
            new Typeface.Builder(variable)
                .setFontVariationSettings("'wdth' 47, 'wght' 270.325, 'opsz' 28")
                .build();
        android.util.Log.i("AlpineHome", "Previous light clock restored");
        return face;
      } catch (RuntimeException unavailable) {
        android.util.Log.i("AlpineHome", "Using fallback clock face");
      }
    }
    return font(root, "font-clock.otf", "sans-serif-thin");
  }

  private static Typeface font(File root, String name, String fallback) {
    File file = new File(root, "alpine/" + name);
    try {
      if (file.isFile()) {
        Typeface loaded = Typeface.createFromFile(file);
        android.util.Log.i("AlpineHome", "Private font loaded: " + name);
        return loaded;
      }
    } catch (RuntimeException ignored) {
    }
    return Typeface.create(fallback, Typeface.NORMAL);
  }

  void tick() {
    updateSolarLight();
    invalidate();
  }

  private void updateSolarLight() {
    ZonedDateTime now = ZonedDateTime.now();
    if (!now.toLocalDate().equals(solarDate) || !now.getZone().equals(solarZone)) {
      solarDate = now.toLocalDate();
      solarZone = now.getZone();
      int[] times =
          Double.isFinite(latitude)
              ? Sun.riseAndSet(solarDate, latitude, longitude, solarZone)
              : null;
      sunrise = times == null ? WallpaperMood.DEFAULT_SUNRISE : times[0];
      sunset = times == null ? WallpaperMood.DEFAULT_SUNSET : times[1];
    }
    float[] light = AlpineSolarLight.at(now.getHour() * 60 + now.getMinute(), sunrise, sunset);
    ColorMatrix saturation = new ColorMatrix();
    saturation.setSaturation(light[0]);
    saturation.postConcat(
        new ColorMatrix(
            new float[] {
              light[1], 0, 0, 0, light[4], 0, light[2], 0, 0, light[5], 0, 0, light[3], 0, light[6],
              0, 0, 0, 1, 0
            }));
    sceneryFilter = new ColorMatrixColorFilter(saturation);
    sceneryPaint.setColorFilter(sceneryFilter);
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    int w = getWidth(), h = getHeight();
    c.drawColor(0xff14131d);
    if (image != null) c.drawBitmap(image, null, new Rect(0, 0, w, h), sceneryPaint);
    String now =
        new SimpleDateFormat(
                android.text.format.DateFormat.is24HourFormat(getContext()) ? "H:mm" : "h:mm",
                Locale.ENGLISH)
            .format(new Date());
    if (!now.equals(time) || clockWidth != w) {
      time = now;
      clockWidth = w;
      paint.setTypeface(clockFont);
      paint.setTextSize(h * .55f);
      paint.setShader(null);
      paint.setStyle(Paint.Style.FILL);
      Rect bounds = new Rect();
      paint.getTextBounds(time, 0, time.length(), bounds);
      // A stable clock envelope independent of the narrow "1" or the current hour.
      float size = paint.getTextSize() * (h * .325f) / Math.max(1, bounds.height());
      paint.setTextSize(size);
      float width = paint.measureText(time);
      if (width > w * .38f) paint.setTextSize(paint.getTextSize() * (w * .38f) / width);
      width = paint.measureText(time);
      paint.getTextBounds(time, 0, time.length(), bounds);
      digits = new Path();
      paint.getTextPath(time, 0, time.length(), (w - width) / 2f, h * .505f, digits);
    }
    // Frosted scenery inside the letter shapes, then a tinted clear-glass fill and rim.
    int save = c.save();
    c.clipPath(digits);
    if (frost != null) {
      sceneryPaint.setAlpha(140);
      c.drawBitmap(frost, null, new Rect(0, 0, w, h), sceneryPaint);
      sceneryPaint.setAlpha(255);
    }
    paint.setShader(
        new LinearGradient(
            w * .33f,
            h * .17f,
            w * .67f,
            h * .53f,
            new int[] {0x70ffc4de, 0x60e0cbff, 0x70ffbadb},
            null,
            Shader.TileMode.CLAMP));
    paint.setStyle(Paint.Style.FILL);
    c.drawPath(digits, paint);
    c.restoreToCount(save);
    paint.setShader(
        new LinearGradient(
            0, h * .17f, 0, h * .52f, 0x90ffffff, 0x30ffcbd9, Shader.TileMode.CLAMP));
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(Math.max(1, w / 1600f));
    c.drawPath(digits, paint);
    paint.setStyle(Paint.Style.FILL);
    paint.setShader(null);
    // Redraw precisely the same pixels above the clock. No separately generated, drifting
    // cutout.
    terrain.reset();
    terrain.moveTo(0, h);
    for (float[] point : RIDGE) terrain.lineTo(point[0] * w, point[1] * h);
    terrain.lineTo(w, h);
    terrain.close();
    save = c.save();
    c.clipPath(terrain);
    if (image != null) c.drawBitmap(image, null, new Rect(0, 0, w, h), sceneryPaint);
    c.restoreToCount(save);
    paint.setShader(
        new LinearGradient(
            0,
            h * .57f,
            0,
            h,
            new int[] {0x00000000, 0x31090810, 0xb809080f},
            new float[] {0, .5f, 1},
            Shader.TileMode.CLAMP));
    c.drawRect(0, h * .57f, w, h, paint);
    paint.setShader(null);
  }

  Drawable glass(float radius, float tint) {
    return new Frost(this, radius, tint);
  }

  private static final class Frost extends Drawable {
    final AlpineArt art;
    final float radius, tint;
    final Paint paint = new Paint(3);
    final RectF rect = new RectF();
    View anchor;
    boolean edge = true;

    Frost(AlpineArt art, float radius, float tint) {
      this.art = art;
      this.radius = radius;
      this.tint = tint;
    }

    @Override
    public void draw(Canvas canvas) {
      rect.set(getBounds());
      int save = canvas.save();
      Path path = new Path();
      path.addRoundRect(rect, radius, radius, Path.Direction.CW);
      canvas.clipPath(path);
      if (art.frost != null && anchor != null && art.getWidth() > 0) {
        int[] at = new int[2];
        int[] origin = new int[2];
        anchor.getLocationOnScreen(at);
        art.getLocationOnScreen(origin);
        BitmapShader shader =
            new BitmapShader(art.frost, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Matrix matrix = new Matrix();
        matrix.setScale(
            (float) art.getWidth() / art.frost.getWidth(),
            (float) art.getHeight() / art.frost.getHeight());
        matrix.postTranslate(origin[0] - at[0], origin[1] - at[1]);
        shader.setLocalMatrix(matrix);
        paint.setColor(Color.WHITE);
        paint.setShader(shader);
        paint.setColorFilter(art.sceneryFilter);
        canvas.drawRect(rect, paint);
        paint.setColorFilter(null);
        paint.setShader(null);
      }
      paint.setColor(Color.argb((int) (255 * tint), 15, 13, 24));
      canvas.drawRect(rect, paint);
      paint.setShader(
          new LinearGradient(
              0, 0, 0, rect.height(), 0x17ffffff, 0x00ffffff, Shader.TileMode.CLAMP));
      canvas.drawRect(rect, paint);
      paint.setShader(null);
      canvas.restoreToCount(save);
      rect.inset(1, 1);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(1.25f);
      paint.setColor(0x12ffffff);
      if (edge) canvas.drawRoundRect(rect, radius, radius, paint);
      paint.setStyle(Paint.Style.FILL);
    }

    @Override
    public void setAlpha(int alpha) {}

    @Override
    public void setColorFilter(ColorFilter filter) {}

    @Override
    public int getOpacity() {
      return PixelFormat.TRANSLUCENT;
    }
  }

  void frost(View view, float radius, float tint) {
    frost(view, radius, tint, true);
  }

  void frost(View view, float radius, float tint, boolean edge) {
    Frost drawable = new Frost(this, radius, tint);
    drawable.edge = edge;
    drawable.anchor = view;
    view.setBackground(drawable);
  }

  static Bitmap blur(Bitmap source) {
    Bitmap small = Bitmap.createScaledBitmap(source, 480, 270, true);
    int w = small.getWidth(), h = small.getHeight();
    int[] a = new int[w * h], b = new int[w * h];
    small.getPixels(a, 0, w, 0, 0, w, h);
    int radius = 9;
    for (int pass = 0; pass < 3; pass++) {
      for (int y = 0; y < h; y++)
        for (int x = 0; x < w; x++) {
          int r = 0, g = 0, bl = 0, n = 0;
          for (int d = -radius; d <= radius; d++) {
            int p = a[y * w + Math.max(0, Math.min(w - 1, x + d))];
            r += (p >> 16) & 255;
            g += (p >> 8) & 255;
            bl += p & 255;
            n++;
          }
          b[y * w + x] = 0xff000000 | (r / n << 16) | (g / n << 8) | (bl / n);
        }
      for (int y = 0; y < h; y++)
        for (int x = 0; x < w; x++) {
          int r = 0, g = 0, bl = 0, n = 0;
          for (int d = -radius; d <= radius; d++) {
            int p = b[Math.max(0, Math.min(h - 1, y + d)) * w + x];
            r += (p >> 16) & 255;
            g += (p >> 8) & 255;
            bl += p & 255;
            n++;
          }
          a[y * w + x] = 0xff000000 | (r / n << 16) | (g / n << 8) | (bl / n);
        }
    }
    Bitmap result = Bitmap.createBitmap(a, w, h, Bitmap.Config.ARGB_8888);
    if (small != source) small.recycle();
    return result;
  }
}
