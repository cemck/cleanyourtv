package com.gathin.tvhome;

import static org.junit.Assert.*;
import android.media.session.PlaybackState;
import org.junit.Test;

public class AlpineNowPlayingTest {
  @Test public void realPlaybackOutranksPausedOrBufferingSessions() {
    assertTrue(AlpineNowPlaying.rank(PlaybackState.STATE_PLAYING, "Track")
        > AlpineNowPlaying.rank(PlaybackState.STATE_PAUSED, "Previous track"));
    assertTrue(AlpineNowPlaying.rank(PlaybackState.STATE_PLAYING, "Track")
        > AlpineNowPlaying.rank(PlaybackState.STATE_BUFFERING, "Loading"));
  }
  @Test public void pausedMediaRemainsAvailableToResume() {
    assertTrue(AlpineNowPlaying.rank(PlaybackState.STATE_PAUSED, "Track") > 0);
  }
  @Test public void disconnectedBluetoothAndStoppedMediaStayHidden() {
    assertEquals(0, AlpineNowPlaying.rank(PlaybackState.STATE_ERROR, "Bluetooth"));
    assertEquals(0, AlpineNowPlaying.rank(PlaybackState.STATE_STOPPED, "Old track"));
    assertEquals(0, AlpineNowPlaying.rank(PlaybackState.STATE_NONE, "Old track"));
  }
  @Test public void emptyMetadataCannotCreateAPhantomWidget() {
    assertEquals(0, AlpineNowPlaying.rank(PlaybackState.STATE_PLAYING, null));
    assertEquals(0, AlpineNowPlaying.rank(PlaybackState.STATE_PLAYING, "  "));
  }
}
