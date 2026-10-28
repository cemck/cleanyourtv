package com.gathin.tvhome;

import android.app.PendingIntent;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.*;
import android.media.session.MediaController;
import android.os.*;
import android.text.TextUtils;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import java.util.*;
import java.util.concurrent.Executor;

/** Event-driven media-session widget, independent of all Home/key helpers. */
final class AlpineNowPlaying extends LinearLayout {
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final MediaSessionManager manager;
  private final ComponentName access;
  private final ArrayList<MediaController> sessions = new ArrayList<>();
  private final LinearLayout open, copy;
  private final ImageView cover;
  private final TextView source, title, artist;
  private final ImageButton play, next;
  private final Runnable changed;
  private final Executor images;
  private final boolean motion;
  private MediaController current;
  private boolean listening;
  private String track = "";
  private int generation;
  private final MediaController.Callback updates =
      new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
          track = "";
          render();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
          render();
        }

        @Override
        public void onSessionDestroyed() {
          reload();
        }
      };
  private final MediaSessionManager.OnActiveSessionsChangedListener active = this::replace;

  AlpineNowPlaying(
      Context context, AlpineArt art, Executor executor, boolean animate, Runnable change) {
    super(context);
    images = executor;
    motion = animate;
    changed = change;
    manager = context.getSystemService(MediaSessionManager.class);
    access = new ComponentName(context, MediaAccess.class);
    setOrientation(HORIZONTAL);
    setGravity(Gravity.CENTER_VERTICAL);
    setPadding(0, 0, dp(6), 0);
    setVisibility(GONE);
    art.frost(this, dp(15), .34f, false);
    open = new LinearLayout(context);
    open.setGravity(Gravity.CENTER_VERTICAL);
    open.setPadding(dp(10), dp(8), dp(4), dp(8));
    target(open);
    open.setOnClickListener(v -> openPlayer());
    addView(open, new LinearLayout.LayoutParams(0, -1, 1));
    cover = new ImageView(context);
    cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
    GradientDrawable clip = new GradientDrawable();
    clip.setColor(0xff201e28);
    clip.setCornerRadius(dp(7));
    cover.setBackground(clip);
    cover.setClipToOutline(true);
    cover.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    open.addView(cover, new LinearLayout.LayoutParams(dp(44), dp(44)));
    copy = new LinearLayout(context);
    copy.setOrientation(VERTICAL);
    copy.setGravity(Gravity.CENTER_VERTICAL);
    LinearLayout.LayoutParams text = new LinearLayout.LayoutParams(0, -1, 1);
    text.leftMargin = dp(8);
    open.addView(copy, text);
    source = label(8, art.mediumFont, 0xbfffffff);
    title = label(12, art.mediumFont, Color.WHITE);
    artist = label(10, art.textFont, 0xcfffffff);
    source.setLetterSpacing(.04f);
    copy.addView(source);
    copy.addView(title);
    copy.addView(artist);
    play =
        control(
            "Play or pause",
            android.R.drawable.ic_media_play,
            () -> {
              if (current == null) return;
              PlaybackState state = current.getPlaybackState();
              if (state != null
                  && (state.getState() == PlaybackState.STATE_PLAYING
                      || state.getState() == PlaybackState.STATE_BUFFERING
                      || state.getState() == PlaybackState.STATE_CONNECTING))
                current.getTransportControls().pause();
              else current.getTransportControls().play();
            });
    next =
        control(
            "Next track",
            android.R.drawable.ic_media_next,
            () -> {
              if (current != null) current.getTransportControls().skipToNext();
            });
  }

  private int dp(float n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private TextView label(float size, Typeface face, int color) {
    TextView text = new TextView(getContext());
    text.setTextSize(size);
    text.setTypeface(face);
    text.setTextColor(color);
    text.setSingleLine(true);
    text.setEllipsize(TextUtils.TruncateAt.END);
    text.setIncludeFontPadding(false);
    text.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    return text;
  }

  private void target(View view) {
    view.setId(View.generateViewId());
    view.setFocusable(true);
    view.setClickable(true);
    view.setDefaultFocusHighlightEnabled(false);
    view.setSoundEffectsEnabled(true);
    FocusGlow.mark(
        view,
        view instanceof ImageButton ? view : this,
        view instanceof ImageButton ? -.5f : dp(15));
    view.setOnFocusChangeListener(
        (v, focused) ->
            v.animate()
                .scaleX(focused ? 1.04f : 1)
                .scaleY(focused ? 1.04f : 1)
                .setDuration(180)
                .setInterpolator(new DecelerateInterpolator())
                .start());
  }

  private ImageButton control(String name, int icon, Runnable action) {
    ImageButton button = new ImageButton(getContext());
    target(button);
    button.setContentDescription(name);
    button.setImageDrawable(new MediaGlyph(icon));
    button.setImageTintList(ColorStateList.valueOf(0xeeffffff));
    button.setBackgroundColor(Color.TRANSPARENT);
    button.setPadding(dp(4), dp(4), dp(4), dp(4));
    button.setOnClickListener(
        v -> {
          try {
            action.run();
          } catch (RuntimeException unavailable) {
            reload();
          }
        });
    addView(button, new LinearLayout.LayoutParams(dp(26), dp(32)));
    return button;
  }

  List<View> targets() {
    List<View> result = new ArrayList<>();
    if (getVisibility() == VISIBLE) {
      result.add(open);
      if (play.isEnabled()) result.add(play);
      if (next.getVisibility() == VISIBLE) result.add(next);
    }
    return result;
  }

  void start() {
    if (manager == null) return;
    try {
      if (!listening) {
        manager.addOnActiveSessionsChangedListener(active, access, handler);
        listening = true;
      }
      reload();
    } catch (SecurityException unavailable) {
      hide();
    }
  }

  void stop() {
    if (listening) manager.removeOnActiveSessionsChangedListener(active);
    listening = false;
    for (MediaController session : sessions) session.unregisterCallback(updates);
    sessions.clear();
    generation++;
    animate().cancel();
    copy.animate().cancel();
    copy.setAlpha(1);
  }

  private void reload() {
    if (!listening) return;
    try {
      replace(manager.getActiveSessions(access));
    } catch (SecurityException unavailable) {
      hide();
    }
  }

  private void replace(List<MediaController> fresh) {
    if (!listening) return;
    for (MediaController session : sessions) session.unregisterCallback(updates);
    sessions.clear();
    if (fresh != null) sessions.addAll(fresh);
    for (MediaController session : sessions) session.registerCallback(updates, handler);
    render();
  }

  static int rank(int state, String title) {
    if (title == null || title.trim().isEmpty()) return 0;
    if (state == PlaybackState.STATE_PLAYING) return 3;
    if (state == PlaybackState.STATE_BUFFERING || state == PlaybackState.STATE_CONNECTING) return 2;
    return state == PlaybackState.STATE_PAUSED ? 1 : 0;
  }

  private void render() {
    if (!listening) return;
    MediaController selected = null;
    MediaMetadata metadata = null;
    PlaybackState playback = null;
    int priority = 0;
    for (MediaController session : sessions) {
      MediaMetadata m = session.getMetadata();
      PlaybackState state = session.getPlaybackState();
      CharSequence raw = m == null ? null : m.getDescription().getTitle();
      String name = raw == null ? "" : raw.toString();
      int score = state == null || m == null ? 0 : rank(state.getState(), name);
      if (score > priority) {
        selected = session;
        metadata = m;
        playback = state;
        priority = score;
      }
    }
    if (selected == null) {
      current = null;
      hide();
      return;
    }
    current = selected;
    MediaDescription description = metadata.getDescription();
    String name = String.valueOf(description.getTitle());
    String performer =
        description.getSubtitle() == null ? "" : description.getSubtitle().toString();
    String app = "Media";
    try {
      app =
          getContext()
              .getPackageManager()
              .getApplicationLabel(
                  getContext().getPackageManager().getApplicationInfo(current.getPackageName(), 0))
              .toString();
    } catch (Exception ignored) {
    }
    boolean playing = priority > 1;
    String status =
        (priority == 3 ? "Playing" : priority == 2 ? "Loading" : "Paused") + " · " + app;
    long actions = playback.getActions();
    play.setImageDrawable(
        new MediaGlyph(
            playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play));
    play.setContentDescription((playing ? "Pause " : "Play ") + name);
    play.setEnabled(
        (actions
                & (PlaybackState.ACTION_PLAY_PAUSE
                    | (playing ? PlaybackState.ACTION_PAUSE : PlaybackState.ACTION_PLAY)))
            != 0);
    play.setAlpha(play.isEnabled() ? 1 : .35f);
    next.setVisibility((actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0 ? VISIBLE : GONE);
    open.setContentDescription(
        "Open " + app + ": " + name + (performer.isEmpty() ? "" : ", " + performer));
    Bitmap embedded = description.getIconBitmap();
    String key =
        current.getPackageName()
            + "|"
            + name
            + "|"
            + performer
            + "|"
            + description.getIconUri()
            + "|"
            + current.getSessionToken().hashCode();
    if (!key.equals(track)) {
      track = key;
      int id = ++generation;
      final String detail = performer;
      Runnable text =
          () -> {
            source.setText(status);
            title.setText(name);
            artist.setText(detail);
            artist.setVisibility(detail.isEmpty() ? GONE : VISIBLE);
            copy.animate().alpha(1).setDuration(160).start();
          };
      copy.animate().cancel();
      if (motion && isShown() && animationsEnabled())
        copy.animate().alpha(0).setDuration(90).withEndAction(text).start();
      else {
        text.run();
        copy.setAlpha(1);
      }
      Bitmap bitmap = embedded;
      if (bitmap != null) showCover(new BitmapDrawable(getResources(), bitmap));
      else {
        try {
          showCover(getContext().getPackageManager().getApplicationIcon(current.getPackageName()));
        } catch (Exception ignored) {
          cover.setImageResource(android.R.drawable.ic_media_play);
        }
        if (description.getIconUri() != null)
          images.execute(
              () -> {
                Bitmap loaded =
                    AlpineData.artwork(getContext(), description.getIconUri().toString());
                if (loaded != null)
                  handler.post(
                      () -> {
                        if (id == generation && listening)
                          showCover(new BitmapDrawable(getResources(), loaded));
                        else loaded.recycle();
                      });
              });
      }
    } else {
      source.setText(status);
      title.setText(name);
      artist.setText(performer);
      artist.setVisibility(performer.isEmpty() ? GONE : VISIBLE);
    }
    boolean entering = getVisibility() != VISIBLE;
    animate().cancel();
    setVisibility(VISIBLE);
    if (entering && motion && animationsEnabled()) {
      setAlpha(0);
      animate().alpha(1).setDuration(200).start();
    } else setAlpha(1);
    changed.run();
  }

  private final class MediaGlyph extends Drawable {
    private final int kind;
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);

    MediaGlyph(int icon) {
      kind = icon;
      ink.setColor(0xeeffffff);
    }

    @Override
    public int getIntrinsicWidth() {
      return dp(20);
    }

    @Override
    public int getIntrinsicHeight() {
      return dp(20);
    }

    @Override
    public void draw(Canvas canvas) {
      int save = canvas.save();
      canvas.translate(getBounds().left, getBounds().top);
      canvas.scale(getBounds().width() / 24f, getBounds().height() / 24f);
      if (kind == android.R.drawable.ic_media_pause) {
        canvas.drawRoundRect(6, 5, 10, 19, 1, 1, ink);
        canvas.drawRoundRect(14, 5, 18, 19, 1, 1, ink);
      } else {
        Path triangle = new Path();
        triangle.moveTo(7, 5);
        triangle.lineTo(18, 12);
        triangle.lineTo(7, 19);
        triangle.close();
        canvas.drawPath(triangle, ink);
        if (kind == android.R.drawable.ic_media_next)
          canvas.drawRoundRect(18, 5, 20, 19, 1, 1, ink);
      }
      canvas.restoreToCount(save);
    }

    @Override
    public void setAlpha(int alpha) {
      ink.setAlpha(alpha);
      invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
      ink.setColorFilter(filter);
      invalidateSelf();
    }

    @Override
    public int getOpacity() {
      return PixelFormat.TRANSLUCENT;
    }
  }

  private void showCover(Drawable image) {
    Drawable previous = cover.getDrawable();
    while (previous instanceof TransitionDrawable) {
      TransitionDrawable layers = (TransitionDrawable) previous;
      previous = layers.getDrawable(layers.getNumberOfLayers() - 1);
    }
    if (motion && isShown() && animationsEnabled() && previous != null) {
      if (previous.getConstantState() != null)
        previous = previous.getConstantState().newDrawable(getResources()).mutate();
      TransitionDrawable fade = new TransitionDrawable(new Drawable[] {previous, image});
      fade.setCrossFadeEnabled(true);
      cover.setImageDrawable(fade);
      fade.startTransition(200);
    } else cover.setImageDrawable(image);
  }

  private boolean animationsEnabled() {
    return android.animation.ValueAnimator.areAnimatorsEnabled();
  }

  private void hide() {
    current = null;
    if (getVisibility() == GONE) return;
    int id = ++generation;
    animate().cancel();
    if (hasFocus()) {
      View below = open.focusSearch(View.FOCUS_DOWN);
      if (below != null) below.requestFocus();
    }
    Runnable gone =
        () -> {
          if (id != generation) return;
          setVisibility(GONE);
          setAlpha(1);
          changed.run();
        };
    if (motion && isShown() && animationsEnabled())
      animate().alpha(0).setDuration(160).withEndAction(gone).start();
    else gone.run();
  }

  private void openPlayer() {
    if (current == null) return;
    // Some TV apps publish a session PendingIntent that does not open their TV activity.
    // Prefer the installed Leanback activity, as the dock does, without changing playback.
    Intent app =
        getContext()
            .getPackageManager()
            .getLeanbackLaunchIntentForPackage(current.getPackageName());
    if (app != null) {
      getContext().startActivity(app);
      return;
    }
    PendingIntent intent = current.getSessionActivity();
    try {
      if (intent != null) intent.send();
    } catch (PendingIntent.CanceledException ignored) {
    }
  }
}
