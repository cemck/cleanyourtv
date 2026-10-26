package com.gathin.tvhome;

/** Smooth solar lighting of the original landscape; all values are independent of Android. */
final class AlpineSolarLight {
  // saturation, red/green/blue gain, then red/green/blue lift.
  static final float[] NIGHT = {.94f, .46f, .42f, .58f, 0, 0, 2};
  static final float[] DAWN = {1.02f, .77f, .65f, .70f, 7, 2, 1};
  static final float[] MORNING = {1.03f, 1.04f, .98f, .91f, 6, 3, 0};
  static final float[] NOON = {1f, 1.01f, 1f, .98f, 2, 2, 1};
  static final float[] SUNSET = {1.06f, 1.06f, .94f, .88f, 5, 1, 0};
  static final float[] TWILIGHT = {1f, .64f, .57f, .78f, 4, 0, 2};

  static float[] at(double minute, int sunrise, int sunset) {
    if (sunrise < 0 || sunset > 1440 || sunset <= sunrise) {
      sunrise = WallpaperMood.DEFAULT_SUNRISE;
      sunset = WallpaperMood.DEFAULT_SUNSET;
    }
    double daylight = sunset - sunrise;
    double[] anchors = {
      sunrise - 75,
      sunrise - 25,
      sunrise + daylight * .18,
      (sunrise + sunset) / 2d,
      sunset - daylight * .16,
      sunset + 25,
      sunset + 90
    };
    float[][] grades = {NIGHT, DAWN, MORNING, NOON, SUNSET, TWILIGHT, NIGHT};
    if (minute <= anchors[0] || minute >= anchors[6]) return NIGHT.clone();
    for (int i = 1; i < anchors.length; i++) {
      if (minute <= anchors[i]) {
        double t = (minute - anchors[i - 1]) / (anchors[i] - anchors[i - 1]);
        // Ease the transitions with zero velocity at the light's turning points.
        t = t * t * (3 - 2 * t);
        float[] result = new float[7];
        for (int c = 0; c < result.length; c++)
          result[c] = (float) (grades[i - 1][c] + (grades[i][c] - grades[i - 1][c]) * t);
        return result;
      }
    }
    return NIGHT.clone();
  }
}
