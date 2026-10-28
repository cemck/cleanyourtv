package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.view.animation.DecelerateInterpolator;

/** A quiet, border-free focus halo. Analytic layers only: no blur bitmap or idle rendering. */
final class FocusGlow extends View implements ViewTreeObserver.OnGlobalFocusChangeListener,
    ViewTreeObserver.OnPreDrawListener {
  private static final class Shape {
    final View view;
    final float radius;

    Shape(View target, float corner) {
      view = target;
      radius = corner;
    }
  }

  static void mark(View focus, View surface, float radius) {
    focus.setTag(com.gathin.tvhome.R.id.focus_glow_shape, new Shape(surface, radius));
  }

  private final boolean motion;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF current = new RectF(), previous = new RectF(), layer = new RectF();
  private final Rect visible = new Rect();
  private final RectF pending = new RectF();
  private final Matrix targetMatrix = new Matrix(), overlayMatrix = new Matrix(), inverse = new Matrix();
  private final Path inside = new Path();
  private Shape target;
  private ValueAnimator animation;
  private float phase = 1, previousLevel, previousRadius;

  FocusGlow(Context context, boolean animate) {
    super(context);
    motion = animate;
    setFocusable(false);
    setClickable(false);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    getViewTreeObserver().addOnGlobalFocusChangeListener(this);
    getViewTreeObserver().addOnPreDrawListener(this);
    onGlobalFocusChanged(null, getRootView().findFocus());
  }

  @Override
  protected void onDetachedFromWindow() {
    getViewTreeObserver().removeOnGlobalFocusChangeListener(this);
    getViewTreeObserver().removeOnPreDrawListener(this);
    if (animation != null) animation.cancel();
    target = null;
    super.onDetachedFromWindow();
  }

  @Override
  public void onWindowFocusChanged(boolean focused) {
    super.onWindowFocusChanged(focused);
    onGlobalFocusChanged(null, focused ? getRootView().findFocus() : null);
  }

  @Override
  public void onGlobalFocusChanged(View old, View fresh) {
    previous.setEmpty();
    previousLevel = 0;
    if (bounds(target, previous)) {
      previousRadius = radius(target, previous);
      previousLevel = phase;
    }
    if (animation != null) animation.cancel();
    Object tag = fresh == null ? null : fresh.getTag(com.gathin.tvhome.R.id.focus_glow_shape);
    target = tag instanceof Shape ? (Shape) tag : null;
    if (motion && ValueAnimator.areAnimatorsEnabled()) {
      phase = 0;
      animation = ValueAnimator.ofFloat(0, 1);
      animation.setDuration(180);
      animation.setInterpolator(new DecelerateInterpolator());
      animation.addUpdateListener(
          a -> {
            phase = (float) a.getAnimatedValue();
            invalidate();
          });
      animation.start();
    } else {
      phase = 1;
      invalidate();
    }
  }

  private boolean bounds(Shape shape, RectF result) {
    if (shape == null || !shape.view.isShown() || shape.view.getRootView() != getRootView()
        || !shape.view.getLocalVisibleRect(visible))
      return false;
    targetMatrix.reset();
    overlayMatrix.reset();
    toRoot(shape.view, targetMatrix);
    toRoot(this, overlayMatrix);
    if (!overlayMatrix.invert(inverse)) return false;
    targetMatrix.postConcat(inverse);
    result.set(0, 0, shape.view.getWidth(), shape.view.getHeight());
    targetMatrix.mapRect(result);
    return !result.isEmpty();
  }

  /** Same window coordinates throughout, including scroll and animated child transforms. */
  private static void toRoot(View view, Matrix matrix) {
    if (view.getParent() instanceof View) {
      View parent = (View) view.getParent();
      toRoot(parent, matrix);
      matrix.preTranslate(-parent.getScrollX(), -parent.getScrollY());
    }
    matrix.preTranslate(view.getLeft(), view.getTop());
    matrix.preConcat(view.getMatrix());
  }

  @Override
  public boolean onPreDraw() {
    if (bounds(target, pending) && !pending.equals(current)) invalidate();
    return true;
  }

  private float radius(Shape shape, RectF rect) {
    return shape.radius < 0
        ? Math.min(rect.width(), rect.height()) * -shape.radius
        : Math.min(
            Math.min(rect.width(), rect.height()) / 2, shape.radius * shape.view.getScaleX());
  }

  @Override
  protected void onDraw(Canvas canvas) {
    if (!previous.isEmpty()) halo(canvas, previous, previousRadius, previousLevel * (1 - phase));
    if (bounds(target, current)) halo(canvas, current, radius(target, current), phase);
  }

  private void halo(Canvas canvas, RectF rect, float corner, float strength) {
    if (strength <= 0) return;
    int save = canvas.save();
    inside.reset();
    inside.addRoundRect(rect, corner, corner, Path.Direction.CW);
    canvas.clipOutPath(inside);
    float spread = 14 * getResources().getDisplayMetrics().density;
    for (int i = 20; i >= 1; i--) {
      float distance = i / 20f;
      int alpha = Math.round(4.5f * (1 - distance) * (1 - distance) * strength);
      if (alpha == 0) continue;
      float out = spread * distance;
      layer.set(rect);
      layer.inset(-out, -out);
      paint.setColor(Color.argb(alpha, 222, 229, 255));
      canvas.drawRoundRect(layer, corner + out, corner + out, paint);
    }
    canvas.restoreToCount(save);
  }
}
