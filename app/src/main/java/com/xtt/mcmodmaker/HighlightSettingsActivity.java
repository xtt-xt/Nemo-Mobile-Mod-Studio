package com.xtt.mcmodmaker;

import android.app.Activity;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.xtt.mcmodmaker.editor.UserColorScheme;
import com.xtt.mcmodmaker.util.SettingsManager;
import com.xtt.mcmodmaker.util.UiUtils;

import dev1503.oreui.StyleSheet;
import dev1503.oreui.dialog.OreDialogBuilder;
import dev1503.oreui.widgets.OreButton;
import dev1503.oreui.widgets.OreCard;
import dev1503.oreui.widgets.OreSwitch;
import dev1503.oreui.widgets.OreTextView;
import androidx.appcompat.app.AlertDialog;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

/** 代码高亮设置：文件类型高亮开关 + 各语法元素颜色配置。 */
public class HighlightSettingsActivity extends Activity {

    public static final String PREFS_NAME = "highlight_settings";
    // 文件类型高亮开关
    public static final String KEY_HL_PY = "hl_py";
    public static final String KEY_HL_JSON = "hl_json";
    public static final String KEY_HL_LANG = "hl_lang";
    public static final String KEY_HL_JS = "hl_js";
    public static final String KEY_HL_MCFUNCTION = "hl_mcfunction";
    // 颜色配置
    public static final String KEY_COLOR_KEYWORD = "color_keyword";
    public static final String KEY_COLOR_COMMENT = "color_comment";
    public static final String KEY_COLOR_STRING = "color_string";
    public static final String KEY_COLOR_NUMBER = "color_number";
    public static final String KEY_COLOR_FUNCTION = "color_function";

