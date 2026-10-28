package com.gathin.tvhome;

import android.app.Activity;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.tv.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Standard theme Inputs dropdown, with the Alpine wallpaper's cached glass blur. */
final class AlpineInputMenu extends ContextWrapper {
  private final AlpineArt art;
  private final ViewGroup root;
  private final View anchor;
  private final float density;
  private final boolean reducedMotion, coocaaSources, inputsConnectedOnly;
  private final String liveTvPkg,
      liveTvAction,
      liveTvInputId,
      sourcePanelService,
      sourcePanelAction,
      sourcePanelExtra,
      inputAction,
      inputUriScheme;
  private final List<String> inputSources;
  private PopupWindow openPopup;

  AlpineInputMenu(
      Activity context, AlpineArt backdrop, ViewGroup container, View pill, boolean motion) {
    super(context);
    art = backdrop;
    root = container;
    anchor = pill;
    density = getResources().getDisplayMetrics().density;
    Config cfg = Config.load(this);
    reducedMotion = !motion || cfg.reducedMotion;
    coocaaSources = cfg.coocaaSources;
    inputsConnectedOnly = cfg.inputsConnectedOnly;
    liveTvPkg = cfg.liveTvPackage;
    liveTvAction = cfg.liveTvAction;
    liveTvInputId = cfg.liveTvInputId;
    sourcePanelService = cfg.sourcePanelService;
    sourcePanelAction = cfg.sourcePanelAction;
    sourcePanelExtra = cfg.sourcePanelExtra;
    inputAction = cfg.inputAction;
    inputUriScheme = cfg.inputUriScheme;
    inputSources = cfg.inputSources;
  }

  PopupWindow show() {
    showInputs();
    return openPopup;
  }

  private Intent liveTvIntent() {
    if (liveTvPkg.isEmpty()) return null;
    Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
    if (i != null || liveTvAction.isEmpty()) return i;
    Intent byAction = new Intent(liveTvAction).setPackage(liveTvPkg);
    return getPackageManager().resolveActivity(byAction, 0) != null ? byAction : null;
  }

  private void openSourcePanel() {
    Intent i = new Intent(sourcePanelAction);
    ComponentName c = ComponentName.unflattenFromString(sourcePanelService);
    if (c != null) i.setComponent(c);
    int eq = sourcePanelExtra.indexOf('=');
    if (eq > 0) {
      try {
        i.putExtra(
            sourcePanelExtra.substring(0, eq).trim(),
            Integer.parseInt(sourcePanelExtra.substring(eq + 1).trim()));
      } catch (NumberFormatException ignored) {
      }
    }
    try {
      if (getPackageManager().resolveActivity(i, 0) != null) {
        startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        return;
      }
      startService(i);
    } catch (Exception e) {
      Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
    }
  }

  private void open(Intent i) {
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    try {
      startActivity(i);
    } catch (ActivityNotFoundException | SecurityException e) {
      Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
    }
  }

  private Integer mappedSource(String inputId) {
    return InputSources.mapped(inputSources, inputId);
  }

  private void openInput(String inputId) {

    String sourceId = inputId;
    TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
    if (tim != null) {
      try {
        TvInputInfo info = tim.getTvInputInfo(inputId);
        if (info != null && info.getParentId() != null) sourceId = info.getParentId();
      } catch (RuntimeException ignored) {
      }
    }
    Integer source = mappedSource(sourceId);
    if (source != null && source >= 0 && !inputAction.isEmpty()) {
      try {
        startActivity(
            new Intent(inputAction)
                .putExtra("input", source.intValue())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        return;
      } catch (ActivityNotFoundException | SecurityException e) {

      }
    }
    Uri uri = TvContract.buildChannelUriForPassthroughInput(inputId);
    Intent view =
        (inputUriScheme.isEmpty()
                ? new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "vnd.android.cursor.item/channel")
                : new Intent(Intent.ACTION_VIEW, uri.buildUpon().scheme(inputUriScheme).build()))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

    if (!liveTvPkg.isEmpty()) {
      try {
        startActivity(new Intent(view).setPackage(liveTvPkg));
        return;
      } catch (ActivityNotFoundException | SecurityException ignored) {
      }
    }
    try {
      startActivity(view);
      return;
    } catch (ActivityNotFoundException | SecurityException e) {
      if (inputAction.isEmpty()) {
        Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        return;
      }
    }
    open(new Intent(inputAction).setDataAndType(uri, "vnd.android.cursor.item/channel"));
  }

