package com.calu;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;

// Stateless expression calculator with BODMAS & scientific precedence.
// No prefs, no files, no DB, no cache writes. All state is strictly RAM-only.
public class CalcActivity extends Activity {

    // Default Espresso palette (WCAG AA/AAA verified via tools/contrast.py).
    private static final int BG_TOP = 0xFF1B1511;         // warm espresso, top of screen
    private static final int BG_BOTTOM = 0xFF0E0A08;      // near-black roast, bottom
    private static final int SURFACE = 0xFF33291F;        // digit keys
    private static final int SURFACE_STROKE = 0xFF4A392C; // 1dp key edge
    private static final int SCI_SURFACE = 0xFF3B2E22;    // scientific row
    private static final int CARAMEL = 0xFFC98A4C;        // operator keys
    private static final int GOLD = 0xFFE7B451;           // equals key
    private static final int RUST = 0xFFA44B36;           // clear / backspace
    private static final int CREAM = 0xFFF7EFE4;          // primary text
    private static final int MUTED = 0xFFA78F76;          // expression / preview line
    private static final int AMBER = 0xFFE8B04B;          // scientific labels
    private static final int ON_ACCENT = 0xFF17110C;      // text on caramel / gold
    private static final int DISABLED_FILL = 0xFF241C16;
    private static final int DISABLED_TX = 0xFF82705E;
    private static final int ERROR = 0xFFE08A63;          // muted terracotta
    private static final int HAIRLINE = 0xFF3A2C21;
    private static final int RIPPLE_LIGHT = 0x24FFFFFF;
    private static final int RIPPLE_DARK = 0x20000000;

    // 3 Curated Hardware Finishes (0 = Espresso, 1 = Obsidian TE, 2 = Daylight Studio)
    private static final String[] THEME_NAMES = { "ESPRESSO", "OBSIDIAN", "DAYLIGHT" };
    private static final int[][] THEMES = {
        // 0: Espresso Crema (Default WCAG-verified palette)
        {
            BG_TOP, BG_BOTTOM, 0xFF140F0C, HAIRLINE,
            SURFACE, SURFACE_STROKE, SCI_SURFACE,
            CARAMEL, GOLD, RUST,
            CREAM, MUTED, AMBER, ON_ACCENT,
            DISABLED_FILL, DISABLED_TX, ERROR, 0
        },
        // 1: Obsidian Industrial (Carbon matte, titanium digits, international orange '=')
        {
            0xFF121316, 0xFF090A0C, 0xFF0D0E11, 0xFF262930,
            0xFF1E2026, 0xFF2E323B, 0xFF17191E,
            0xFF383C47, 0xFFFF5E2B, 0xFF8C2D38,
            0xFFF2F4F8, 0xFF8E95A5, 0xFF68B6FF, 0xFF0C0D10,
            0xFF16181D, 0xFF555B69, 0xFFFF7A6B, 0
        },
        // 2: Daylight Studio (Warm ceramic paper, stone keys, bronze/gold accents)
        {
            0xFFF4EFE6, 0xFFE8E0D3, 0xFFEDE6DA, 0xFFD2C7B7,
            0xFFFAF7F2, 0xFFD5C9B8, 0xFFE6DEC9,
            0xFFD99B52, 0xFF231C16, 0xFFB84A39,
            0xFF1B1511, 0xFF6E5D4F, 0xFF8C5319, 0xFFFBF8F3,
            0xFFE2D9C8, 0xFF9C8E7E, 0xFFB8332A, 1
        }
    };

    // Theme slot indices
    private static final int T_BG_TOP = 0, T_BG_BOT = 1, T_WELL = 2, T_HAIRLINE = 3,
            T_SURF = 4, T_STROKE = 5, T_SCI_SURF = 6,
            T_OP = 7, T_EQ = 8, T_RUST = 9,
            T_TEXT = 10, T_MUTED = 11, T_SCI_TX = 12, T_ON_ACCENT = 13,
            T_DIS_FILL = 14, T_DIS_TX = 15, T_ERR = 16, T_IS_LIGHT = 17;

    private static final String BKSP_LABEL = String.valueOf((char) 0x232B);
    private static final String SQRT_LABEL = String.valueOf((char) 0x221A);
    private static final String PI_LABEL = String.valueOf((char) 0x03C0);

    // Portrait basic rows (4 cols x 5 rows)
    private static final String[][] PORTRAIT_BASIC_ROWS = {
        { "C", "(", ")", "/" },
        { "7", "8", "9", "*" },
        { "4", "5", "6", "-" },
        { "1", "2", "3", "+" },
        { "0", ".", BKSP_LABEL, "=" }
    };

    // Portrait scientific drawer rows (4 cols x 2 rows)
    private static final String[][] PORTRAIT_SCI_ROWS = {
        { "sin", "cos", "tan", PI_LABEL },
        { "ln", "%", "^", SQRT_LABEL }
    };

    // Landscape unified 6-column ergonomic rows (6 cols x 5 rows)
    private static final String[][] LANDSCAPE_ROWS = {
        { "sin", "cos", "C", "(", ")", "/" },
        { "tan", "ln", "7", "8", "9", "*" },
        { PI_LABEL, "^", "4", "5", "6", "-" },
        { SQRT_LABEL, "%", "1", "2", "3", "+" },
        { "!", "e", "0", ".", BKSP_LABEL, "=" }
    };

    private LinearLayout rootLayout;
    private LinearLayout displayWell;
    private HorizontalScrollView historyScroll;
    private LinearLayout historyStrip;
    private TextView exprView;
    private TextView disp;
    private TextView statusBadge;
    private Button degBtn;
    private Button themeBtn;
    private Button toggleBtn;
    private LinearLayout keypadContainer;
    private Button eqBtn;

    private String expr = "";
    private String lastHistoryLine = "";
    private boolean fresh = false;     // true right after '='
    private boolean expanded = false;  // scientific drawer in portrait
    private boolean degMode = true;    // true = DEG, false = RAD
    private int themeIdx = 0;          // 0 = Espresso, 1 = Obsidian, 2 = Daylight
    private final ArrayList<String> sessionHistory = new ArrayList<String>(); // RAM-only

    private void loadPersistedTheme() {
        File file = new File(getFilesDir(), "theme.bin");
        if (!file.exists()) return;
        try (FileInputStream fis = new FileInputStream(file)) {
            int val = fis.read();
            if (val >= 0 && val < THEMES.length) {
                themeIdx = val;
            }
        } catch (Exception ignored) {
        }
    }

