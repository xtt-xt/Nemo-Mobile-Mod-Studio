package com.xtt.mcmodmaker.editor;

import android.content.Context;

import com.xtt.mcmodmaker.HighlightSettingsActivity;
import com.xtt.mcmodmaker.util.SettingsManager;

import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import io.github.rosemoe.sora.widget.schemes.SchemeVS2019;

/**
 * 用户自定义颜色方案：读取"代码高亮设置"中的颜色配置，
 * 覆盖 sora-editor 默认的语法颜色（关键字/注释/字符串/数字/函数名）。
 * 继承 SchemeVS2019（深色主题）而非 EditorColorScheme（浅色基类），
 * 保证编辑器背景保持深色。
 */
public class UserColorScheme extends SchemeVS2019 {

    private final SettingsManager prefs;

    public UserColorScheme(Context context) {
        prefs = SettingsManager.getInstance(context);
        // 补全 SchemeVS2019 未显式定义的颜色，确保非透明（防止键名等渲染成透明/黑色）
        setColor(EditorColorScheme.ATTRIBUTE_NAME, 0xFF9CDCFE);   // 属性名：浅蓝
        setColor(EditorColorScheme.ATTRIBUTE_VALUE, 0xFFCE9178);  // 属性值：暖橙
        setColor(EditorColorScheme.OPERATOR, 0xFFD4D4D4);         // 运算符：浅灰
    }

    @Override
    public int getColor(int colorId) {
        // 注意：父类 EditorColorScheme 构造函数内部（applyDefault/setColor）
        // 会回调本方法，此时 prefs 尚未初始化（null），必须回退默认色。
        if (prefs == null) {
            return super.getColor(colorId);
        }
        switch (colorId) {
            case KEYWORD:
                return prefs.getInt(HighlightSettingsActivity.KEY_COLOR_KEYWORD, super.getColor(colorId));
            case COMMENT:
                return prefs.getInt(HighlightSettingsActivity.KEY_COLOR_COMMENT, super.getColor(colorId));
            case LITERAL:
                // 字符串 / 数字统一走 LITERAL（VS2019 主题中为暖橙色）
                return prefs.getInt(HighlightSettingsActivity.KEY_COLOR_STRING, super.getColor(colorId));
            case FUNCTION_NAME:
                return prefs.getInt(HighlightSettingsActivity.KEY_COLOR_FUNCTION, super.getColor(colorId));
            default:
                return super.getColor(colorId);
        }
    }
}