  private void showInputs() {

    List<CoocaaSources.Source> coocaa =
        coocaaSources ? CoocaaSources.list(this) : Collections.emptyList();
    if (coocaa.isEmpty() && !sourcePanelService.isEmpty() && liveTvIntent() == null) {
      openSourcePanel();
      return;
    }

    boolean underGear = false;
    View anchor = this.anchor;
    View panel = LayoutInflater.from(this).inflate(R.layout.popup_inputs, root, false);
    LinearLayout rows = panel.findViewById(R.id.rows);

    int width = Math.max(anchor.getWidth(), (int) (150 * density));
    final PopupWindow popup = glassPopup(panel, width);

    TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);

    boolean connectedOnly = inputsConnectedOnly;
    List<View> folded = new ArrayList<>();
    boolean sawNonHdmi = false;
    String tunerId = null;
    boolean tunerIsHw = false;
    boolean tunerIsAnalog = false;
    if (tim != null) {
      List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());

      Collections.sort(
          inputs,
          (a, b) -> {
            boolean ha = a.getType() == TvInputInfo.TYPE_HDMI,
                hb = b.getType() == TvInputInfo.TYPE_HDMI;
            if (ha != hb) return ha ? -1 : 1;
            return String.valueOf(a.loadLabel(this))
                .compareToIgnoreCase(String.valueOf(b.loadLabel(this)));
          });

      Set<String> inputIds = new HashSet<>();
      for (TvInputInfo info : inputs) inputIds.add(info.getId());
      Map<String, List<TvInputInfo>> cecDevices = new HashMap<>();
      for (TvInputInfo info : inputs) {
        String parent = info.getParentId();
        if (parent == null || !inputIds.contains(parent)) continue;
        if (!info.isPassthroughInput() || info.isHidden(this)) continue;
        List<TvInputInfo> onPort = cecDevices.get(parent);
        if (onPort == null) cecDevices.put(parent, onPort = new ArrayList<>());
        onPort.add(info);
      }

