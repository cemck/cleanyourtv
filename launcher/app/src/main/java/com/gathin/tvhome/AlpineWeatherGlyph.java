package com.gathin.tvhome;

import android.content.Context;
import android.graphics.drawable.Drawable;

/** Google's weather artwork, bundled locally without extra weather-image requests. */
final class AlpineWeatherGlyph {
  static String key(int code, boolean day) {
    if (code == 0) return day ? "sunny" : "clear";
    if (code == 1) return day ? "mostly_sunny" : "mostly_clear";
    if (code == 2) return day ? "partly_cloudy" : "partly_clear";
    if (code == 3 || code == 45 || code == 48) return "cloudy";
    if (code >= 95) return "strong_tstorms";
    if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "snow_showers";
    if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return "showers";
    return "cloudy";
  }

  static Drawable load(Context context, String weather) {
    String key = weather.substring(0, weather.indexOf(' '));
    // Existing cached weather strings used Unicode symbols; retain them until the next refresh.
    if (key.contains("☀")) key = "sunny";
    else if (key.contains("☾")) key = "clear";
    else if (key.contains("☁")) key = "cloudy";
    else if (key.contains("☂")) key = "showers";
    else if (key.contains("❄")) key = "snow_showers";
    int id;
    switch (key) {
      case "sunny":
        id = R.drawable.google_weather_sunny;
        break;
      case "clear":
        id = R.drawable.google_weather_clear;
        break;
      case "mostly_sunny":
        id = R.drawable.google_weather_mostly_sunny;
        break;
      case "mostly_clear":
        id = R.drawable.google_weather_mostly_clear;
        break;
      case "partly_cloudy":
        id = R.drawable.google_weather_partly_cloudy;
        break;
      case "partly_clear":
        id = R.drawable.google_weather_partly_clear;
        break;
      case "showers":
        id = R.drawable.google_weather_showers;
        break;
      case "snow_showers":
        id = R.drawable.google_weather_snow_showers;
        break;
      case "strong_tstorms":
        id = R.drawable.google_weather_strong_tstorms;
        break;
      default:
        id = R.drawable.google_weather_cloudy;
    }
    return context.getDrawable(id);
  }
}
