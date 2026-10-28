package com.gathin.tvhome;

import java.util.*;

/** A recent resume card, Twitch's recommendation feed, then varied discoveries across apps. */
final class AlpineShelf {
  static boolean twitch(String pkg) {
    return "tv.twitch.android.app".equals(pkg);
  }

  private static boolean available(AlpineData.Program row, long now) {
    return !row.app.pkg.toLowerCase(Locale.ROOT).contains("browse")
        && (row.start <= 0 || row.start <= now)
        && (row.end <= 0 || row.end > now);
  }

  static List<AlpineData.Program> select(
      List<AlpineData.Program> watching, List<AlpineData.Program> recommended, long now) {
    long rotation = now / 120_000;
    List<AlpineData.Program> resumes = new ArrayList<>();
    Map<String, List<AlpineData.Program>> feeds = new LinkedHashMap<>();
    for (AlpineData.Program row : watching)
      if (available(row, now)
          && !twitch(row.app.pkg)
          && row.progress < .97f
          && (row.when <= 0 || now - row.when < 30L * 24 * 60 * 60_000)) resumes.add(row);
    resumes.sort((a, b) -> Long.compare(b.when, a.when));
    for (AlpineData.Program row : recommended)
      if (available(row, now))
        feeds.computeIfAbsent(row.app.pkg, key -> new ArrayList<>()).add(row);
    List<AlpineData.Program> result = new ArrayList<>();
    Set<String> apps = new HashSet<>();
    if (!resumes.isEmpty()) add(result, apps, resumes.get(0));
    List<AlpineData.Program> twitch = feeds.remove("tv.twitch.android.app");
    if (twitch != null && !twitch.isEmpty()) {
      // Prefer explicitly live streams over other recommendations from Twitch's own feed.
      List<AlpineData.Program> live = new ArrayList<>();
      for (AlpineData.Program row : twitch) if (row.live) live.add(row);
      if (!live.isEmpty()) add(result, apps, live.get((int) Math.floorMod(rotation, live.size())));
    }
    List<List<AlpineData.Program>> discovery = new ArrayList<>(feeds.values());
    for (int i = 0; i < discovery.size() && result.size() < 3; i++) {
      List<AlpineData.Program> feed =
          discovery.get((int) Math.floorMod(rotation + i, discovery.size()));
      add(result, apps, feed.get((int) Math.floorMod(rotation / discovery.size(), feed.size())));
    }
    for (AlpineData.Program row : resumes) if (result.size() < 3) add(result, apps, row);
    return result;
  }

  private static void add(
      List<AlpineData.Program> result, Set<String> apps, AlpineData.Program row) {
    if (apps.add(row.app.pkg)) result.add(row);
  }
}
