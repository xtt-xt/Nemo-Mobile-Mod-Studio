package com.xtt.mcmodmaker.editor;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import com.xtt.mcmodmaker.util.FileUtils;
import com.xtt.mcmodmaker.util.UiUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import dev1503.oreui.StyleSheet;
import dev1503.oreui.dialog.OreDialogBuilder;
import dev1503.oreui.widgets.OreAlert;
import dev1503.oreui.widgets.OreButton;
import dev1503.oreui.widgets.OreEditText;
import dev1503.oreui.widgets.OreSwitch;
import dev1503.oreui.widgets.OreTextView;

/**
 * 配方可视化编辑器。
 * 支持：有序/无序合成、熔炉、酿造、锻造配方的可视化编辑与自动保存。
 */
public class RecipeEditor {

    /** 宿主（EditorActivity）提供的回调。 */
    public interface Host {
        /** 删除配方后刷新文件列表。 */
        void refreshFileList(File dir);

        /** 切换到源码模式。 */
        void showSourceEditor(File file);
    }

    private final Context context;
    private final Host host;

    private File currentRecipeFile;
    private LinearLayout recipeContentArea;

    private GridLayout recipeGrid;
    private LinearLayout recipeMappingLayout;
    private Set<String> selectedTags = new HashSet<>();
    private JSONObject currentRecipeRoot;
    private String currentRecipeType;

    private OreEditText visualResultItem;
    private OreEditText visualResultCount;

    private String currentUnlockContext = "AlwaysUnlocked";
    private List<String> unlockItems = new ArrayList<>();
    private List<String> shapelessInputs = new ArrayList<>();
    private List<String> furnaceInputs = new ArrayList<>();
    private OreEditText brewingInputItem;
    private OreEditText brewingReagentItem;
    private OreEditText smithingTemplateItem;
    private OreEditText smithingBaseItem;
    private OreEditText smithingAdditionItem;

    public RecipeEditor(Context context, Host host) {
        this.context = context;
        this.host = host;
    }

    // ==================== 入口 ====================

