package com.xtt.mcmodmaker.editor;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.xtt.mcmodmaker.core.ItemRef;
import com.xtt.mcmodmaker.core.ItemRegistry;
import com.xtt.mcmodmaker.core.RecipeModel;
import com.xtt.mcmodmaker.core.RecipeStore;
import com.xtt.mcmodmaker.util.FileUtils;
import com.xtt.mcmodmaker.util.UiUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev1503.oreui.StyleSheet;
import dev1503.oreui.dialog.OreDialogBuilder;
import dev1503.oreui.widgets.OreAlert;
import dev1503.oreui.widgets.OreButton;
import dev1503.oreui.widgets.OreCard;
import dev1503.oreui.widgets.OreEditText;
import dev1503.oreui.widgets.OreSwitch;
import dev1503.oreui.widgets.OreTextView;

/**
 * 配方的可视化编辑器。
 *
 * 与旧版的区别：
 * 数据只活在 {@link RecipeModel} 里，界面是它的投影；改动先改模型再标脏，
 * 由 {@link RecipeStore} 防抖落盘，不再每敲一个字符写一次文件。
 * 物品一律从 {@link ItemPickerDialog} 选，不再手打 id。
 */
public class RecipeEditor {

    /** 宿主（EditorActivity）提供的回调。 */
    public interface Host {
        /** 删除配方后刷新文件列表。 */
        void refreshFileList(File dir);

        /** 在给定容器里打开源码编辑器（顶部模式切换栏保留在上面）。 */
        void showSourceEditor(File file, LinearLayout container);

        /**
         * 把源码编辑器里的改动写回磁盘。
         *
         * @return 内容不合法（比如 JSON 语法错）或保存失败时返回 false，
         *         此时不能切回可视化，否则会拿旧内容盖掉源码
         */
        boolean commitSourceEditor();

        /** 取当前工程的根目录，用于扫描自定义物品。 */
        File projectDir();
    }

    private static final String[] CRAFTING_TAGS = {"crafting_table"};
    private static final String[] SHAPELESS_TAGS = {"crafting_table", "stonecutter", "cartography_table"};
    private static final String[] FURNACE_TAGS = {"furnace", "blast_furnace", "smoker", "campfire"};
    private static final String[] BREWING_TAGS = {"brewing_stand"};
    private static final String[] SMITHING_TAGS = {"smithing_table"};

    /** 有序合成格子的固定高度：有图标和没图标的高度必须一致，否则一排格子参差不齐。 */
    private static final int CELL_HEIGHT_DP = 78;

    private final Context context;
    private final Host host;
    private final RecipeStore store = new RecipeStore();

    private File currentFile;
    private RecipeModel model;
    private LinearLayout contentArea;

    // 有序合成的格子，[行][列]
    private final CellView[][] cells = new CellView[3][3];
    private LinearLayout keySummary;
    private LinearLayout listContainer;
    private LinearLayout issueContainer;
    private OreTextView unlockButton;
    /** 当前是否停在源码模式（模式切换栏上的两个按钮共用它来判断方向）。 */
    private boolean sourceMode;

    /** 一个合成格子的视图：图标在上、名字在下，右上角挂字母角标。 */
    private static final class CellView {
        OreCard card;
        ImageView icon;
        OreTextView name;
        TextView badge;
    }