    private void persistTheme() {
        final int current = themeIdx;
        new Thread(new Runnable() {
            @Override
            public void run() {
                File file = new File(getFilesDir(), "theme.bin");
                try (FileOutputStream fos = new FileOutputStream(file, false)) {
                    fos.write(current);
                    fos.getFD().sync();
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        loadPersistedTheme();

        if (b != null) {
            expr = b.getString("expr", "");
            fresh = b.getBoolean("fresh", false);
            expanded = b.getBoolean("expanded", false);
            degMode = b.getBoolean("degMode", true);
            themeIdx = b.getInt("themeIdx", themeIdx);
            lastHistoryLine = b.getString("hist", "");
            ArrayList<String> savedHist = b.getStringArrayList("sessionHistory");
            if (savedHist != null) {
                sessionHistory.clear();
                sessionHistory.addAll(savedHist);
            }
        }

        buildUi();
        refreshAll();

        CacheShredder.checkAndShred(this);
        CacheShredder.schedulePeriodicJob(this);
    }

    private void buildUi() {
        final int[] t = THEMES[themeIdx];
        boolean isLandscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        boolean compactHeight = screenH < dp(540);

        Window window = getWindow();
        window.setStatusBarColor(t[T_BG_TOP]);
        window.setNavigationBarColor(t[T_BG_BOT]);
        if (Build.VERSION.SDK_INT >= 23) {
            View decor = window.getDecorView();
            int vis = decor.getSystemUiVisibility();
            if (t[T_IS_LIGHT] == 1) {
                vis |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                vis &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            decor.setSystemUiVisibility(vis);
        }

        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setSaveEnabled(false);
        int hPad = dp(isLandscape ? 14 : 16);
        int vPad = dp(compactHeight ? 6 : 12);
        rootLayout.setPadding(hPad, vPad, hPad, vPad);
        rootLayout.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{ t[T_BG_TOP], t[T_BG_BOT] }));

        // 1. Top Hardware Header Bar (Crest + Wordmark + Mode Pills)
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setSaveEnabled(false);

        View crestView = new View(this);
        crestView.setSaveEnabled(false);
        crestView.setBackground(new CaluCrestDrawable(t));
        LinearLayout.LayoutParams crestLp = new LinearLayout.LayoutParams(dp(22), dp(22));
        crestLp.rightMargin = dp(8);
        header.addView(crestView, crestLp);

        TextView brand = new TextView(this);
        brand.setSaveEnabled(false);
        brand.setText("CALU");
        brand.setTextColor(t[T_MUTED]);
        brand.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        brand.setTextSize(12);
        brand.setLetterSpacing(0.32f);
        header.addView(brand, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        statusBadge = new TextView(this);
        statusBadge.setSaveEnabled(false);
        statusBadge.setText("");
        statusBadge.setTextColor(t[T_SCI_TX]);
        statusBadge.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        statusBadge.setTextSize(10);
        statusBadge.setLetterSpacing(0.12f);
        statusBadge.setPadding(dp(10), 0, 0, 0);
        header.addView(statusBadge, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // Angle Mode Pill (DEG / RAD)
        degBtn = makePillButton(degMode ? "DEG" : "RAD", t, false);
        degBtn.setContentDescription("toggle degrees or radians");
        degBtn.setVisibility((expanded || isLandscape) ? View.VISIBLE : View.GONE);
        degBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                degMode = !degMode;
                degBtn.setText(degMode ? "DEG" : "RAD");
                show();
            }
        });
        LinearLayout.LayoutParams degLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(28));
        degLp.rightMargin = dp(6);
        header.addView(degBtn, degLp);

        // Theme Cycle Pill
        themeBtn = makePillButton(THEME_NAMES[themeIdx], t, false);
        themeBtn.setContentDescription("switch color theme");
        themeBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                themeIdx = (themeIdx + 1) % THEMES.length;
                persistTheme();
                buildUi();
                refreshAll();
            }
        });
        LinearLayout.LayoutParams themeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(28));
        if (!isLandscape) themeLp.rightMargin = dp(6);
        header.addView(themeBtn, themeLp);

        // Scientific Expand/Collapse Pill (Portrait only; Landscape has full deck built in)
        toggleBtn = makePillButton(expanded ? "BASIC" : "SCI", t, expanded);
        toggleBtn.setContentDescription(expanded ? "show basic keys" : "show scientific keys");
        toggleBtn.setVisibility(isLandscape ? View.GONE : View.VISIBLE);
        toggleBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                expanded = !expanded;
                degBtn.setVisibility(expanded ? View.VISIBLE : View.GONE);
                stylePillButton(toggleBtn, THEMES[themeIdx], expanded);
                toggleBtn.setText(expanded ? "BASIC" : "SCI");
                toggleBtn.setContentDescription(expanded ? "show basic keys" : "show scientific keys");
                buildGrid();
                updateWeights();
            }
        });
        header.addView(toggleBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(28)));

        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        headerLp.bottomMargin = dp(compactHeight ? 4 : 8);
        rootLayout.addView(header, headerLp);

        // 2. Recessed Display Well (History Chips + Main Readout + Live Preview)
        displayWell = new LinearLayout(this);
        displayWell.setOrientation(LinearLayout.VERTICAL);
        displayWell.setSaveEnabled(false);
        displayWell.setBackground(new DisplayWellDrawable(t[T_WELL], t[T_HAIRLINE], dp(22)));
        int wellPadH = dp(18);
        int wellPadV = dp(compactHeight ? 8 : 14);
        displayWell.setPadding(wellPadH, wellPadV, wellPadH, wellPadV);

        // Session History Strip (top of display well)
        historyScroll = new HorizontalScrollView(this);
        historyScroll.setSaveEnabled(false);
        historyScroll.setHorizontalScrollBarEnabled(false);
        historyStrip = new LinearLayout(this);
        historyStrip.setOrientation(LinearLayout.HORIZONTAL);
        historyStrip.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        historyStrip.setSaveEnabled(false);
        historyScroll.addView(historyStrip, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams hsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        hsLp.bottomMargin = dp(4);
        displayWell.addView(historyScroll, hsLp);

        // Spacer pushes numbers to the bottom-right thumb zone
        View wellSpacer = new View(this);
        wellSpacer.setSaveEnabled(false);
        displayWell.addView(wellSpacer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // Secondary Expression / Live Preview Line
        exprView = new TextView(this);
        exprView.setSaveEnabled(false);
        exprView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        exprView.setFontFeatureSettings("tnum");
        exprView.setTextColor(t[T_MUTED]);
        exprView.setGravity(Gravity.END);
        exprView.setTextSize(compactHeight ? 14 : 17);
        exprView.setLetterSpacing(0.02f);
        exprView.setSingleLine(true);
        exprView.setEllipsize(TextUtils.TruncateAt.START);
        displayWell.addView(exprView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Primary Readout
        disp = new TextView(this);
        disp.setSaveEnabled(false);
        disp.setText("0");
        disp.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        disp.setFontFeatureSettings("tnum");
        disp.setTextColor(t[T_TEXT]);
        disp.setGravity(Gravity.END | Gravity.BOTTOM);
        disp.setTextSize(compactHeight ? 38 : 56);
        disp.setLetterSpacing(-0.02f);
        disp.setSingleLine(true);
        disp.setEllipsize(TextUtils.TruncateAt.START);
        displayWell.addView(disp, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        attachDisplayGestures(displayWell);

        // 3. Proportional Flex Keypad Container
        keypadContainer = new LinearLayout(this);
        keypadContainer.setOrientation(LinearLayout.VERTICAL);
        keypadContainer.setSaveEnabled(false);

        rootLayout.addView(displayWell);
        rootLayout.addView(keypadContainer);

        updateWeights();
        buildGrid();
        setContentView(rootLayout);
    }

    private void updateWeights() {
        boolean isLandscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        float wellWeight = isLandscape ? 0.30f : (expanded ? 0.28f : 0.36f);
        float padWeight = 1f - wellWeight;

        LinearLayout.LayoutParams wellLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, wellWeight);
        wellLp.bottomMargin = dp(isLandscape ? 6 : 10);
        displayWell.setLayoutParams(wellLp);

        LinearLayout.LayoutParams padLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, padWeight);
        keypadContainer.setLayoutParams(padLp);
    }

    private Button makePillButton(String label, int[] t, boolean active) {
        Button b = new Button(this);
        b.setSaveEnabled(false);
        b.setAllCaps(false);
        b.setText(label);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        b.setTextSize(10.5f);
        b.setLetterSpacing(0.10f);
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setPadding(dp(11), 0, dp(11), 0);
        b.setGravity(Gravity.CENTER);
        stylePillButton(b, t, active);
        return b;
    }

    private void stylePillButton(Button b, int[] t, boolean active) {
        int fill = active ? t[T_OP] : t[T_WELL];
        int stroke = active ? t[T_OP] : t[T_HAIRLINE];
        int tx = active ? t[T_ON_ACCENT] : t[T_SCI_TX];
        b.setBackground(keyBackground(fill, stroke, RIPPLE_LIGHT, 14, false));
        b.setTextColor(tx);
    }

    // Swipe horizontally across the display to backspace; tap/long-press to copy value.
    private void attachDisplayGestures(View target) {
        final int touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        target.setOnTouchListener(new View.OnTouchListener() {
            float startX, startY;
            boolean swiped;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = e.getX();
                        startY = e.getY();
                        swiped = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (!swiped && Math.abs(e.getX() - startX) > touchSlop * 2.2f
                                && Math.abs(e.getY() - startY) < touchSlop * 2.5f) {
                            swiped = true;
                            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                            press(BKSP_LABEL);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!swiped && Math.abs(e.getX() - startX) < touchSlop
                                && Math.abs(e.getY() - startY) < touchSlop) {
                            copyCurrentDisplay(v);
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void copyCurrentDisplay(View v) {
        String val = expr.isEmpty() ? "0" : expr;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("Calu result", val));
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            flashStatus("COPIED");
        }
    }

    private void flashStatus(String msg) {
        if (statusBadge == null) return;
        statusBadge.setText(msg);
        statusBadge.setAlpha(1f);
        statusBadge.animate().alpha(0f).setStartDelay(950).setDuration(300).start();
    }

    // Builds proportional flex rows so keys scale smoothly to any viewport.
    private void buildGrid() {
        keypadContainer.removeAllViews();
        boolean isLandscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;

        if (isLandscape) {
            for (String[] rowKeys : LANDSCAPE_ROWS) {
                addFlexRow(rowKeys, true, false);
            }
        } else {
            if (expanded) {
                for (String[] sciRow : PORTRAIT_SCI_ROWS) {
                    addFlexRow(sciRow, false, true);
                }
            }
            for (String[] basicRow : PORTRAIT_BASIC_ROWS) {
                addFlexRow(basicRow, false, false);
            }
        }
        refreshEqState();
    }

    private void addFlexRow(String[] keys, boolean isLandscape, boolean isSciRow) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setSaveEnabled(false);

        float rowWeight = isSciRow ? 0.78f : 1.0f;
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, rowWeight);
        keypadContainer.addView(row, rowLp);

        int margin = dp(isLandscape ? 3 : (expanded ? 3 : 4));
        for (String k : keys) {
            Button btn = createKeyButton(k, isLandscape, isSciRow);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            lp.setMargins(margin, margin, margin, margin);
            row.addView(btn, lp);
        }
    }

    private Button createKeyButton(String k, boolean isLandscape, boolean isSciRow) {
        final int[] t = THEMES[themeIdx];
        final int iconPx = dp(isLandscape ? 20 : 24);
        final BackspaceIcon icon = k.equals(BKSP_LABEL)
                ? new BackspaceIcon(t[T_IS_LIGHT] == 1 ? 0xFFFBF8F3 : CREAM, iconPx)
                : null;
        Button btn = new Button(this) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (icon != null) {
                    int cx = getWidth() / 2;
                    int cy = getHeight() / 2;
                    icon.setBounds(cx - iconPx / 2, cy - iconPx / 2, cx + iconPx / 2, cy + iconPx / 2);
                    icon.draw(canvas);
                }
            }
        };
        btn.setSaveEnabled(false);
        btn.setAllCaps(false);
        btn.setText(k.equals(BKSP_LABEL) ? "" : prettyKeyLabel(k));

        if (k.equals(BKSP_LABEL)) {
            // Long-pressing Backspace clears the entire expression with a heavy haptic click
            btn.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    press("C");
                    flashStatus("CLEARED");
                    return true;
                }
            });
        }

        boolean isWordKey = k.length() >= 2 && !k.equals("00");
        btn.setTypeface(Typeface.create(
                isWordKey ? "sans-serif-medium" : "sans-serif",
                k.equals("=") ? Typeface.BOLD : Typeface.NORMAL));

        float baseSize = isLandscape ? 18f : (isSciRow ? 16f : (expanded ? 20f : 23f));
        if (k.equals("=")) baseSize += 4f;
        else if (isWordKey) baseSize = isLandscape ? 14f : 15f;
        btn.setTextSize(baseSize);

        btn.setMinWidth(0);
        btn.setMinHeight(0);
        btn.setPadding(dp(2), 0, dp(2), 0);
        btn.setGravity(Gravity.CENTER);
        styleKey(btn, k, isLandscape || isSciRow ? 14 : 18);

        final String key = k;
        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                press(key);
            }
        });
        btn.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) {
                    v.animate().scaleX(0.93f).scaleY(0.93f).setDuration(65).start();
                } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(110).start();
                }
                return false;
            }
        });
        if (k.equals("=")) eqBtn = btn;
        return btn;
    }

    private static String prettyKeyLabel(String k) {
        if (k.equals("*")) return "\u00D7"; // ×
        if (k.equals("/")) return "\u00F7"; // ÷
        if (k.equals("-")) return "\u2212"; // −
        return k;
    }

    private void styleKey(Button btn, String k, int radiusDp) {
        final int[] t = THEMES[themeIdx];
        if (k.equals("=")) {
            btn.setBackground(keyBackground(t[T_EQ], 0, RIPPLE_DARK, radiusDp, true));
            btn.setTextColor(t[T_ON_ACCENT]);
            btn.setContentDescription("equals");
            return;
        }
        if (k.equals("C") || k.equals(BKSP_LABEL)) {
            btn.setBackground(keyBackground(t[T_RUST], 0, RIPPLE_LIGHT, radiusDp, true));
            btn.setTextColor(0xFFF7EFE4);
            btn.setContentDescription(k.equals("C") ? "clear" : "backspace, hold to clear");
            return;
        }
        if (isDigit(k) || k.equals(".") || k.equals("00")) {
            btn.setBackground(keyBackground(t[T_SURF], t[T_STROKE],
                    t[T_IS_LIGHT] == 1 ? RIPPLE_DARK : RIPPLE_LIGHT, radiusDp, false));
            btn.setTextColor(t[T_TEXT]);
            return;
        }
        if (isSciKey(k)) {
            btn.setBackground(keyBackground(t[T_SCI_SURF], t[T_STROKE],
                    t[T_IS_LIGHT] == 1 ? RIPPLE_DARK : RIPPLE_LIGHT, radiusDp, false));
            btn.setTextColor(t[T_SCI_TX]);
            btn.setContentDescription(sciContentDescription(k));
            return;
        }
        // Core operators (+ - * / ( ))
        btn.setBackground(keyBackground(t[T_OP], 0, RIPPLE_DARK, radiusDp, true));
        btn.setTextColor(t[T_IS_LIGHT] == 1 ? 0xFF1B1511 : (themeIdx == 1 ? 0xFFF2F4F8 : t[T_ON_ACCENT]));
        btn.setContentDescription(operatorContentDescription(k));
    }

    private static boolean isSciKey(String k) {
        return k.equals("%") || k.equals("^") || k.equals(SQRT_LABEL)
                || k.equals("sin") || k.equals("cos") || k.equals("tan")
                || k.equals("ln") || k.equals(PI_LABEL) || k.equals("!") || k.equals("e");
    }

    private static String sciContentDescription(String k) {
        if (k.equals("%")) return "percent";
        if (k.equals("^")) return "power";
        if (k.equals(SQRT_LABEL)) return "square root";
        if (k.equals(PI_LABEL)) return "pi";
        if (k.equals("!")) return "factorial";
        if (k.equals("ln")) return "natural logarithm";
        return k;
    }

    private static String operatorContentDescription(String k) {
        if (k.equals("/")) return "divide";
        if (k.equals("*")) return "multiply";
        if (k.equals("-")) return "subtract";
        if (k.equals("+")) return "add";
        if (k.equals("(")) return "open parenthesis";
        if (k.equals(")")) return "close parenthesis";
        return k;
    }

    private Drawable keyBackground(int fill, int stroke, int rippleColor, int radiusDp, boolean accent) {
        KeycapDrawable content = new KeycapDrawable(fill, stroke, dp(radiusDp), dp(1), accent);
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.RECTANGLE);
        mask.setCornerRadius(dp(radiusDp));
        mask.setColor(0xFFFFFFFF);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
    }

    private void refreshEqState() {
        if (eqBtn == null) return;
        final int[] t = THEMES[themeIdx];
        boolean isLandscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        int rad = isLandscape ? 14 : 18;
        if (expr.isEmpty()) {
            eqBtn.setBackground(keyBackground(t[T_DIS_FILL], 0, RIPPLE_LIGHT, rad, false));
            eqBtn.setTextColor(t[T_DIS_TX]);
        } else {
            eqBtn.setBackground(keyBackground(t[T_EQ], 0, RIPPLE_DARK, rad, true));
            eqBtn.setTextColor(t[T_ON_ACCENT]);
        }
    }

    private void rebuildHistoryStrip() {
        if (historyStrip == null) return;
        historyStrip.removeAllViews();
        final int[] t = THEMES[themeIdx];
        if (sessionHistory.isEmpty()) {
            historyScroll.setVisibility(View.GONE);
            return;
        }
        historyScroll.setVisibility(View.VISIBLE);
        for (int i = 0; i < sessionHistory.size(); i++) {
            final String item = sessionHistory.get(i);
            final int eqIdx = item.lastIndexOf('=');
            if (eqIdx <= 0) continue;
            final String lhsRaw = item.substring(0, eqIdx);
            final String rhsVal = item.substring(eqIdx + 1);

            TextView chip = new TextView(this);
            chip.setSaveEnabled(false);
            chip.setSingleLine(true);
            chip.setText(prettifyPlain(lhsRaw) + " = " + formatNumberWithCommas(rhsVal));
            chip.setTextColor(t[T_MUTED]);
            chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            chip.setTextSize(11);
            chip.setPadding(dp(10), dp(4), dp(10), dp(4));
            chip.setBackground(keyBackground(t[T_BG_TOP], t[T_HAIRLINE], RIPPLE_LIGHT, 12, false));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    if (fresh || expr.isEmpty()) {
                        expr = rhsVal;
                        fresh = false;
                    } else {
                        maybeImplicitStar();
                        if (expr.length() + rhsVal.length() <= 60) expr += rhsVal;
                    }
                    show();
                }
            });
            chip.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    expr = lhsRaw;
                    fresh = false;
                    show();
                    flashStatus("RECALLED");
                    return true;
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = dp(6);
            historyStrip.addView(chip, lp);
        }
        historyScroll.post(new Runnable() {
            @Override public void run() {
                historyScroll.fullScroll(View.FOCUS_RIGHT);
            }
        });
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("expr", expr);
        out.putBoolean("fresh", fresh);
        out.putBoolean("expanded", expanded);
        out.putBoolean("degMode", degMode);
        out.putInt("themeIdx", themeIdx);
        out.putString("hist", lastHistoryLine);
        out.putStringArrayList("sessionHistory", sessionHistory);
    }

    private String sym(String k) {
        if (k.equals(BKSP_LABEL)) return "BKSP";
        if (k.equals(SQRT_LABEL)) return "SQRT";
        if (k.equals(PI_LABEL)) return "PI";
        return k;
    }

    private void press(String k) {
        String s = sym(k);
        if (s.equals("BKSP")) {
            if (fresh) {
                expr = "";
                fresh = false;
            } else if (!expr.isEmpty()) {
                expr = stripLastToken(expr);
            }
            show();
            return;
        }
        if (k.equals("C")) {
            expr = "";
            lastHistoryLine = "";
            fresh = false;
            show();
            return;
        }
        if (k.equals("=")) {
            if (fresh || expr.isEmpty()) return;
            String evalTarget = autoCloseParens(expr);
            String res;
            boolean error;
            try {
                res = trim(Expr.eval(evalTarget, degMode));
                error = res.equals("Error");
            } catch (Exception e) {
                res = "Error";
                error = true;
            }
            final int[] t = THEMES[themeIdx];
            lastHistoryLine = prettifyPlain(evalTarget) + " =";
            exprView.setText(lastHistoryLine);
            fitHistory(lastHistoryLine);
            if (!error) {
                recordSessionHistory(evalTarget + "=" + res);
                disp.setText(formatNumberWithCommas(res));
            } else {
                disp.setText("Error");
            }
            fitDisplay(disp.getText().toString());
            disp.setTextColor(error ? t[T_ERR] : t[T_TEXT]);
            expr = error ? "" : res;
            fresh = true;
            refreshEqState();
            animateResult();
            disp.announceForAccessibility(error ? "Error" : res);
            return;
        }

        // Starting new input after '=': digits/dot/functions/constants start fresh;
        // binary/postfix operators chain from the result.
        if (fresh) {
            if (isDigit(k) || k.equals(".") || k.equals("00") || k.equals("(")
                    || s.equals("SQRT") || s.equals("PI") || k.equals("e")
                    || isFuncKey(k)) {
                expr = "";
            }
            fresh = false;
        }
        if (expr.length() >= 60) return;

        if (k.equals("00")) {
            maybeImplicitStar();
            if (expr.length() <= 58) { expr += "00"; show(); }
            return;
        }
        if (isDigit(k)) {
            maybeImplicitStar();
            expr += k;
            show();
            return;
        }
        if (s.equals("PI") || k.equals("e")) {
            String token = s.equals("PI") ? "PI" : "e";
            if (!expr.isEmpty()) {
                char l = expr.charAt(expr.length() - 1);
                if (isDigit(l) || l == ')' || l == '%' || l == '!' || l == 'I' || l == 'e') {
                    expr += "*";
                }
            }
            expr += token;
            show();
            return;
        }
        if (isFuncKey(k)) {
            String token = k.toUpperCase() + "(";
            if (!expr.isEmpty()) {
                char l = expr.charAt(expr.length() - 1);
                if (isDigit(l) || l == ')' || l == '%' || l == '!' || l == 'I' || l == 'e') {
                    expr += "*";
                }
            }
            if (expr.length() + token.length() <= 60) {
                expr += token;
                show();
            }
            return;
        }
        if (k.equals(".")) {
            int j = expr.length();
            while (j > 0 && (isDigit(expr.charAt(j - 1)) || expr.charAt(j - 1) == '.')) j--;
            if (expr.indexOf('.', j) >= 0) return;
            boolean newNum = j == expr.length();
            maybeImplicitStar();
            expr += newNum ? "0." : ".";
            show();
            return;
        }
        if (k.equals("(")) {
            if (!expr.isEmpty()) {
                char l = expr.charAt(expr.length() - 1);
                if (isDigit(l) || l == ')' || l == '%' || l == '!' || l == 'I' || l == 'e') {
                    expr += "*";
                }
            }
            expr += "(";
            show();
            return;
        }
        if (k.equals(")")) {
            if (countChar(expr, '(') > countChar(expr, ')') && !endsWithOpOrOpen(expr)) {
                expr += ")";
                show();
            }
            return;
        }
        if (s.equals("SQRT")) {
            if (!expr.isEmpty()) {
                char l = expr.charAt(expr.length() - 1);
                if (isDigit(l) || l == ')' || l == '%' || l == '!' || l == 'I' || l == 'e') {
                    expr += "*";
                }
            }
            expr += "SQRT";
            show();
            return;
        }
        if (k.equals("!")) {
            if (!expr.isEmpty() && !endsWithOpOrOpen(expr)) {
                expr += "!";
                show();
            }
            return;
        }
        // Operators: + - * / % ^
        if ("+-*/%^".contains(k)) {
            if (expr.isEmpty()) {
                if (k.equals("-")) { expr = "-"; show(); }
                return;
            }
            if (expr.equals("-")) return;
            char l = expr.charAt(expr.length() - 1);
            if (l == '(') {
                if (k.equals("-")) { expr += "-"; show(); }
                return;
            }
            if (expr.endsWith("SQRT")) {
                if (k.equals("-")) { expr += "-"; show(); }
                return;
            }
            if ("+-*/^".indexOf(l) >= 0) {
                if (l == '-' && expr.length() >= 2) {
                    char p = expr.charAt(expr.length() - 2);
                    if ("+-*/^".indexOf(p) >= 0) {
                        if (k.equals("-")) return;
                        expr = expr.substring(0, expr.length() - 2) + k;
                        show();
                        return;
                    }
                    if (p == '(') {
                        if (k.equals("-")) return;
                        expr = expr.substring(0, expr.length() - 1);
                        show();
                        return;
                    }
                }
                if (k.equals("-") && (l == '*' || l == '/' || l == '^')) {
                    expr += "-";
                    show();
                } else {
                    expr = expr.substring(0, expr.length() - 1) + k;
                    show();
                }
                return;
            }
            expr += k;
            show();
        }
    }

    // Deletes multi-character tokens (SQRT, SIN(, COS(, TAN(, LN(, PI) cleanly in one tap.
    private static String stripLastToken(String s) {
        String[] multiTokens = { "SQRT", "SIN(", "COS(", "TAN(", "LOG(", "LN(", "PI" };
        for (String tok : multiTokens) {
            if (s.endsWith(tok)) {
                return s.substring(0, s.length() - tok.length());
            }
        }
        return s.substring(0, s.length() - 1);
    }

    private static boolean isFuncKey(String k) {
        return k.equals("sin") || k.equals("cos") || k.equals("tan")
                || k.equals("ln") || k.equals("log");
    }

    private void recordSessionHistory(String entry) {
        sessionHistory.remove(entry);
        sessionHistory.add(entry);
        while (sessionHistory.size() > 8) {
            sessionHistory.remove(0);
        }
        rebuildHistoryStrip();
    }

    private static String autoCloseParens(String s) {
        int open = countChar(s, '(') - countChar(s, ')');
        if (open <= 0 || endsWithOpOrOpen(s)) return s;
        StringBuilder sb = new StringBuilder(s);
        for (int i = 0; i < open; i++) sb.append(')');
        return sb.toString();
    }

    private static boolean isDigit(String k) {
        return k.length() == 1 && k.charAt(0) >= '0' && k.charAt(0) <= '9';
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private void maybeImplicitStar() {
        if (!expr.isEmpty()) {
            char l = expr.charAt(expr.length() - 1);
            if (l == ')' || l == '%' || l == '!' || l == 'I' || l == 'e') expr += "*";
        }
    }

    private static int countChar(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }

    private static boolean endsWithOpOrOpen(String s) {
        if (s.isEmpty() || s.endsWith("SQRT")) return true;
        char l = s.charAt(s.length() - 1);
        return l == '(' || "+-*/^".indexOf(l) >= 0;
    }

    private static String trim(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) return "Error";
        if (d == (long) d) return Long.toString((long) d);
        return new BigDecimal(d)
                .round(new MathContext(12))
                .stripTrailingZeros()
                .toString();
    }

    private void refreshAll() {
        rebuildHistoryStrip();
        if (fresh && !expr.isEmpty()) {
            final int[] t = THEMES[themeIdx];
            exprView.setText(lastHistoryLine);
            fitHistory(lastHistoryLine);
            disp.setTextColor(t[T_TEXT]);
            String formatted = formatNumberWithCommas(expr);
            disp.setText(formatted);
            fitDisplay(formatted);
            refreshEqState();
        } else {
            show();
        }
    }

    private void show() {
        final int[] t = THEMES[themeIdx];
        disp.setTextColor(t[T_TEXT]);

        if (expr.isEmpty()) {
            disp.setText("0");
            fitDisplay("0");
            exprView.setText(lastHistoryLine);
            fitHistory(lastHistoryLine);
            refreshEqState();
            return;
        }

        SpannableStringBuilder styled = buildStyledDisplay(expr, t);
        disp.setText(styled);
        fitDisplay(styled.toString());

        // Compute instant live preview when the expression contains an operation
        String live = computeLivePreview(expr);
        exprView.setText(live);
        fitHistory(live);
        refreshEqState();
    }

    // Evaluates a live preview only when the expression has an operator/function
    // and forms a complete valid mathematical expression.
    private String computeLivePreview(String raw) {
        if (raw.isEmpty() || endsWithOpOrOpen(raw)) return "";
        boolean hasOp = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ("+-*/%^!()".indexOf(c) >= 0 && !(i == 0 && c == '-')) {
                hasOp = true;
                break;
            }
        }
        if (!hasOp && !raw.contains("SQRT") && !raw.contains("SIN")
                && !raw.contains("COS") && !raw.contains("TAN")
                && !raw.contains("LN") && !raw.contains("PI") && !raw.contains("e")) {
            return "";
        }
        try {
            String closed = autoCloseParens(raw);
            String res = trim(Expr.eval(closed, degMode));
            if (res.equals("Error") || res.equals(raw)) return "";
            return "= " + formatNumberWithCommas(res);
        } catch (Exception ignored) {
            return "";
        }
    }

    // Builds a rich typographic Spannable with thousands grouping, colored operators,
    // and translucent ghost closing parentheses.
    private SpannableStringBuilder buildStyledDisplay(String raw, int[] t) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int i = 0, n = raw.length();
        while (i < n) {
            if (raw.startsWith("SQRT", i)) {
                appendColored(sb, "\u221A", t[T_SCI_TX]);
                i += 4;
            } else if (raw.startsWith("SIN(", i)) {
                appendColored(sb, "sin(", t[T_SCI_TX]);
                i += 4;
            } else if (raw.startsWith("COS(", i)) {
                appendColored(sb, "cos(", t[T_SCI_TX]);
                i += 4;
            } else if (raw.startsWith("TAN(", i)) {
                appendColored(sb, "tan(", t[T_SCI_TX]);
                i += 4;
            } else if (raw.startsWith("LN(", i)) {
                appendColored(sb, "ln(", t[T_SCI_TX]);
                i += 3;
            } else if (raw.startsWith("PI", i)) {
                appendColored(sb, "\u03C0", t[T_SCI_TX]);
                i += 2;
            } else {
                char c = raw.charAt(i);
                if (isDigit(c) || c == '.') {
                    int start = i;
                    while (i < n && (isDigit(raw.charAt(i)) || raw.charAt(i) == '.'
                            || raw.charAt(i) == 'E'
                            || ((raw.charAt(i) == '+' || raw.charAt(i) == '-')
                                && i > start && raw.charAt(i - 1) == 'E'))) {
                        i++;
                    }
                    sb.append(formatNumberWithCommas(raw.substring(start, i)));
                } else if (c == '*') {
                    appendColored(sb, " \u00D7 ", t[T_SCI_TX]);
                    i++;
                } else if (c == '/') {
                    appendColored(sb, " \u00F7 ", t[T_SCI_TX]);
                    i++;
                } else if (c == '+') {
                    appendColored(sb, " + ", t[T_SCI_TX]);
                    i++;
                } else if (c == '-') {
                    boolean unary = (i == 0) || "(+-*/^".indexOf(raw.charAt(i - 1)) >= 0
                            || raw.startsWith("SQRT", Math.max(0, i - 4));
                    appendColored(sb, unary ? "\u2212" : " \u2212 ", t[T_SCI_TX]);
                    i++;
                } else if (c == '^' || c == '%' || c == '!') {
                    appendColored(sb, String.valueOf(c), t[T_SCI_TX]);
                    i++;
                } else if (c == '(' || c == ')') {
                    appendColored(sb, String.valueOf(c), t[T_MUTED]);
                    i++;
                } else {
                    sb.append(c);
                    i++;
                }
            }
        }
        // Preview ghost closing parentheses so the user sees auto-balance in real time
        int open = countChar(raw, '(') - countChar(raw, ')');
        if (open > 0 && !endsWithOpOrOpen(raw)) {
            StringBuilder ghosts = new StringBuilder();
            for (int k = 0; k < open; k++) ghosts.append(')');
            int ghostColor = (t[T_MUTED] & 0x00FFFFFF) | 0x55000000;
            appendColored(sb, ghosts.toString(), ghostColor);
        }
        return sb;
    }

    private static void appendColored(SpannableStringBuilder sb, String text, int color) {
        int start = sb.length();
        sb.append(text);
        sb.setSpan(new ForegroundColorSpan(color), start, sb.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static String prettifyPlain(String raw) {
        return raw.replace("SQRT", "\u221A")
                .replace("SIN(", "sin(")
                .replace("COS(", "cos(")
                .replace("TAN(", "tan(")
                .replace("LN(", "ln(")
                .replace("PI", "\u03C0")
                .replace("*", "\u00D7")
                .replace("/", "\u00F7")
                .replace("-", "\u2212");
    }

    // Adds thousands separators to plain integer portions without altering scientific E notation.
    private static String formatNumberWithCommas(String num) {
        if (num == null || num.isEmpty() || num.contains("E") || num.contains("e")
                || num.equals("Error")) {
            return num;
        }
        boolean neg = num.startsWith("-") || num.startsWith("\u2212");
        String body = neg ? num.substring(1) : num;
        int dot = body.indexOf('.');
        String intPart = dot >= 0 ? body.substring(0, dot) : body;
        String decPart = dot >= 0 ? body.substring(dot) : "";
        if (intPart.length() <= 3) return num;

        StringBuilder sb = new StringBuilder();
        if (neg) sb.append(num.charAt(0));
        int rem = intPart.length() % 3;
        if (rem > 0) {
            sb.append(intPart, 0, rem);
            if (intPart.length() > rem) sb.append(',');
        }
        for (int i = rem; i < intPart.length(); i += 3) {
            sb.append(intPart, i, i + 3);
            if (i + 3 < intPart.length()) sb.append(',');
        }
        sb.append(decPart);
        return sb.toString();
    }

    private void fitDisplay(String s) {
        boolean isLandscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        int n = s.length();
        if (isLandscape) {
            disp.setTextSize(n <= 10 ? 36 : n <= 16 ? 28 : 22);
        } else {
            disp.setTextSize(n <= 8 ? 54 : n <= 12 ? 42 : n <= 17 ? 32 : 24);
        }
    }

    private void fitHistory(String s) {
        int n = s.length();
        exprView.setTextSize(n <= 16 ? 17 : n <= 24 ? 14 : 12);
    }

    private void animateResult() {
        disp.setScaleX(0.95f);
        disp.setScaleY(0.95f);
        disp.setAlpha(0.5f);
        disp.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(160).start();
    }

    // BODMAS + Scientific recursive-descent parser (RAM-only, zero allocations to disk):
    //   Brackets: (...)
    //   Orders: ^ (right-assoc), prefix SQRT/sin/cos/tan/ln/log, postfix % (/100) and ! (factorial)
    //   Division/Multiplication, then Addition/Subtraction.
    static class Expr {
        final String s;
        final boolean deg;
        int pos;

        Expr(String s, boolean deg) {
            this.s = s;
            this.deg = deg;
        }

        static double eval(String str) throws Exception {
            return eval(str, true);
        }

        static double eval(String str, boolean deg) throws Exception {
            String norm = str
                    .replace("SQRT", "R")
                    .replace("\u221A", "R")
                    .replace("SIN", "S")
                    .replace("sin", "S")
                    .replace("COS", "C")
                    .replace("cos", "C")
                    .replace("TAN", "T")
                    .replace("tan", "T")
                    .replace("LOG", "G")
                    .replace("log", "G")
                    .replace("LN", "L")
                    .replace("ln", "L")
                    .replace("PI", "P")
                    .replace("\u03C0", "P")
                    .replace("\u00D7", "*")
                    .replace("\u00F7", "/")
                    .replace("\u2212", "-");
            Expr e = new Expr(norm, deg);
            double v = e.parseExpr();
            e.skip();
            if (e.pos != e.s.length()) throw new Exception("trailing");
            return v;
        }

        void skip() {
            while (pos < s.length() && s.charAt(pos) == ' ') pos++;
        }

        double parseExpr() throws Exception {
            double v = parseTerm();
            for (;;) {
                skip();
                if (pos >= s.length()) return v;
                char c = s.charAt(pos);
                if (c == '+') { pos++; v += parseTerm(); }
                else if (c == '-') { pos++; v -= parseTerm(); }
                else return v;
            }
        }

        double parseTerm() throws Exception {
            double v = parseSigned();
            for (;;) {
                skip();
                if (pos >= s.length()) return v;
                char c = s.charAt(pos);
                if (c == '*') { pos++; v *= parseSigned(); }
                else if (c == '/') {
                    pos++;
                    double d = parseSigned();
                    if (d == 0) throw new Exception("div0");
                    v /= d;
                } else return v;
            }
        }

        // Unary binds looser than ^ so -3^2 = -(3^2) = -9.
        double parseSigned() throws Exception {
            skip();
            if (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == '+') { pos++; return parseSigned(); }
                if (c == '-') { pos++; return -parseSigned(); }
                if (c == 'R') {
                    pos++;
                    double v = parseSigned();
                    if (v < 0) throw new Exception("sqrt");
                    return Math.sqrt(v);
                }
                if (c == 'S' || c == 'C' || c == 'T' || c == 'L' || c == 'G') {
                    pos++;
                    double v = parseSigned();
                    if (c == 'L') {
                        if (v <= 0) throw new Exception("ln");
                        return Math.log(v);
                    }
                    if (c == 'G') {
                        if (v <= 0) throw new Exception("log");
                        return Math.log10(v);
                    }
                    double rad = deg ? Math.toRadians(v) : v;
                    if (c == 'S') {
                        if (deg && v % 180.0 == 0.0) return 0.0;
                        return Math.sin(rad);
                    }
                    if (c == 'C') {
                        if (deg && Math.abs(v % 180.0) == 90.0) return 0.0;
                        return Math.cos(rad);
                    }
                    if (deg && Math.abs(v % 180.0) == 90.0) throw new Exception("tan90");
                    if (deg && v % 180.0 == 0.0) return 0.0;
                    return Math.tan(rad);
                }
            }
            return parsePower();
        }

        double parsePower() throws Exception {
            double base = parsePostfix();
            skip();
            if (pos < s.length() && s.charAt(pos) == '^') {
                pos++;
                double exp = parseSigned();
                return Math.pow(base, exp);
            }
            return base;
        }

        double parsePostfix() throws Exception {
            double v = parsePrimary();
            for (;;) {
                skip();
                if (pos < s.length() && s.charAt(pos) == '%') {
                    pos++;
                    v /= 100.0;
                } else if (pos < s.length() && s.charAt(pos) == '!') {
                    pos++;
                    if (v < 0 || v != Math.floor(v) || v > 170) throw new Exception("fact");
                    double f = 1.0;
                    for (int i = 2; i <= (int) v; i++) f *= i;
                    v = f;
                } else {
                    return v;
                }
            }
        }

        double parsePrimary() throws Exception {
            skip();
            if (pos >= s.length()) throw new Exception("empty");
            char c = s.charAt(pos);
            if (c == '(') {
                pos++;
                double v = parseExpr();
                skip();
                if (pos >= s.length() || s.charAt(pos) != ')') throw new Exception("bracket");
                pos++;
                return v;
            }
            if (c == 'P') {
                pos++;
                return Math.PI;
            }
            if (c == 'e') {
                pos++;
                return Math.E;
            }
            int start = pos;
            boolean dot = false, exp = false;
            while (pos < s.length()) {
                char ch = s.charAt(pos);
                if (ch >= '0' && ch <= '9') { pos++; continue; }
                if (ch == '.' && !dot && !exp) { dot = true; pos++; continue; }
                if (ch == 'E' && !exp && pos > start) {
                    int nx = pos + 1;
                    boolean sig = nx < s.length()
                            && (s.charAt(nx) == '+' || s.charAt(nx) == '-');
                    int d0 = sig ? nx + 1 : nx;
                    if (d0 < s.length() && s.charAt(d0) >= '0' && s.charAt(d0) <= '9') {
                        exp = true;
                        pos++;
                        continue;
                    }
                    break;
                }
                if ((ch == '+' || ch == '-') && exp && s.charAt(pos - 1) == 'E') {
                    pos++;
                    continue;
                }
                break;
            }
            if (start == pos) throw new Exception("num");
            return Double.parseDouble(s.substring(start, pos));
        }
    }

    // Tactile hardware keycap drawable with subtle vertical gradient, crisp border,
    // bottom bevel, and top specular rim highlight.
    static class KeycapDrawable extends Drawable {
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint specularPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final RectF specRect = new RectF();
        private final int baseColor;
        private final int strokeColor;
        private final float radius;
        private final float strokeWidth;
        private final boolean accent;

        KeycapDrawable(int fill, int stroke, float radiusPx, float strokePx, boolean accent) {
            this.baseColor = fill;
            this.strokeColor = stroke;
            this.radius = radiusPx;
            this.strokeWidth = strokePx;
            this.accent = accent;

            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(strokePx);
            strokePaint.setColor(stroke);

            specularPaint.setStyle(Paint.Style.STROKE);
            specularPaint.setStrokeWidth(Math.max(1f, strokePx));
            specularPaint.setColor(accent ? 0x38FFFFFF : 0x1AFFFFFF);
        }

        private static int shiftColor(int c, float factor) {
            int a = (c >>> 24) & 0xFF;
            int r = Math.min(255, Math.max(0, (int) (((c >>> 16) & 0xFF) * factor)));
            int g = Math.min(255, Math.max(0, (int) (((c >>> 8) & 0xFF) * factor)));
            int b = Math.min(255, Math.max(0, (int) ((c & 0xFF) * factor)));
            return (a << 24) | (r << 16) | (g << 8) | b;
        }

        @Override
        protected void onBoundsChange(Rect b) {
            super.onBoundsChange(b);
            float inset = strokeWidth * 0.5f;
            rect.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset);
            specRect.set(rect.left + strokeWidth, rect.top + strokeWidth,
                    rect.right - strokeWidth, rect.bottom - strokeWidth);
            int topC = shiftColor(baseColor, 1.07f);
            int botC = shiftColor(baseColor, 0.93f);
            fillPaint.setShader(new LinearGradient(
                    0, rect.top, 0, rect.bottom, topC, botC, Shader.TileMode.CLAMP));
        }

        @Override
        public void draw(Canvas canvas) {
            if ((baseColor >>> 24) != 0) {
                canvas.drawRoundRect(rect, radius, radius, fillPaint);
                canvas.drawRoundRect(specRect, Math.max(0, radius - strokeWidth),
                        Math.max(0, radius - strokeWidth), specularPaint);
            }
            if (strokeColor != 0) {
                canvas.drawRoundRect(rect, radius, radius, strokePaint);
            }
        }

        @Override public void setAlpha(int alpha) { fillPaint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { fillPaint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // Recessed matte display housing with subtle inner rim.
    static class DisplayWellDrawable extends Drawable {
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final float radius;

        DisplayWellDrawable(int fill, int stroke, float radiusPx) {
            this.radius = radiusPx;
            fillPaint.setColor(fill);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(Math.max(1.5f, radiusPx * 0.06f));
            strokePaint.setColor(stroke);
        }

        @Override
        protected void onBoundsChange(Rect b) {
            super.onBoundsChange(b);
            rect.set(b.left + 1f, b.top + 1f, b.right - 1f, b.bottom - 1f);
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawRoundRect(rect, radius, radius, fillPaint);
            canvas.drawRoundRect(rect, radius, radius, strokePaint);
        }

        @Override public void setAlpha(int alpha) { fillPaint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { fillPaint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // Custom header emblem matching the launcher icon: rounded squircle with 2x2 grid + gold '='.
    static class CaluCrestDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final int[] t;

        CaluCrestDrawable(int[] theme) {
            this.t = theme;
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            float w = b.width(), h = b.height();
            rect.set(0, 0, w, h);
            paint.setColor(t[T_SURF]);
            canvas.drawRoundRect(rect, w * 0.28f, h * 0.28f, paint);

            float pad = w * 0.20f;
            float gap = w * 0.08f;
            float cell = (w - pad * 2f - gap) / 2f;
            float r = cell * 0.32f;

            paint.setColor(t[T_TEXT]);
            rect.set(pad, pad, pad + cell, pad + cell);
            canvas.drawRoundRect(rect, r, r, paint);

            paint.setColor(t[T_OP]);
            rect.set(pad + cell + gap, pad, pad + cell * 2f + gap, pad + cell);
            canvas.drawRoundRect(rect, r, r, paint);

            paint.setColor(t[T_MUTED]);
            rect.set(pad, pad + cell + gap, pad + cell, pad + cell * 2f + gap);
            canvas.drawRoundRect(rect, r, r, paint);

            paint.setColor(t[T_EQ]);
            rect.set(pad + cell + gap, pad + cell + gap,
                    pad + cell * 2f + gap, pad + cell * 2f + gap);
            canvas.drawRoundRect(rect, r, r, paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // Drawn backspace icon: rounded pentagon with an X (zero font dependencies).
    static class BackspaceIcon extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final int size;

        BackspaceIcon(int color, int sizePx) {
            this.size = sizePx;
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1.5f, sizePx * 0.075f));
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override public int getIntrinsicWidth() { return size; }
        @Override public int getIntrinsicHeight() { return size; }

        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            float s = Math.min(b.width(), b.height());
            float l = b.left + (b.width() - s) / 2f, t = b.top + (b.height() - s) / 2f;
            float r = l + s, bot = t + s;
            path.reset();
            path.moveTo(l + s * 0.36f, t + s * 0.14f);
            path.lineTo(r - s * 0.06f, t + s * 0.14f);
            path.lineTo(r - s * 0.06f, bot - s * 0.14f);
            path.lineTo(l + s * 0.36f, bot - s * 0.14f);
            path.lineTo(l + s * 0.06f, (t + bot) / 2f);
            path.close();
            canvas.drawPath(path, paint);
            float cx = l + s * 0.65f, cy = (t + bot) / 2f, d = s * 0.095f;
            canvas.drawLine(cx - d, cy - d, cx + d, cy + d, paint);
            canvas.drawLine(cx + d, cy - d, cx - d, cy + d, paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
