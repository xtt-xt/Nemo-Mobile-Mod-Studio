package com.xtt.mcmodmaker.editor;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.Window;

import androidx.appcompat.app.AlertDialog;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import com.xtt.mcmodmaker.core.ItemRef;
import com.xtt.mcmodmaker.core.ItemRegistry;
import com.xtt.mcmodmaker.util.UiUtils;

import java.util.ArrayList;
import java.util.List;

import dev1503.oreui.StyleSheet;
import dev1503.oreui.dialog.OreDialogBuilder;
import dev1503.oreui.widgets.OreButton;
import dev1503.oreui.widgets.OreCard;
import dev1503.oreui.widgets.OreEditText;
import dev1503.oreui.widgets.OreTextView;

/**
 * 物品选择器：搜索 + 点选，替掉所有手打物品 id 的地方。
 *
 * 空查询先列出本工程的自定义物品和常用原版物品，输入关键字后按
 * 「id 前缀 → 名称前缀 → 包含」三层优先排序。
 */
public final class ItemPickerDialog {

    /** 选中回调。选中的 ItemRef 里 data 可能带也可能不带，由调用方决定是否要改。 */
    public interface OnPick {
        void onPick(ItemRef item);
    }

    private static final int MAX_ROWS = 60;
    private static final long SEARCH_DEBOUNCE_MS = 180;

    private final Context context;
    private final OnPick onPick;
    private final Handler main = new Handler(Looper.getMainLooper());

    private OreEditText searchBox;
    private LinearLayout resultContainer;
    private OreTextView statusText;
    private androidx.appcompat.app.AlertDialog dialog;

    private final Runnable rebuildTask = new Runnable() {
        @Override
        public void run() {
            rebuildResults();
        }
    };

    private ItemPickerDialog(Context context, OnPick onPick) {
        this.context = context;
        this.onPick = onPick;
    }

    /**
     * 弹出选择器。
     *
     * @param current 当前已选物品，用于在列表里标出「当前」
     */
    public static void show(Context context, String title, ItemRef current, OnPick onPick) {
        new ItemPickerDialog(context, onPick).open(title, current);
    }

