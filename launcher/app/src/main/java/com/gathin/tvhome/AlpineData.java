package com.gathin.tvhome;

import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Installed apps, public TV-provider rows, and cached weather; no fabricated viewing history. */
final class AlpineData {
  static final class App {
    final String pkg, label;
    final Drawable icon, banner;
    final Intent launch;

    App(String p, String l, Drawable i, Drawable b, Intent in) {
      pkg = p;
      label = l;
      icon = i;
      banner = b;
      launch = in;
    }
  }

  static final class Program {
    final App app;
    final String title, subtitle, art;
    final Intent intent;
    final long when;
    final float progress;
    final boolean preview, live;
    final long start, end;

    Program(App a, String t, String sub, String image, Intent in, long time, float p) {
      this(a, t, sub, image, in, time, p, false, false, 0, 0);
    }

    Program(
        App a,
        String t,
        String sub,
        String image,
        Intent in,
        long time,
        float p,
        boolean recommendation,
        boolean onAir,
        long from,
        long until) {
      app = a;
      title = t;
      subtitle = sub;
      art = image;
      intent = in;
      when = time;
      progress = p;
      preview = recommendation;
      live = onAir;
      start = from;
      end = until;
    }

    String artworkKey(long now) {
      return art + ((live || AlpineShelf.twitch(app.pkg)) ? "|" + now / 120_000 : "");
    }
  }