    public RecipeEditor(Context context, Host host) {
        this.context = context;
        this.host = host;
        store.setListener(new RecipeStore.Listener() {
            @Override
            public void onSaveFailed(String message) {
                Toast.makeText(RecipeEditor.this.context, "保存失败：" + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    // ==================== 入口 ====================

    /** 打开配方编辑器（顶部模式切换栏 + 删除按钮）。 */
    public void open(final File recipeFile, final LinearLayout editorContainer) {
        editorContainer.removeAllViews();
        currentFile = recipeFile;
        sourceMode = false;

        LinearLayout modeBar = new LinearLayout(context);
        modeBar.setOrientation(LinearLayout.HORIZONTAL);
        modeBar.setPadding(0, 0, 0, UiUtils.dp(context, 6));

        final OreButton btnVisual = new OreButton(context);
        btnVisual.setText("可视化编辑");
        btnVisual.setStyleSheet(StyleSheet.STYLE_GREEN);
        final OreButton btnSource = new OreButton(context);
        btnSource.setText("编辑源码");
        btnSource.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        final OreButton btnDelete = new OreButton(context);
        btnDelete.setText("删除配方");
        btnDelete.setStyleSheet(StyleSheet.STYLE_RED);

        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        btnVisual.setLayoutParams(bp);
        btnSource.setLayoutParams(bp);
        btnDelete.setLayoutParams(bp);

        modeBar.addView(btnVisual);
        modeBar.addView(btnSource);
        modeBar.addView(btnDelete);
        editorContainer.addView(modeBar);

        contentArea = new LinearLayout(context);
        contentArea.setOrientation(LinearLayout.VERTICAL);
        contentArea.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        editorContainer.addView(contentArea);

        btnVisual.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (sourceMode) {
                    // 源码改过就先落盘再切回可视化；语法错之类失败就留在源码里改
                    if (!host.commitSourceEditor()) return;
                    store.suspend();
                    sourceMode = false;
                }
                btnVisual.setStyleSheet(StyleSheet.STYLE_GREEN);
                btnSource.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
                showVisual();
            }
        });
        btnSource.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                // 待落盘的改动先写掉，再挂起定时保存，免得后台把源码的改动盖回去
                store.flush();
                store.suspend();
                sourceMode = true;
                btnSource.setStyleSheet(StyleSheet.STYLE_GREEN);
                btnVisual.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
                if (contentArea != null) {
                    contentArea.removeAllViews();
                    host.showSourceEditor(recipeFile, contentArea);
                }
            }
        });
        btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                confirmDelete(recipeFile);
            }
        });

        showVisual();
    }

    /** 离开页面前把没落盘的改动写下去。 */
    public void flushPending() {
        // 停在源码模式时，编辑框里的内容还没有主人，先让它落盘
        if (sourceMode) host.commitSourceEditor();
        store.flush();
    }

    /**
     * 右侧面板的宽度变了（切换浏览 / 聚焦 / 编辑）之后重画一次。
     *
     * 源码模式或还没打开文件时不用管，那种情况由调用方逐层重排就够了。
     */
    public void onModeChanged() {
        if (sourceMode || model == null || currentFile == null) return;
        rebuildVisualKeepScroll();
    }

    // ==================== 可视化主体 ====================

    private void showVisual() {
        showVisual(false);
    }

    /**
     * 画出可视化界面。
     *
     * @param reuseModel true 表示沿用内存里的模型（结构改动后重建界面时用），
     *                   否则从磁盘重新读一遍。不沿用的话，刚改的解锁方式之类
     *                   还没落盘的改动会被磁盘上的旧内容盖掉。
     */
    private void showVisual(boolean reuseModel) {
        if (currentFile == null || contentArea == null) return;
        contentArea.removeAllViews();
        cells[0][0] = null;
        keySummary = null;
        listContainer = null;
        issueContainer = null;
        unlockButton = null;

        RecipeModel parsed;
        boolean mergedOnOpen = false;
        if (reuseModel && model != null) {
            parsed = model;
        } else {
            String raw = FileUtils.readText(currentFile);
            String parseError = null;
            parsed = null;
            try {
                parsed = RecipeModel.parse(raw);
            } catch (Exception e) {
                parseError = String.valueOf(e.getMessage());
            }
            if (parsed == null) {
                contentArea.addView(errorBlock("配方文件解析失败", parseError));
                return;
            }
        }

        model = parsed;
        if (!reuseModel) {
            store.attach(currentFile, model);
            // 打开就先把重复的字母并掉：同一个物品不该占两个字母
            mergedOnOpen = model.mergeSameItems();
        }

        if (!model.supported()) {
            contentArea.addView(errorBlock("这个配方无法可视化编辑",
                    "不是编辑器认识的五种类型（有序 / 无序 / 熔炉 / 酿造 / 锻造），请用源码编辑。"));
            return;
        }

        ScrollView scroll = new ScrollView(context);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(UiUtils.dp(context, 4), UiUtils.dp(context, 2),
                UiUtils.dp(context, 4), UiUtils.dp(context, 12));
        scroll.addView(body);
        contentArea.addView(scroll);

        // 标题区
        body.addView(headerBlock());
        UiUtils.addGap(context, body, UiUtils.dp(context, 8));

        // 校验问题
        issueContainer = new LinearLayout(context);
        issueContainer.setOrientation(LinearLayout.VERTICAL);
        body.addView(issueContainer);

        // 类型专属区
        body.addView(sectionLabel("配方内容"));
        switch (model.type) {
            case RecipeModel.SHAPED:
                buildShaped(body);
                break;
            case RecipeModel.SHAPELESS:
                buildShapeless(body);
                break;
            case RecipeModel.FURNACE:
                buildFurnace(body);
                break;
            case RecipeModel.BREWING:
                buildBrewing(body);
                break;
            case RecipeModel.SMITHING:
                buildSmithing(body);
                break;
            default:
                break;
        }

        // 产出（有序/无序）
        if (model.type == RecipeModel.SHAPED || model.type == RecipeModel.SHAPELESS) {
            UiUtils.addGap(context, body, UiUtils.dp(context, 10));
            body.addView(sectionLabel("产出"));
            body.addView(buildResultBlock());
        }

        // 适用方块
        UiUtils.addGap(context, body, UiUtils.dp(context, 10));
        body.addView(sectionLabel("适用方块"));
        body.addView(buildTagRow());

        // 解锁方式
        UiUtils.addGap(context, body, UiUtils.dp(context, 10));
        body.addView(sectionLabel("解锁方式"));
        body.addView(buildUnlockBlock());

        if (!reuseModel) {
            store.resume();
            // 打开时并掉了重复字母，就把合并结果落盘，免得界面看着和文件对不上
            if (mergedOnOpen) store.markDirty();
        }
        refreshIssues();
    }

    private View headerBlock() {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);

        OreTextView title = new OreTextView(context);
        title.setText(model.localName().isEmpty() ? "（未命名配方）" : model.localName());
        title.setTextColor(Color.WHITE);
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        box.addView(title);

        OreTextView meta = new OreTextView(context);
        meta.setText(model.typeName() + "　·　format_version " + model.formatVersion);
        meta.setTextColor(Color.parseColor("#9A9A9A"));
        meta.setTextSize(10);
        meta.setPadding(0, UiUtils.dp(context, 3), 0, 0);
        box.addView(meta);

        OreTextView ident = new OreTextView(context);
        ident.setText(model.identifier);
        ident.setTextColor(Color.parseColor("#6A6A6A"));
        ident.setTextSize(9);
        ident.setMaxLines(1);
        ident.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        ident.setPadding(0, UiUtils.dp(context, 1), 0, 0);
        box.addView(ident);
        return box;
    }

    private View errorBlock(String title, String detail) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = UiUtils.dp(context, 12);
        box.setPadding(pad, pad, pad, pad);

        OreTextView t = new OreTextView(context);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(13);
        box.addView(t);

        if (detail != null && !detail.isEmpty()) {
            OreTextView d = new OreTextView(context);
            d.setText(detail);
            d.setTextColor(Color.parseColor("#B0B0B0"));
            d.setTextSize(10);
            d.setPadding(0, UiUtils.dp(context, 6), 0, 0);
            box.addView(d);
        }
        return box;
    }

    private OreTextView sectionLabel(String text) {
        OreTextView t = new OreTextView(context);
        t.setText(text);
        t.setTextColor(Color.parseColor("#8FD08F"));
        t.setTextSize(11);
        t.setPadding(0, 0, 0, UiUtils.dp(context, 4));
        return t;
    }

    private void refreshIssues() {
        if (issueContainer == null || model == null) return;
        issueContainer.removeAllViews();
        List<String> errs = model.validate();
        errs.addAll(unknownIdWarnings());
        if (errs.isEmpty()) return;
        OreAlert alert = new OreAlert(context);
        alert.setStyleSheet(StyleSheet.STYLE_ALERT_YELLOW);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < errs.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append("· ").append(errs.get(i));
        }
        alert.setText(sb.toString());
        issueContainer.addView(alert);
        UiUtils.addGap(context, issueContainer, UiUtils.dp(context, 6));
    }

    /**
     * 找出引用了、但在原版库与本工程里都查不到的 id。
     *
     * 这类 id 游戏里不会报错，只是安静地不生效，所以必须在这里点出来。
     */
    private List<String> unknownIdWarnings() {
        List<String> out = new ArrayList<>();
        List<ItemRef> refs = new ArrayList<>();
        if (model.type == RecipeModel.SHAPED) {
            refs.addAll(model.key.values());
        } else if (model.type == RecipeModel.SHAPELESS) {
            refs.addAll(model.ingredients);
        } else if (model.type == RecipeModel.FURNACE) {
            refs.add(model.furnaceInput);
            refs.add(model.furnaceOutput);
        } else if (model.type == RecipeModel.BREWING) {
            refs.add(model.brewInput);
            refs.add(model.brewReagent);
            refs.add(model.brewOutput);
        } else if (model.type == RecipeModel.SMITHING) {
            refs.add(model.smithTemplate);
            refs.add(model.smithBase);
            refs.add(model.smithAddition);
            refs.add(model.smithResult);
        }
        refs.add(model.result);
        refs.addAll(model.extraResults);
        refs.addAll(model.unlockItems);

        List<String> seen = new ArrayList<>();
        for (ItemRef r : refs) {
            if (r == null || r.isEmpty()) continue;
            if (seen.contains(r.id)) continue;
            seen.add(r.id);
            if (!ItemRegistry.exists(context, r.id)) {
                out.add("找不到物品 " + r.id + "，游戏里会静默失效");
            }
        }
        return out;
    }

    private void changed() {
        boolean merged = false;
        if (model != null) {
            model.pruneKey();
            // 同一个物品被分成了两个字母就并回一个，图案里的字母也要跟着重刷
            merged = model.mergeSameItems();
            if (merged) model.pruneKey();
        }
        if (merged) renderGrid();
        refreshIssues();
        store.markDirty();
    }

    // ==================== 有序合成 ====================

    private void buildShaped(LinearLayout parent) {
        LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);

        int m = UiUtils.dp(context, 3);
        for (int r = 0; r < 3; r++) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 3; c++) {
                final int rr = r;
                final int cc = c;

                // 格子 = 面板铺满 + 图标在上名字在下 + 右上角字母角标
                FrameLayout wrapper = new FrameLayout(context);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, UiUtils.dp(context, CELL_HEIGHT_DP), 1f);
                lp.setMargins(m, m, m, m);
                wrapper.setLayoutParams(lp);

                OreCard card = new OreCard(context);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setGravity(Gravity.CENTER);
                card.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
                card.setPadding(UiUtils.dp(context, 3), UiUtils.dp(context, 6),
                        UiUtils.dp(context, 3), UiUtils.dp(context, 6));
                card.setLayoutParams(new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
                card.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        onCellClick(rr, cc);
                    }
                });
                wrapper.addView(card);

                ImageView icon = new ImageView(context);
                int iconPx = UiUtils.dp(context, 32);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(iconPx, iconPx);
                ilp.bottomMargin = UiUtils.dp(context, 3);
                icon.setLayoutParams(ilp);
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                icon.setVisibility(View.GONE);
                card.addView(icon);

                OreTextView name = new OreTextView(context);
                name.setTextSize(9);
                name.setGravity(Gravity.CENTER);
                name.setMaxLines(2);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                name.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                card.addView(name);

                TextView badge = new TextView(context);
                badge.setTextSize(8);
                badge.setTextColor(Color.parseColor("#E8E8E8"));
                badge.setGravity(Gravity.CENTER);
                badge.setPadding(UiUtils.dp(context, 4), 0, UiUtils.dp(context, 4), 0);
                badge.setBackgroundColor(Color.parseColor("#99303030"));
                FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT, UiUtils.dp(context, 13),
                        Gravity.END | Gravity.TOP);
                badge.setLayoutParams(blp);
                wrapper.addView(badge);

                CellView cell = new CellView();
                cell.card = card;
                cell.icon = icon;
                cell.name = name;
                cell.badge = badge;
                cells[r][c] = cell;

                row.addView(wrapper);
            }
            grid.addView(row);
        }
        // 左边九宫格、右边字母映射，并排放在一行里
        LinearLayout combo = new LinearLayout(context);
        combo.setOrientation(LinearLayout.HORIZONTAL);
        combo.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        grid.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.35f));
        combo.addView(grid);

        keySummary = new LinearLayout(context);
        keySummary.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        sp.leftMargin = UiUtils.dp(context, 10);
        keySummary.setLayoutParams(sp);
        combo.addView(keySummary);

        parent.addView(combo);

        renderGrid();
    }

    /** 把模型里的图案和映射刷到界面上。 */
    private void renderGrid() {
        if (model == null) return;
        for (int r = 0; r < 3; r++) {
            String rowStr = model.pattern[r];
            for (int c = 0; c < 3; c++) {
                CellView cell = cells[r][c];
                if (cell == null) continue;
                char letter = c < rowStr.length() ? rowStr.charAt(c) : ' ';
                ItemRef item = letter == ' ' ? null : model.key.get(letter);
                if (letter == ' ') {
                    cell.card.setStyleSheet(StyleSheet.STYLE_INACTIVATED);
                    cell.name.setText("+");
                    cell.name.setTextSize(22);
                    cell.name.setTextColor(Color.parseColor("#6E6E6E"));
                    cell.icon.setVisibility(View.GONE);
                } else if (item == null || item.isEmpty()) {
                    cell.card.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
                    cell.name.setTextSize(9);
                    cell.name.setText("未指定");
                    cell.name.setTextColor(Color.parseColor("#E0A030"));
                    cell.icon.setVisibility(View.GONE);
                } else {
                    cell.card.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
                    cell.name.setTextSize(9);
                    cell.name.setText(ItemRegistry.nameOf(context, item.id));
                    cell.name.setTextColor(Color.WHITE);
                    loadCellIcon(cell, item.id);
                }
                if (letter == ' ') {
                    cell.badge.setVisibility(View.GONE);
                } else {
                    cell.badge.setText(String.valueOf(letter));
                    cell.badge.setVisibility(View.VISIBLE);
                }
            }
        }
        renderKeySummary();
    }

    /**
     * 给格子挂图标。
     *
     * 图标是格子里独立的 ImageView（在名字上方），不再用按钮的 drawableTop：
     * drawableTop 和文字是各自算位置的，格子一高就对不齐。
     */
    private void loadCellIcon(final CellView cell, final String id) {
        cell.icon.setTag(id);
        final int px = UiUtils.dp(context, 32);
        IconLoader.loadBitmap(context, id, host.projectDir(), px, new IconLoader.OnIcon() {
            @Override
            public void onIcon(Bitmap bitmap) {
                // 期间格子可能已经被换成别的物品了，丢弃过期结果
                if (!id.equals(cell.icon.getTag())) return;
                cell.icon.setImageBitmap(bitmap);
                cell.icon.setVisibility(View.VISIBLE);
            }
        });
    }

    private void renderKeySummary() {
        if (keySummary == null || model == null) return;
        keySummary.removeAllViews();

        List<Character> letters = model.usedLetters();
        if (letters.isEmpty()) {
            keySummary.addView(hintText("点上面的格子放材料"));
            return;
        }

        for (final Character letter : letters) {
            keySummary.addView(materialRow(letter, model.key.get(letter), new View.OnClickListener() {
                @Override public void onClick(View v) {
                    showLetterMenu(letter);
                }
            }));
            UiUtils.addGap(context, keySummary, UiUtils.dp(context, 3));
        }
    }

    /**
     * 一条材料行：[字母] [名字] [id]。
     *
     * 名字和 id 相同（清单里没有中文名）时只显示一次，避免
     * 「xxx:yyy  xxx:yyy」这种重复。
     */
    private View materialRow(char letter, ItemRef item, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, UiUtils.dp(context, 3), 0, UiUtils.dp(context, 3));
        row.setOnClickListener(onClick);

        boolean missing = item == null || item.isEmpty();

        TextView key = new TextView(context);
        key.setText(String.valueOf(letter));
        key.setTextSize(11);
        key.setGravity(Gravity.CENTER);
        key.setTextColor(Color.WHITE);
        key.setTypeface(Typeface.DEFAULT_BOLD);
        key.setBackgroundColor(Color.parseColor("#4A4A4A"));
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(
                UiUtils.dp(context, 20), UiUtils.dp(context, 20));
        klp.rightMargin = UiUtils.dp(context, 7);
        key.setLayoutParams(klp);
        row.addView(key);

        if (missing) {
            OreTextView name = new OreTextView(context);
            name.setText("未指定物品");
            name.setTextColor(Color.parseColor("#E0A030"));
            name.setTextSize(11);
            name.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(name);
            return row;
        }

        // 映射这一栏宽度有限，名字在上、id 与附加值缩成一行小字在下面
        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        OreTextView name = new OreTextView(context);
        name.setText(ItemRegistry.nameOf(context, item.id));
        name.setTextColor(Color.parseColor("#DDDDDD"));
        name.setTextSize(11);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(name);

        StringBuilder sub = new StringBuilder(shortId(item));
        if (item.hasData()) sub.append("   data ").append(item.data);
        OreTextView idText = new OreTextView(context);
        idText.setText(sub.toString());
        idText.setTextColor(Color.parseColor(item.hasData() ? "#7ED17E" : "#7A7A7A"));
        idText.setTextSize(9);
        idText.setMaxLines(1);
        idText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(idText);

        row.addView(texts);
        return row;
    }

    /** 从两份清单里找条目，用于判断有没有独立中文名与是不是方块。 */
    private ItemRegistry.Entry findEntry(String id) {
        for (ItemRegistry.Entry e : ItemRegistry.customItems()) {
            if (e.id.equals(id)) return e;
        }
        return null;
    }

    private void onCellClick(final int row, final int col) {
        if (model == null) return;
        char letter = model.pattern[row].charAt(col);
        if (letter == ' ' || model.key.get(letter) == null) {
            pickForCell(row, col);
        } else {
            showCellMenu(row, col);
        }
    }

    /**
     * 格子的修改弹窗：上面是当前物品（图标 + 名字 + id），下面是四个竖排按钮。
     *
     * 「更换物品」改的是字母对应的映射，同字母的格子会一起变；
     * 「单独更换」先给这个格子分一个新字母再改，只影响这一格。
     */
    private void showCellMenu(final int row, final int col) {
        char letter = model.pattern[row].charAt(col);
        final ItemRef item = model.key.get(letter);

        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = UiUtils.dp(context, 8);
        box.setPadding(pad, pad, pad, pad);

        LinearLayout head = new LinearLayout(context);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(context);
        int iconPx = UiUtils.dp(context, 40);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(iconPx, iconPx);
        ilp.rightMargin = UiUtils.dp(context, 10);
        icon.setLayoutParams(ilp);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setVisibility(View.INVISIBLE);
        head.addView(icon);
        if (item != null && !item.isEmpty()) {
            IconLoader.load(context, item.id, host.projectDir(), iconPx, icon);
        }

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        OreTextView name = new OreTextView(context);
        name.setText(item == null || item.isEmpty() ? "未指定物品"
                : ItemRegistry.nameOf(context, item.id));
        name.setTextColor(Color.WHITE);
        name.setTextSize(13);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(name);

        OreTextView idText = new OreTextView(context);
        idText.setText(letter + " = " + (item == null ? "未指定" : item.asString())
                + (item != null && item.hasData() ? "   data " + item.data : ""));
        idText.setTextColor(Color.parseColor("#8A8A8A"));
        idText.setTextSize(10);
        idText.setMaxLines(2);
        texts.addView(idText);

        head.addView(texts);
        box.addView(head);

        // 同字母还出现在别的格子里时才提一句，免得用户以为「单独更换」没区别
        if (item != null && !item.isEmpty() && countLetter(letter) > 1) {
            OreTextView tip = new OreTextView(context);
            tip.setText("这个字母还用在另外 " + (countLetter(letter) - 1) + " 个格子上");
            tip.setTextColor(Color.parseColor("#9A9A9A"));
            tip.setTextSize(9);
            tip.setPadding(0, UiUtils.dp(context, 8), 0, 0);
            box.addView(tip);
        }

        OreDialogBuilder b = new OreDialogBuilder(context);
        b.setTitle("第 " + (row + 1) + " 行 第 " + (col + 1) + " 列");
        b.setView(box);
        b.setButtonOrientation(LinearLayout.VERTICAL);
        b.addButton("更换物品", StyleSheet.STYLE_GREEN, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                d.dismiss();
                pickForCell(row, col);
            }
        });
        b.addButton("单独更换", StyleSheet.STYLE_DARK_GRAY, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                d.dismiss();
                pickForSingleCell(row, col);
            }
        });
        b.addButton("清空", StyleSheet.STYLE_RED, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                d.dismiss();
                clearCell(row, col);
            }
        });
        b.addButton("取消", StyleSheet.STYLE_DARK_GRAY, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                d.dismiss();
            }
        });
        androidx.appcompat.app.AlertDialog dialog = b.show();
        android.view.Window w = dialog.getWindow();
        if (w != null) {
            // 宽度跟物品选择器一致，两个弹窗看起来是一套的
            android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
            w.setLayout((int) (Math.min(dm.widthPixels, dm.heightPixels) * 0.88f),
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    /** 这个字母在图案里出现了几次。 */
    private int countLetter(char letter) {
        int n = 0;
        for (String row : model.pattern) {
            for (int i = 0; i < row.length(); i++) {
                if (row.charAt(i) == letter) n++;
            }
        }
        return n;
    }

    /** 只改这一格：先给它一个新字母，再选物品。 */
    private void pickForSingleCell(final int row, final int col) {
        char existing = model.pattern[row].charAt(col);
        ItemRef current = existing == ' ' ? null : model.key.get(existing);
        pick("第 " + (row + 1) + " 行 第 " + (col + 1) + " 列的物品（只改这一个）",
                current, new ItemPickerDialog.OnPick() {
                    @Override public void onPick(ItemRef item) {
                        char letter = model.nextFreeLetter();
                        model.key.put(letter, item);
                        model.pattern[row] = replaceChar(model.pattern[row], col, letter);
                        renderGrid();
                        changed();
                    }
                });
    }

    private void pickForCell(final int row, final int col) {
        char existing = model.pattern[row].charAt(col);
        ItemRef current = existing == ' ' ? null : model.key.get(existing);
        pick("第 " + (row + 1) + " 行 第 " + (col + 1) + " 列的物品",
                current, new ItemPickerDialog.OnPick() {
                    @Override public void onPick(ItemRef item) {
                        char letter = model.pattern[row].charAt(col);
                        if (letter == ' ') {
                            // 这个物品前面已经用过就直接复用那个字母，不要另开一个新字母
                            char reused = model.letterOf(item);
                            letter = reused != ' ' ? reused : model.nextFreeLetter();
                            if (reused == ' ') model.key.put(letter, item);
                            model.pattern[row] = replaceChar(model.pattern[row], col, letter);
                        } else {
                            model.key.put(letter, item);
                        }
                        renderGrid();
                        changed();
                    }
                });
    }

    private void clearCell(int row, int col) {
        char letter = model.pattern[row].charAt(col);
        model.pattern[row] = replaceChar(model.pattern[row], col, ' ');
        if (letter != ' ' && !patternUsesAnywhere(letter)) {
            model.key.remove(letter);
        }
        renderGrid();
        changed();
    }

    private void showLetterMenu(final Character letter) {
        final ItemRef item = model.key.get(letter);
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(UiUtils.dp(context, 6), UiUtils.dp(context, 6),
                UiUtils.dp(context, 6), UiUtils.dp(context, 6));
        OreTextView info = new OreTextView(context);
        info.setText(letter + " = " + (item == null ? "未指定" : item.asString()));
        info.setTextColor(Color.WHITE);
        info.setTextSize(11);
        box.addView(info);

        OreDialogBuilder b = new OreDialogBuilder(context);
        b.setTitle("材料 " + letter);
        b.setView(box);
        b.setButtonOrientation(LinearLayout.HORIZONTAL);
        b.addButton("更换", StyleSheet.STYLE_GREEN, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                d.dismiss();
                pick("选择 " + letter + " 的物品", item,
                        new ItemPickerDialog.OnPick() {
                            @Override public void onPick(ItemRef picked) {
                                model.key.put(letter, picked);
                                renderGrid();
                                changed();
                            }
                        });
            }
        });
        b.addButton("附加值", StyleSheet.STYLE_DARK_GRAY, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                d.dismiss();
                showDataEditor(item, new ItemSetter() {
                    @Override public void set(ItemRef updated) {
                        model.key.put(letter, updated);
                        renderGrid();
                        changed();
                    }
                });
            }
        });
        b.addButton("全部清空", StyleSheet.STYLE_RED, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                d.dismiss();
                for (int r = 0; r < 3; r++) {
                    String row = model.pattern[r];
                    StringBuilder sb = new StringBuilder(row);
                    for (int c = 0; c < sb.length(); c++) {
                        if (sb.charAt(c) == letter) sb.setCharAt(c, ' ');
                    }
                    model.pattern[r] = sb.toString();
                }
                model.key.remove(letter);
                renderGrid();
                changed();
            }
        });
        b.setNegativeButton("取消", StyleSheet.STYLE_DARK_GRAY, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                d.dismiss();
            }
        });
        b.show();
    }

    private boolean patternUsesAnywhere(char letter) {
        for (String row : model.pattern) {
            if (row.indexOf(letter) >= 0) return true;
        }
        return false;
    }

    private static String replaceChar(String s, int index, char c) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() <= index) sb.append(' ');
        sb.setCharAt(index, c);
        return sb.toString();
    }

    // ==================== 其他四种类型 ====================

    private void buildShapeless(LinearLayout parent) {
        listContainer = new LinearLayout(context);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        parent.addView(listContainer);
        renderShapeless();

        OreButton add = new OreButton(context);
        add.setText("+ 添加原料");
        add.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        add.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        add.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                pick("添加原料", null, new ItemPickerDialog.OnPick() {
                    @Override public void onPick(ItemRef item) {
                        model.ingredients.add(item);
                        renderShapeless();
                        changed();
                    }
                });
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = UiUtils.dp(context, 6);
        add.setLayoutParams(lp);
        parent.addView(add);
    }

    private void renderShapeless() {
        if (listContainer == null) return;
        listContainer.removeAllViews();
        if (model.ingredients.isEmpty()) {
            listContainer.addView(hintText("还没有原料"));
            return;
        }
        for (int i = 0; i < model.ingredients.size(); i++) {
            final int index = i;
            ItemRef r = model.ingredients.get(i);
            listContainer.addView(itemRow(i + 1, r, new ItemSetter() {
                @Override public void set(ItemRef item) {
                    model.ingredients.set(index, item);
                    renderShapeless();
                    changed();
                }
            }, new Runnable() {
                @Override public void run() {
                    model.ingredients.remove(index);
                    renderShapeless();
                    changed();
                }
            }));
        }
    }

    private void buildFurnace(LinearLayout parent) {
        parent.addView(itemPickerRow("输入物品", model.furnaceInput, new ItemSetter() {
            @Override public void set(ItemRef item) {
                model.furnaceInput = item;
                changed();
            }
        }));
        parent.addView(itemPickerRow("输出物品", model.furnaceOutput, new ItemSetter() {
            @Override public void set(ItemRef item) {
                model.furnaceOutput = item;
                changed();
            }
        }));
    }

    private void buildBrewing(LinearLayout parent) {
        parent.addView(itemPickerRow("输入药水", model.brewInput, new ItemSetter() {
            @Override public void set(ItemRef item) {
                model.brewInput = item;
                changed();
            }
        }));
        parent.addView(itemPickerRow("试剂", model.brewReagent, new ItemSetter() {
            @Override public void set(ItemRef item) {
                model.brewReagent = item;
                changed();
            }
        }));
        parent.addView(itemPickerRow("产物", model.brewOutput, new ItemSetter() {
            @Override public void set(ItemRef item) {
                model.brewOutput = item;
                changed();
            }
        }));
    }

    private void buildSmithing(LinearLayout parent) {
        parent.addView(itemPickerRow("锻造模板", model.smithTemplate, new ItemSetter() {
            @Override public void set(ItemRef item) {
                model.smithTemplate = item;
                changed();
            }
        }));
        parent.addView(itemPickerRow("基底", model.smithBase, new ItemSetter() {
            @Override public void set(ItemRef item) {
                model.smithBase = item;
                changed();
            }
        }));
        parent.addView(itemPickerRow("添加物", model.smithAddition, new ItemSetter() {
            @Override public void set(ItemRef item) {
                model.smithAddition = item;
                changed();
            }
        }));
        parent.addView(itemPickerRow("产物", model.smithResult, new ItemSetter() {
            @Override public void set(ItemRef item) {
                model.smithResult = item;
                changed();
            }
        }));
    }

    // ==================== 通用行控件 ====================

    /** 物品赋值回调（不用 java.util.function，minSdk 23 上没有）。 */
    private interface ItemSetter {
        void set(ItemRef item);
    }

    /** 一行「标签 + 物品按钮」：点按钮选物品，长按改附加值。 */
    private View itemPickerRow(String label, final ItemRef current, final ItemSetter setter) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, UiUtils.dp(context, 3), 0, UiUtils.dp(context, 3));

        OreTextView lab = new OreTextView(context);
        lab.setText(label);
        lab.setTextColor(Color.parseColor("#BBBBBB"));
        lab.setTextSize(10);
        lab.setLayoutParams(new LinearLayout.LayoutParams(
                UiUtils.dp(context, 72), LinearLayout.LayoutParams.WRAP_CONTENT));
        row.addView(lab);

        final OreButton btn = new OreButton(context);
        btn.setMaxLines(1);
        btn.setEllipsize(android.text.TextUtils.TruncateAt.END);
        btn.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        bindItem(btn, current);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                pick("选择" + label, currentOfButton(btn),
                        new ItemPickerDialog.OnPick() {
                            @Override public void onPick(ItemRef item) {
                                bindItem(btn, item);
                                setter.set(item);
                            }
                        });
            }
        });
        btn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                showDataEditor(currentOfButton(btn), new ItemSetter() {
                    @Override public void set(ItemRef updated) {
                        if (updated == null) return;
                        bindItem(btn, updated);
                        setter.set(updated);
                    }
                });
                return true;
            }
        });
        row.addView(btn);
        return row;
    }

    private View itemRow(int index, ItemRef item, final ItemSetter setter, final Runnable onDelete) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, UiUtils.dp(context, 3), 0, UiUtils.dp(context, 3));

        OreTextView idx = new OreTextView(context);
        idx.setText(String.valueOf(index));
        idx.setTextColor(Color.parseColor("#8A8A8A"));
        idx.setTextSize(10);
        idx.setLayoutParams(new LinearLayout.LayoutParams(
                UiUtils.dp(context, 22), LinearLayout.LayoutParams.WRAP_CONTENT));
        row.addView(idx);

        final OreButton btn = new OreButton(context);
        btn.setMaxLines(1);
        btn.setEllipsize(android.text.TextUtils.TruncateAt.END);
        btn.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        bindItem(btn, item);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                pick("选择原料", currentOfButton(btn),
                        new ItemPickerDialog.OnPick() {
                            @Override public void onPick(ItemRef picked) {
                                bindItem(btn, picked);
                                setter.set(picked);
                            }
                        });
            }
        });
        btn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                showDataEditor(currentOfButton(btn), new ItemSetter() {
                    @Override public void set(ItemRef updated) {
                        if (updated == null) return;
                        bindItem(btn, updated);
                        setter.set(updated);
                    }
                });
                return true;
            }
        });
        row.addView(btn);

        OreButton del = new OreButton(context);
        del.setText("×");
        del.setStyleSheet(StyleSheet.STYLE_RED);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                UiUtils.dp(context, 48), LinearLayout.LayoutParams.WRAP_CONTENT);
        dlp.leftMargin = UiUtils.dp(context, 4);
        del.setLayoutParams(dlp);
        del.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                onDelete.run();
            }
        });
        row.addView(del);
        return row;
    }

    /** 把物品写进按钮：文字和样式一起，并把引用挂到 tag 上供后续读取。 */
    private void bindItem(OreButton btn, ItemRef item) {
        btn.setTag(item);
        if (item == null || item.isEmpty()) {
            btn.setText("点我选择物品");
            btn.setTextColor(Color.parseColor("#8A8A8A"));
            btn.setStyleSheet(StyleSheet.STYLE_INACTIVATED);
            return;
        }
        btn.setTextColor(Color.WHITE);
        String display = ItemRegistry.nameOf(context, item.id);
        StringBuilder sb = new StringBuilder(display);
        // 只有在确实有别于短 id 的中文名时才补 id，避免重复两遍同一个串
        if (!display.equals(shortId(item))) sb.append("   ").append(shortId(item));
        if (item.hasData()) sb.append("   data ").append(item.data);
        btn.setText(sb.toString());
        btn.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
    }

    /** 统一入口：把当前工程目录一并传下去，自定义方块才能显示图标。 */
    private void pick(String title, ItemRef current, ItemPickerDialog.OnPick onPick) {
        ItemPickerDialog.show(context, title, current, host.projectDir(), onPick);
    }

    private ItemRef currentOfButton(OreButton btn) {
        Object tag = btn.getTag();
        if (tag == null) return null;
        if (tag instanceof ItemRef) return (ItemRef) tag;
        if (tag instanceof String) return ItemRef.parse((String) tag);
        return null;
    }

    private OreTextView hintText(String text) {
        OreTextView t = new OreTextView(context);
        t.setText(text);
        t.setTextColor(Color.parseColor("#8A8A8A"));
        t.setTextSize(10);
        t.setPadding(UiUtils.dp(context, 4), UiUtils.dp(context, 4), 0, UiUtils.dp(context, 4));
        return t;
    }

    // ==================== 产出 ====================

    /** 一件产出：[物品按钮] [数量]。 */
    private View resultRow(final boolean primary, final int extraIndex) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ItemRef current = primary ? model.result
                : (extraIndex < model.extraResults.size() ? model.extraResults.get(extraIndex) : null);

        final OreButton btn = new OreButton(context);
        btn.setMaxLines(1);
        btn.setEllipsize(android.text.TextUtils.TruncateAt.END);
        final LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        btn.setLayoutParams(blp);
        bindItem(btn, current);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                pick(primary ? "选择产出物品" : "选择额外产出",
                        primary ? model.result : currentOfButton(btn),
                        new ItemPickerDialog.OnPick() {
                            @Override public void onPick(ItemRef item) {
                                if (primary) model.result = item;
                                else model.extraResults.set(extraIndex, item);
                                bindItem(btn, item);
                                changed();
                            }
                        });
            }
        });
        btn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                showDataEditor(currentOfButton(btn), new ItemSetter() {
                    @Override public void set(ItemRef updated) {
                        if (updated == null) return;
                        if (primary) model.result = updated;
                        else model.extraResults.set(extraIndex, updated);
                        bindItem(btn, updated);
                        changed();
                    }
                });
                return true;
            }
        });
        row.addView(btn);

        final OreEditText count = new OreEditText(context);
        count.setHint("数量");
        count.setSingleLine(true);
        count.setInputType(InputType.TYPE_CLASS_NUMBER);
        count.setText(String.valueOf(primary ? model.resultCount
                : (current != null && current.count > 0 ? current.count : 1)));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                UiUtils.dp(context, 72), LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = UiUtils.dp(context, 6);
        count.setLayoutParams(clp);
        count.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                String t = s.toString().trim();
                if (t.isEmpty()) return;
                try {
                    int n = Integer.parseInt(t);
                    if (n <= 0) return;
                    if (primary) {
                        if (n == model.resultCount) return;
                        model.resultCount = n;
                    } else {
                        ItemRef r = model.extraResults.get(extraIndex);
                        if (r.count == n) return;
                        r.count = n;
                    }
                    changed();
                } catch (NumberFormatException ignored) {
                }
            }
        });
        row.addView(count);

        // 额外产出可以删掉
        if (!primary) {
            OreButton del = new OreButton(context);
            del.setText("×");
            del.setStyleSheet(StyleSheet.STYLE_RED);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    UiUtils.dp(context, 44), LinearLayout.LayoutParams.WRAP_CONTENT);
            dlp.leftMargin = UiUtils.dp(context, 4);
            del.setLayoutParams(dlp);
            del.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    model.extraResults.remove(extraIndex);
                    rebuildVisualKeepScroll();
                    changed();
                }
            });
            row.addView(del);
        }
        return row;
    }

    /** 产出区：第一件 + 额外产出 + 「添加产出」。 */
    private View buildResultBlock() {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(resultRow(true, -1));

        if (!model.multiResultSupported()) return box;

        for (int i = 0; i < model.extraResults.size(); i++) {
            UiUtils.addGap(context, box, UiUtils.dp(context, 4));
            box.addView(resultRow(false, i));
        }

        OreButton add = new OreButton(context);
        add.setText("+ 添加产出");
        add.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = UiUtils.dp(context, 6);
        add.setLayoutParams(lp);
        add.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                pick("添加额外产出", null, new ItemPickerDialog.OnPick() {
                    @Override public void onPick(ItemRef item) {
                        ItemRef r = item.copy();
                        r.count = 1;
                        model.extraResults.add(r);
                        rebuildVisualKeepScroll();
                        changed();
                    }
                });
            }
        });
        box.addView(add);
        if (!model.extraResults.isEmpty()) {
            box.addView(hintText("第一件进输出槽，其余合成完成后直接放进背包"));
        }
        return box;
    }

    // ==================== 适用方块 ====================

    private View buildTagRow() {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);

        String[] available;
        switch (model.type) {
            case RecipeModel.SHAPED: available = CRAFTING_TAGS; break;
            case RecipeModel.SHAPELESS: available = SHAPELESS_TAGS; break;
            case RecipeModel.FURNACE: available = FURNACE_TAGS; break;
            case RecipeModel.BREWING: available = BREWING_TAGS; break;
            default: available = SMITHING_TAGS; break;
        }

        for (final String tag : available) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            final OreSwitch sw = new OreSwitch(context);
            sw.setChecked(model.tags.contains(tag));
            sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                    if (checked) {
                        if (!model.tags.contains(tag)) model.tags.add(tag);
                    } else {
                        model.tags.remove(tag);
                    }
                    changed();
                }
            });
            row.addView(sw);

            OreTextView lab = new OreTextView(context);
            lab.setText(tagDisplay(tag));
            lab.setTextColor(Color.parseColor("#CCCCCC"));
            lab.setTextSize(10);
            lab.setPadding(UiUtils.dp(context, 6), 0, 0, 0);
            row.addView(lab);

            box.addView(row);
        }

        if (model.tags.isEmpty()) {
            box.addView(hintText("没有勾选任何方块，这条配方不会在任何地方出现"));
        }
        return box;
    }

    private static String tagDisplay(String tag) {
        switch (tag) {
            case "crafting_table": return "工作台";
            case "stonecutter": return "切石机";
            case "cartography_table": return "制图台";
            case "furnace": return "熔炉";
            case "blast_furnace": return "高炉";
            case "smoker": return "烟熏炉";
            case "campfire": return "营火";
            case "brewing_stand": return "酿造台";
            case "smithing_table": return "锻造台";
            default: return tag;
        }
    }

    // ==================== 解锁方式 ====================

    private View buildUnlockBlock() {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);

        OreButton btn = new OreButton(context);
        btn.setText(unlockLabel(model.unlockMode));
        btn.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btn.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showUnlockMenu();
            }
        });
        box.addView(btn);

        if (model.unlockMode == RecipeModel.UNLOCK_ITEMS) {
            for (int i = 0; i < model.unlockItems.size(); i++) {
                final int index = i;
                box.addView(itemRow(i + 1, model.unlockItems.get(i), new ItemSetter() {
                    @Override public void set(ItemRef item) {
                        model.unlockItems.set(index, item);
                        changed();
                    }
                }, new Runnable() {
                    @Override public void run() {
                        model.unlockItems.remove(index);
                        rebuildVisualKeepScroll();
                        changed();
                    }
                }));
            }
            OreButton add = new OreButton(context);
            add.setText("+ 添加解锁物品");
            add.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
            add.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    pick("添加解锁物品", null, new ItemPickerDialog.OnPick() {
                        @Override public void onPick(ItemRef item) {
                            model.unlockItems.add(item);
                            rebuildVisualKeepScroll();
                            changed();
                        }
                    });
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = UiUtils.dp(context, 4);
            add.setLayoutParams(lp);
            box.addView(add);
            box.addView(hintText("玩家获得上面任意一件物品后，这条配方才会出现在配方书里"));
        }
        return box;
    }

    private void showUnlockMenu() {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(UiUtils.dp(context, 6), UiUtils.dp(context, 6),
                UiUtils.dp(context, 6), UiUtils.dp(context, 6));
        OreTextView tip = new OreTextView(context);
        tip.setText("当前：" + currentUnlockLabel());
        tip.setTextColor(Color.parseColor("#9A9A9A"));
        tip.setTextSize(10);
        box.addView(tip);

        OreDialogBuilder b = new OreDialogBuilder(context);
        b.setTitle("解锁方式");
        b.setView(box);
        b.addButton("默认解锁（始终可见）", StyleSheet.STYLE_GREEN, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                d.dismiss();
                model.unlockMode = RecipeModel.UNLOCK_ALWAYS;
                rebuildVisualKeepScroll();
                changed();
            }
        });
        b.addButton("获得特定物品时解锁", StyleSheet.STYLE_DARK_GRAY, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                d.dismiss();
                model.unlockMode = RecipeModel.UNLOCK_ITEMS;
                if (model.unlockItems.isEmpty()) model.unlockItems.add(new ItemRef("minecraft:apple"));
                rebuildVisualKeepScroll();
                changed();
            }
        });
        b.addButton("玩家在水中时解锁", StyleSheet.STYLE_DARK_GRAY, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                d.dismiss();
                model.unlockMode = RecipeModel.UNLOCK_WATER;
                rebuildVisualKeepScroll();
                changed();
            }
        });
        b.addButton("不写解锁字段", StyleSheet.STYLE_DARK_GRAY, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                d.dismiss();
                model.unlockMode = RecipeModel.UNLOCK_NONE;
                rebuildVisualKeepScroll();
                changed();
            }
        });
        b.show();
    }

    private String currentUnlockLabel() {
        switch (model.unlockMode) {
            case RecipeModel.UNLOCK_ITEMS: return "获得特定物品时解锁";
            case RecipeModel.UNLOCK_WATER: return "玩家在水中时解锁";
            case RecipeModel.UNLOCK_NONE: return "不写解锁字段";
            default: return "默认解锁";
        }
    }

    private static String unlockLabel(int mode) {
        switch (mode) {
            case RecipeModel.UNLOCK_ITEMS: return "获得特定物品时解锁  （点击修改）";
            case RecipeModel.UNLOCK_WATER: return "玩家在水中时解锁  （点击修改）";
            case RecipeModel.UNLOCK_NONE: return "不写解锁字段  （点击修改）";
            default: return "默认解锁（始终可见）  （点击修改）";
        }
    }

    /** 结构变了要整体重建，但把滚动位置和模型带过去，不让用户以为自己跳页了。 */
    private void rebuildVisualKeepScroll() {
        if (contentArea == null) return;
        int y = 0;
        View child = contentArea.getChildAt(1);
        if (child instanceof ScrollView) y = ((ScrollView) child).getScrollY();
        showVisual(true);
        View again = contentArea.getChildAt(1);
        if (again instanceof ScrollView) ((ScrollView) again).scrollTo(0, y);
    }

    // ==================== 附加值 ====================

    private void showDataEditor(final ItemRef item, final ItemSetter setter) {
        if (item == null || item.isEmpty()) {
            Toast.makeText(context, "先选一个物品再改附加值", Toast.LENGTH_SHORT).show();
            return;
        }
        final OreEditText input = new OreEditText(context);
        input.setHint("留空或 -1 表示不写 data");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setText(item.hasData() ? String.valueOf(item.data) : "");

        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = UiUtils.dp(context, 8);
        box.setPadding(pad, pad, pad, pad);

        OreTextView info = new OreTextView(context);
        info.setText(item.id + "\n只有羊毛、染色玻璃这类分变体的物品才需要填附加值");
        info.setTextColor(Color.parseColor("#AAAAAA"));
        info.setTextSize(9);
        info.setPadding(0, 0, 0, UiUtils.dp(context, 6));
        box.addView(info);
        box.addView(input);

        OreDialogBuilder b = new OreDialogBuilder(context);
        b.setTitle("附加值");
        b.setView(box);
        b.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                String t = input.getText().toString().trim();
                ItemRef copy = item.copy();
                if (t.isEmpty() || "-1".equals(t)) {
                    copy.data = ItemRef.UNSET;
                } else {
                    try {
                        copy.data = Integer.parseInt(t);
                    } catch (NumberFormatException e) {
                        Toast.makeText(context, "请输入整数", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                d.dismiss();
                setter.set(copy);
            }
        });
        b.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                d.dismiss();
            }
        });
        b.show();
    }

    // ==================== 删除 ====================

    private void confirmDelete(final File recipeFile) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = UiUtils.dp(context, 12);
        box.setPadding(pad, pad, pad, pad);
        OreTextView msg = new OreTextView(context);
        msg.setText("确定删除配方 \"" + recipeFile.getName() + "\" 吗？\n此操作不可恢复。");
        msg.setTextColor(Color.WHITE);
        msg.setTextSize(11);
        box.addView(msg);

        OreDialogBuilder b = new OreDialogBuilder(context);
        b.setTitle("删除配方");
        b.setView(box);
        b.setPositiveButton("删除", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                store.discard();
                File parent = recipeFile.getParentFile();
                if (recipeFile.delete()) {
                    Toast.makeText(context, "配方已删除", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show();
                }
                host.refreshFileList(parent);
                d.dismiss();
            }
        });
        b.getPositiveButton().setStyleSheet(StyleSheet.STYLE_RED);
        b.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                d.dismiss();
            }
        });
        b.show();
    }

    // ==================== 小工具 ====================

    private static String shorten(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String shortId(ItemRef item) {
        if (item == null || item.id == null) return "";
        String id = item.id;
        return id.startsWith("minecraft:") ? id.substring(10) : id;
    }
}