      int hdmiPorts = 0, hdmiConnected = 0;
      for (TvInputInfo info : inputs) {
        if (!info.isPassthroughInput() || info.getType() != TvInputInfo.TYPE_HDMI) continue;
        if (info.getParentId() != null && inputIds.contains(info.getParentId())) continue;
        hdmiPorts++;
        if (tim.getInputState(info.getId()) == TvInputManager.INPUT_STATE_CONNECTED)
          hdmiConnected++;
      }
      boolean hdmiStateBogus = hdmiPorts > 1 && hdmiConnected == hdmiPorts;
      for (TvInputInfo info : inputs) {
        if (info.getType() == TvInputInfo.TYPE_TUNER
            && (info.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
            && (liveTvInputId.isEmpty() || liveTvInputId.equals(info.getId()))) {

          boolean hw = info.getId().matches(".*/HW\\d+$");
          boolean analog = info.getId().toLowerCase(Locale.ROOT).contains("analog");
          boolean better =
              tunerId == null
                  || (hw && !tunerIsHw)
                  || (hw == tunerIsHw && tunerIsAnalog && !analog)
                  || (hw == tunerIsHw
                      && analog == tunerIsAnalog
                      && info.getId().compareTo(tunerId) < 0);
          if (better) {
            tunerId = info.getId();
            tunerIsHw = hw;
            tunerIsAnalog = analog;
          }
        }

        if (!info.isPassthroughInput() || info.isHidden(this)) continue;

        if (Integer.valueOf(-1).equals(mappedSource(info.getId()))) continue;

        if (info.getParentId() != null && inputIds.contains(info.getParentId())) continue;
        CharSequence l = info.loadLabel(this);
        String label = l == null ? info.getId() : l.toString();
        boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
        if (hdmi && !label.toUpperCase(Locale.ROOT).contains("HDMI")) label = "HDMI " + label;
        CharSequence custom = InputNames.tidy(info.loadCustomLabel(this));
        boolean named = custom != null && custom.length() > 0 && !custom.toString().equals(label);
        if (!hdmi && rows.getChildCount() > 0 && !sawNonHdmi) {
          View line = addSeparator(rows);
          if (connectedOnly) fold(line, folded);
        }
        if (!hdmi) sawNonHdmi = true;
        List<TvInputInfo> devices = cecDevices.get(info.getId());
        if (devices != null) {

          for (TvInputInfo device : devices) {
            CharSequence name = InputNames.tidy(device.loadCustomLabel(this));
            if (name == null || name.length() == 0) name = named ? custom : device.loadLabel(this);
            String deviceLabel =
                name == null || name.length() == 0 ? label : name + " \u00B7 " + label;
            final String deviceId = device.getId();
            int state = hdmiStateBogus ? -1 : tim.getInputState(deviceId);
            View row =
                addInputRow(
                    rows,
                    popup,
                    R.drawable.ic_input,
                    deviceLabel,
                    state,
                    () -> openInput(deviceId));
            if (connectedOnly && InputFold.folds(true, state)) fold(row, folded);
          }
          continue;
        }

        if (named) label = custom + " \u00B7 " + label;
        final String id = info.getId();

        int state = !hdmi || hdmiStateBogus ? -1 : tim.getInputState(id);
        View row =
            addInputRow(
                rows,
                popup,
                hdmi ? R.drawable.ic_input : R.drawable.ic_av,
                label,
                state,
                () -> openInput(id));
        if (connectedOnly && InputFold.folds(hdmi, state)) fold(row, folded);
      }
    }
    if (!folded.isEmpty()) {
      View more = LayoutInflater.from(this).inflate(R.layout.item_input, rows, false);
      ((ImageView) more.findViewById(R.id.icon)).setImageResource(R.drawable.ic_input);
      ((TextView) more.findViewById(R.id.title)).setText(R.string.more_inputs);
      more.findViewById(R.id.status).setVisibility(View.GONE);
      more.findViewById(R.id.dot).setVisibility(View.GONE);
      more.setOnClickListener(
          v -> {
            for (View f : folded) f.setVisibility(View.VISIBLE);
            rows.removeView(more);
            for (View f : folded) {
              if (f.isFocusable()) {
                f.requestFocus();
                break;
              }
            }
          });
      rows.addView(more);
    }

