package com.gathin.tvhome;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

public class AlpineDockTest {
  private final List<String> defaults = Arrays.asList(
      "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "settings", "apps");
  private final Set<String> installed = new HashSet<>(Arrays.asList(
      "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "new.app"));

  @Test public void existingSavedOrderMigratesWithoutChangingPlaces() {
    List<String> order = new ArrayList<>(defaults);
    Collections.swap(order, 1, 7);
    assertEquals(order, AlpineDock.restore(String.join(",", order), defaults, installed));
  }

  @Test public void addedAppOutsideOriginalPrioritySurvivesRestart() {
    List<String> order = new ArrayList<>(defaults);
    order.set(3, "new.app");
    assertEquals(order, AlpineDock.restore(String.join(",", order), defaults, installed));
  }

  @Test public void removalStaysEmptyInsteadOfRefillingFromDefaults() {
    List<String> order = new ArrayList<>(defaults);
    order.set(4, "empty4");
    List<String> restored = AlpineDock.restore(String.join(",", order), defaults, installed);
    assertEquals(order, restored);
    assertFalse(restored.contains("e"));
  }

  @Test public void missingInstalledAppLeavesOnlyItsPlaceEmpty() {
    installed.remove("c");
    List<String> restored = AlpineDock.restore(String.join(",", defaults), defaults, installed);
    assertEquals("empty2", restored.get(2));
    assertEquals("d", restored.get(3));
  }

  @Test public void damagedLayoutCannotDuplicateAppsOrLoseUtilities() {
    List<String> restored = AlpineDock.restore("a,a,missing", defaults, installed);
    assertEquals(12, restored.size());
    assertEquals(1, Collections.frequency(restored, "a"));
    assertEquals(1, Collections.frequency(restored, "settings"));
    assertEquals(1, Collections.frequency(restored, "apps"));
  }

  @Test public void firstRunUsesExistingInitialDock() {
    assertEquals(defaults, AlpineDock.restore(null, defaults, installed));
  }
}