    /** 打开配方编辑器（含模式切换栏、删除按钮）。 */
    public void open(final File recipeFile, final LinearLayout editorContainer) {
        editorContainer.removeAllViews();

        LinearLayout modeBar = new LinearLayout(context);
        modeBar.setOrientation(LinearLayout.HORIZONTAL);
        modeBar.setPadding(0, 0, 0, 8);

        final OreButton btnVisual = new OreButton(context);
        btnVisual.setText("可视化编辑");
        btnVisual.setStyleSheet(StyleSheet.STYLE_GREEN);
        final OreButton btnSource = new OreButton(context);
        btnSource.setText("编辑源码");
        btnSource.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        final OreButton btnDelete = new OreButton(context);
        btnDelete.setText("删除配方");
        btnDelete.setStyleSheet(StyleSheet.STYLE_RED);

        LinearLayout.LayoutParams btnParam = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        btnVisual.setLayoutParams(btnParam);
        btnSource.setLayoutParams(btnParam);
        btnDelete.setLayoutParams(btnParam);

        modeBar.addView(btnVisual);
        modeBar.addView(btnSource);
        modeBar.addView(btnDelete);
        editorContainer.addView(modeBar);

        final LinearLayout contentArea = new LinearLayout(context);
        contentArea.setOrientation(LinearLayout.VERTICAL);
        contentArea.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        editorContainer.addView(contentArea);

        showVisual(contentArea, recipeFile);

        btnVisual.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnVisual.setStyleSheet(StyleSheet.STYLE_GREEN);
                btnSource.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
                contentArea.removeAllViews();
                showVisual(contentArea, recipeFile);
            }
        });
        btnSource.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnSource.setStyleSheet(StyleSheet.STYLE_GREEN);
                btnVisual.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
                host.showSourceEditor(recipeFile);
            }
        });
        btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDeleteRecipeConfirm(recipeFile);
            }
        });
    }

    /** 在指定容器中展示可视化编辑界面。 */
    public void showVisual(final LinearLayout container, final File recipeFile) {
        currentRecipeFile = recipeFile;
        recipeContentArea = container;

        ScrollView scroll = new ScrollView(context);
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(8, 8, 8, 8);

        JSONObject root;
        try {
            root = new JSONObject(FileUtils.readText(recipeFile));
        } catch (Exception e) {
            OreTextView errText = new OreTextView(context);
            errText.setText("无法解析配方");
            errText.setTextColor(Color.WHITE);
            layout.addView(errText);
            scroll.addView(layout);
            container.addView(scroll);
            return;
        }
        currentRecipeRoot = root;

        // 识别配方类型
        String recipeType = "";
        JSONObject body = null;
        String[] typeKeys = {
                "minecraft:recipe_shaped", "minecraft:recipe_shapeless",
                "minecraft:recipe_furnace", "minecraft:recipe_brewing_mix",
                "minecraft:recipe_smithing_transform"
        };
        String[] typeIds = {
                "recipe_shaped", "recipe_shapeless",
                "recipe_furnace", "recipe_brewing_mix", "recipe_smithing_transform"
        };
        for (int i = 0; i < typeKeys.length; i++) {
            if (root.has(typeKeys[i])) {
                recipeType = typeIds[i];
                body = root.optJSONObject(typeKeys[i]);
                break;
            }
        }
        currentRecipeType = recipeType;
        if (body == null) {
            OreTextView errText = new OreTextView(context);
            errText.setText("不支持的配方类型");
            errText.setTextColor(Color.WHITE);
            layout.addView(errText);
            scroll.addView(layout);
            container.addView(scroll);
            currentRecipeRoot = null;
            return;
        }

        // 类型与名称
        OreTextView typeLabel = new OreTextView(context);
        String typeDisplay = recipeType.equals("recipe_shaped") ? "有序合成"
                : recipeType.equals("recipe_shapeless") ? "无序合成"
                : recipeType.equals("recipe_furnace") ? "熔炉"
                : recipeType.equals("recipe_brewing_mix") ? "酿造" : "锻造";
        typeLabel.setText("类型: " + typeDisplay);
        typeLabel.setTextColor(Color.WHITE);
        layout.addView(typeLabel);

        JSONObject description = body.optJSONObject("description");
        String identifier = description == null ? "unknown" : description.optString("identifier", "unknown");
        String localName = identifier.contains(":") ? identifier.split(":")[1] : identifier;
        OreTextView nameText = new OreTextView(context);
        nameText.setText("名称: " + localName);
        nameText.setTextColor(Color.WHITE);
        layout.addView(nameText);

        // 解锁方式解析
        JSONObject unlockObj = body.optJSONObject("unlock");
        JSONArray unlockArr = body.optJSONArray("unlock");
        currentUnlockContext = "AlwaysUnlocked";
        unlockItems.clear();
        if (unlockObj != null) {
            currentUnlockContext = unlockObj.optString("context", "AlwaysUnlocked");
        } else if (unlockArr != null) {
            currentUnlockContext = "PlayerHasManyItems";
            for (int i = 0; i < unlockArr.length(); i++) {
                JSONObject itemObj = unlockArr.optJSONObject(i);
                if (itemObj != null) {
                    String itemStr = itemObj.optString("item", "");
                    int data = itemObj.optInt("data", -1);
                    if (data >= 0) itemStr = itemStr + ":" + data;
                    unlockItems.add(itemStr);
                }
            }
            if (unlockItems.isEmpty()) unlockItems.add("minecraft:apple");
        }

        // 适用方块 (tags)
        JSONArray tags = body.optJSONArray("tags");
        selectedTags.clear();
        if (tags != null) {
            for (int i = 0; i < tags.length(); i++) selectedTags.add(tags.optString(i));
        }

        LinearLayout tagGroup = new LinearLayout(context);
        tagGroup.setOrientation(LinearLayout.HORIZONTAL);
        String[] availableTags;
        if (recipeType.equals("recipe_shaped")) {
            availableTags = new String[]{"crafting_table"};
        } else if (recipeType.equals("recipe_shapeless")) {
            availableTags = new String[]{"crafting_table", "stonecutter", "cartography_table"};
        } else if (recipeType.equals("recipe_furnace")) {
            availableTags = new String[]{"furnace", "blast_furnace", "smoker", "campfire"};
        } else if (recipeType.equals("recipe_brewing_mix")) {
            availableTags = new String[]{"brewing_stand"};
        } else {
            availableTags = new String[]{"smithing_table"};
        }

        for (final String tag : availableTags) {
            LinearLayout tagRow = new LinearLayout(context);
            tagRow.setOrientation(LinearLayout.HORIZONTAL);
            tagRow.setGravity(Gravity.CENTER_VERTICAL);
            final OreSwitch sw = new OreSwitch(context);
            sw.setChecked(selectedTags.contains(tag));
            sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) selectedTags.add(tag);
                    else selectedTags.remove(tag);
                    autoSave();
                }
            });
            tagRow.addView(sw);
            OreTextView tagLabel = new OreTextView(context);
            tagLabel.setText(getTagDisplayName(tag));
            tagLabel.setTextColor(Color.WHITE);
            tagLabel.setTextSize(10);
            tagLabel.setPadding(4, 0, 8, 0);
            tagRow.addView(tagLabel);
            tagGroup.addView(tagRow);
        }
        layout.addView(tagGroup);

        // 无序合成输入物品
        if (recipeType.equals("recipe_shapeless")) {
            shapelessInputs.clear();
            JSONArray ingredients = body.optJSONArray("ingredients");
            if (ingredients != null) {
                for (int i = 0; i < ingredients.length(); i++) {
                    JSONObject ing = ingredients.optJSONObject(i);
                    if (ing != null) {
                        String itemStr = ing.optString("item", "");
                        int data = ing.optInt("data", -1);
                        if (data >= 0) itemStr += ":" + data;
                        shapelessInputs.add(itemStr);
                    }
                }
            }
            if (shapelessInputs.isEmpty()) shapelessInputs.add("minecraft:apple");
            OreTextView inputLabel = new OreTextView(context);
            inputLabel.setText("输入物品");
            inputLabel.setTextColor(Color.WHITE);
            inputLabel.setTextSize(12);
            layout.addView(inputLabel);
            buildShapelessInputUI(layout);
        }

        // 有序合成 3×3 网格 + 映射
        if (recipeType.equals("recipe_shaped")) {
            recipeGrid = new GridLayout(context);
            recipeGrid.setColumnCount(3);
            recipeGrid.setRowCount(3);
            final JSONObject key = body.optJSONObject("key");
            final JSONArray pattern = body.optJSONArray("pattern");
            for (int row = 0; row < 3; row++) {
                String rowStr = "";
                if (pattern != null && row < pattern.length()) rowStr = pattern.optString(row, "");
                while (rowStr.length() < 3) rowStr += " ";
                for (int col = 0; col < 3; col++) {
                    char ch = rowStr.charAt(col);
                    String letter = (ch == ' ') ? "" : String.valueOf(ch);
                    final OreButton cell = new OreButton(context);
                    cell.setText(letter);
                    cell.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
                    cell.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            showLetterInputDialog(cell);
                        }
                    });
                    recipeGrid.addView(cell, new GridLayout.LayoutParams(
                            GridLayout.spec(row), GridLayout.spec(col)));
                }
            }
            layout.addView(recipeGrid);
            recipeMappingLayout = new LinearLayout(context);
            recipeMappingLayout.setOrientation(LinearLayout.VERTICAL);
            if (key != null) {
                Iterator<String> it = key.keys();
                while (it.hasNext()) {
                    String letter = it.next();
                    addMappingRow(recipeMappingLayout, letter, key.optJSONObject(letter));
                }
            }
            layout.addView(recipeMappingLayout);
        } else {
            recipeGrid = null;
            recipeMappingLayout = null;
        }

        // 熔炉输入
        if (recipeType.equals("recipe_furnace")) {
            furnaceInputs.clear();
            JSONObject inputObj = body.optJSONObject("input");
            if (inputObj != null) {
                String itemStr = inputObj.optString("item", "");
                int data = inputObj.optInt("data", -1);
                if (data >= 0) itemStr = itemStr + ":" + data;
                furnaceInputs.add(itemStr);
            }
            if (furnaceInputs.isEmpty()) furnaceInputs.add("minecraft:apple");
            OreTextView furnaceInputLabel = new OreTextView(context);
            furnaceInputLabel.setText("熔炉输入");
            furnaceInputLabel.setTextColor(Color.WHITE);
            furnaceInputLabel.setTextSize(12);
            layout.addView(furnaceInputLabel);
            buildFurnaceInputUI(layout);
        }

        // 酿造配方：输入药水、试剂
        if (recipeType.equals("recipe_brewing_mix")) {
            OreTextView brewInputLabel = new OreTextView(context);
            brewInputLabel.setText("输入药水");
            brewInputLabel.setTextColor(Color.WHITE);
            brewInputLabel.setTextSize(12);
            layout.addView(brewInputLabel);

            brewingInputItem = new OreEditText(context);
            brewingInputItem.setHint("输入药水 (如 minecraft:potion:0)");
            brewingInputItem.setText(body.optString("input", ""));
            brewingInputItem.addTextChangedListener(autoSaveWatcher());
            layout.addView(brewingInputItem);

            OreTextView brewReagentLabel = new OreTextView(context);
            brewReagentLabel.setText("试剂");
            brewReagentLabel.setTextColor(Color.WHITE);
            brewReagentLabel.setTextSize(12);
            layout.addView(brewReagentLabel);

            brewingReagentItem = new OreEditText(context);
            brewingReagentItem.setHint("试剂 (如 minecraft:blaze_powder:0)");
            brewingReagentItem.setText(body.optString("reagent", ""));
            brewingReagentItem.addTextChangedListener(autoSaveWatcher());
            layout.addView(brewingReagentItem);
        }

        // 锻造配方：模板、基底、添加物
        if (recipeType.equals("recipe_smithing_transform")) {
            OreAlert smithingWarning = new OreAlert(context);
            smithingWarning.setText("由于不知名原因，锻造配方可能无法正常使用");
            smithingWarning.setStyleSheet(StyleSheet.STYLE_ALERT_YELLOW);
            layout.addView(smithingWarning);

            OreTextView templateLabel = new OreTextView(context);
            templateLabel.setText("锻造模板");
            templateLabel.setTextColor(Color.WHITE);
            templateLabel.setTextSize(12);
            layout.addView(templateLabel);

            smithingTemplateItem = new OreEditText(context);
            smithingTemplateItem.setHint("模板 (如 minecraft:netherite_upgrade_smithing_template)");
            smithingTemplateItem.setText(body.optString("template", ""));
            smithingTemplateItem.addTextChangedListener(autoSaveWatcher());
            layout.addView(smithingTemplateItem);

            OreTextView baseLabel = new OreTextView(context);
            baseLabel.setText("基底");
            baseLabel.setTextColor(Color.WHITE);
            baseLabel.setTextSize(12);
            layout.addView(baseLabel);

            smithingBaseItem = new OreEditText(context);
            smithingBaseItem.setHint("基底 (如 minecraft:diamond_boots)");
            smithingBaseItem.setText(body.optString("base", ""));
            smithingBaseItem.addTextChangedListener(autoSaveWatcher());
            layout.addView(smithingBaseItem);

            OreTextView additionLabel = new OreTextView(context);
            additionLabel.setText("添加物");
            additionLabel.setTextColor(Color.WHITE);
            additionLabel.setTextSize(12);
            layout.addView(additionLabel);

            smithingAdditionItem = new OreEditText(context);
            smithingAdditionItem.setHint("添加物 (如 minecraft:netherite_ingot)");
            smithingAdditionItem.setText(body.optString("addition", ""));
            smithingAdditionItem.addTextChangedListener(autoSaveWatcher());
            layout.addView(smithingAdditionItem);
        }

        // 产出物品
        OreTextView resultLabel = new OreTextView(context);
        resultLabel.setText("产出物品");
        resultLabel.setTextColor(Color.WHITE);
        resultLabel.setTextSize(12);
        layout.addView(resultLabel);

        LinearLayout resultLayout = new LinearLayout(context);
        resultLayout.setOrientation(LinearLayout.HORIZONTAL);

        visualResultItem = new OreEditText(context);
        visualResultItem.setHint("产出物品ID");
        visualResultCount = new OreEditText(context);
        visualResultCount.setHint("数量");

        TextWatcher resultWatcher = autoSaveWatcher();
        visualResultItem.addTextChangedListener(resultWatcher);
        visualResultCount.addTextChangedListener(resultWatcher);

        if (recipeType.equals("recipe_shaped") || recipeType.equals("recipe_shapeless")
                || recipeType.equals("recipe_furnace")) {
            JSONObject resultObj = body.optJSONObject("result");
            if (resultObj != null) {
                visualResultItem.setText(resultObj.optString("item", ""));
                visualResultCount.setText(String.valueOf(resultObj.optInt("count", 1)));
            }
            visualResultCount.setVisibility(View.VISIBLE);
        } else if (recipeType.equals("recipe_brewing_mix")) {
            visualResultItem.setText(body.optString("output", ""));
            visualResultItem.setHint("输出药水 (如 minecraft:potion:31)");
            visualResultCount.setVisibility(View.GONE);
        } else if (recipeType.equals("recipe_smithing_transform")) {
            visualResultItem.setText(body.optString("result", ""));
            visualResultItem.setHint("产物 (如 minecraft:netherite_boots)");
            visualResultCount.setVisibility(View.GONE);
        }
        resultLayout.addView(visualResultItem);
        resultLayout.addView(visualResultCount);
        layout.addView(resultLayout);

        // 解锁方式选择
        OreButton unlockSelectBtn = new OreButton(context);
        unlockSelectBtn.setText(getUnlockDisplayName(currentUnlockContext));
        unlockSelectBtn.setTag("unlock_select_btn");
        unlockSelectBtn.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        unlockSelectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showUnlockSelectDialog();
            }
        });
        layout.addView(unlockSelectBtn);
        if (currentUnlockContext.equals("PlayerHasManyItems")) {
            buildUnlockItemsUI(layout);
        }

        scroll.addView(layout);
        container.addView(scroll);
    }

    // ==================== 通用 ====================

    private TextWatcher autoSaveWatcher() {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override public void afterTextChanged(Editable s) {
                autoSave();
            }
        };
    }

    private String getTagDisplayName(String tag) {
        if (tag == null) return "";
        if (tag.equals("crafting_table")) return "工作台";
        if (tag.equals("stonecutter")) return "切石机";
        if (tag.equals("cartography_table")) return "制图台";
        if (tag.equals("furnace")) return "熔炉";
        if (tag.equals("blast_furnace")) return "高炉";
        if (tag.equals("smoker")) return "烟熏炉";
        if (tag.equals("campfire")) return "营火";
        if (tag.equals("brewing_stand")) return "酿造台";
        if (tag.equals("smithing_table")) return "锻造台";
        return tag;
    }

    private String getUnlockDisplayName(String context) {
        if (context == null) return "默认解锁";
        if (context.equals("AlwaysUnlocked")) return "默认解锁";
        if (context.equals("PlayerHasManyItems")) return "获得特定物品时解锁";
        if (context.equals("PlayerInWater")) return "玩家在水中时解锁";
        return context;
    }

    // ==================== 数据更新与保存 ====================

    private void autoSave() {
        if (currentRecipeFile == null || currentRecipeRoot == null) return;
        updateRecipeFromGrid();
        saveRecipeToFile(currentRecipeFile, currentRecipeRoot);
    }

    private void updateRecipeFromGrid() {
        if (currentRecipeRoot == null || currentRecipeType == null) return;
        try {
            JSONObject body;
            if (!currentRecipeRoot.has("minecraft:" + currentRecipeType)) return;
            body = currentRecipeRoot.getJSONObject("minecraft:" + currentRecipeType);

            // 有序合成：pattern + key
            if (currentRecipeType.equals("recipe_shaped")) {
                JSONArray pattern = new JSONArray();
                if (recipeGrid != null) {
                    for (int row = 0; row < 3; row++) {
                        StringBuilder rowLetters = new StringBuilder();
                        for (int col = 0; col < 3; col++) {
                            OreButton cell = (OreButton) recipeGrid.getChildAt(row * 3 + col);
                            String letter = (cell != null) ? cell.getText().toString().trim() : "";
                            rowLetters.append(letter.isEmpty() ? " " : letter);
                        }
                        String line = rowLetters.toString();
                        if (!line.equals("   ")) pattern.put(line);
                    }
                }
                body.put("pattern", pattern);

                JSONObject key = new JSONObject();
                if (recipeMappingLayout != null) {
                    for (int i = 0; i < recipeMappingLayout.getChildCount(); i++) {
                        View child = recipeMappingLayout.getChildAt(i);
                        if (child instanceof LinearLayout) {
                            LinearLayout row = (LinearLayout) child;
                            if (row.getChildCount() < 2) continue;
                            OreEditText letEdit = (OreEditText) row.getChildAt(0);
                            OreEditText itmEdit = (OreEditText) row.getChildAt(1);
                            if (letEdit != null && itmEdit != null) {
                                String l = letEdit.getText().toString().trim();
                                String it = itmEdit.getText().toString().trim();
                                if (!l.isEmpty() && !it.isEmpty()) {
                                    JSONObject itemObj = new JSONObject();
                                    itemObj.put("item", it);
                                    key.put(l, itemObj);
                                }
                            }
                        }
                    }
                }
                body.put("key", key);
            }

            // 无序合成：ingredients
            if (currentRecipeType.equals("recipe_shapeless")) {
                JSONArray ingredientsArray = new JSONArray();
                if (shapelessInputs != null) {
                    for (String itemStr : shapelessInputs) {
                        if (!itemStr.trim().isEmpty()) {
                            JSONObject itemObj = new JSONObject();
                            String itemId = itemStr.trim();
                            int data = -1;
                            int colonIndex = itemId.lastIndexOf(':');
                            if (colonIndex > 0) {
                                String afterColon = itemId.substring(colonIndex + 1);
                                try {
                                    data = Integer.parseInt(afterColon);
                                    itemId = itemId.substring(0, colonIndex);
                                } catch (NumberFormatException ignored) {
                                }
                            }
                            itemObj.put("item", itemId);
                            if (data >= 0) itemObj.put("data", data);
                            ingredientsArray.put(itemObj);
                        }
                    }
                }
                body.put("ingredients", ingredientsArray);
            }

            // 熔炉输入
            if (currentRecipeType.equals("recipe_furnace")) {
                if (furnaceInputs != null && !furnaceInputs.isEmpty()) {
                    String itemStr = furnaceInputs.get(0).trim();
                    if (!itemStr.isEmpty()) {
                        JSONObject input = new JSONObject();
                        String itemId = itemStr;
                        int data = -1;
                        int colonIndex = itemId.lastIndexOf(':');
                        if (colonIndex > 0) {
                            String afterColon = itemId.substring(colonIndex + 1);
                            try {
                                data = Integer.parseInt(afterColon);
                                itemId = itemId.substring(0, colonIndex);
                            } catch (NumberFormatException ignored) {
                            }
                        }
                        input.put("item", itemId);
                        if (data >= 0) input.put("data", data);
                        body.put("input", input);
                    } else {
                        body.remove("input");
                    }
                } else {
                    body.remove("input");
                }
            }

            // tags
            JSONArray tagsArray = new JSONArray();
            if (selectedTags != null) for (String tag : selectedTags) tagsArray.put(tag);
            body.put("tags", tagsArray);

            // 产出 + 酿造/锻造特有字段
            if (currentRecipeType.equals("recipe_brewing_mix")) {
                if (brewingInputItem != null) {
                    String inputVal = brewingInputItem.getText().toString().trim();
                    if (!inputVal.isEmpty()) body.put("input", inputVal);
                    else body.remove("input");
                }
                if (brewingReagentItem != null) {
                    String reagentVal = brewingReagentItem.getText().toString().trim();
                    if (!reagentVal.isEmpty()) body.put("reagent", reagentVal);
                    else body.remove("reagent");
                }
                if (visualResultItem != null) {
                    String outVal = visualResultItem.getText().toString().trim();
                    if (!outVal.isEmpty()) body.put("output", outVal);
                    else body.remove("output");
                }
            } else if (currentRecipeType.equals("recipe_smithing_transform")) {
                if (visualResultItem != null) {
                    String resultVal = visualResultItem.getText().toString().trim();
                    if (!resultVal.isEmpty()) body.put("result", resultVal);
                    else body.remove("result");
                }
                if (smithingTemplateItem != null) {
                    String val = smithingTemplateItem.getText().toString().trim();
                    if (!val.isEmpty()) body.put("template", val);
                    else body.remove("template");
                }
                if (smithingBaseItem != null) {
                    String val = smithingBaseItem.getText().toString().trim();
                    if (!val.isEmpty()) body.put("base", val);
                    else body.remove("base");
                }
                if (smithingAdditionItem != null) {
                    String val = smithingAdditionItem.getText().toString().trim();
                    if (!val.isEmpty()) body.put("addition", val);
                    else body.remove("addition");
                }
            } else if (visualResultItem != null && visualResultCount != null) {
                String item = visualResultItem.getText().toString().trim();
                String countStr = visualResultCount.getText().toString().trim();
                int count = 1;
                try {
                    count = Integer.parseInt(countStr);
                } catch (NumberFormatException ignored) {
                }
                if (currentRecipeType.equals("recipe_shaped") || currentRecipeType.equals("recipe_shapeless")
                        || currentRecipeType.equals("recipe_furnace")) {
                    JSONObject result = new JSONObject();
                    result.put("item", item);
                    result.put("count", count);
                    body.put("result", result);
                }
            }

            // 解锁
            if (currentUnlockContext != null && !currentUnlockContext.isEmpty()) {
                if (currentUnlockContext.equals("PlayerHasManyItems")) {
                    JSONArray unlockArray = new JSONArray();
                    if (unlockItems != null) {
                        for (String itemStr : unlockItems) {
                            if (!itemStr.trim().isEmpty()) {
                                JSONObject itemObj = new JSONObject();
                                String itemId = itemStr.trim();
                                int data = -1;
                                int colonIndex = itemId.lastIndexOf(':');
                                if (colonIndex > 0) {
                                    String afterColon = itemId.substring(colonIndex + 1);
                                    try {
                                        data = Integer.parseInt(afterColon);
                                        itemId = itemId.substring(0, colonIndex);
                                    } catch (NumberFormatException ignored) {
                                    }
                                }
                                itemObj.put("item", itemId);
                                if (data >= 0) itemObj.put("data", data);
                                unlockArray.put(itemObj);
                            }
                        }
                    }
                    body.put("unlock", unlockArray);
                } else {
                    JSONObject unlock = new JSONObject();
                    unlock.put("context", currentUnlockContext);
                    body.put("unlock", unlock);
                }
            } else {
                body.remove("unlock");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveRecipeToFile(File file, JSONObject recipeJson) {
        try {
            FileUtils.writeText(file, recipeJson.toString(2));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addMappingRow(LinearLayout mappingLayout, String letter, JSONObject itemObj) {
        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 4, 0, 4);

        final OreEditText letterEdit = new OreEditText(context);
        letterEdit.setHint("字母");
        if (letter != null) letterEdit.setText(letter);
        letterEdit.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(letterEdit);

        final OreEditText itemEdit = new OreEditText(context);
        itemEdit.setHint("物品ID");
        if (itemObj != null) itemEdit.setText(itemObj.optString("item", ""));
        itemEdit.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f));
        row.addView(itemEdit);

        OreButton deleteBtn = new OreButton(context);
        deleteBtn.setText("×");
        deleteBtn.setStyleSheet(StyleSheet.STYLE_RED);
        deleteBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                View parent = (View) row.getParent();
                if (parent instanceof LinearLayout) {
                    ((LinearLayout) parent).removeView(row);
                }
                autoSave();
            }
        });
        row.addView(deleteBtn);

        TextWatcher tw = autoSaveWatcher();
        letterEdit.addTextChangedListener(tw);
        itemEdit.addTextChangedListener(tw);

        mappingLayout.addView(row);
    }

    /** 确保映射行存在（网格字母输入时自动补一行）。 */
    private void ensureMappingExists(String letter) {
        if (recipeMappingLayout == null || letter == null || letter.trim().isEmpty()) return;
        for (int i = 0; i < recipeMappingLayout.getChildCount(); i++) {
            View child = recipeMappingLayout.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout row = (LinearLayout) child;
                if (row.getChildCount() > 0) {
                    OreEditText letterEdit = (OreEditText) row.getChildAt(0);
                    if (letterEdit != null && letter.equals(letterEdit.getText().toString().trim())) {
                        return;
                    }
                }
            }
        }
        addMappingRow(recipeMappingLayout, letter, null);
    }

    // ==================== 对话框 ====================

    private void showLetterInputDialog(final OreButton cell) {
        final OreEditText letterInput = new OreEditText(context);
        letterInput.setHint("输入字母 (A-Z)");
        letterInput.setText(cell.getText().toString());

        OreDialogBuilder builder = new OreDialogBuilder(context);
        builder.setTitle("输入字母");
        builder.setView(letterInput);
        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String letter = letterInput.getText().toString().trim().toUpperCase();
                if (letter.length() == 1 && letter.charAt(0) >= 'A' && letter.charAt(0) <= 'Z') {
                    cell.setText(letter);
                    ensureMappingExists(letter);
                    autoSave();
                } else if (letter.isEmpty()) {
                    cell.setText("");
                    autoSave();
                } else {
                    Toast.makeText(context, "请输入单个字母", Toast.LENGTH_SHORT).show();
                }
                dialog.dismiss();
            }
        });
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.show();
    }

    private void showUnlockSelectDialog() {
        final String[] contexts = {"AlwaysUnlocked", "PlayerHasManyItems", "PlayerInWater"};
        final String[] chineseNames = new String[contexts.length];
        for (int i = 0; i < contexts.length; i++) {
            chineseNames[i] = getUnlockDisplayName(contexts[i]);
        }

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = UiUtils.dp(context, 12);
        layout.setPadding(pad, pad, pad, pad);

        final OreDialogBuilder builder = new OreDialogBuilder(context);
        builder.setTitle("选择解锁方式");
        builder.setView(layout);
        final android.app.Dialog[] dialogRef = new android.app.Dialog[1];

        for (int i = 0; i < contexts.length; i++) {
            final int index = i;
            OreButton btn = new OreButton(context);
            btn.setText(chineseNames[i]);
            btn.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String newContext = contexts[index];
                    if (!newContext.equals(currentUnlockContext)) {
                        if (newContext.equals("PlayerHasManyItems")) {
                            unlockItems.clear();
                            unlockItems.add("minecraft:apple");
                        }
                        currentUnlockContext = newContext;
                        autoSave();

                        View unlockBtn = recipeContentArea.findViewWithTag("unlock_select_btn");
                        if (unlockBtn instanceof OreButton) {
                            ((OreButton) unlockBtn).setText(getUnlockDisplayName(currentUnlockContext));
                        }
                        View oldItems = recipeContentArea.findViewWithTag("unlock_items_container");
                        if (oldItems != null && recipeContentArea instanceof android.view.ViewGroup) {
                            ((android.view.ViewGroup) recipeContentArea).removeView(oldItems);
                        }
                        if (currentUnlockContext.equals("PlayerHasManyItems")
                                && recipeContentArea instanceof LinearLayout) {
                            buildUnlockItemsUI((LinearLayout) recipeContentArea);
                        }
                    }
                    if (dialogRef[0] != null) dialogRef[0].dismiss();
                }
            });
            layout.addView(btn);
            UiUtils.addGap(context, layout, 6);
        }

        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        dialogRef[0] = builder.show();
    }

    private void buildUnlockItemsUI(final LinearLayout parent) {
        View oldContainer = parent.findViewWithTag("unlock_items_container");
        if (oldContainer != null) {
            parent.removeView(oldContainer);
        }

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setTag("unlock_items_container");

        for (int i = 0; i < unlockItems.size(); i++) {
            final int index = i;
            String itemStr = unlockItems.get(i);

            final OreEditText itemEdit = new OreEditText(context);
            itemEdit.setHint("物品ID");
            itemEdit.setText(itemStr);

            OreButton delBtn = new OreButton(context);
            delBtn.setText("×");
            delBtn.setStyleSheet(StyleSheet.STYLE_RED);
            delBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    unlockItems.remove(index);
                    autoSave();
                    buildUnlockItemsUI(parent);
                }
            });

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            itemEdit.setLayoutParams(itemParams);
            row.addView(itemEdit);
            row.addView(delBtn);
            container.addView(row);
            UiUtils.addGap(context, container, 4);

            itemEdit.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override public void afterTextChanged(Editable s) {
                    if (index < unlockItems.size()) {
                        unlockItems.set(index, s.toString().trim());
                    }
                    autoSave();
                }
            });
        }

        OreButton addBtn = new OreButton(context);
        addBtn.setText("+ 添加物品");
        addBtn.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        addBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                unlockItems.add("minecraft:apple");
                autoSave();
                buildUnlockItemsUI(parent);
            }
        });
        container.addView(addBtn);

        parent.addView(container);
    }

    private void buildShapelessInputUI(final LinearLayout parent) {
        View oldContainer = parent.findViewWithTag("shapeless_inputs_container");
        if (oldContainer != null) {
            parent.removeView(oldContainer);
        }

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setTag("shapeless_inputs_container");

        for (int i = 0; i < shapelessInputs.size(); i++) {
            final int index = i;
            String itemStr = shapelessInputs.get(i);

            final OreEditText itemEdit = new OreEditText(context);
            itemEdit.setHint("物品ID");
            itemEdit.setText(itemStr);

            OreButton delBtn = new OreButton(context);
            delBtn.setText("×");
            delBtn.setStyleSheet(StyleSheet.STYLE_RED);
            delBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    shapelessInputs.remove(index);
                    autoSave();
                    rebuildVisual();
                }
            });

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            itemEdit.setLayoutParams(itemParams);
            row.addView(itemEdit);
            row.addView(delBtn);
            container.addView(row);
            UiUtils.addGap(context, container, 4);

            itemEdit.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override public void afterTextChanged(Editable s) {
                    if (index < shapelessInputs.size()) {
                        shapelessInputs.set(index, s.toString().trim());
                    }
                    autoSave();
                }
            });
        }

        OreButton addBtn = new OreButton(context);
        addBtn.setText("+ 添加物品");
        addBtn.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        addBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shapelessInputs.add("minecraft:apple");
                autoSave();
                rebuildVisual();
            }
        });
        container.addView(addBtn);

        parent.addView(container);
    }

    private void buildFurnaceInputUI(final LinearLayout parent) {
        View oldContainer = parent.findViewWithTag("furnace_input_container");
        if (oldContainer != null) {
            parent.removeView(oldContainer);
        }

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setTag("furnace_input_container");

        for (int i = 0; i < furnaceInputs.size(); i++) {
            final int index = i;
            String itemStr = furnaceInputs.get(i);

            final OreEditText itemEdit = new OreEditText(context);
            itemEdit.setHint("物品ID");
            itemEdit.setText(itemStr);

            OreButton delBtn = new OreButton(context);
            delBtn.setText("×");
            delBtn.setStyleSheet(StyleSheet.STYLE_RED);
            delBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    furnaceInputs.remove(index);
                    autoSave();
                    rebuildVisual();
                }
            });

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            itemEdit.setLayoutParams(itemParams);
            row.addView(itemEdit);
            row.addView(delBtn);
            container.addView(row);
            UiUtils.addGap(context, container, 4);

            itemEdit.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override public void afterTextChanged(Editable s) {
                    if (index < furnaceInputs.size()) {
                        furnaceInputs.set(index, s.toString().trim());
                    }
                    autoSave();
                }
            });
        }

        OreButton addBtn = new OreButton(context);
        addBtn.setText("+ 添加物品");
        addBtn.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        addBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                furnaceInputs.add("minecraft:apple");
                autoSave();
                rebuildVisual();
            }
        });
        container.addView(addBtn);

        parent.addView(container);
    }

    /** 重建整个可视化界面（删除/添加物品后调用）。 */
    private void rebuildVisual() {
        if (recipeContentArea != null && currentRecipeFile != null) {
            recipeContentArea.removeAllViews();
            showVisual(recipeContentArea, currentRecipeFile);
        }
    }

    private void showDeleteRecipeConfirm(final File recipeFile) {
        OreTextView msg = new OreTextView(context);
        msg.setText("确定要删除配方 \"" + recipeFile.getName() + "\" 吗？\n此操作不可恢复！");
        msg.setTextColor(Color.WHITE);
        msg.setTextSize(14);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        int pad = UiUtils.dp(context, 16);
        layout.setPadding(pad, pad, pad, pad);
        layout.addView(msg);

        OreDialogBuilder builder = new OreDialogBuilder(context);
        builder.setTitle("删除配方");
        builder.setView(layout);
        builder.setPositiveButton("删除", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (recipeFile.delete()) {
                    Toast.makeText(context, "配方已删除", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show();
                }
                host.refreshFileList(currentDirOf(recipeFile));
                dialog.dismiss();
            }
        });
        builder.getPositiveButton().setStyleSheet(StyleSheet.STYLE_RED);
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.show();
    }

    private File currentDirOf(File file) {
        File parent = file.getParentFile();
        return parent != null ? parent : file;
    }
}