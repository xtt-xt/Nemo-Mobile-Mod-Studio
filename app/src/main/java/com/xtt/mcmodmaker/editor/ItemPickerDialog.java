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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import com.xtt.mcmodmaker.core.ItemRef;
import com.xtt.mcmodmaker.core.ItemRegistry;
import com.xtt.mcmodmaker.util.UiUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import dev1503.oreui.StyleSheet;
import dev1503.oreui.dialog.OreDialogBuilder;
import dev1503.oreui.widgets.OreCard;
import dev1503.oreui.widgets.OreEditText;
import dev1503.oreui.widgets.OreTextView;

/**
 * 物品选择器：搜索 + 三列九宫格点选，替掉所有手打物品 id 的地方。
 *
 * 每个格子是「图在上、名字在下」：方块渲染成立方体，普通物品用原版贴图，
 * 缺图的一律上占位图，所以一屏里格子的排布永远是齐的。
 * 空查询先列出本工程的自定义物品和常用原版物品，输入关键字后按
 * 「id 前缀 → 名称前缀 → 包含」三层优先排序。
 */
public final class ItemPickerDialog {

    /** 选中回调。选中的 ItemRef 里 data 可能带也可能不带，由调用方决定是否要改。 */
    public interface OnPick {
        void onPick(ItemRef item);
    }

    private static final int COLUMNS = 3;
    private static final int CELL_HEIGHT_DP = 94;
    /** 每屏先画这么多行，滑到底再追加一批：一次画全部会明显卡一下。 */
    private static final int PAGE_ROWS = 4;
    /** 候选总数不设上限，滚到底就把剩下的全放出来。 */
    private static final int MAX_ROWS = 5000;
    private static final long SEARCH_DEBOUNCE_MS = 180;
    /** 滚动停手多久才补下一批。滚动中加行会改内容高度，列表会跳一下。 */
    private static final long LOAD_MORE_DELAY_MS = 120;

    private final Context context;
    private final OnPick onPick;
    private final Handler main = new Handler(Looper.getMainLooper());
    /** 图标用的工程目录，自定义方块的贴图从这里读。 */
    private File projectDirForIcons;

    private OreEditText searchBox;
    private LinearLayout gridContainer;
    private ScrollView scroll;
    private androidx.appcompat.app.AlertDialog dialog;

    /** 当前的候选，翻页时从里面取。 */
    private List<ItemRegistry.Entry> results = new ArrayList<>();
    /** 已经画出来的条数。 */
    private int shownCount;

    private final Runnable rebuildTask = new Runnable() {
        @Override
        public void run() {
            rebuildResults();
        }
    };

    private final Runnable loadMoreTask = new Runnable() {
        @Override
        public void run() {
            appendPage();
        }
    };

    private ItemPickerDialog(Context context, OnPick onPick) {
        this.context = context;
        this.onPick = onPick;
    }

    private File projectDir() {
        return projectDirForIcons;
    }

    /**
     * 弹出选择器。
     *
     * @param current 当前已选物品，用于在列表里标出「当前」
     */
    public static void show(Context context, String title, ItemRef current, OnPick onPick) {
        show(context, title, current, null, onPick);
    }

