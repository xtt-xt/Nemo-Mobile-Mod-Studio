package com.xtt.mcmodmaker;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Toast;

import com.xtt.mcmodmaker.core.Constants;
import com.xtt.mcmodmaker.core.ModTemplates;
import com.xtt.mcmodmaker.core.ProjectManager;
import com.xtt.mcmodmaker.editor.FileNode;
import com.xtt.mcmodmaker.editor.RecipeEditor;
import com.xtt.mcmodmaker.editor.SimpleSyntaxLanguage;
import com.xtt.mcmodmaker.editor.UserColorScheme;
import com.xtt.mcmodmaker.util.FileUtils;
import com.xtt.mcmodmaker.util.JsonUtils;
import com.xtt.mcmodmaker.util.SettingsManager;
import com.xtt.mcmodmaker.util.UiUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import dev1503.oreui.StyleSheet;
import dev1503.oreui.dialog.OreDialogBuilder;
import dev1503.oreui.widgets.OreAccordion;
import dev1503.oreui.widgets.OreAlert;
import dev1503.oreui.widgets.OreButton;
import dev1503.oreui.widgets.OreCard;
import dev1503.oreui.widgets.OreEditText;
import dev1503.oreui.widgets.OreTextView;
import io.github.rosemoe.sora.lang.EmptyLanguage;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.schemes.SchemeVS2019;

/**
 * 项目编辑器：三栏式界面（左：功能/文件导航；中：文件列表；右：编辑器）。
 * 重构版：文件树使用 {@link FileNode}，配方编辑委托 {@link RecipeEditor}，通用逻辑复用 util 包。
 */
public class EditorActivity extends Activity implements RecipeEditor.Host {

    private static final int MODE_BROWSE = 0;
    private static final int MODE_FOCUS = 1;
    private static final int MODE_EDIT = 2;
    private static final int BLOCK_WIDTH_DP = 110;
    private static final int BLOCK_SPACING_DP = 8;
    private static final int REQUEST_IMPORT_IMAGE = 2001;

    private String projectPath;
    private File projectDir;
    private File currentDir;
    private FileNode rootNode;
    private int currentMode = MODE_BROWSE;

    private LinearLayout leftPanel;
    private LinearLayout leftContainer;
    private LinearLayout middlePanel;
    private LinearLayout middleContainer;
    private LinearLayout rightPanel;
    private LinearLayout editorContainer;
    private LinearLayout modeButtonContainer;
    private OreButton btnBrowse;
    private OreButton btnFocus;
    private OreButton btnEdit;

    private RecipeEditor recipeEditor;

    // 代码编辑器状态
    private CodeEditor codeEditText;
    private String originalContent = "";

    // ==================== 生命周期 ====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }

