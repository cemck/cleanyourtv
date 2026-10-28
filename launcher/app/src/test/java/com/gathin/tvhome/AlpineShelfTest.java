package com.gathin.tvhome;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

public class AlpineShelfTest {
    private static final long NOW = 1_700_000_040_000L;
    private static AlpineData.Program row(String pkg, String title, boolean preview,
            boolean live, long when, long start, long end, float progress) {
        return new AlpineData.Program(new AlpineData.App(pkg, pkg, null, null, null), title,
                preview ? "Recommended" : "Continue watching", "", null, when, progress,
                preview, live, start, end);
    }
    private static AlpineData.Program rec(String pkg, String title) {
        return row(pkg, title, true, false, 0, 0, 0, 0);
    }

    @Test public void twitchUsesRecommendationsRatherThanWatchHistory() {
        AlpineData.Program old = row("tv.twitch.android.app", "Old stream", false, true,
                NOW - 1000, 0, 0, 0);
        AlpineData.Program fresh = row("tv.twitch.android.app", "Live stream", true, true,
                0, NOW - 1000, NOW + 60_000, 0);
        List<AlpineData.Program> result = AlpineShelf.select(Arrays.asList(old), Arrays.asList(fresh), NOW);
        assertEquals(1, result.size());
        assertSame(fresh, result.get(0));
    }

    @Test public void liveTwitchCardsTakePriorityOverOtherTwitchRecommendations() {
        AlpineData.Program live = row("tv.twitch.android.app", "On air", true, true, 0, 0, 0, 0);
        List<AlpineData.Program> result = AlpineShelf.select(Collections.emptyList(),
                Arrays.asList(rec("tv.twitch.android.app", "Other"), live), NOW);
        assertSame(live, result.get(0));
    }

    @Test public void offlineTwitchRecommendationsAreNotShownAsLive() {
        assertTrue(AlpineShelf.select(Collections.emptyList(),
                Arrays.asList(rec("tv.twitch.android.app", "Offline channel")), NOW).isEmpty());
    }

    @Test public void discoveryRotatesAcrossPublishersAndPrograms() {
        AlpineData.Program resume = row("netflix", "Resume", false, false, NOW - 1000, 0, 0, .4f);
        List<AlpineData.Program> recommended = new ArrayList<>();
        recommended.add(rec("tv.twitch.android.app", "Stream"));
        for (String pkg : Arrays.asList("prime", "youtube", "ard", "zdf")) {
            recommended.add(rec(pkg, pkg + " A"));
            recommended.add(rec(pkg, pkg + " B"));
        }
        Set<String> publishers = new HashSet<>(), titles = new HashSet<>();
        for (int i = 0; i < 8; i++) {
            List<AlpineData.Program> selected = AlpineShelf.select(Arrays.asList(resume), recommended, NOW + i * 120_000);
            assertSame(resume, selected.get(0));
            assertEquals(3, selected.size());
            publishers.add(selected.get(2).app.pkg);
            titles.add(selected.get(2).title);
        }
        assertEquals(new HashSet<>(Arrays.asList("prime", "youtube", "ard", "zdf")), publishers);
        assertEquals(8, titles.size());
    }

    @Test public void endedAndFutureLiveProgramsAreNotRecommended() {
        List<AlpineData.Program> result = AlpineShelf.select(Collections.emptyList(), Arrays.asList(
                row("tv.twitch.android.app", "Ended", true, true, 0, 0, NOW, 0),
                row("tv.twitch.android.app", "Future", true, true, 0, NOW + 60_000, 0, 0),
                rec("youtube", "Available")), NOW);
        assertEquals(1, result.size());
        assertEquals("Available", result.get(0).title);
    }

    @Test public void completedStaleAndBrowserHistoryDoNotFillTheRow() {
        List<AlpineData.Program> result = AlpineShelf.select(Arrays.asList(
                row("netflix", "Completed", false, false, NOW, 0, 0, 1),
                row("prime", "Old", false, false, NOW - 31L * 24 * 60 * 60_000, 0, 0, .5f),
                row("com.tcl.browser", "History", false, false, NOW, 0, 0, 0)),
                Arrays.asList(rec("com.vewd.core.browser", "Browser")), NOW);
        assertTrue(result.isEmpty());
    }

    @Test public void onePublisherCannotOccupyMultipleCards() {
        List<AlpineData.Program> result = AlpineShelf.select(Arrays.asList(
                row("netflix", "Older", false, false, NOW - 2000, 0, 0, .5f),
                row("netflix", "Latest", false, false, NOW - 1000, 0, 0, .5f)),
                Arrays.asList(rec("netflix", "Discovery"), rec("prime", "Movie")), NOW);
        assertEquals(2, result.size());
        assertEquals("Latest", result.get(0).title);
        assertEquals("prime", result.get(1).app.pkg);
    }

    @Test public void liveThumbnailsExpireButStaticPostersRemainCached() {
        AlpineData.Program live = row("tv.twitch.android.app", "Stream", true, true, 0, 0, 0, 0);
        assertNotEquals(live.artworkKey(NOW), live.artworkKey(NOW + 120_000));
        AlpineData.Program movie = rec("prime", "Movie");
        assertEquals(movie.artworkKey(NOW), movie.artworkKey(NOW + 120_000));
    }
}