    /** 带工程目录的版本：自定义方块的贴图要从工程里读，才能显示图标。 */
    public static void show(Context context, String title, ItemRef current,
                            File projectDir, OnPick onPick) {
        ItemPickerDialog d = new ItemPickerDialog(context, onPick);
        d.projectDirForIcons = projectDir;
        d.open(title, current);
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

        scroll = new ScrollView(context);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        scroll.setClipToPadding(false);
        gridContainer = new LinearLayout(context);
        gridContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(gridContainer);
        content.addView(scroll);

        // 滑到底自动续下一批。滚动过程中每一帧都加行会让列表高度连续变化，
        // 手感上就是「弹一下」，所以等停手之后再补。
        scroll.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int x, int y, int oldX, int oldY) {
                View child = scroll.getChildAt(0);
                if (child == null) return;
                boolean nearBottom = y + scroll.getHeight() >= child.getHeight() - UiUtils.dp(context, 200);
                main.removeCallbacks(loadMoreTask);
                if (nearBottom) main.postDelayed(loadMoreTask, LOAD_MORE_DELAY_MS);
            }
        });

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
            int width = (int) (Math.min(dm.widthPixels, dm.heightPixels) * 0.88f);
            int height = (int) (dm.heightPixels * 0.84f);
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

    /** 重新搜索结果，并画第一批。 */
    private void rebuildResults() {
        if (gridContainer == null) return;
        String query = searchBox == null ? "" : searchBox.getText().toString().trim();
        main.removeCallbacks(loadMoreTask);
        results = ItemRegistry.search(context, query, MAX_ROWS);
        gridContainer.removeAllViews();
        shownCount = 0;

        if (results.isEmpty()) {
            OreTextView empty = new OreTextView(context);
            empty.setText("没有匹配的物品，可以点「手动输入」直接填 id");
            empty.setTextColor(Color.parseColor("#AAAAAA"));
            empty.setTextSize(11);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, UiUtils.dp(context, 24), 0, 0);
            gridContainer.addView(empty);
            return;
        }
        appendPage();
        if (scroll != null) scroll.scrollTo(0, 0);
    }

    /** 追加一页格子。 */
    private void appendPage() {
        if (gridContainer == null || results == null) return;
        int end = Math.min(shownCount + PAGE_ROWS * COLUMNS, results.size());
        if (end <= shownCount) return;

        // 上一页末行可能没填满，接着往里补，不要另起一行留个空洞
        LinearLayout row = null;
        for (int i = shownCount; i < end; i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                gridContainer.addView(row);
                UiUtils.addGap(context, gridContainer, UiUtils.dp(context, 4));
            }
            row.addView(buildCell(results.get(i)), cellParams());
            // 末尾不足一行时补空位，保证每格一样宽
            if (i == results.size() - 1) {
                for (int j = row.getChildCount(); j < COLUMNS; j++) {
                    row.addView(new View(context), cellParams());
                }
            }
        }
        shownCount = end;
    }

    private LinearLayout.LayoutParams cellParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, UiUtils.dp(context, CELL_HEIGHT_DP), 1f);
        int m = UiUtils.dp(context, 2);
        lp.setMargins(m, m, m, m);
        return lp;
    }

    /** 一个格子：图标 + 名字 + id，整块可点。 */
    private View buildCell(final ItemRegistry.Entry entry) {
        OreCard card = new OreCard(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(UiUtils.dp(context, 3), UiUtils.dp(context, 5),
                UiUtils.dp(context, 3), UiUtils.dp(context, 5));

        ImageView icon = new ImageView(context);
        int iconSize = UiUtils.dp(context, 32);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(iconSize, iconSize);
        ilp.bottomMargin = UiUtils.dp(context, 3);
        icon.setLayoutParams(ilp);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        card.addView(icon);
        IconLoader.load(context, entry.id, projectDir(), iconSize, icon);

        OreTextView name = new OreTextView(context);
        name.setText(entry.display());
        name.setTextColor(entry.custom ? Color.parseColor("#9BE89B") : Color.WHITE);
        name.setTextSize(10);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER);
        name.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(name);

        // 名字和 id 不同才补 id；相同就没必要重复一遍
        if (entry.hasDistinctName() || entry.custom) {
            OreTextView idText = new OreTextView(context);
            idText.setText(shortId(entry.id));
            idText.setTextColor(Color.parseColor("#7A7A7A"));
            idText.setTextSize(9);
            idText.setMaxLines(1);
            idText.setEllipsize(android.text.TextUtils.TruncateAt.END);
            idText.setGravity(Gravity.CENTER);
            idText.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            card.addView(idText);
        }

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