    private SettingsManager settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = SettingsManager.getInstance(this);
        // 首次运行：写入默认主题色（与 UserColorScheme 回退一致），
        // 避免颜色框全部显示 #FFFFFF
        if (!settings.contains(KEY_COLOR_KEYWORD) || !settings.contains(KEY_COLOR_COMMENT)
                || !settings.contains(KEY_COLOR_STRING) || !settings.contains(KEY_COLOR_NUMBER)
                || !settings.contains(KEY_COLOR_FUNCTION)) {
            UserColorScheme scheme = new UserColorScheme(this);
            if (!settings.contains(KEY_COLOR_KEYWORD)) settings.putInt(KEY_COLOR_KEYWORD, scheme.getColor(EditorColorScheme.KEYWORD));
            if (!settings.contains(KEY_COLOR_COMMENT)) settings.putInt(KEY_COLOR_COMMENT, scheme.getColor(EditorColorScheme.COMMENT));
            if (!settings.contains(KEY_COLOR_STRING)) settings.putInt(KEY_COLOR_STRING, scheme.getColor(EditorColorScheme.LITERAL));
            if (!settings.contains(KEY_COLOR_NUMBER)) settings.putInt(KEY_COLOR_NUMBER, scheme.getColor(EditorColorScheme.LITERAL));
            if (!settings.contains(KEY_COLOR_FUNCTION)) settings.putInt(KEY_COLOR_FUNCTION, scheme.getColor(EditorColorScheme.FUNCTION_NAME));
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiUtils.dp(this, 16), UiUtils.dp(this, 16),
                UiUtils.dp(this, 16), UiUtils.dp(this, 16));
        root.setBackgroundColor(Color.parseColor("#1E1E1E"));
        setContentView(root);
        // 状态栏 / 导航栏与页面背景一致
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.parseColor("#1E1E1E"));
            getWindow().setNavigationBarColor(Color.parseColor("#1E1E1E"));
        }

        OreButton btnBack = new OreButton(this);
        btnBack.setText("返回");
        btnBack.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        root.addView(btnBack);
        UiUtils.addGap(this, root, 16);

        OreTextView title = new OreTextView(this);
        title.setText("代码高亮设置");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        root.addView(title);
        UiUtils.addGap(this, root, 12);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        // ===== 文件类型高亮开关 =====
        OreTextView sec1 = new OreTextView(this);
        sec1.setText("文件类型高亮");
        sec1.setTextColor(Color.parseColor("#AAAAAA"));
        sec1.setTextSize(13);
        content.addView(sec1);
        UiUtils.addGap(this, content, 6);

        content.addView(toggleRow("Python (.py)", KEY_HL_PY));
        content.addView(toggleRow("JSON (.json)", KEY_HL_JSON));
        content.addView(toggleRow("语言文件 (.lang)", KEY_HL_LANG));
        content.addView(toggleRow("JavaScript (.js)", KEY_HL_JS));
        content.addView(toggleRow("Minecraft 函数 (.mcfunction)", KEY_HL_MCFUNCTION));
        UiUtils.addGap(this, content, 16);

        // ===== 颜色配置 =====
        OreTextView sec2 = new OreTextView(this);
        sec2.setText("语法颜色");
        sec2.setTextColor(Color.parseColor("#AAAAAA"));
        sec2.setTextSize(13);
        content.addView(sec2);
        UiUtils.addGap(this, content, 6);

        content.addView(colorRow("关键字", KEY_COLOR_KEYWORD));
        content.addView(colorRow("注释", KEY_COLOR_COMMENT));
        content.addView(colorRow("字符串", KEY_COLOR_STRING));
        content.addView(colorRow("数字", KEY_COLOR_NUMBER));
        content.addView(colorRow("函数名", KEY_COLOR_FUNCTION));
    }

    private OreCard toggleRow(String label, final String key) {
        OreCard card = new OreCard(this);
        card.setPadding(UiUtils.dp(this, 16), UiUtils.dp(this, 18),
                UiUtils.dp(this, 16), UiUtils.dp(this, 18));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        OreTextView tv = new OreTextView(this);
        tv.setText(label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(13);
        row.addView(tv, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        final OreSwitch sw = new OreSwitch(this);
        sw.setChecked(settings.getBoolean(key, true));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                settings.putBoolean(key, isChecked);
            }
        });
        // label weight=1 撑满剩余空间，开关自然贴右（row 必须 MATCH_PARENT）
        row.addView(sw);
        card.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = UiUtils.dp(this, 5);
        params.bottomMargin = UiUtils.dp(this, 5);
        card.setLayoutParams(params);
        return card;
    }
    private OreCard colorRow(String label, final String key) {
        OreCard card = new OreCard(this);
        card.setPadding(UiUtils.dp(this, 16), UiUtils.dp(this, 18),
                UiUtils.dp(this, 16), UiUtils.dp(this, 18));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        OreTextView tv = new OreTextView(this);
        tv.setText(label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(13);
        row.addView(tv, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        // 颜色值用输入框样式展示（深底+边框+圆角），文字色可自定义
        // 注意：不能用 OreEditText（其 onDraw 会强制 setTextColor 覆盖自定义颜色）
        final OreTextView colorView = new OreTextView(this);
        int color = settings.getInt(key, Color.WHITE);
        colorView.setText(toHex(color));
        colorView.setTextColor(color);
        colorView.setTextSize(12);
        colorView.setGravity(Gravity.CENTER);
        colorView.setPadding(UiUtils.dp(this, 8), UiUtils.dp(this, 8),
                UiUtils.dp(this, 8), UiUtils.dp(this, 8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#3E3E42"));
        bg.setStroke(UiUtils.dp(this, 1), Color.parseColor("#666666"));
        bg.setCornerRadius(UiUtils.dp(this, 4));
        colorView.setBackground(bg);
        // 固定宽度、右对齐（label weight 撑满）
        LinearLayout.LayoutParams cvParams = new LinearLayout.LayoutParams(
                UiUtils.dp(this, 96), LinearLayout.LayoutParams.WRAP_CONTENT);
        cvParams.leftMargin = UiUtils.dp(this, 12);
        row.addView(colorView, cvParams);
        card.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                pickColor(key, colorView);
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = UiUtils.dp(this, 5);
        params.bottomMargin = UiUtils.dp(this, 5);
        card.setLayoutParams(params);
        return card;
    }

    private String toHex(int color) {
        return String.format("#%06X", (0xFFFFFF & color));
    }

    /** 预置色板：点击选择颜色。 */
    private void pickColor(final String key, final OreTextView colorView) {
        final int[] palette = {
                0xFF569CD6, 0xFF4EC9B0, 0xFFCE9178, 0xFF6A9955, 0xFFC586C0,
                0xFFDCDCAA, 0xFF9CDCFE, 0xFFF48771, 0xFFFFD700, 0xFF7ED321,
                0xFFB5CEA8, 0xFFE5C07B, 0xFF98C379, 0xFF61AFEF, 0xFFE06C75,
                0xFFFFFFFF, 0xFF888888, 0xFF333333
        };
        final String[] names = {
                "蓝", "青", "橙", "绿", "紫",
                "黄", "浅蓝", "珊瑚", "金", "草绿",
                "薄荷", "琥珀", "叶绿", "天蓝", "玫红",
                "白", "灰", "深灰"
        };
        OreDialogBuilder builder = new OreDialogBuilder(this);
        final AlertDialog[] dialogHolder = new AlertDialog[1];
        builder.setTitle("选择颜色");
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(UiUtils.dp(this, 12), UiUtils.dp(this, 12),
                UiUtils.dp(this, 12), UiUtils.dp(this, 12));
        LinearLayout row = null;
        for (int i = 0; i < palette.length; i++) {
            if (i % 6 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row);
            }
            final int color = palette[i];
            final String name = names[i];
            OreButton swatch = new OreButton(this);
            swatch.setText(name);
            swatch.setTextSize(10);
            swatch.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, UiUtils.dp(this, 36), 1.0f);
            lp.setMargins(UiUtils.dp(this, 2), UiUtils.dp(this, 2),
                    UiUtils.dp(this, 2), UiUtils.dp(this, 2));
            swatch.setLayoutParams(lp);
            swatch.setTextColor(color);
            swatch.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    settings.putInt(key, color);
                    colorView.setText(toHex(color));
                    colorView.setTextColor(color);
                    if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                }
            });
            row.addView(swatch);
        }
        builder.setView(grid);
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
        });
        dialogHolder[0] = builder.show();
    }
}