    if (rows.getChildCount() == 0) {
      for (CoocaaSources.Source s : coocaa) {
        int icon =
            "HDMI".equals(s.name)
                ? R.drawable.ic_input
                : "VOD".equals(s.name) ? R.drawable.ic_livetv : R.drawable.ic_av;
        addInputRow(
            rows,
            popup,
            icon,
            s.label,
            -1,
            () -> {
              if (!CoocaaSources.open(this, s)) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
              }
            });
      }
    }

    if (rows.getChildCount() == 0 && !sourcePanelService.isEmpty()) {
      addInputRow(
          rows, popup, R.drawable.ic_input, getString(R.string.inputs), -1, this::openSourcePanel);
    }
    Intent liveTv = liveTvIntent();
    if (liveTv != null && tunerId != null) {

      boolean configured = !liveTvInputId.isEmpty();
      Intent tuned =
          new Intent(Intent.ACTION_VIEW)
              .setDataAndType(
                  configured
                      ? TvContract.buildChannelsUriForInput(tunerId)
                      : TvContract.buildChannelUriForPassthroughInput(tunerId),
                  configured
                      ? TvContract.Channels.CONTENT_TYPE
                      : TvContract.Channels.CONTENT_ITEM_TYPE)
              .setPackage(liveTvPkg);
      if (tuned.resolveActivity(getPackageManager()) != null) liveTv = tuned;
    }
    if (liveTv != null) {
      if (rows.getChildCount() > 0) addSeparator(rows);
      final Intent liveTvIntent = liveTv;
      addInputRow(
          rows,
          popup,
          R.drawable.ic_livetv,
          getString(R.string.live_tv),
          -1,
          () -> open(liveTvIntent));
    }
    if (rows.getChildCount() == 0) {
      Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show();
      return;
    }

    panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
    popup.setWidth(Math.max(width, Math.min(panel.getMeasuredWidth(), (int) (360 * density))));

    int alignment =
        getResources().getBoolean(R.bool.rtl_menu_bar) != underGear ? Gravity.END : Gravity.START;
    dropIn(popup, panel, anchor, alignment);
    for (int i = 0; i < rows.getChildCount(); i++) {
      View child = rows.getChildAt(i);
      if (child.getVisibility() == View.VISIBLE && child.isFocusable()) {
        child.requestFocus();
        break;
      }
    }
  }

  private static void fold(View view, List<View> folded) {
    view.setVisibility(View.GONE);
    folded.add(view);
  }

  private PopupWindow glassPopup(View panel, int width) {
    art.frost(panel, 20 * density, .44f);
    final PopupWindow popup =
        new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
    popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); // needed for Back to dismiss
    popup.setOutsideTouchable(true);
    popup.setAnimationStyle(0);
    popup.setElevation(0f);
    popup.setOnDismissListener(
        () -> {
          if (openPopup == popup) openPopup = null;
        });
    openPopup = popup;
    return popup;
  }

  private void dropIn(PopupWindow popup, View panel, View anchor, int gravity) {
    panel.setAlpha(0f);
    panel.setPivotY(0f);
    panel.setScaleY(0.92f);
    panel.setTranslationY(-8 * density);
    popup.showAsDropDown(anchor, 0, (int) (10 * density), gravity);
    panel
        .animate()
        .alpha(1f)
        .scaleY(1f)
        .translationY(0f)
        .setDuration(reducedMotion ? Motion.REDUCED_MS : 220)
        .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f))
        .start();
  }

  private View addSeparator(LinearLayout rows) {
    View line = new View(this);
    line.setBackgroundColor(0x1FFFFFFF);
    LinearLayout.LayoutParams lp =
        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) density);
    int m = (int) (6 * density), h = (int) (12 * density);
    lp.setMargins(h, m, h, m);
    rows.addView(line, lp);
    return line;
  }

  private View addInputRow(
      LinearLayout rows, PopupWindow popup, int iconRes, String title, int state, Runnable action) {
    View row = LayoutInflater.from(this).inflate(R.layout.item_input, rows, false);
    ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
    ((TextView) row.findViewById(R.id.title)).setText(title);
    ((TextView) row.findViewById(R.id.title)).setTypeface(art.mediumFont);
    TextView status = row.findViewById(R.id.status);
    status.setTypeface(art.textFont);
    View dot = row.findViewById(R.id.dot);

    if (state == TvInputManager.INPUT_STATE_CONNECTED) {
      status.setText(R.string.connected);
      dot.setVisibility(View.VISIBLE);
    } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
      status.setText(R.string.not_connected);
      dot.setVisibility(View.GONE);
    } else {
      status.setVisibility(View.GONE);
      dot.setVisibility(View.GONE);
    }
    row.setOnClickListener(
        v -> {
          popup.dismiss();
          action.run();
        });
    rows.addView(row);
    return row;
  }
}
