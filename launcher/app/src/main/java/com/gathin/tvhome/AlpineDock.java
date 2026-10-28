package com.gathin.tvhome;

import java.util.*;

/** Restores exact pin membership as well as order; empty places must not refill on restart. */
final class AlpineDock {
  static List<String> restore(String saved, List<String> defaults, Set<String> installed) {
    List<String> source = saved == null || saved.isEmpty()
        ? defaults : Arrays.asList(saved.split(",", -1));
    List<String> result = new ArrayList<>();
    Set<String> used = new HashSet<>();
    for (int i = 0; i < 12; i++) {
      String key = i < source.size() ? source.get(i) : "";
      boolean valid = key.equals("settings") || key.equals("apps") || installed.contains(key);
      result.add(valid && used.add(key) ? key : "empty" + i);
    }
    for (String utility : Arrays.asList("settings", "apps")) {
      if (result.contains(utility)) continue;
      int slot = 11;
      for (int i = 11; i >= 0; i--)
        if (result.get(i).startsWith("empty")) { slot = i; break; }
      while (result.get(slot).equals("settings") || result.get(slot).equals("apps")) slot--;
      result.set(slot, utility);
    }
    return result;
  }
}
