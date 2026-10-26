package com.gathin.tvhome;

import static org.junit.Assert.*;

import org.junit.Test;

public class AlpineSolarLightTest {
    @Test
    public void nightWrapsAcrossMidnight() {
        assertArrayEquals(AlpineSolarLight.NIGHT, AlpineSolarLight.at(0, 420, 1080), .00001f);
        assertArrayEquals(AlpineSolarLight.NIGHT, AlpineSolarLight.at(1439, 420, 1080), .00001f);
    }

    @Test
    public void daylightAndGoldenHourFollowTheSunRatherThanFixedClockHours() {
        for (int[] sun : new int[][] {{480, 990}, {300, 1320}}) {
            int rise = sun[0], set = sun[1];
            assertArrayEquals(AlpineSolarLight.NOON,
                    AlpineSolarLight.at((rise + set) / 2d, rise, set), .00001f);
            assertArrayEquals(AlpineSolarLight.MORNING,
                    AlpineSolarLight.at(rise + (set - rise) * .18, rise, set), .00001f);
            assertArrayEquals(AlpineSolarLight.SUNSET,
                    AlpineSolarLight.at(set - (set - rise) * .16, rise, set), .00001f);
        }
    }

    @Test
    public void sourceWarmthAndSaturationRemainInDaylight() {
        float[] noon = AlpineSolarLight.at(750, 420, 1080);
        assertEquals(1f, noon[0], .00001f);
        assertTrue(noon[1] >= noon[3]);
        float[] sunset = AlpineSolarLight.at(974.4, 420, 1080);
        assertTrue(sunset[0] >= 1f);
        assertTrue(sunset[1] > sunset[3]);
    }

    @Test
    public void lightingDoesNotJumpAtPhaseBoundaries() {
        for (double minute : new double[] {345, 395, 538.8, 750, 974.4, 1105, 1170}) {
            assertArrayEquals(AlpineSolarLight.at(minute - .001, 420, 1080),
                    AlpineSolarLight.at(minute + .001, 420, 1080), .001f);
        }
    }

    @Test
    public void invalidSolarTimesUseDocumentedDefaults() {
        assertArrayEquals(AlpineSolarLight.at(720, WallpaperMood.DEFAULT_SUNRISE,
                        WallpaperMood.DEFAULT_SUNSET),
                AlpineSolarLight.at(720, 1000, 900), .00001f);
    }
}