  static List<App> apps(Context ctx) {
    PackageManager pm = ctx.getPackageManager();
    Map<String, App> found = new HashMap<>();
    for (ResolveInfo r :
        pm.queryIntentActivities(
            new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)) {
      String pkg = r.activityInfo.packageName;
      if (pkg.equals(ctx.getPackageName())) continue;
      try {
        Drawable banner = r.activityInfo.loadBanner(pm);
        found.put(
            pkg,
            new App(
                pkg,
                r.loadLabel(pm).toString(),
                r.activityInfo.loadIcon(pm),
                banner,
                new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .setComponent(new ComponentName(pkg, r.activityInfo.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
      } catch (RuntimeException ignored) {
      }
    }
    List<App> list = new ArrayList<>(found.values());
    list.sort(Comparator.comparing(a -> a.label.toLowerCase(Locale.ROOT)));
    return list;
  }

  static List<Program> programs(Context ctx, List<App> apps, boolean preview) {
    Map<String, App> installed = new HashMap<>();
    for (App a : apps) installed.put(a.pkg, a);
    List<Program> result = new ArrayList<>();
    Uri uri =
        Uri.parse(
            "content://android.media.tv/" + (preview ? "preview_program" : "watch_next_program"));
    // No SQL selection: ordinary READ_TV_LISTINGS callers can read searchable rows only.
    try (Cursor c = ctx.getContentResolver().query(uri, null, null, null, null)) {
      if (c != null)
        while (c.moveToNext() && c.getPosition() < 4096) {
          App app = installed.get(str(c, "package_name"));
          String title = str(c, "title"), raw = str(c, "intent_uri");
          if (app == null || title.isEmpty() || raw.isEmpty()) continue;
          if (number(c, "browsable", 1) == 0) continue;
          boolean live = number(c, "live", 0) == 1;
          long start = number(c, "start_time_utc_millis", 0),
              end = number(c, "end_time_utc_millis", 0);
          Intent open;
          try {
            open = Intent.parseUri(raw, Intent.URI_INTENT_SCHEME);
            open.setPackage(app.pkg);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          } catch (Exception ignored) {
            open = app.launch;
          }
          long last = number(c, "last_playback_position_millis", 0),
              duration = number(c, "duration_millis", 0),
              when = number(c, "last_engagement_time_utc_millis", 0);
          String season = str(c, "season_display_number"),
              episode = str(c, "episode_display_number"),
              sub = str(c, "episode_title");
          if (!season.isEmpty() || !episode.isEmpty())
            sub =
                (season.isEmpty() ? "" : "S" + season)
                    + (episode.isEmpty() ? "" : " E" + episode)
                    + (sub.isEmpty() ? "" : " · " + sub);
          if (live) sub = "Live now";
          else if (preview && AlpineShelf.twitch(app.pkg)) sub = "Recommended on Twitch";
          else if (sub.isEmpty()) sub = preview ? "Recommended for you" : "Continue watching";
          result.add(
              new Program(
                  app,
                  title,
                  sub,
                  str(c, "poster_art_uri"),
                  open,
                  when,
                  duration > 0 ? Math.min(1f, (float) last / duration) : 0,
                  preview,
                  live,
                  start,
                  end));
        }
    } catch (RuntimeException error) {
      Log.i("AlpineHome", "Program access unavailable: " + error.getClass().getSimpleName());
    }
    result.sort((a, b) -> Long.compare(b.when, a.when));
    Map<String, Integer> sources = new LinkedHashMap<>();
    for (Program program : result)
      sources.put(program.app.pkg, sources.getOrDefault(program.app.pkg, 0) + 1);
    int twitchLive = 0, twitchCount = 0;
    for (Program program : result)
      if (AlpineShelf.twitch(program.app.pkg)) {
        twitchCount++;
        if (program.live) twitchLive++;
      }
    Log.i(
        "AlpineHome",
        (preview ? "Preview" : "Watch Next")
            + " provider sources: "
            + sources
            + "; Twitch live flags="
            + twitchLive
            + "/"
            + twitchCount);
    return result;
  }

  private static String str(Cursor c, String name) {
    int i = c.getColumnIndex(name);
    return i < 0 || c.isNull(i) ? "" : c.getString(i);
  }

  private static long number(Cursor c, String name, long fallback) {
    int i = c.getColumnIndex(name);
    return i < 0 || c.isNull(i) ? fallback : c.getLong(i);
  }

  static Bitmap artwork(Context ctx, String source) {
    if (source == null || source.isEmpty()) return null;
    try {
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inSampleSize = 2;
      if (source.startsWith("https://")) {
        URLConnection connection = new URL(source).openConnection();
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(6000);
        try (InputStream in = connection.getInputStream()) {
          return BitmapFactory.decodeStream(in, null, options);
        }
      }
      if (source.startsWith("content://") || source.startsWith("android.resource://"))
        try (InputStream in = ctx.getContentResolver().openInputStream(Uri.parse(source))) {
          return BitmapFactory.decodeStream(in, null, options);
        }
    } catch (Exception ignored) {
    }
    return null;
  }

  static String weather(Context ctx) throws Exception {
    File privateFile = new File(Config.dir(ctx), "alpine/private.json");
    if (!privateFile.isFile()) return "";
    JSONObject config = new JSONObject(read(new FileInputStream(privateFile)));
    String url =
        "https://api.open-meteo.com/v1/forecast?latitude="
            + config.getDouble("latitude")
            + "&longitude="
            + config.getDouble("longitude")
            + "&current=temperature_2m,weather_code,is_day&timezone=auto";
    URLConnection connection = new URL(url).openConnection();
    connection.setConnectTimeout(7000);
    connection.setReadTimeout(7000);
    JSONObject now;
    try (InputStream in = connection.getInputStream()) {
      now = new JSONObject(read(in)).getJSONObject("current");
    }
    int code = now.getInt("weather_code");
    boolean day = now.optInt("is_day", 0) == 1;
    String icon = AlpineWeatherGlyph.key(code, day);
    String description =
        code == 0
            ? (day ? "Clear" : "Clear night")
            : code == 1
                ? "Mostly clear"
                : code == 2
                    ? "Partly cloudy"
                    : code == 3
                        ? "Overcast"
                        : code < 50
                            ? "Foggy"
                            : code < 70
                                ? "Rain"
                                : code < 80 ? "Snow" : code < 95 ? "Showers" : "Thunderstorms";
    return icon + " " + Math.round(now.getDouble("temperature_2m")) + "° " + description;
  }

  private static String read(InputStream in) throws IOException {
    try (InputStream input = in;
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] b = new byte[8192];
      int n;
      while ((n = input.read(b)) != -1) {
        out.write(b, 0, n);
        if (out.size() > 5_000_000) throw new IOException("Response too large");
      }
      return out.toString(StandardCharsets.UTF_8.name());
    }
  }
}
