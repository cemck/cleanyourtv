package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AlpineWeatherGlyphTest {
    @Test
    public void clearAndPartlyCloudyConditionsUseDayAndNightArtwork() {
        assertEquals("sunny", AlpineWeatherGlyph.key(0, true));
        assertEquals("clear", AlpineWeatherGlyph.key(0, false));
        assertEquals("mostly_sunny", AlpineWeatherGlyph.key(1, true));
        assertEquals("mostly_clear", AlpineWeatherGlyph.key(1, false));
        assertEquals("partly_cloudy", AlpineWeatherGlyph.key(2, true));
        assertEquals("partly_clear", AlpineWeatherGlyph.key(2, false));
    }

    @Test
    public void rainShowersDoNotUseTheSnowArtwork() {
        for (int code : new int[] {51, 55, 61, 65, 66, 67, 80, 81, 82})
            assertEquals("showers", AlpineWeatherGlyph.key(code, false));
    }

    @Test
    public void snowAndThunderstormsHaveDistinctArtwork() {
        for (int code : new int[] {71, 73, 75, 77, 85, 86})
            assertEquals("snow_showers", AlpineWeatherGlyph.key(code, true));
        for (int code : new int[] {95, 96, 99})
            assertEquals("strong_tstorms", AlpineWeatherGlyph.key(code, true));
    }

    @Test
    public void overcastAndFogUseCloudArtwork() {
        assertEquals("cloudy", AlpineWeatherGlyph.key(3, true));
        assertEquals("cloudy", AlpineWeatherGlyph.key(45, false));
        assertEquals("cloudy", AlpineWeatherGlyph.key(48, true));
    }
}