        projectPath = getIntent().getStringExtra("project_path");
        if (projectPath == null) {
            Toast.makeText(this, "项目路径无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        projectDir = new File(projectPath);
        currentDir = projectDir;
        rootNode = scanDirectory(projectDir);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
        }

        recipeEditor = new RecipeEditor(this, this);

        buildUi();
        switchMode(MODE_BROWSE);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(Color.parseColor("#1A1A1A"));

        // ===== 左侧面板 =====
        leftPanel = new LinearLayout(this);
        leftPanel.setOrientation(LinearLayout.VERTICAL);
        leftPanel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2f));
        leftPanel.setPadding(8, 8, 8, 8);
        root.addView(leftPanel);

        addFunctionAccordion(leftPanel);
        UiUtils.addGap(this, leftPanel, 8);

        // 模式按钮
        modeButtonContainer = new LinearLayout(this);
        modeButtonContainer.setOrientation(LinearLayout.HORIZONTAL);
        modeButtonContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);

        btnBrowse = modeButton("浏览", StyleSheet.STYLE_GREEN, btnParams, MODE_BROWSE);
        btnFocus = modeButton("聚焦", StyleSheet.STYLE_DARK_GRAY, btnParams, MODE_FOCUS);
        btnEdit = modeButton("编辑", StyleSheet.STYLE_DARK_GRAY, btnParams, MODE_EDIT);
        modeButtonContainer.addView(btnBrowse);
        modeButtonContainer.addView(btnFocus);
        modeButtonContainer.addView(btnEdit);
        leftPanel.addView(modeButtonContainer);
        UiUtils.addGap(this, leftPanel, 8);

        ScrollView leftScroll = new ScrollView(this);
        leftScroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        leftContainer = new LinearLayout(this);
        leftContainer.setOrientation(LinearLayout.VERTICAL);
        leftScroll.addView(leftContainer);
        leftPanel.addView(leftScroll);

        root.addView(divider());

        // ===== 中间面板 =====
        middlePanel = new LinearLayout(this);
        middlePanel.setOrientation(LinearLayout.VERTICAL);
        middlePanel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 3f));
        middlePanel.setPadding(8, 8, 8, 8);

        // 中间头部：根目录/返回/新建/导入
        LinearLayout middleHeader = new LinearLayout(this);
        middleHeader.setOrientation(LinearLayout.HORIZONTAL);
        middleHeader.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headerBtnParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);

        middleHeader.addView(headerButton("根目录", StyleSheet.STYLE_DARK_GRAY, headerBtnParams, new View.OnClickListener() {
            @Override public void onClick(View v) {
                currentDir = projectDir;
                loadFileList(currentDir);
            }
        }));
        middleHeader.addView(headerButton("返回", StyleSheet.STYLE_DARK_GRAY, headerBtnParams, new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (currentDir != null && !currentDir.equals(projectDir)) {
                    File parent = currentDir.getParentFile();
                    if (parent != null && parent.getAbsolutePath().startsWith(projectPath)) {
                        loadFileList(parent);
                    }
                }
            }
        }));
        middleHeader.addView(headerButton("新建", StyleSheet.STYLE_DARK_GRAY, headerBtnParams, new View.OnClickListener() {
            @Override public void onClick(View v) { showNewItemDialog(); }
        }));
        middleHeader.addView(headerButton("导入", StyleSheet.STYLE_DARK_GRAY, headerBtnParams, new View.OnClickListener() {
            @Override public void onClick(View v) { showImportDialog(); }
        }));
        middlePanel.addView(middleHeader);
        UiUtils.addGap(this, middlePanel, 8);

        ScrollView middleScroll = new ScrollView(this);
        middleScroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        middleContainer = new LinearLayout(this);
        middleContainer.setOrientation(LinearLayout.VERTICAL);
        middleScroll.addView(middleContainer);
        middlePanel.addView(middleScroll);

        root.addView(middlePanel);
        root.addView(divider());

        // ===== 右侧面板 =====
        rightPanel = new LinearLayout(this);
        rightPanel.setOrientation(LinearLayout.VERTICAL);
        rightPanel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 5f));
        rightPanel.setPadding(8, 8, 8, 8);

        editorContainer = new LinearLayout(this);
        editorContainer.setOrientation(LinearLayout.VERTICAL);
        editorContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        rightPanel.addView(editorContainer);

        root.addView(rightPanel);
        setContentView(root);
    }

    private View divider() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT));
        v.setBackgroundColor(Color.parseColor("#333333"));
        return v;
    }

    private OreButton modeButton(String text, StyleSheet style, LinearLayout.LayoutParams params, final int mode) {
        OreButton btn = new OreButton(this);
        btn.setText(text);
        btn.setStyleSheet(style);
        btn.setLayoutParams(params);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { switchMode(mode); }
        });
        return btn;
    }

    private OreButton headerButton(String text, StyleSheet style, LinearLayout.LayoutParams params, View.OnClickListener listener) {
        OreButton btn = new OreButton(this);
        btn.setText(text);
        btn.setStyleSheet(style);
        btn.setLayoutParams(params);
        btn.setOnClickListener(listener);
        return btn;
    }

    // ==================== 模式切换 ====================

    private void switchMode(int mode) {
        currentMode = mode;
        btnBrowse.setStyleSheet(mode == MODE_BROWSE ? StyleSheet.STYLE_GREEN : StyleSheet.STYLE_DARK_GRAY);
        btnFocus.setStyleSheet(mode == MODE_FOCUS ? StyleSheet.STYLE_GREEN : StyleSheet.STYLE_DARK_GRAY);
        btnEdit.setStyleSheet(mode == MODE_EDIT ? StyleSheet.STYLE_GREEN : StyleSheet.STYLE_DARK_GRAY);

        LinearLayout.LayoutParams leftParams = (LinearLayout.LayoutParams) leftPanel.getLayoutParams();
        LinearLayout.LayoutParams middleParams = (LinearLayout.LayoutParams) middlePanel.getLayoutParams();
        LinearLayout.LayoutParams rightParams = (LinearLayout.LayoutParams) rightPanel.getLayoutParams();

        View funcAccordion = leftPanel.getChildAt(0);
        if (funcAccordion != null) funcAccordion.setVisibility(mode == MODE_FOCUS ? View.GONE : View.VISIBLE);

        if (mode == MODE_FOCUS) {
            modeButtonContainer.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams vertParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            btnBrowse.setLayoutParams(vertParams);
            btnFocus.setLayoutParams(vertParams);
            btnEdit.setLayoutParams(vertParams);
        } else {
            modeButtonContainer.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams horzParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            btnBrowse.setLayoutParams(horzParams);
            btnFocus.setLayoutParams(horzParams);
            btnEdit.setLayoutParams(horzParams);
        }

        leftParams.weight = mode == MODE_BROWSE ? 2f : mode == MODE_FOCUS ? 0.5f : 1f;
        middleParams.weight = mode == MODE_BROWSE ? 3f : mode == MODE_FOCUS ? 4.5f : 2f;
        rightParams.weight = mode == MODE_BROWSE ? 5f : mode == MODE_FOCUS ? 5f : 7f;
        leftPanel.setLayoutParams(leftParams);
        middlePanel.setLayoutParams(middleParams);
        rightPanel.setLayoutParams(rightParams);

        if (mode == MODE_BROWSE) buildBrowseLeft();
        else if (mode == MODE_FOCUS) buildFocusLeft();
        else if (mode == MODE_EDIT) buildEditLeft();
    }

    private void buildBrowseLeft() {
        leftContainer.removeAllViews();
        final LinearLayout tabContainer = new LinearLayout(this);
        tabContainer.setOrientation(LinearLayout.VERTICAL);
        leftContainer.addView(tabContainer);

        final OreButton tabQuick = new OreButton(this);
        tabQuick.setText("快速跳转");
        tabQuick.setStyleSheet(StyleSheet.STYLE_GREEN);
        final OreButton tabTree = new OreButton(this);
        tabTree.setText("文件树");
        tabTree.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);

        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        tabQuick.setLayoutParams(tabParams);
        tabTree.setLayoutParams(tabParams);
        tabRow.addView(tabQuick);
        tabRow.addView(tabTree);
        tabContainer.addView(tabRow);

        final LinearLayout subContainer = new LinearLayout(this);
        subContainer.setOrientation(LinearLayout.VERTICAL);
        tabContainer.addView(subContainer);

        showQuickJump(subContainer);

        tabQuick.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                tabQuick.setStyleSheet(StyleSheet.STYLE_GREEN);
                tabTree.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
                showQuickJump(subContainer);
            }
        });
        tabTree.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                tabTree.setStyleSheet(StyleSheet.STYLE_GREEN);
                tabQuick.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
                showFileTree(subContainer);
            }
        });
    }

    private void buildFocusLeft() {
        leftContainer.removeAllViews();
        LinearLayout shortcut = new LinearLayout(this);
        shortcut.setOrientation(LinearLayout.VERTICAL);

        OreButton btnBeh = new OreButton(this);
        btnBeh.setText("行为包");
        btnBeh.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btnBeh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                File f = findPackDir("behavior_pack_");
                if (f != null) loadFileList(f);
                else Toast.makeText(EditorActivity.this, "未找到行为包", Toast.LENGTH_SHORT).show();
            }
        });
        shortcut.addView(btnBeh);
        UiUtils.addGap(this, shortcut, 4);

        OreButton btnRes = new OreButton(this);
        btnRes.setText("资源包");
        btnRes.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btnRes.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                File f = findPackDir("resource_pack_");
                if (f != null) loadFileList(f);
                else Toast.makeText(EditorActivity.this, "未找到资源包", Toast.LENGTH_SHORT).show();
            }
        });
        shortcut.addView(btnRes);
        UiUtils.addGap(this, shortcut, 4);

        OreButton btnScript = new OreButton(this);
        btnScript.setText("脚本目录");
        btnScript.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btnScript.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { jumpToCategory("脚本"); }
        });
        shortcut.addView(btnScript);
        UiUtils.addGap(this, shortcut, 4);

        OreButton btnTexture = new OreButton(this);
        btnTexture.setText("纹理目录");
        btnTexture.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btnTexture.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { jumpToCategory("纹理"); }
        });
        shortcut.addView(btnTexture);
        UiUtils.addGap(this, shortcut, 4);

        leftContainer.addView(shortcut);
    }

    private void buildEditLeft() {
        leftContainer.removeAllViews();
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        addFunctionAccordion(panel);
        leftContainer.addView(panel);
    }

    private void addFunctionAccordion(LinearLayout panel) {
        OreAccordion accordion = new OreAccordion(this);
        accordion.setTitle("功能");
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        OreButton btnBack = new OreButton(this);
        btnBack.setText("返回主界面");
        btnBack.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        content.addView(btnBack);
        UiUtils.addGap(this, content, 4);

        OreButton btnBackup = new OreButton(this);
        btnBackup.setText("创建备份");
        btnBackup.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btnBackup.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { createBackupInEditor(); }
        });
        content.addView(btnBackup);

        accordion.setContentView(content);
        panel.addView(accordion);
    }

    private void createBackupInEditor() {
        ProjectManager pm = new ProjectManager(this);
        pm.createBackup(projectDir);
    }

    // ==================== 快速跳转 / 文件树 ====================

    private void showQuickJump(LinearLayout parent) {
        parent.removeAllViews();
        File behDir = findPackDir("behavior_pack_");
        File resDir = findPackDir("resource_pack_");
        File scriptDir = findScriptDir();
        File texturesDir = findTexturesDir();

        if (behDir != null) {
            parent.addView(jumpButton("行为包", new View.OnClickListener() {
                @Override public void onClick(View v) { loadFileList(behDir); }
            }));
            UiUtils.addGap(this, parent, 4);
        }
        if (resDir != null) {
            parent.addView(jumpButton("资源包", new View.OnClickListener() {
                @Override public void onClick(View v) { loadFileList(resDir); }
            }));
            UiUtils.addGap(this, parent, 4);
        }
        if (scriptDir != null) {
            parent.addView(jumpButton("脚本", new View.OnClickListener() {
                @Override public void onClick(View v) { loadFileList(scriptDir); }
            }));
            UiUtils.addGap(this, parent, 4);
        }
        if (texturesDir != null) {
            parent.addView(jumpButton("纹理", new View.OnClickListener() {
                @Override public void onClick(View v) { loadFileList(texturesDir); }
            }));
            UiUtils.addGap(this, parent, 4);
        }
        parent.addView(jumpButton("清单文件", new View.OnClickListener() {
            @Override public void onClick(View v) { jumpToCategory("清单文件"); }
        }));
        UiUtils.addGap(this, parent, 4);
        parent.addView(jumpButton("配置", new View.OnClickListener() {
            @Override public void onClick(View v) { jumpToCategory("配置"); }
        }));
        UiUtils.addGap(this, parent, 4);
    }

    /** 控件库按钮：OreButton + STYLE_DARK_GRAY，与左侧分类其他按钮统一样式。 */
    private OreButton jumpButton(String text, View.OnClickListener listener) {
        OreButton btn = new OreButton(this);
        btn.setText(text);
        btn.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btn.setOnClickListener(listener);
        return btn;
    }

    private void showFileTree(LinearLayout parent) {
        parent.removeAllViews();
        buildTreeView(rootNode, parent);
    }

    private void buildTreeView(FileNode node, LinearLayout parent) {
        if (node.children == null) return;
        for (final FileNode child : node.children) {
            if (child.isDirectory) {
                OreAccordion accordion = new OreAccordion(this);
                accordion.setTitle(child.name);
                accordion.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { loadFileList(child.file); }
                });
                LinearLayout childContainer = new LinearLayout(this);
                childContainer.setOrientation(LinearLayout.VERTICAL);
                childContainer.setPadding(16, 0, 0, 0);
                buildTreeView(child, childContainer);
                if (childContainer.getChildCount() > 0) {
                    accordion.setContentView(childContainer);
                } else {
                    OreTextView empty = new OreTextView(this);
                    empty.setText("（空）");
                    empty.setTextColor(Color.GRAY);
                    empty.setTextSize(10);
                    accordion.setContentView(empty);
                }
                parent.addView(accordion);
            } else {
                OreButton fileBtn = new OreButton(this);
                fileBtn.setText(child.name);
                fileBtn.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
                fileBtn.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { openFileInEditor(child.file); }
                });
                parent.addView(fileBtn);
                UiUtils.addGap(this, parent, 2);
            }
        }
    }

    private FileNode scanDirectory(File dir) {
        FileNode node = new FileNode(dir);
        File[] files = dir.listFiles();
        if (files == null) return node;
        for (File f : files) {
            String name = f.getName();
            if (f.isDirectory() && (name.equals(".mcs") || name.endsWith("_back"))) continue;
            node.children.add(scanDirectory(f));
        }
        return node;
    }

    // ==================== 目录查找 ====================

    private File findPackDir(String prefix) {
        File[] rootFiles = projectDir.listFiles();
        if (rootFiles == null) return null;
        for (File f : rootFiles) {
            if (f.isDirectory() && f.getName().toLowerCase().startsWith(prefix)) return f;
        }
        return null;
    }

    private File findScriptDir() {
        File behDir = findPackDir("behavior_pack_");
        if (behDir == null) return null;
        File[] contents = behDir.listFiles();
        if (contents == null) return null;
        for (File sub : contents) {
            if (sub.isDirectory() && sub.getName().startsWith("Script_NeteaseMod")) return sub;
        }
        return null;
    }

    private File findTexturesDir() {
        File resDir = findPackDir("resource_pack_");
        if (resDir == null) return null;
        File texDir = new File(resDir, "textures");
        return texDir.exists() ? texDir : null;
    }

    // ==================== 文件列表 ====================

    private void loadFileList(File directory) {
        currentDir = directory;
        middleContainer.removeAllViews();
        showLoadingIndicator();
        // 脚本目录缺少预设文件时显示补齐卡片
        if (currentDir.getName().startsWith("Script_NeteaseMod")) {
            File initPy = new File(currentDir, "__init__.py");
            File modMainPy = new File(currentDir, "modMain.py");
            if (!initPy.exists() || !modMainPy.exists()) {
                OreCard card = new OreCard(this);
                card.setPadding(12, 10, 12, 10);
                LinearLayout cardLayout = new LinearLayout(this);
                cardLayout.setOrientation(LinearLayout.HORIZONTAL);
                cardLayout.setGravity(Gravity.CENTER_VERTICAL);
                cardLayout.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                ImageView icon = new ImageView(this);
                icon.setImageResource(android.R.drawable.ic_menu_add);
                icon.setColorFilter(Color.parseColor("#AAAAAA"));
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(48, 48);
                iconParams.setMargins(0, 0, 12, 0);
                icon.setLayoutParams(iconParams);
                cardLayout.addView(icon);
                OreTextView text = new OreTextView(this);
                text.setText("补齐脚本预设 (__init__.py, modMain.py)");
                text.setTextColor(Color.WHITE);
                text.setTextSize(13);
                text.setLayoutParams(new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
                cardLayout.addView(text);
                card.addView(cardLayout);
                card.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { createScriptFiles(); }
                });
                middleContainer.addView(card);
                UiUtils.addGap(this, middleContainer, 8);
            }
        }

        File[] allFiles = directory.listFiles();
        if (allFiles == null || allFiles.length == 0) {
            middleContainer.removeAllViews();
            OreTextView empty = new OreTextView(this);
            empty.setText("（空）");
            empty.setTextColor(Color.GRAY);
            middleContainer.addView(empty);
            return;
        }

        List<File> folders = new ArrayList<>();
        List<File> files = new ArrayList<>();
        for (File f : allFiles) {
            String name = f.getName();
            if (f.isDirectory() && (name.equals(".mcs") || name.endsWith("_back"))) continue;
            if (f.isDirectory()) folders.add(f);
            else files.add(f);
        }
        final List<File> sorted = new ArrayList<>();
        sorted.addAll(folders);
        sorted.addAll(files);

        middleContainer.post(new Runnable() {
            @Override
            public void run() {
                int containerWidth = middleContainer.getWidth();
                if (containerWidth == 0) {
                    containerWidth = getResources().getDisplayMetrics().widthPixels / 2;
                }
                float density = getResources().getDisplayMetrics().density;

                if (currentMode == MODE_EDIT) {
                    LinearLayout listLayout = new LinearLayout(EditorActivity.this);
                    listLayout.setOrientation(LinearLayout.VERTICAL);
                    for (File f : sorted) {
                        listLayout.addView(createFileCard(f, LinearLayout.LayoutParams.MATCH_PARENT, true));
                        UiUtils.addGap(EditorActivity.this, listLayout, 6);
                    }
                    middleContainer.removeAllViews();
                    middleContainer.addView(listLayout);
                    return;
                }

                int blockWidth = (int) (BLOCK_WIDTH_DP * density + 0.5f);
                int spacing = (int) (BLOCK_SPACING_DP * density + 0.5f);
                int columns = (containerWidth + spacing) / (blockWidth + spacing);
                if (columns <= 0) columns = 1;

                LinearLayout rowsLayout = new LinearLayout(EditorActivity.this);
                rowsLayout.setOrientation(LinearLayout.VERTICAL);
                LinearLayout currentRow = null;
                for (int i = 0; i < sorted.size(); i++) {
                    if (i % columns == 0) {
                        currentRow = new LinearLayout(EditorActivity.this);
                        currentRow.setOrientation(LinearLayout.HORIZONTAL);
                        rowsLayout.addView(currentRow);
                    }
                    currentRow.addView(createFileCard(sorted.get(i), blockWidth, false));
                }
                middleContainer.removeAllViews();
                middleContainer.addView(rowsLayout);
            }
        });
    }

    /** 中间区域加载动画：文件列表构建完成前显示。 */
    private void showLoadingIndicator() {
        LinearLayout loading = new LinearLayout(this);
        loading.setOrientation(LinearLayout.VERTICAL);
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(0, UiUtils.dp(this, 32), 0, UiUtils.dp(this, 32));
        loading.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        ProgressBar bar = new ProgressBar(this);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                UiUtils.dp(this, 36), UiUtils.dp(this, 36)));
        loading.addView(bar);
        OreTextView tv = new OreTextView(this);
        tv.setText("加载中");
        tv.setTextColor(Color.parseColor("#AAAAAA"));
        tv.setTextSize(12);
        tv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tvLp.topMargin = UiUtils.dp(this, 10);
        tv.setLayoutParams(tvLp);
        loading.addView(tv);
        middleContainer.addView(loading);
    }

    private OreCard createFileCard(final File file, int width, boolean isListMode) {
        OreCard card = new OreCard(this);
        card.setPadding(6, 8, 6, 8);

        LinearLayout cardLayout;
        if (isListMode) {
            cardLayout = new LinearLayout(this);
            cardLayout.setOrientation(LinearLayout.HORIZONTAL);
            cardLayout.setGravity(Gravity.CENTER_VERTICAL);
        } else {
            cardLayout = new LinearLayout(this);
            cardLayout.setOrientation(LinearLayout.VERTICAL);
            cardLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        }

        boolean isImage = file.getName().toLowerCase().matches(".*\\.(png|jpg|jpeg)$");
        // 关键：cardLayout 必须占满 OreCard 宽度（MATCH_PARENT），
        // 否则内部 weight=1.0 的文本在 WRAP_CONTENT 父容器中宽度被计算为 0，右侧内容显示不出。
        cardLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);

        if (file.isDirectory()) {
            icon.setImageResource(R.drawable.ic_folder);
            icon.setColorFilter(Color.parseColor("#AAAAAA"));
        } else if (isImage) {
            BitmapDrawable thumb = loadPixelThumbnail(file, width);
            if (thumb != null) {
                icon.setImageDrawable(thumb);
            } else {
                icon.setImageResource(android.R.drawable.ic_menu_gallery);
                icon.setColorFilter(Color.parseColor("#AAAAAA"));
            }
        } else {
            icon.setImageResource(android.R.drawable.ic_menu_edit);
            icon.setColorFilter(Color.parseColor("#AAAAAA"));
        }

        OreTextView nameText = new OreTextView(this);
        nameText.setText(file.getName());
        nameText.setTextColor(Color.WHITE);
        nameText.setTextSize(isListMode ? 13 : 10);
        nameText.setGravity(Gravity.CENTER);
        nameText.setMaxLines(2);
        nameText.setEllipsize(TextUtils.TruncateAt.END);

        if (isListMode) {
            int iconSize = 44;
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            iconParams.setMargins(0, 0, 12, 0);
            icon.setLayoutParams(iconParams);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            nameText.setLayoutParams(textParams);
            cardLayout.addView(icon);
            cardLayout.addView(nameText);
            card.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        } else {
            int iconSize = (int) (width * 0.4);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            iconParams.gravity = Gravity.CENTER;
            icon.setLayoutParams(iconParams);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            nameText.setLayoutParams(textParams);
            cardLayout.addView(icon);
            cardLayout.addView(nameText);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(0, 0, 8, 8);
            card.setLayoutParams(cardParams);
        }

        card.addView(cardLayout);
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (file.isDirectory()) loadFileList(file);
                else openFileInEditor(file);
            }
        });
        return card;
    }

    private BitmapDrawable loadPixelThumbnail(File imageFile, int targetWidth) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);
            int reqWidth = targetWidth > 0 ? targetWidth : 150;
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqWidth);
            options.inJustDecodeBounds = false;
            options.inScaled = false;
            options.inPreferQualityOverSpeed = false;
            Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);
            if (bitmap == null) return null;
            BitmapDrawable drawable = new BitmapDrawable(getResources(), bitmap);
            drawable.setFilterBitmap(false);
            return drawable;
        } catch (Exception e) {
            return null;
        }
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private Bitmap loadBitmapSafe(File file, int maxWidth, int maxHeight) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight, maxWidth, maxHeight);
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (Exception e) {
            return null;
        }
    }

    private int calculateSampleSize(int width, int height, int reqWidth, int reqHeight) {
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            while ((height / inSampleSize) > reqHeight && (width / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    // ==================== 打开文件 ====================

    private void openFileInEditor(File file) {
        editorContainer.removeAllViews();

        if (isInRecipesDirectory(file) && file.getName().toLowerCase().endsWith(".json")) {
            recipeEditor.open(file, editorContainer);
            return;
        }

        String lowerName = file.getName().toLowerCase();
        if (lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            buildImagePreview(file);
            return;
        }
        buildCodeEditor(file);
    }

    private boolean isInRecipesDirectory(File file) {
        File parent = file.getParentFile();
        while (parent != null && !parent.equals(projectDir)) {
            if ("netease_recipes".equals(parent.getName())) return true;
            parent = parent.getParentFile();
        }
        return false;
    }

    // ==================== RecipeEditor.Host ====================

    @Override
    public void refreshFileList(File dir) {
        if (dir != null) loadFileList(dir);
    }

    @Override
    public void showSourceEditor(File file) {
        editorContainer.removeAllViews();
        buildCodeEditor(file, editorContainer);
    }

    // ==================== 分类跳转 ====================

    private void jumpToCategory(String category) {
        File targetDir = null;
        File[] rootFiles = projectDir.listFiles();

        if ("脚本".equals(category)) {
            targetDir = findScriptDir();
            if (targetDir != null && targetDir.exists()) {
                loadFileList(targetDir);
            } else {
                Toast.makeText(this, "没有找到脚本目录", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (rootFiles != null) {
            for (File f : rootFiles) {
                String name = f.getName().toLowerCase();
                if (category.equals("行为包") && f.isDirectory()
                        && (name.startsWith("behavior_pack_") || name.startsWith("behaviour_pack_"))) {
                    targetDir = f;
                    break;
                } else if (category.equals("资源包") && f.isDirectory() && name.startsWith("resource_pack_")) {
                    targetDir = f;
                    break;
                }
            }
        }

        if ("纹理".equals(category)) {
            targetDir = findTexturesDir();
        } else if ("清单文件".equals(category)) {
            middleContainer.removeAllViews();
            if (rootFiles != null) {
                for (File f : rootFiles) {
                    String name = f.getName().toLowerCase();
                    if (f.isDirectory() && name.startsWith("behavior_pack_")) {
                        showSpecificFile(new File(f, "manifest.json"), "行为包 manifest.json");
                    } else if (f.isDirectory() && name.startsWith("resource_pack_")) {
                        showSpecificFile(new File(f, "manifest.json"), "资源包 manifest.json");
                    }
                }
                showSpecificFile(new File(projectDir, "studio.json"), "工作室配置");
            }
            return;
        } else if ("配置".equals(category)) {
            middleContainer.removeAllViews();
            showSpecificFile(new File(projectDir, "studio.json"), "工作室配置");
            showSpecificFile(new File(projectDir, "work.mcscfg"), "工作区配置");
            return;
        }

        if (targetDir != null && targetDir.exists()) {
            loadFileList(targetDir);
        } else {
            Toast.makeText(this, "没有找到对应目录", Toast.LENGTH_SHORT).show();
        }
    }

    private void showSpecificFile(final File file, String label) {
        if (!file.exists()) return;
        OreCard card = new OreCard(this);
        card.setPadding(8, 8, 8, 8);
        LinearLayout cardLayout = new LinearLayout(this);
        cardLayout.setOrientation(LinearLayout.HORIZONTAL);
        cardLayout.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(android.R.drawable.ic_menu_view);
        icon.setColorFilter(Color.parseColor("#AAAAAA"));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(36, 36);
        iconParams.setMargins(0, 0, 10, 0);
        icon.setLayoutParams(iconParams);
        cardLayout.addView(icon);
        OreTextView text = new OreTextView(this);
        text.setText(label);
        text.setTextColor(Color.WHITE);
        text.setTextSize(12);
        cardLayout.addView(text);
        card.addView(cardLayout);
        card.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openFileInEditor(file); }
        });
        middleContainer.addView(card);
        UiUtils.addGap(this, middleContainer, 6);
    }

    // ==================== 图片预览 ====================

    private void buildImagePreview(final File imageFile) {
        LinearLayout previewLayout = new LinearLayout(this);
        previewLayout.setOrientation(LinearLayout.VERTICAL);
        previewLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout actionBar = new LinearLayout(this);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);
        actionBar.setPadding(0, 0, 0, 8);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);

        actionBar.addView(previewButton("重命名", StyleSheet.STYLE_DARK_GRAY, btnParams, new View.OnClickListener() {
            @Override public void onClick(View v) { showRenameDialog(imageFile); }
        }));
        actionBar.addView(previewButton("删除", StyleSheet.STYLE_RED, btnParams, new View.OnClickListener() {
            @Override public void onClick(View v) { showDeleteFileConfirm(imageFile); }
        }));
        actionBar.addView(previewButton("复制纹理路径", StyleSheet.STYLE_DARK_GRAY, btnParams, new View.OnClickListener() {
            @Override public void onClick(View v) {
                String fullPath = imageFile.getAbsolutePath();
                int index = fullPath.indexOf("textures");
                if (index != -1) {
                    String relativePath = fullPath.substring(index);
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText("path", relativePath));
                    Toast.makeText(EditorActivity.this, "路径已复制: " + relativePath, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(EditorActivity.this, "未找到 textures 目录", Toast.LENGTH_SHORT).show();
                }
            }
        }));
        previewLayout.addView(actionBar);

        final ImageView imageView = new ImageView(this);
        imageView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        imageView.setBackgroundColor(Color.parseColor("#222222"));
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        new Thread(new Runnable() {
            @Override public void run() {
                final Bitmap bitmap = loadBitmapSafe(imageFile, 2048, 2048);
                UiUtils.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (bitmap != null) {
                            BitmapDrawable drawable = new BitmapDrawable(getResources(), bitmap);
                            drawable.setFilterBitmap(false);
                            imageView.setImageDrawable(drawable);
                        } else {
                            Toast.makeText(EditorActivity.this, "图片加载失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();

        previewLayout.addView(imageView);
        editorContainer.addView(previewLayout);
    }

    private OreButton previewButton(String text, StyleSheet style, LinearLayout.LayoutParams params, View.OnClickListener listener) {
        OreButton btn = new OreButton(this);
        btn.setText(text);
        btn.setStyleSheet(style);
        btn.setLayoutParams(params);
        btn.setOnClickListener(listener);
        return btn;
    }

    // ==================== 代码编辑器 ====================

    private void buildCodeEditor(final File file, final LinearLayout parent) {
        LinearLayout editorLayout = new LinearLayout(this);
        editorLayout.setOrientation(LinearLayout.VERTICAL);
        editorLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setPadding(0, 0, 0, 8);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);

        OreButton btnSave = new OreButton(this);
        btnSave.setText("保存");
        btnSave.setStyleSheet(StyleSheet.STYLE_GREEN);
        btnSave.setLayoutParams(btnParams);
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { saveFileContent(file, codeEditText.getText().toString()); }
        });
        toolbar.addView(btnSave);

        OreButton btnReset = new OreButton(this);
        btnReset.setText("重置");
        btnReset.setStyleSheet(StyleSheet.STYLE_RED);
        btnReset.setLayoutParams(btnParams);
        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                codeEditText.setText(originalContent);
                Content c = codeEditText.getText();
                int lastLine = Math.max(0, c.getLineCount() - 1);
                codeEditText.setSelection(lastLine, c.getColumnCount(lastLine));
            }
        });
        toolbar.addView(btnReset);
        OreButton btnFullscreen = new OreButton(this);
        btnFullscreen.setText("竖屏");
        btnFullscreen.setStyleSheet(StyleSheet.STYLE_PURPLE);
        btnFullscreen.setLayoutParams(btnParams);
        btnFullscreen.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                saveFileContent(file, codeEditText.getText().toString());
                Intent intent = new Intent(EditorActivity.this, SourceEditorActivity.class);
                intent.putExtra(SourceEditorActivity.EXTRA_PROJECT_DIR, projectDir.getAbsolutePath());
                intent.putExtra(SourceEditorActivity.EXTRA_CURRENT_DIR,
                        currentDir != null ? currentDir.getAbsolutePath() : file.getParentFile().getAbsolutePath());
                intent.putExtra(SourceEditorActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
                startActivity(intent);
            }
        });
        toolbar.addView(btnFullscreen);
        editorLayout.addView(toolbar);

        codeEditText = new CodeEditor(this);
        codeEditText.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        codeEditText.setTypefaceText(Typeface.MONOSPACE);
        codeEditText.setTextSize(11);
        codeEditText.setColorScheme(new UserColorScheme(this));
        codeEditText.setLineNumberEnabled(true);
        codeEditText.setUndoEnabled(true);
        codeEditText.setHighlightCurrentLine(true);
        codeEditText.setHighlightCurrentBlock(true);

        String content = FileUtils.readText(file);
        originalContent = content;
        codeEditText.setText(content);
        codeEditText.setSelection(0, 0);
        String fileName = file.getName().toLowerCase();
        boolean hl = hlEnabled(fileName);
        if (hl && fileName.endsWith(".py")) {
            codeEditText.setEditorLanguage(new SimpleSyntaxLanguage(false));
        } else if (hl && (fileName.endsWith(".json") || fileName.endsWith(".js") || fileName.endsWith(".mcfunction"))) {
            codeEditText.setEditorLanguage(new SimpleSyntaxLanguage(false));
        } else if (hl && fileName.endsWith(".lang")) {
            codeEditText.setEditorLanguage(new SimpleSyntaxLanguage(false));
        } else {
            codeEditText.setEditorLanguage(new EmptyLanguage());
        }
        editorLayout.addView(codeEditText);
        parent.addView(editorLayout);
    }
    /** 根据设置判断该文件类型是否启用语法高亮。 */
    private boolean hlEnabled(String fileName) {
        SettingsManager settings = SettingsManager.getInstance(this);
        if (fileName.endsWith(".py")) return settings.getBoolean(HighlightSettingsActivity.KEY_HL_PY, true);
        if (fileName.endsWith(".json")) return settings.getBoolean(HighlightSettingsActivity.KEY_HL_JSON, true);
        if (fileName.endsWith(".lang")) return settings.getBoolean(HighlightSettingsActivity.KEY_HL_LANG, true);
        if (fileName.endsWith(".js")) return settings.getBoolean(HighlightSettingsActivity.KEY_HL_JS, true);
        if (fileName.endsWith(".mcfunction")) return settings.getBoolean(HighlightSettingsActivity.KEY_HL_MCFUNCTION, true);
        return false;
    }

    private void buildCodeEditor(final File file) {
        buildCodeEditor(file, editorContainer);
    }

    private void saveFileContent(File file, String content) {
        try {
            FileUtils.writeText(file, content);
            originalContent = content;
            Toast.makeText(this, "文件已保存", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== 删除 / 重命名 ====================

    private void showDeleteFileConfirm(final File file) {
        OreTextView msg = new OreTextView(this);
        msg.setText("确定要删除文件 \"" + file.getName() + "\" 吗？");
        msg.setTextColor(Color.WHITE);
        msg.setTextSize(14);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        int pad = UiUtils.dp(this, 16);
        layout.setPadding(pad, pad, pad, pad);
        layout.addView(msg);

        OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("删除文件");
        builder.setView(layout);
        builder.setPositiveButton("删除", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                if (FileUtils.deleteRecursive(file)) {
                    Toast.makeText(EditorActivity.this, "文件已删除", Toast.LENGTH_SHORT).show();
                    editorContainer.removeAllViews();
                    loadFileList(currentDir);
                } else {
                    Toast.makeText(EditorActivity.this, "删除失败", Toast.LENGTH_SHORT).show();
                }
                dialog.dismiss();
            }
        });
        builder.getPositiveButton().setStyleSheet(StyleSheet.STYLE_RED);
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
        });
        builder.show();
    }

    private void showRenameDialog(final File file) {
        final OreEditText input = new OreEditText(this);
        input.setHint("输入新名称");
        input.setText(file.getName());

        OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("重命名文件");
        builder.setView(input);
        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                String newName = input.getText().toString().trim();
                if (newName.isEmpty()) { Toast.makeText(EditorActivity.this, "名称不能为空", Toast.LENGTH_SHORT).show(); return; }
                File newFile = new File(file.getParent(), newName);
                if (newFile.exists()) { Toast.makeText(EditorActivity.this, "文件名已存在", Toast.LENGTH_SHORT).show(); return; }
                boolean success = file.renameTo(newFile);
                if (success) {
                    Toast.makeText(EditorActivity.this, "重命名成功", Toast.LENGTH_SHORT).show();
                    loadFileList(currentDir);
                    if (newFile.getName().toLowerCase().matches(".*\\.(png|jpg|jpeg)$")) {
                        buildImagePreview(newFile);
                    }
                } else {
                    Toast.makeText(EditorActivity.this, "重命名失败", Toast.LENGTH_SHORT).show();
                }
                dialog.dismiss();
            }
        });
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
        });
        builder.getNegativeButton().setStyleSheet(StyleSheet.STYLE_RED);
        builder.show();
    }

    // ==================== 导入图片 ====================

    private void showImportDialog() {
        OreTextView msgText = new OreTextView(this);
        msgText.setText("选择导入类型");
        msgText.setTextColor(Color.WHITE);
        msgText.setTextSize(14);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        int pad = UiUtils.dp(this, 16);
        layout.setPadding(pad, pad, pad, pad);
        layout.addView(msgText);

        OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("导入文件");
        builder.setView(layout);
        builder.setPositiveButton("导入图片", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                openImagePicker();
                dialog.dismiss();
            }
        });
        builder.getPositiveButton().setStyleSheet(StyleSheet.STYLE_GREEN);
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
        });
        builder.getNegativeButton().setStyleSheet(StyleSheet.STYLE_RED);
        builder.show();
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, REQUEST_IMPORT_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMPORT_IMAGE && resultCode == RESULT_OK && data != null) {
            List<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) {
                ClipData clipData = data.getClipData();
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    uris.add(clipData.getItemAt(i).getUri());
                }
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            for (Uri uri : uris) importImageToTextures(uri);
            loadFileList(currentDir);
        }
    }

    private void importImageToTextures(Uri imageUri) {
        try {
            String originalName = getFileNameFromUri(imageUri);
            if (originalName == null) originalName = "imported_" + System.currentTimeMillis() + ".png";

            File texturesDir = findTexturesDir();
            if (texturesDir == null) {
                Toast.makeText(this, "未找到textures目录", Toast.LENGTH_SHORT).show();
                return;
            }

            File destFile = new File(texturesDir, originalName);
            if (destFile.exists()) {
                String nameWithoutExt = originalName;
                String ext = "";
                int dot = originalName.lastIndexOf('.');
                if (dot != -1) {
                    nameWithoutExt = originalName.substring(0, dot);
                    ext = originalName.substring(dot);
                }
                destFile = new File(texturesDir, nameWithoutExt + "_" + System.currentTimeMillis() + ext);
            }

            java.io.InputStream is = getContentResolver().openInputStream(imageUri);
            if (is == null) throw new IOException("无法读取图片");
            try (java.io.InputStream in = is; java.io.FileOutputStream out = new java.io.FileOutputStream(destFile)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) out.write(buffer, 0, len);
            }
            Toast.makeText(this, "图片导入成功", Toast.LENGTH_SHORT).show();
            loadFileList(currentDir);
        } catch (Exception e) {
            Toast.makeText(this, "导入失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String name = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex);
                }
                cursor.close();
            }
        }
        if (name == null) name = uri.getLastPathSegment();
        return name;
    }

    // ==================== 新建 ====================

    private void showNewItemDialog() {
        OreTextView msg = new OreTextView(this);
        msg.setText("新建类型");
        msg.setTextColor(Color.WHITE);
        msg.setTextSize(14);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        int pad = UiUtils.dp(this, 16);
        layout.setPadding(pad, pad, pad, pad);
        layout.addView(msg);
        UiUtils.addGap(this, layout, 12);

        ScrollView scrollContent = new ScrollView(this);
        LinearLayout buttonContainer = new LinearLayout(this);
        buttonContainer.setOrientation(LinearLayout.VERTICAL);
        buttonContainer.setPadding(0, 8, 0, 8);

        final OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("新建");
        builder.setView(layout);
        final android.app.Dialog[] outerDialog = new android.app.Dialog[1];

        buttonContainer.addView(newItemButton("新建文件", new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (outerDialog[0] != null) outerDialog[0].dismiss();
                showNewFileDialog();
            }
        }));
        UiUtils.addGap(this, buttonContainer, 8);
        buttonContainer.addView(newItemButton("新建配方", new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (outerDialog[0] != null) outerDialog[0].dismiss();
                showNewRecipeDialog();
            }
        }));
        UiUtils.addGap(this, buttonContainer, 8);
        buttonContainer.addView(newItemButton("新建文件夹", new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (outerDialog[0] != null) outerDialog[0].dismiss();
                showNewFolderDialog();
            }
        }));
        if (needScriptPreset()) {
            UiUtils.addGap(this, buttonContainer, 8);
            buttonContainer.addView(newItemButton("添加脚本预设", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (outerDialog[0] != null) outerDialog[0].dismiss();
                    addScriptPreset();
                }
            }));
        }

        scrollContent.addView(buttonContainer);
        layout.addView(scrollContent);
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
        });
        outerDialog[0] = builder.show();
    }

    private OreButton newItemButton(String text, View.OnClickListener listener) {
        OreButton btn = new OreButton(this);
        btn.setText(text);
        btn.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btn.setOnClickListener(listener);
        return btn;
    }

    private void showNewFolderDialog() {
        final OreEditText input = new OreEditText(this);
        input.setHint("输入文件夹名，如 textures");
        input.setTextSize(14);

        OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("新建文件夹");
        builder.setView(input);
        builder.setPositiveButton("创建", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) { Toast.makeText(EditorActivity.this, "文件夹名不能为空", Toast.LENGTH_SHORT).show(); return; }
                if (hasInvalidChars(name)) { Toast.makeText(EditorActivity.this, "文件夹名包含非法字符", Toast.LENGTH_SHORT).show(); return; }
                createNewFolder(name);
                dialog.dismiss();
            }
        });
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
        });
        builder.show();
    }

    private void showNewFileDialog() {
        final OreEditText input = new OreEditText(this);
        input.setHint("输入文件名，如 my_script.py");
        input.setTextSize(14);

        OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("新建文件");
        builder.setView(input);
        builder.setPositiveButton("创建", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) { Toast.makeText(EditorActivity.this, "文件名不能为空", Toast.LENGTH_SHORT).show(); return; }
                if (hasInvalidChars(name)) { Toast.makeText(EditorActivity.this, "文件名包含非法字符", Toast.LENGTH_SHORT).show(); return; }
                createNewFile(name);
                dialog.dismiss();
            }
        });
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
        });
        builder.show();
    }

    private boolean hasInvalidChars(String name) {
        return name.contains("/") || name.contains("\\") || name.contains(":")
                || name.contains("*") || name.contains("?") || name.contains("\"")
                || name.contains("<") || name.contains(">") || name.contains("|");
    }

    private void createNewFolder(String name) {
        if (currentDir == null) { Toast.makeText(this, "当前目录无效", Toast.LENGTH_SHORT).show(); return; }
        File newDir = new File(currentDir, name);
        if (newDir.exists()) { Toast.makeText(this, "文件夹已存在", Toast.LENGTH_SHORT).show(); return; }
        if (newDir.mkdirs()) {
            Toast.makeText(this, "文件夹创建成功", Toast.LENGTH_SHORT).show();
            loadFileList(currentDir);
        } else {
            Toast.makeText(this, "文件夹创建失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void createNewFile(String name) {
        if (currentDir == null) { Toast.makeText(this, "当前目录无效", Toast.LENGTH_SHORT).show(); return; }
        File newFile = new File(currentDir, name);
        if (newFile.exists()) { Toast.makeText(this, "文件已存在", Toast.LENGTH_SHORT).show(); return; }
        try {
            if (newFile.createNewFile()) {
                Toast.makeText(this, "文件创建成功", Toast.LENGTH_SHORT).show();
                loadFileList(currentDir);
            } else {
                Toast.makeText(this, "文件创建失败", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Toast.makeText(this, "创建失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== 脚本预设 ====================

    private boolean needScriptPreset() {
        return findScriptDir() == null && findPackDir("behavior_pack_") != null;
    }

    private void addScriptPreset() {
        File behDir = findPackDir("behavior_pack_");
        if (behDir == null) {
            Toast.makeText(this, "未找到行为包", Toast.LENGTH_SHORT).show();
            return;
        }
        String suffix = FileUtils.randomString(8);
        File scriptDir = new File(behDir, "Script_NeteaseMod" + suffix);
        if (!scriptDir.mkdirs()) {
            Toast.makeText(this, "创建脚本目录失败", Toast.LENGTH_SHORT).show();
            return;
        }
        FileUtils.writeTextQuietly(new File(scriptDir, "__init__.py"), "");
        FileUtils.writeTextQuietly(new File(scriptDir, "modMain.py"), ModTemplates.scriptMain(scriptDir.getName()));
        Toast.makeText(this, "脚本预设创建成功", Toast.LENGTH_SHORT).show();
        loadFileList(scriptDir);
    }

    private void createScriptFiles() {
        if (currentDir == null || !currentDir.getName().startsWith("Script_NeteaseMod")) {
            Toast.makeText(this, "当前不是脚本文件夹", Toast.LENGTH_SHORT).show();
            return;
        }
        File initFile = new File(currentDir, "__init__.py");
        File modMainFile = new File(currentDir, "modMain.py");
        try {
            if (!initFile.exists()) initFile.createNewFile();
            if (!modMainFile.exists()) {
                FileUtils.writeText(modMainFile, ModTemplates.scriptMain(currentDir.getName()));
            }
            Toast.makeText(this, "脚本预设创建成功", Toast.LENGTH_SHORT).show();
            loadFileList(currentDir);
        } catch (IOException e) {
            Toast.makeText(this, "创建失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== 新建配方 ====================

    private void showNewRecipeDialog() {
        final OreEditText inputName = new OreEditText(this);
        inputName.setHint("配方名称（英文）");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = UiUtils.dp(this, 12);
        layout.setPadding(pad, pad, pad, pad);
        layout.addView(inputName);
        UiUtils.addGap(this, layout, 12);

        final String[] types = {"有序合成", "无序合成", "熔炉", "酿造", "锻造"};
        final String[] typeIds = {"recipe_shaped", "recipe_shapeless", "recipe_furnace",
                "recipe_brewing_mix", "recipe_smithing_transform"};

        final OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("新建配方");
        builder.setView(layout);
        final android.app.Dialog[] dialogRef = new android.app.Dialog[1];

        final String namespace = JsonUtils.getProjectNamespace(projectDir);

        for (int i = 0; i < types.length; i++) {
            final int index = i;
            OreButton typeBtn = new OreButton(this);
            typeBtn.setText(types[i]);
            typeBtn.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
            typeBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    String name = inputName.getText().toString().trim();
                    if (name.isEmpty()) { Toast.makeText(EditorActivity.this, "名称不能为空", Toast.LENGTH_SHORT).show(); return; }
                    if (dialogRef[0] != null) dialogRef[0].dismiss();
                    createNewRecipe(name, namespace, typeIds[index]);
                }
            });
            layout.addView(typeBtn);
            UiUtils.addGap(this, layout, 6);
        }

        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
        });
        builder.getNegativeButton().setStyleSheet(StyleSheet.STYLE_RED);
        dialogRef[0] = builder.show();
    }

    private void createNewRecipe(String name, String namespace, String recipeType) {
        File recipesDir = null;

        // 1. 当前目录位于 netease_recipes 内则就地创建
        if (currentDir != null) {
            File dir = currentDir;
            while (dir != null && !dir.equals(projectDir)) {
                if (dir.getName().equals("netease_recipes")) {
                    recipesDir = currentDir;
                    break;
                }
                dir = dir.getParentFile();
            }
        }

        // 2. 默认 behavior_pack/netease_recipes
        if (recipesDir == null) {
            File behDir = findPackDir("behavior_pack_");
            if (behDir == null) {
                Toast.makeText(this, "未找到行为包", Toast.LENGTH_SHORT).show();
                return;
            }
            recipesDir = new File(behDir, "netease_recipes");
        }

        if (!recipesDir.exists()) recipesDir.mkdirs();

        String fileName = namespace + "_" + name + ".json";
        File recipeFile = new File(recipesDir, fileName);
        if (recipeFile.exists()) {
            Toast.makeText(this, "配方已存在", Toast.LENGTH_SHORT).show();
            return;
        }

        String template = ModTemplates.recipeTemplate(recipeType, namespace, name);
        if (template.isEmpty()) {
            Toast.makeText(this, "不支持的配方类型", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            FileUtils.writeText(recipeFile, template);
            Toast.makeText(this, "配方创建成功", Toast.LENGTH_SHORT).show();
            loadFileList(recipesDir);
        } catch (IOException e) {
            Toast.makeText(this, "创建失败", Toast.LENGTH_SHORT).show();
        }
    }
}