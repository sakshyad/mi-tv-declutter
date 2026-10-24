package com.ansub.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextClock;
import android.widget.TextView;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {

    // Top-row apps, in this order of preference:
    //  1. a list saved at install time:  am start -n com.ansub.tvhome.v2/com.ansub.tvhome.MainActivity --es apps "pkg1,pkg2"
    //     (--es apps auto  goes back to 2; add --es stamp <unique> so it's applied only once, see saveConfig)
    //  2. apps.txt (built in as Apps.LIST), topped up with other TV apps installed on this TV.
    // Missing apps are skipped. Topping up stops at six apps, but a chosen list can be any length:
    // apps wrap into rows of six, like Apple TV, and the page scrolls down once they don't all fit.
    // On the TV, hold OK on an app (or press Menu) to move or remove it, or add another; that saves list 1.
    // A network tile always sits at the top right, level with the clock. Settings shortcuts can join
    // it there, off until picked the same way:
    //   --es quick "bluetooth,sound,picture"
    // (--es quick none  takes them away again; "wifi" and "ethernet" are the network tile now).
    // Home v3 mixes Duotone (the tinted wallpaper) and Marquee (the glow under the dock): --es duotone 70
    // gives Duotone 70% and Marquee 30%. 50 is an even mix, 0 is Marquee only, 100 Duotone only.
    private static final String PREFS = "config";
    private static final List<String> NOT_APPS = Arrays.asList("com.android.vending", "com.android.tv.settings",
            "com.android.tv", "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx",
            "com.google.android.leanbacklauncher", "com.ansub.tvhome");
    private static final String PLAY_STORE = "com.android.vending";
    private static final String SETTINGS = "settings";
    private static final String INPUTS = "inputs";
    private static final String ADD = "add";
    // A dock entry "hdmi2" opens HDMI 2, with assets/logos/hdmi2.png as its logo.
    private static final String HDMI = "hdmi";
    // What's plugged in here, named under its tile unless the input has a name set in Settings > Inputs.
    private static final Map<String, String> HDMI_NAMES = new HashMap<>();
    static {
        HDMI_NAMES.put("hdmi2", "PlayStation");
        HDMI_NAMES.put("hdmi3", "Jio Set-Top Box");
    }
    private static final String[] EDIT_ITEMS = {"Move", "Replace", "Remove from home", "Uninstall"};
    private static final String[] EDIT_ITEMS_SYSTEM = {"Move", "Replace", "Remove from home", "App info"};
    private static final int PER_ROW = 6, MAX_INPUTS = 4;

    // Settings shortcuts: name, label, icon. Tile keys are QUICK + name.
    private static final String QUICK = "quick:";
    private static final String[] QUICK_NAMES = {"bluetooth", "sound", "picture"};
    private static final String[] QUICK_LABELS = {"Bluetooth", "Sound", "Picture"};
    private static final int[][] QUICK_ICONS = {LineIcon.BLUETOOTH, LineIcon.SOUND, LineIcon.PICTURE};
    private static final String NETWORK = "network";
    private static final int OFFLINE = 0, ETHERNET = 4; // network states; 1 to 3 are Wi-Fi bars
    // TCL keeps its own Picture and Sound panels in a service; Android's Sound page is missing there.
    private static final String TCL_SHOW_WINDOW = "com.tcl.settings.SHOW_WINDOW";

    // Layout is designed at 1920x1080 and scaled to the TV's real UI size by px().
    private static final int SIDE = 112, RADIUS = 22; // clock and top icons sit SIDE in from the edges
    private static final int ICON_TOP = 64, ICON_BUTTON = 80, ICON_SIZE = 38, ICON_GAP = 24; // level with the clock
    // The dock: one row of white logos with no tiles, six cells on screen, sliding sideways for the rest.
    // DOCK_TOP is where its scroller starts on screen; the cells sit CELL_TOP lower, so the glow above
    // them still fits inside it.
    private static final int DOCK_TOP = 545, DOCK_PAD = 84, CELL = 292, CELL_H = 170, CELL_TOP = 257, ON_SCREEN = 6;
    private static final int LOGO_W = 250, LOGO_H = 124, LOGO_AREA = 112 * 112; // logo size limits, at 1x scale
    private static final int GLOW = 1380, GLOW_H = 650, GLOW_Y = 325; // the glow's width, height and centre in the dock
    private static final float FOCUS_SCALE = 1.16f, ICON_FOCUS_SCALE = 1.32f;
    private float scale = 1f;

    // Each dock app's brand colour, for the glow behind it. Anything else glows soft white.
    private static final Map<String, Integer> BRAND = new HashMap<>();
    static {
        BRAND.put("com.netflix.ninja", 0xE50914);
        BRAND.put("com.amazon.amazonvideo.livingroom", 0x1FB6FF);
        BRAND.put("in.startv.hotstar", 0x4F7BFF);
        BRAND.put("com.graymatrix.did", 0x8230C6);
        BRAND.put("com.sonyliv", 0xF5A623);
        BRAND.put("com.apple.atve.androidtv.appletv", 0xE8E8ED);
        BRAND.put("com.google.android.youtube.tv", 0xFF2D2D);
        BRAND.put("com.sand.aircasttv", 0x2FBF71);
        BRAND.put("com.xiaomi.mitv.smartshare", 0x0BB0B8);
        BRAND.put("com.jio.media.stb.ondemand", 0xD6008A);
        BRAND.put("com.android.vending", 0x34A853);
        BRAND.put("hdmi2", 0x0070D1); // PlayStation
        BRAND.put("hdmi3", 0x2A5CFF); // Jio set-top box
    }

    private static Typeface regular, semibold;

    private FrameLayout root; // the page
    private HorizontalScrollView dock; // the dock's sideways scroller
    private FrameLayout dockRow;       // inside it: the glow, then one cell per app
    private View glow;
    private BitmapDrawable glowShape;
    private int glowColor = -1, firstCell; // firstCell: the leftmost cell on screen
    private ValueAnimator glowFade;
    private BitmapDrawable wallpaper; // null when the plain gradient is showing instead
    private final List<View> tiles = new ArrayList<>();
    private boolean dirty = true;
    private int mix; // the --es duotone percentage, read in onCreate
    private java.util.Locale shownLocale; // the TV's language (and region) when the page was built
    private String focusedKey;
    private final List<String> rowApps = new ArrayList<>(); // the top row as shown, in order
    private String movingKey;   // the app being moved with left/right, if any
    private TextView moveHint;  // shown above the apps while moving
    private boolean longPressed; // OK was held: swallow the key-up so the app doesn't open

    // Apps installed, removed or updated. This often lands once Home is already back in front (an app
    // uninstalled from the menu is gone only after Android's prompt has closed), so catch up then.
    private final BroadcastReceiver packagesChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            dirty = true;
            refreshApps();
        }
    };

    // The network tile follows the connection live: Wi-Fi bars, Ethernet or Offline. Only its icon and
    // label change, so nothing is rebuilt.
    private ImageView netIcon;
    private int netState = -1;
    private final ConnectivityManager.NetworkCallback networkChanged = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network n) { runOnUiThread(MainActivity.this::refreshNetwork); }
        @Override public void onLost(Network n) { runOnUiThread(MainActivity.this::refreshNetwork); }
        // Also fires when the Wi-Fi signal gets stronger or weaker.
        @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities c) {
            runOnUiThread(MainActivity.this::refreshNetwork);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        saveConfig(getIntent());
        mix = Variant.DUOTONE ? getSharedPreferences(PREFS, 0).getInt("duotone", 50) : 0;
        loadFonts();
        scale = getResources().getDisplayMetrics().widthPixels / 1920f;
        getWindow().setBackgroundDrawable(background());

        root = new FrameLayout(this);
        root.setClipChildren(false);

        // The time, top left, always 24-hour, in the original home screen's clock font, and the day and
        // date under it (like "Friday · 23 October"), both 90% see-through. The time is centred over the
        // date, so the two line up whatever the day and month are.
        shownLocale = java.util.Locale.getDefault();
        TextClock time = clock("HH:mm", "HH:mm", px(120), Color.WHITE, semibold);
        time.setFontFeatureSettings("tnum");
        time.setIncludeFontPadding(false);
        TextClock date = clock("EEEE '·' d MMMM", "EEEE '·' d MMMM", px(35), Color.WHITE, semibold);
        date.setIncludeFontPadding(false);
        android.widget.LinearLayout when = new android.widget.LinearLayout(this);
        when.setOrientation(android.widget.LinearLayout.VERTICAL);
        when.setAlpha(0.1f);
        time.setGravity(Gravity.CENTER_HORIZONTAL);
        when.addView(time);
        when.addView(date);
        ((android.widget.LinearLayout.LayoutParams) date.getLayoutParams()).topMargin = px(14);
        root.addView(when, at(px(SIDE), px(72), -2, -2));

        // The dock scrolls itself (see showCell), so stop the scroller's own jump to the focused cell.
        dock = new HorizontalScrollView(this) {
            @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) { return 0; }
            // The scroller moves focus itself on Left/Right, which skips Android's focus click; play it here.
            @Override public boolean arrowScroll(int dir) {
                boolean moved = super.arrowScroll(dir);
                if (moved) playSoundEffect(SoundEffectConstants.getContantForFocusDirection(dir));
                return moved;
            }
        };
        dock.setHorizontalScrollBarEnabled(false);
        dock.setOverScrollMode(View.OVER_SCROLL_NEVER);
        dock.setFocusable(false);
        // A logo cut by the screen edge fades out there, hinting that the row goes on.
        dock.setHorizontalFadingEdgeEnabled(true);
        dock.setFadingEdgeLength(px(DOCK_PAD + 80));
        dockRow = new FrameLayout(this);
        dockRow.setClipChildren(false);
        dock.addView(dockRow, new FrameLayout.LayoutParams(-2, -1));
        root.addView(dock, at(0, px(DOCK_TOP), -1, getResources().getDisplayMetrics().heightPixels - px(DOCK_TOP)));

        // A soft light in the focused app's colour behind the logos: an oval that fades out to its edge.
        glowShape = new BitmapDrawable(getResources(), glowMask(px(GLOW), px(GLOW_H), Variant.DUOTONE ? (100 - mix) / 50f : 1));
        glowShape.setTint(0);
        glow = new View(this);
        glow.setBackground(glowShape);
        dockRow.addView(glow, at(0, px(GLOW_Y) - px(GLOW_H) / 2, px(GLOW), px(GLOW_H)));

        moveHint = text(px(24), 0xA8FFFFFF, regular);
        moveHint.setText(Strings.t("Use the arrows to move it, then press OK"));
        moveHint.setVisibility(View.GONE);
        root.addView(moveHint, at(px(SIDE), px(DOCK_TOP + CELL_TOP - 80), -2, -2));

        setContentView(root);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(packagesChanged, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(packagesChanged, f);

        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) try {
            if (android.os.Build.VERSION.SDK_INT >= 24) cm.registerDefaultNetworkCallback(networkChanged);
            else cm.registerNetworkCallback(new NetworkRequest.Builder().build(), networkChanged);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packagesChanged);
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) try { cm.unregisterNetworkCallback(networkChanged); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (saveConfig(intent)) {
            if (Variant.DUOTONE && getSharedPreferences(PREFS, 0).getInt("duotone", 50) != mix) recreate();
            else buildTiles();
        }
    }

    // Saves an app list passed with --es apps "..." and shortcuts passed with --es quick "...".
    // Returns true if it changed.
    // When Android kills the home screen in the background, it can start it again with the same old
    // intent; the stamp install.sh sends stops that from undoing changes made on the TV since.
    private boolean saveConfig(Intent intent) {
        String apps = intent == null ? null : intent.getStringExtra("apps");
        String quick = intent == null ? null : intent.getStringExtra("quick");
        String duotone = intent == null ? null : intent.getStringExtra("duotone");
        if (apps == null && quick == null && duotone == null) return false;
        SharedPreferences prefs = getSharedPreferences(PREFS, 0);
        String stamp = intent.getStringExtra("stamp");
        if (stamp != null && stamp.equals(prefs.getString("stamp", null))) return false;
        SharedPreferences.Editor e = prefs.edit();
        if (stamp != null) e.putString("stamp", stamp);
        if (apps != null) {
            apps = apps.trim();
            if (apps.isEmpty() || apps.equalsIgnoreCase("auto")) e.remove("apps");
            else e.putString("apps", apps);
        }
        if (quick != null) {
            quick = quick.trim();
            if (quick.isEmpty() || quick.equalsIgnoreCase("none")) e.remove("quick");
            else e.putString("quick", quick);
        }
        if (duotone != null) {
            try { e.putInt("duotone", Math.max(0, Math.min(100, Integer.parseInt(duotone.trim())))); }
            catch (NumberFormatException bad) { e.remove("duotone"); } // anything else goes back to 50
        }
        e.apply();
        dirty = true;
        return true;
    }

    private List<String> appList(PackageManager pm) {
        List<String> out = new ArrayList<>();
        String saved = getSharedPreferences(PREFS, 0).getString("apps", null);
        String[] wanted = saved != null ? saved.split("[,\\s]+") : Apps.LIST;
        for (String p : wanted) if (!p.isEmpty() && !out.contains(p) && (p.startsWith(HDMI) ? hdmiInput(p) != null : launchIntent(pm, p) != null)) out.add(p);
        if (saved == null) {
            // Top up with other TV apps people installed (or updated), skipping launchers and system tools.
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                if (out.size() >= PER_ROW) break;
                String p = ri.activityInfo.packageName;
                int flags = ri.activityInfo.applicationInfo.flags;
                boolean stockSystem = (flags & ApplicationInfo.FLAG_SYSTEM) != 0
                        && (flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
                if (stockSystem || out.contains(p) || NOT_APPS.contains(p) || p.equals(getPackageName())) continue;
                out.add(p);
            }
        }
        return out;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Rebuild only when apps were installed, removed or updated.
        if (dirty) {
            dirty = false;
            buildTiles();
        }
        refreshNetwork();
        View f = getCurrentFocus();
        if (f == null && !tiles.isEmpty()) tiles.get(0).requestFocus();
    }

    // Back from a menu or a window on top (like the uninstall prompt): catch up on apps that changed.
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) refreshApps();
    }

    // Rebuilds the tiles if apps changed, but only while Home is in front with no menu open and no
    // app being moved; otherwise the next onResume, focus change or end of the move does it.
    private void refreshApps() {
        if (!dirty || !hasWindowFocus() || movingKey != null) return;
        dirty = false;
        buildTiles();
    }

    // The TV's language changed: start over so the labels and the date follow it.
    @Override
    public void onConfigurationChanged(android.content.res.Configuration c) {
        super.onConfigurationChanged(c);
        if (!java.util.Locale.getDefault().equals(shownLocale)) recreate();
    }

    @Override
    public void onBackPressed() { /* home screen: nothing to go back to */ }

    // The wallpaper (assets/wallpaper.jpg), with black fading in over the lower half so the dock sits on
    // dark. Drawn once by the window: no extra view, no animation. Decoded at screen size in full 32-bit
    // colour, so a 4K image costs no more memory than a 1080p one and dark areas don't band as much.
    private Drawable background() {
        int sw = getResources().getDisplayMetrics().widthPixels, sh = getResources().getDisplayMetrics().heightPixels;
        Bitmap wall = null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        o.inJustDecodeBounds = true;
        try (InputStream in = getAssets().open("wallpaper.jpg")) {
            BitmapFactory.decodeStream(in, null, o);
        } catch (Exception ignored) {}
        o.inSampleSize = 1;
        while (o.outWidth / (o.inSampleSize * 2) >= sw && o.outHeight / (o.inSampleSize * 2) >= sh) o.inSampleSize *= 2;
        o.inJustDecodeBounds = false;
        if (o.outWidth > 0) try (InputStream in = getAssets().open("wallpaper.jpg")) {
            wall = BitmapFactory.decodeStream(in, null, o);
        } catch (Exception | OutOfMemoryError ignored) {}
        if (wall == null) return gradientBackground();
        // ponytail: stretched to the screen, fine for a 16:9 wallpaper on a 16:9 TV; crop if that ever changes
        GradientDrawable shade = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0, 0, 0x59000000, 0xB3000000, 0xD9000000});
        shade.setDither(true);
        wallpaper = new BitmapDrawable(getResources(), wall);
        wallpaper.setColorFilter(blackPoint(new float[] {1,0,0,0,0, 0,1,0,0,0, 0,0,1,0,0, 0,0,0,1,0}));
        return new LayerDrawable(new Drawable[] {wallpaper, shade});
    }

    // The original warm gradient, if the wallpaper can't be loaded.
    private Drawable gradientBackground() {
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xFF15100F, 0xFF0A0809, 0xFF050405});
        GradientDrawable warm = radial(0x36FF8048, 0.08f, -0.12f, px(1250));
        GradientDrawable plum = radial(0x2EAA3C8C, 1.0f, 1.1f, px(1000));
        for (GradientDrawable g : new GradientDrawable[] {base, warm, plum}) g.setDither(true);
        return new LayerDrawable(new Drawable[] {base, warm, plum});
    }

    private static GradientDrawable radial(int color, float cx, float cy, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setColors(new int[] {color, color & 0x00FFFFFF});
        g.setGradientCenter(cx, cy);
        g.setGradientRadius(r);
        return g;
    }

    private void buildTiles() {
        // While tiles are swapped out, removing the focused one would hand focus down the row (and
        // overwrite focusedKey), so hold focus off until the new tiles are in.
        String want = focusedKey;
        int wantAt = rowApps.indexOf(want); // where it was, in case the app is gone now
        root.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        for (View t : tiles) {
            View wrap = (View) t.getParent();
            ((ViewGroup) wrap.getParent()).removeView(wrap);
        }
        tiles.clear();
        rowApps.clear();
        movingKey = null;
        moveHint.setVisibility(View.GONE);

        PackageManager pm = getPackageManager();
        List<String> apps = appList(pm);

        // One row: the apps, then Play Store (if not among them) and Add app. The dock slides
        // sideways once there are more than six.
        for (String pkg : apps) {
            if (pkg.startsWith(HDMI)) {
                rowApps.add(pkg);
                addTile(pkg, labelOf(pkg), logo(pkg), null, logo(pkg) == null ? LineIcon.HDMI : null, rowApps.size() - 1);
                continue;
            }
            Intent li = launchIntent(pm, pkg);
            if (li == null) continue;
            String label = pkg;
            try { label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(); } catch (Exception ignored) {}
            // An app with no white logo in assets/logos shows its TV banner (or icon) small instead.
            Drawable logo = logo(pkg), banner = null;
            if (logo == null) try {
                banner = pm.getActivityBanner(li);
                if (banner == null) banner = pm.getApplicationBanner(pkg);
                if (banner == null) banner = pm.getApplicationIcon(pkg);
            } catch (Exception ignored) {}
            int i = rowApps.size();
            rowApps.add(pkg);
            addTile(pkg, label, logo, banner, null, i);
        }
        int slot = rowApps.size();
        if (!apps.contains(PLAY_STORE) && launchIntent(pm, PLAY_STORE) != null) {
            Drawable logo = logo(PLAY_STORE);
            addTile(PLAY_STORE, "Play Store", logo, null, logo == null ? LineIcon.PLAY : null, slot++);
        }
        addTile(ADD, Strings.t("Add app"), null, null, LineIcon.PLUS, slot++);
        dockRow.setMinimumWidth(2 * px(DOCK_PAD) + slot * px(CELL));
        firstCell = 0;

        // Bare icons at the top right, level with the clock: any settings shortcuts picked, Inputs
        // (when the TV has HDMI), Settings, then the network icon. Added last, so the first app keeps
        // first focus; Up from the dock reaches them.
        List<Integer> quick = quickList();
        boolean hasInputs = !hdmiInputs().isEmpty();
        int n = quick.size() + (hasInputs ? 1 : 0) + 2;
        int ib = px(ICON_BUTTON), ig = px(ICON_GAP);
        // The last icon's right edge sits SIDE in from the screen edge.
        int qx = getResources().getDisplayMetrics().widthPixels - px(SIDE) + (ib - px(ICON_SIZE)) / 2
                - n * ib - (n - 1) * ig;
        for (int q : quick) {
            addIcon(QUICK + QUICK_NAMES[q], Strings.t(QUICK_LABELS[q]), new LineIcon(QUICK_ICONS[q]), qx);
            qx += ib + ig;
        }
        if (hasInputs) {
            addIcon(INPUTS, Strings.t("Inputs"), new LineIcon(LineIcon.HDMI), qx);
            qx += ib + ig;
        }
        addIcon(SETTINGS, Strings.t("Settings"), new LineIcon(LineIcon.SETTINGS), qx);
        qx += ib + ig;
        netIcon = addIcon(NETWORK, Strings.t("Network"), null, qx);
        netState = -1;
        refreshNetwork();

        root.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        // An app that was uninstalled hands focus to the one now in its place, as Remove does.
        if (wantAt >= 0 && !rowApps.contains(want))
            want = rowApps.isEmpty() ? ADD : rowApps.get(Math.min(wantAt, rowApps.size() - 1));
        View restore = null;
        for (View t : tiles) if (t.getTag().equals(want)) restore = t;
        if (restore != null) restore.requestFocus();
    }

    // One dock cell, number i: a white logo floating on the wallpaper, no tile behind it. Without a logo
    // it shows the app's banner small (with rounded corners), or a line icon (Play Store, Add app).
    // Focused, it grows a little and its name fades in underneath; the glow follows it.
    private void addTile(final String key, String label, Drawable logo, Drawable banner, int[] lineIcon, int i) {
        int w = px(CELL), h = px(CELL_H);
        FrameLayout card = new FrameLayout(this);
        final ImageView iv = new ImageView(this);
        FrameLayout.LayoutParams ip;
        if (logo != null) {
            iv.setImageDrawable(logo);
            ip = new FrameLayout.LayoutParams(logo.getIntrinsicWidth(), logo.getIntrinsicHeight(), Gravity.CENTER);
        } else if (banner != null) {
            int bw = px(LOGO_W) * 4 / 5, bh = bw * 9 / 16;
            iv.setImageDrawable(fit(banner, bw, bh));
            GradientDrawable round = new GradientDrawable();
            round.setCornerRadius(px(RADIUS) / 2f);
            iv.setBackground(round);
            iv.setClipToOutline(true);
            ip = new FrameLayout.LayoutParams(bw, bh, Gravity.CENTER);
        } else {
            iv.setImageDrawable(new LineIcon(lineIcon));
            ip = new FrameLayout.LayoutParams(px(72), px(72), Gravity.CENTER);
        }
        iv.setAlpha(0.72f);
        card.addView(iv, ip);
        card.setFocusable(true);
        card.setTag(key);
        card.setContentDescription(label);

        final TextView caption = text(px(30), 0xF2FFFFFF, outfit(500, regular));
        caption.setText(label);
        caption.setSingleLine(true);
        caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
        caption.setGravity(Gravity.CENTER_HORIZONTAL);
        caption.setAlpha(0f);
        caption.setTranslationY(px(6));

        final int color = BRAND.containsKey(key) ? BRAND.get(key) : 0xFFFFFF;
        final DecelerateInterpolator ease = new DecelerateInterpolator(2f);
        card.setOnFocusChangeListener((v, has) -> {
            if (has) {
                focusedKey = key;
                showCell((((FrameLayout.LayoutParams) ((View) v.getParent()).getLayoutParams()).leftMargin
                        - px(DOCK_PAD)) / px(CELL), color);
            }
            v.animate().scaleX(has ? FOCUS_SCALE : 1f).scaleY(has ? FOCUS_SCALE : 1f)
                    .setDuration(240).setInterpolator(ease).withLayer().start();
            iv.animate().alpha(has ? 1f : 0.72f).setDuration(240).start();
            caption.animate().alpha(has ? 1f : 0f).translationY(has ? 0 : px(6))
                    .setDuration(has ? 220 : 120).setInterpolator(ease).start();
        });
        final boolean editable = rowApps.contains(key);
        card.setOnClickListener(v -> open(key));
        if (editable) card.setOnLongClickListener(v -> { showEditMenu(key); return true; });
        card.setOnKeyListener((v, code, ev) -> {
            boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                    || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
            boolean down = ev.getAction() == KeyEvent.ACTION_DOWN;
            if (key.equals(movingKey)) return moveKey(key, code, ok, down);
            if (editable && code == KeyEvent.KEYCODE_MENU) {
                if (!down) showEditMenu(key);
                return true;
            }
            if (!ok) return false;
            if (down) {
                if (ev.getRepeatCount() == 0) longPressed = false;
                else if (editable && !longPressed && (ev.isLongPress()
                        || ev.getEventTime() - ev.getDownTime() >= ViewConfiguration.getLongPressTimeout())) {
                    longPressed = true;
                    showEditMenu(key);
                }
            } else if (!longPressed) {
                open(key);
            }
            return true;
        });

        FrameLayout wrap = new FrameLayout(this);
        wrap.setClipChildren(false);
        wrap.addView(card, new FrameLayout.LayoutParams(w, h));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(w, -2);
        cp.topMargin = h + px(12);
        wrap.addView(caption, cp);
        dockRow.addView(wrap, at(gridX(i), gridY(i), w, -2));
        tiles.add(card);
    }

    // The dock follows focus: the glow slides to cell i and takes its colour, and the row slides
    // sideways just enough to keep cell i among the six on screen.
    private void showCell(int i, int color) {
        if (i < firstCell) firstCell = i;
        else if (i >= firstCell + ON_SCREEN) firstCell = i - ON_SCREEN + 1;
        final int x = firstCell * px(CELL);
        dock.post(() -> dock.smoothScrollTo(x, 0));
        DecelerateInterpolator ease = new DecelerateInterpolator(2f);
        glow.animate().translationX(gridX(i) + px(CELL) / 2f - px(GLOW) / 2f).alpha(1f)
                .setDuration(glowColor < 0 ? 0 : 420).setInterpolator(ease).start();
        if (color == glowColor) return;
        if (glowFade != null) glowFade.cancel();
        final int from = glowColor < 0 ? color : glowColor;
        glowColor = color;
        glowFade = ValueAnimator.ofObject(new ArgbEvaluator(), from, color);
        glowFade.setDuration(420);
        glowFade.addUpdateListener(a -> {
            int c = (Integer) a.getAnimatedValue() & 0xFFFFFF;
            glowShape.setTint(0xFF000000 | c);
            if (Variant.DUOTONE && wallpaper != null) wallpaper.setColorFilter(duotone(c));
        });
        glowFade.start();
    }

    // The glow's shape: 24% in the middle, 6% halfway out, clear at the edge (times strength). A plain gradient this faint
    // is drawn in 1-level steps that show as rings when the TV is seen from the side (the TV's GPU ignores
    // dither), so each pixel gets a little noise to break the steps up. Built once; tinting it is free.
    private static Bitmap glowMask(int w, int h, float strength) {
        byte[] a = new byte[w * h];
        java.util.Random noise = new java.util.Random(1);
        float rx = w / 2f, ry = h / 2f;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            float dx = (x + 0.5f - rx) / rx, dy = (y + 0.5f - ry) / ry, r = (float) Math.sqrt(dx * dx + dy * dy);
            float v = strength * (r < 0.5f ? 61 - 46 * r / 0.5f : r < 1 ? 15 * (1 - r) / 0.5f : 0);
            if (v > 0) a[y * w + x] = (byte) Math.min(255, (int) (v + noise.nextFloat()));
        }
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8);
        b.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(a));
        return b;
    }

    // Home v3: tints the black-and-white wallpaper in an app's colour (45% at an even mix, 90% at Duotone
    // only), keeping its light and dark
    // (like a "color" blend). One colour filter on the image the window already draws, so it costs nothing.
    private android.graphics.ColorMatrixColorFilter duotone(int rgb) {
        float r = (rgb >> 16 & 0xFF) / 255f, g = (rgb >> 8 & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float lum = Math.max(0.05f, 0.299f * r + 0.587f * g + 0.114f * b), s = 0.9f * mix / 100;
        // A dark colour like Netflix red needs a big push to keep the brightness, and that burns the cowl
        // out to flat red while the chest stays dark, so the figure seems to jump up. Capping the push
        // keeps every colour's highlights clear; dark colours just tint a little darker.
        float push = Math.min(1f, 2f / (Math.max(r, Math.max(g, b)) / lum));
        float[] k = {r / lum * push, g / lum * push, b / lum * push}, m = new float[20];
        for (int row = 0; row < 3; row++) {
            m[row * 5] = s * k[row] * 0.299f;
            m[row * 5 + 1] = s * k[row] * 0.587f;
            m[row * 5 + 2] = s * k[row] * 0.114f;
            m[row * 5 + row] += 1 - s; // the rest stays the original colour
        }
        m[18] = 1; // alpha unchanged
        return blackPoint(m);
    }

    // The wallpaper has a faint grey haze around Batman (levels 4-20 of 255) that the TV lifts into
    // visible faded colour. Everything that dark becomes true black; the rest stretches back to full range.
    private static android.graphics.ColorMatrixColorFilter blackPoint(float[] m) {
        float cut = 20f, gain = 255f / (255f - cut);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 5; col++) m[row * 5 + col] *= gain;
            m[row * 5 + 4] -= cut * gain;
        }
        return new android.graphics.ColorMatrixColorFilter(m);
    }

    // A bare icon, no tile, label or circle: it grows a little while focused. The dock glow dims then.
    private ImageView addIcon(final String key, String label, Drawable icon, int x) {
        int b = px(ICON_BUTTON), size = px(ICON_SIZE);
        FrameLayout card = new FrameLayout(this);
        final ImageView iv = new ImageView(this);
        iv.setImageDrawable(icon);
        iv.setAlpha(0.85f);
        card.addView(iv, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        card.setFocusable(true);
        card.setTag(key);
        card.setContentDescription(label);
        final DecelerateInterpolator ease = new DecelerateInterpolator(2f);
        card.setOnFocusChangeListener((v, has) -> {
            if (has) {
                focusedKey = key;
                glow.animate().alpha(0.45f).setDuration(420).start();
            }
            iv.animate().alpha(has ? 1f : 0.85f).setDuration(220).start();
            v.animate().scaleX(has ? ICON_FOCUS_SCALE : 1f).scaleY(has ? ICON_FOCUS_SCALE : 1f)
                    .setDuration(220).setInterpolator(ease).withLayer().start();
        });
        card.setOnClickListener(v -> open(key));
        FrameLayout wrap = new FrameLayout(this);
        wrap.setClipChildren(false);
        wrap.addView(card, new FrameLayout.LayoutParams(b, b));
        root.addView(wrap, at(x, px(ICON_TOP), b, b));
        tiles.add(card);
        return iv;
    }

    private void open(String key) {
        Intent i;
        if (ADD.equals(key)) {
            showAddMenu(-1);
            return;
        } else if (SETTINGS.equals(key)) {
            i = new Intent(Settings.ACTION_SETTINGS);
        } else if (INPUTS.equals(key)) {
            showInputs();
            return;
        } else if (NETWORK.equals(key)) {
            openNetworkSettings();
            return;
        } else if (key.startsWith(HDMI)) {
            TvInputInfo in = hdmiInput(key);
            if (in != null) openInput(in.getId());
            return;
        } else if (key.startsWith(QUICK)) {
            openQuick(key.substring(QUICK.length()));
            return;
        } else {
            i = launchIntent(getPackageManager(), key);
            if (i == null) return;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception ignored) {}
    }

    // Live TV apps an HDMI tile can open, in order. Droidlogic (Amlogic) TVs ship Live TV under their
    // own package, with TvActivity as its entry point.
    private static final String[][] LIVE_TV = {{"com.android.tv", "com.android.tv.MainActivity"},
            {"com.droidlogic.android.tv", "com.android.tv.TvActivity"}};
    private static final String INPUT_PLAYER = "com.google.android.tv.inputplayer";

    // Opens an HDMI input: the stock Live TV app first, then Droidlogic's, then whatever the TV picks.
    // Each one is tried only when the one before it can't start, so a TV where the stock app already
    // worked takes exactly the same path as before. The one exception: when the TV itself sends HDMI to
    // Google TV's input player, that goes first, because some Google TVs ship com.android.tv as a stub
    // that starts fine but only says "Hello World!" (issue #5).
    private void openInput(String id) {
        Intent view = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id));
        List<Intent> tries = new ArrayList<>();
        ResolveInfo def = getPackageManager().resolveActivity(view, PackageManager.MATCH_DEFAULT_ONLY);
        if (def != null && def.activityInfo != null && INPUT_PLAYER.equals(def.activityInfo.packageName))
            tries.add(new Intent(view).setClassName(INPUT_PLAYER, def.activityInfo.name));
        for (String[] live : LIVE_TV) {
            Intent t = new Intent(view).setClassName(live[0], live[1]);
            if (t.resolveActivity(getPackageManager()) != null) tries.add(t);
        }
        tries.add(view);
        for (Intent t : tries) {
            t.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(t);
                return;
            } catch (Exception ignored) {}
        }
    }

    // The Inputs icon: a side panel with the TV's HDMI inputs, by the names set in Settings > Inputs.
    private void showInputs() {
        final List<TvInputInfo> inputs = hdmiInputs();
        List<String> labels = new ArrayList<>();
        for (TvInputInfo in : inputs) labels.add(inputLabel(in));
        panel(Strings.t("Inputs"), labels, null, which -> openInput(inputs.get(which).getId()));
    }

    // The shortcuts picked with --es quick, in that order, as indexes into QUICK_NAMES. A shortcut
    // this TV has no screen for is left out, so it never shows a tile that does nothing.
    private List<Integer> quickList() {
        List<Integer> out = new ArrayList<>();
        String saved = getSharedPreferences(PREFS, 0).getString("quick", null);
        if (saved == null) return out;
        for (String name : saved.split("[,\\s]+")) {
            int q = Arrays.asList(QUICK_NAMES).indexOf(name.toLowerCase(java.util.Locale.ROOT));
            if (q < 0 || out.contains(q) || quickIntents(QUICK_NAMES[q]).isEmpty()) continue;
            out.add(q);
        }
        return out;
    }

    // OFFLINE, 1 to 3 for Wi-Fi with that many bars, or ETHERNET (any other link, which on a TV is a cable).
    private int networkState() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return OFFLINE;
        try {
            int rssi = Integer.MIN_VALUE;
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                NetworkCapabilities c = cm.getNetworkCapabilities(cm.getActiveNetwork());
                if (c == null) return OFFLINE;
                if (!c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return ETHERNET;
                if (android.os.Build.VERSION.SDK_INT >= 29) rssi = c.getSignalStrength();
            } else {
                android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
                if (ni == null || !ni.isConnected()) return OFFLINE;
                if (ni.getType() != ConnectivityManager.TYPE_WIFI) return ETHERNET;
            }
            if (rssi == Integer.MIN_VALUE) { // Android 9 and older: ask the Wi-Fi service
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null && wm.getConnectionInfo() != null) rssi = wm.getConnectionInfo().getRssi();
            }
            return 1 + WifiManager.calculateSignalLevel(rssi, 3);
        } catch (Exception e) {
            return OFFLINE;
        }
    }

    private void refreshNetwork() {
        if (netIcon == null) return;
        int state = networkState();
        if (state == netState) return;
        netState = state;
        if (state == ETHERNET) {
            netIcon.setImageDrawable(new LineIcon(LineIcon.ETHERNET));
        } else {
            // The whole Wi-Fi fan dim, with the bars it has bright on top. Offline is a slash instead.
            LineIcon fan = new LineIcon(LineIcon.WIFI);
            fan.setAlpha(0x47);
            int[] top = state == OFFLINE ? LineIcon.SLASH : state == 1 ? LineIcon.WIFI_1
                    : state == 2 ? LineIcon.WIFI_2 : LineIcon.WIFI;
            netIcon.setImageDrawable(new LayerDrawable(new Drawable[] {fan, new LineIcon(top)}));
        }
        ((View) netIcon.getParent()).setContentDescription(
                state == OFFLINE ? Strings.t("Offline") : state == ETHERNET ? "Ethernet" : "Wi-Fi");
    }

    // Wi-Fi settings (on Android TV that page lists Ethernet too), else the TV's network page, else Settings.
    private void openNetworkSettings() {
        for (String a : new String[] {Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS,
                Settings.ACTION_SETTINGS}) {
            try {
                startActivity(new Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception ignored) {}
        }
    }

    // Screens a shortcut can open, best first, keeping only those this TV has.
    private List<Intent> quickIntents(String name) {
        List<Intent> all = new ArrayList<>();
        String tclType = name.equals("sound") ? "SoundPreset" : name.equals("picture") ? "picture" : null;
        if (tclType != null)
            all.add(new Intent(TCL_SHOW_WINDOW).setClassName("com.tcl.settings", "com.tcl.settings.ShowWindowService")
                    .putExtra("Type", tclType));
        switch (name) {
            case "bluetooth":
                all.add(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                all.add(new Intent("com.google.android.intent.action.CONNECT_INPUT")); // Android TV's pairing screen
                break;
            case "sound": all.add(new Intent(Settings.ACTION_SOUND_SETTINGS)); break;
            case "picture": all.add(new Intent(Settings.ACTION_DISPLAY_SETTINGS)); break;
        }
        PackageManager pm = getPackageManager();
        List<Intent> out = new ArrayList<>();
        for (Intent i : all) {
            boolean service = TCL_SHOW_WINDOW.equals(i.getAction());
            if (service ? pm.resolveService(i, 0) != null : i.resolveActivity(pm) != null) out.add(i);
        }
        return out;
    }

    // Opens a shortcut's screen: the first one that starts wins.
    private void openQuick(String name) {
        for (Intent i : quickIntents(name)) {
            try {
                if (TCL_SHOW_WINDOW.equals(i.getAction())) startService(i);
                else startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception ignored) {}
        }
    }

    interface Pick { void at(int which); }

    // A side panel in the home screen's own look: a title, then one row per choice, with the same
    // glass focus as the tiles. Back closes it. iconPkgs (or null) puts each app's icon by its name.
    private void panel(String title, final List<String> labels, final List<String> iconPkgs, final Pick pick) {
        final Dialog d = new Dialog(this, android.R.style.Theme_Material_Dialog_NoActionBar);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(px(48), px(92), px(48), px(48));

        TextView head = text(px(40), Color.WHITE, semibold);
        head.setText(title);
        head.setSingleLine(true);
        head.setEllipsize(android.text.TextUtils.TruncateAt.END);
        head.setPadding(px(24), 0, px(24), 0);
        box.addView(head);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        GradientDrawable focus = new GradientDrawable();
        focus.setCornerRadius(px(RADIUS));
        focus.setColor(0x38FFFFFF);
        list.setSelector(focus);
        // Icons load as rows appear, drawn small to save RAM.
        final PackageManager pm = getPackageManager();
        final int size = px(48);
        final Drawable[] icons = new Drawable[labels.size()];
        list.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return labels.size(); }
            @Override public Object getItem(int pos) { return labels.get(pos); }
            @Override public long getItemId(int pos) { return pos; }
            @Override public View getView(int pos, View convert, ViewGroup parent) {
                TextView t = convert != null ? (TextView) convert : text(px(30), 0xE6FFFFFF, regular);
                t.setText(labels.get(pos));
                t.setSingleLine(true);
                t.setEllipsize(android.text.TextUtils.TruncateAt.END);
                t.setGravity(Gravity.CENTER_VERTICAL);
                t.setMinHeight(px(88));
                t.setPadding(px(24), 0, px(24), 0);
                Drawable icon = null;
                if (iconPkgs != null) {
                    if (icons[pos] == null) {
                        try { icons[pos] = fit(pm.getApplicationIcon(iconPkgs.get(pos)), size, size); }
                        catch (Exception e) { icons[pos] = new ColorDrawable(Color.TRANSPARENT); }
                        icons[pos].setBounds(0, 0, size, size);
                    }
                    icon = icons[pos];
                }
                t.setCompoundDrawablesRelative(icon, null, null, null);
                t.setCompoundDrawablePadding(px(24));
                return t;
            }
        });
        list.setOnItemClickListener((parent, v, pos, id) -> {
            d.dismiss();
            pick.at(pos);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1f);
        lp.topMargin = px(36);
        box.addView(list, lp);

        d.setContentView(box);
        Window w = d.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(0xF7161113));
        w.setLayout(px(640), -1);
        w.setGravity(Gravity.END);
        w.setDimAmount(0.5f);
        d.show();
    }

    // A small menu that drops down under a tile, in the same glass as the tiles. Opens above the
    // tile when there's no room below. Back closes it.
    private void dropdown(View anchor, String[] items, final Pick pick) {
        final Dialog d = new Dialog(this, android.R.style.Theme_Material_Dialog_NoActionBar);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = px(10), rowH = px(64);
        box.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(RADIUS));
        bg.setColor(0xFF231D1F);
        bg.setStroke(Math.max(1, px(2)), 0x17FFFFFF);
        box.setBackground(bg);
        float widest = 0; // some languages need more room than the tile is wide
        for (int i = 0; i < items.length; i++) {
            final int which = i;
            final TextView t = text(px(26), 0xD9FFFFFF, regular);
            t.setText(Strings.t(items[i]));
            t.setSingleLine(true);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setPadding(px(22), 0, px(22), 0);
            widest = Math.max(widest, t.getPaint().measureText(t.getText().toString()) + 2 * px(22) + 2 * pad);
            final GradientDrawable focus = new GradientDrawable();
            focus.setCornerRadius(px(RADIUS - pad));
            focus.setColor(Color.TRANSPARENT);
            t.setBackground(focus);
            t.setFocusable(true);
            t.setOnFocusChangeListener((v, has) -> {
                focus.setColor(has ? 0x38FFFFFF : Color.TRANSPARENT);
                t.setTextColor(has ? Color.WHITE : 0xD9FFFFFF);
            });
            t.setOnClickListener(v -> {
                d.dismiss();
                pick.at(which);
            });
            box.addView(t, new LinearLayout.LayoutParams(-1, rowH));
        }
        d.setContentView(box);

        // Line it up with the tile as drawn (focused tiles are scaled up), below its name.
        int[] at = new int[2];
        anchor.getLocationOnScreen(at);
        int tileBottom = at[1] + Math.round(anchor.getHeight() * anchor.getScaleY());
        int w = Math.max(Math.max(Math.round(anchor.getWidth() * anchor.getScaleX()), px(320)), (int) Math.ceil(widest));
        int h = items.length * rowH + 2 * pad;
        int y = tileBottom + px(64);
        if (y + h > getResources().getDisplayMetrics().heightPixels - px(24)) y = at[1] - h - px(16);
        Window win = d.getWindow();
        win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        win.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        win.setGravity(Gravity.TOP | Gravity.START);
        android.view.WindowManager.LayoutParams lp = win.getAttributes();
        lp.x = at[0];
        lp.y = y;
        lp.width = w;
        lp.height = h;
        win.setAttributes(lp);
        d.show();
    }

    // Hold OK (or Menu) on a top-row app: move it, swap it for another, or take it off the home
    // screen. Removing only takes the tile away: the app stays installed and can be added back.
    // Apps the user installed can also be uninstalled; Android asks to confirm, and the tile goes
    // once the app is gone. Apps that came with the TV can't be, so they get App info instead,
    // where Android offers to turn them off.
    private void showEditMenu(final String key) {
        final int index = rowApps.indexOf(key);
        if (index < 0) return;
        final boolean user = userInstalled(key);
        dropdown(tile(key), user ? EDIT_ITEMS : EDIT_ITEMS_SYSTEM, which -> {
            if (which == 0) startMove(key);
            else if (which == 1) showAddMenu(index);
            else if (which == 3) {
                Uri pkg = Uri.parse("package:" + key);
                try { startActivity(user ? new Intent(Intent.ACTION_DELETE, pkg)
                        : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)); }
                catch (Exception ignored) {}
            } else {
                rowApps.remove(index);
                saveRow();
                focusedKey = rowApps.isEmpty() ? ADD : rowApps.get(Math.min(index, rowApps.size() - 1));
                buildTiles();
            }
        });
    }

    // Every launchable app that isn't on the top row yet, by name. The pick is added at the end,
    // or takes the place of the app at replaceIndex (-1 to add).
    private void showAddMenu(final int replaceIndex) {
        final PackageManager pm = getPackageManager();
        TreeMap<String, String> byLabel = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String category : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String p = ri.activityInfo.packageName;
                if (rowApps.contains(p) || byLabel.containsValue(p) || p.equals(getPackageName())
                        || (NOT_APPS.contains(p) && !p.equals(PLAY_STORE))) continue;
                String label = labelOf(p);
                if (!byLabel.containsKey(label)) byLabel.put(label, p);
            }
        }
        if (byLabel.isEmpty()) {
            panel(Strings.t("Every app is already here"), Collections.singletonList("OK"), null, which -> {});
            return;
        }
        final List<String> pkgs = new ArrayList<>(byLabel.values());
        String title = replaceIndex < 0 ? Strings.t("Add an app")
                : String.format(Strings.t("Replace %s"), labelOf(rowApps.get(replaceIndex)));
        panel(title, new ArrayList<>(byLabel.keySet()), pkgs, which -> {
            if (replaceIndex < 0) rowApps.add(pkgs.get(which));
            else rowApps.set(replaceIndex, pkgs.get(which));
            saveRow();
            focusedKey = pkgs.get(which);
            buildTiles();
        });
    }

    private String labelOf(String pkg) {
        if (pkg.startsWith(HDMI)) {
            TvInputInfo in = hdmiInput(pkg);
            CharSequence custom = in == null ? null : in.loadCustomLabel(this);
            if (custom != null && custom.length() > 0) return custom.toString();
            return HDMI_NAMES.containsKey(pkg) ? HDMI_NAMES.get(pkg) : in == null ? pkg : inputLabel(in);
        }
        PackageManager pm = getPackageManager();
        try { return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(); }
        catch (Exception e) { return pkg; }
    }

    // Saves the top row as the app list, so it no longer follows apps.txt.
    private void saveRow() {
        getSharedPreferences(PREFS, 0).edit().putString("apps", android.text.TextUtils.join(",", rowApps)).apply();
    }

    // Where cell number i sits in the dock.
    private int gridX(int i) {
        return px(DOCK_PAD) + i * px(CELL);
    }

    private int gridY(int i) {
        return px(CELL_TOP);
    }

    private View tile(String key) {
        for (View t : tiles) if (t.getTag().equals(key)) return t;
        return null;
    }

    // Move mode: the tile gets an outline, Left and Right move it along the dock, OK or Back finishes.
    private void startMove(String key) {
        View card = tile(key);
        if (card == null) return;
        movingKey = key;
        GradientDrawable outline = new GradientDrawable();
        outline.setCornerRadius(px(RADIUS));
        outline.setStroke(px(4), Color.WHITE);
        ((FrameLayout) card).setForeground(outline);
        moveHint.setVisibility(View.VISIBLE);
        card.requestFocus();
    }

    private boolean moveKey(String key, int code, boolean ok, boolean down) {
        int step = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
        if (step != 0) {
            int from = rowApps.indexOf(key), to = from + step;
            if (down && to >= 0 && to < rowApps.size()) {
                // Slide the tiles into their new places: no rebuild, so it stays quick on slow TVs.
                rowApps.add(to, rowApps.remove(from));
                for (int i = Math.min(from, to); i <= Math.max(from, to); i++) {
                    View wrap = (View) tile(rowApps.get(i)).getParent();
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) wrap.getLayoutParams();
                    lp.leftMargin = gridX(i);
                    lp.topMargin = gridY(i);
                    wrap.setLayoutParams(lp);
                }
                // The dock and the glow go along with it.
                showCell(to, BRAND.containsKey(key) ? BRAND.get(key) : 0xFFFFFF);
            }
            return true;
        }
        if (ok || code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU) {
            if (!down) {
                movingKey = null;
                longPressed = false;
                ((FrameLayout) tile(key)).setForeground(null);
                moveHint.setVisibility(View.GONE);
                saveRow();
                refreshApps();
            }
            return true;
        }
        return true; // up/down and everything else stay put while moving
    }

    // The TV's own HDMI inputs (not devices behind them), in order, skipping hidden ones.
    private List<TvInputInfo> hdmiInputs() {
        List<TvInputInfo> out = new ArrayList<>();
        TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return out;
        for (TvInputInfo in : tim.getTvInputList()) {
            if (in.getType() == TvInputInfo.TYPE_HDMI && in.getParentId() == null && !in.isHidden(this)) out.add(in);
        }
        Collections.sort(out, (a, b) -> a.getId().compareTo(b.getId()));
        return out.size() > MAX_INPUTS ? out.subList(0, MAX_INPUTS) : out;
    }

    // The HDMI input a dock entry like "hdmi2" means: the second one, or null.
    private TvInputInfo hdmiInput(String key) {
        List<TvInputInfo> inputs = hdmiInputs();
        try {
            int n = Integer.parseInt(key.substring(HDMI.length()));
            return n >= 1 && n <= inputs.size() ? inputs.get(n - 1) : null;
        } catch (NumberFormatException e) { return null; }
    }

    // The name set in Settings > Inputs (like "Bedroom"), else the TV's own name (like "HDMI 1").
    private String inputLabel(TvInputInfo in) {
        CharSequence custom = in.loadCustomLabel(this);
        if (custom != null && custom.length() > 0) return custom.toString();
        CharSequence label = in.loadLabel(this);
        return label != null && label.length() > 0 ? label.toString() : "HDMI";
    }

    private boolean userInstalled(String pkg) {
        try { return (getPackageManager().getApplicationInfo(pkg, 0).flags & ApplicationInfo.FLAG_SYSTEM) == 0; }
        catch (PackageManager.NameNotFoundException e) { return false; }
    }

    private static Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }

    // Xiaomi TVs ship MiLan Pro but don't register it by name; every other TV gets Roboto.
    private static void loadFonts() {
        if (regular != null) return;
        regular = fromFile("/system/fonts/MILanPro_Normal.ttf", Typeface.create("sans-serif", Typeface.NORMAL));
        semibold = fromFile("/system/fonts/MILanPro_SemiBold.ttf", Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    private static Typeface fromFile(String path, Typeface fallback) {
        try { return new File(path).exists() ? Typeface.createFromFile(path) : fallback; }
        catch (Exception e) { return fallback; }
    }

    // Outfit (assets/fonts, SIL Open Font License) at one weight. It's a single variable font, so the
    // weight is picked when it's loaded. Android 7 and older get the fallback.
    private final Map<Integer, Typeface> outfits = new HashMap<>();
    private Typeface outfit(int weight, Typeface fallback) {
        if (outfits.containsKey(weight)) return outfits.get(weight);
        Typeface t = null;
        if (android.os.Build.VERSION.SDK_INT >= 26) try {
            t = new Typeface.Builder(getAssets(), "fonts/Outfit.ttf").setFontVariationSettings("'wght' " + weight).build();
        } catch (Exception ignored) {}
        if (t == null) t = fallback;
        outfits.put(weight, t);
        return t;
    }

    // An app's white logo from assets/logos/<package>.png, or null. Sized by visual weight: every logo
    // covers about the same area, so a thin "N" and a wide wordmark look equally big. Never stretched.
    private Drawable logo(String pkg) {
        Bitmap b;
        try (InputStream in = getAssets().open("logos/" + pkg + ".png")) { b = BitmapFactory.decodeStream(in); }
        catch (Exception e) { return null; }
        if (b == null) return null;
        float r = (float) b.getWidth() / b.getHeight();
        float h = (float) Math.sqrt(LOGO_AREA / r), w = h * r;
        if (w > LOGO_W) { w = LOGO_W; h = w / r; }
        if (h > LOGO_H) { h = LOGO_H; w = h * r; }
        Bitmap s = Bitmap.createScaledBitmap(b, Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)), true);
        if (s != b) b.recycle();
        s.setDensity(Bitmap.DENSITY_NONE); // drawn at exactly this size
        return new BitmapDrawable(getResources(), s);
    }

    // Draws a banner once into a tile-sized bitmap (center-cropped). Some apps ship huge banners
    // that Android 9 refuses to draw ("Canvas: trying to draw too large bitmap"); this also saves RAM.
    private Drawable fit(Drawable d, int w, int h) {
        try {
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
            if (iw <= 0 || ih <= 0) { iw = w; ih = h; }
            float s = Math.max((float) w / iw, (float) h / ih);
            int dw = Math.round(iw * s), dh = Math.round(ih * s);
            d.setBounds((w - dw) / 2, (h - dh) / 2, (w - dw) / 2 + dw, (h - dh) / 2 + dh);
            d.draw(new Canvas(b));
            return new BitmapDrawable(getResources(), b);
        } catch (Throwable t) {
            return d;
        }
    }

    private int px(int v) {
        return Math.round(v * scale);
    }

    private TextClock clock(String f12, String f24, int px, int color, Typeface tf) {
        TextClock c = new TextClock(this);
        c.setFormat12Hour(f12);
        c.setFormat24Hour(f24);
        c.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        c.setTextColor(color);
        c.setTypeface(tf);
        return c;
    }

    private TextView text(int px, int color, Typeface tf) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        t.setTextColor(color);
        if (tf != null) t.setTypeface(tf);
        return t;
    }

    private static FrameLayout.LayoutParams at(int x, int y, int w, int h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.leftMargin = x;
        lp.topMargin = y;
        return lp;
    }
}