    private void open(String title, ItemRef current) {
        OreDialogBuilder builder = new OreDialogBuilder(context);
        builder.setTitle(title == null ? "选择物品" : title);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = UiUtils.dp(context, 4);
        content.setPadding(pad, pad, pad, 0);

        searchBox = new OreEditText(context);
        searchBox.setHint("搜索物品（中文名或 id）");
        searchBox.setSingleLine(true);
        searchBox.setInputType(InputType.TYPE_CLASS_TEXT);
        searchBox.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        content.addView(searchBox);

        if (current != null && !current.isEmpty()) {
            OreTextView cur = new OreTextView(context);
            cur.setText("当前：" + current.asString());
            cur.setTextColor(Color.parseColor("#AAAAAA"));
            cur.setTextSize(10);
            cur.setPadding(UiUtils.dp(context, 6), UiUtils.dp(context, 4), 0, 0);
            content.addView(cur);
        }

        statusText = new OreTextView(context);
        statusText.setTextColor(Color.parseColor("#AAAAAA"));
        statusText.setTextSize(10);
        statusText.setPadding(UiUtils.dp(context, 6), UiUtils.dp(context, 4), 0, UiUtils.dp(context, 2));
        content.addView(statusText);

        ScrollView scroll = new ScrollView(context);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        resultContainer = new LinearLayout(context);
        resultContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(resultContainer);
        content.addView(scroll);

        builder.getRootLayout().addView(content,
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        builder.setNeutralButton("手动输入", StyleSheet.STYLE_DARK_GRAY, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                showManualInput();
            }
        });
        builder.setNegativeButton("关闭", StyleSheet.STYLE_DARK_GRAY, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                d.dismiss();
            }
        });

        dialog = builder.show();

        // 不设高度的话，weight=1 的内容在 wrap_content 里会被算成 0
        Window w = dialog.getWindow();
        if (w != null) {
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            int width = (int) (Math.min(dm.widthPixels, dm.heightPixels) * 0.72f);
            int height = (int) (dm.heightPixels * 0.82f);
            w.setLayout(width, height);
        }

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override public void afterTextChanged(Editable s) {
                main.removeCallbacks(rebuildTask);
                main.postDelayed(rebuildTask, SEARCH_DEBOUNCE_MS);
            }
        });

        rebuildResults();
    }

    private void rebuildResults() {
        if (resultContainer == null) return;
        resultContainer.removeAllViews();

        String query = searchBox == null ? "" : searchBox.getText().toString().trim();
        List<ItemRegistry.Entry> all = ItemRegistry.search(context, query, MAX_ROWS + 50);

        if (all.isEmpty()) {
            OreTextView empty = new OreTextView(context);
            empty.setText("没有匹配的物品，可以点「手动输入」直接填 id");
            empty.setTextColor(Color.parseColor("#AAAAAA"));
            empty.setTextSize(11);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, UiUtils.dp(context, 24), 0, 0);
            resultContainer.addView(empty);
            statusText.setText("");
            return;
        }

        int shown = Math.min(all.size(), MAX_ROWS);
        for (int i = 0; i < shown; i++) {
            resultContainer.addView(buildRow(all.get(i)));
            UiUtils.addGap(context, resultContainer, UiUtils.dp(context, 3));
        }

        if (all.size() > shown) {
            statusText.setText("显示 " + shown + " / 共 " + all.size() + " 条，输入更多关键字可缩小范围");
        } else {
            statusText.setText("共 " + all.size() + " 条");
        }
    }

    private View buildRow(final ItemRegistry.Entry entry) {
        OreCard card = new OreCard(context);
        card.setPadding(UiUtils.dp(context, 8), UiUtils.dp(context, 6),
                UiUtils.dp(context, 8), UiUtils.dp(context, 6));
        card.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        OreTextView name = new OreTextView(context);
        name.setText(entry.display());
        name.setTextColor(entry.custom ? Color.parseColor("#9BE89B") : Color.WHITE);
        name.setTextSize(12);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(name);

        // 名字和 id 相同就没必要重复；有中文名时把 id 用小字附上便于核对
        if (entry.hasDistinctName() || entry.custom) {
            OreTextView idText = new OreTextView(context);
            idText.setText(shortId(entry.id));
            idText.setTextColor(Color.parseColor("#7A7A7A"));
            idText.setTextSize(9);
            idText.setMaxLines(1);
            idText.setEllipsize(android.text.TextUtils.TruncateAt.START);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            ilp.leftMargin = UiUtils.dp(context, 6);
            idText.setLayoutParams(ilp);
            row.addView(idText);
        }

        OreTextView kind = new OreTextView(context);
        kind.setText(entry.custom ? "本工程" : (entry.block ? "方块" : "物品"));
        kind.setTextColor(entry.custom ? Color.parseColor("#E0C060")
                : (entry.block ? Color.parseColor("#8AA8C0") : Color.parseColor("#8A8A8A")));
        kind.setTextSize(8);
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        klp.leftMargin = UiUtils.dp(context, 8);
        kind.setLayoutParams(klp);
        row.addView(kind);

        card.addView(row);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pick(new ItemRef(entry.id));
            }
        });
        return card;
    }

    private static String shortId(String id) {
        return id != null && id.startsWith("minecraft:") ? id.substring(10) : id;
    }

    private void pick(ItemRef item) {
        if (dialog != null) dialog.dismiss();
        onPick.onPick(item);
    }

    private void showManualInput() {
        final OreEditText input = new OreEditText(context);
        input.setHint("如 minecraft:wool:14");
        input.setSingleLine(true);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = UiUtils.dp(context, 8);
        container.setPadding(pad, pad, pad, pad);
        container.addView(input);

        OreDialogBuilder b = new OreDialogBuilder(context);
        b.setTitle("手动输入物品");
        b.setView(container);
        b.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                String text = input.getText().toString().trim();
                ItemRef ref = ItemRef.parse(text);
                if (ref == null || ref.isEmpty()) {
                    Toast.makeText(context, "请输入物品 id", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!ref.id.contains(":")) {
                    Toast.makeText(context, "id 需要带命名空间，例如 minecraft:apple",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                d.dismiss();
                pick(ref);
            }
        });
        b.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                d.dismiss();
            }
        });
        b.getNegativeButton().setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        b.show();
    }
}
