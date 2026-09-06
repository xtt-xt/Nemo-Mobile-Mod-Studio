package com.xtt.mcmodmaker;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import com.xtt.mcmodmaker.editor.SimpleSyntaxLanguage;
import com.xtt.mcmodmaker.editor.UserColorScheme;
import com.xtt.mcmodmaker.util.FileUtils;
import com.xtt.mcmodmaker.util.SettingsManager;
import com.xtt.mcmodmaker.util.UiUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import dev1503.oreui.StyleSheet;
import dev1503.oreui.dialog.OreDialogBuilder;
import dev1503.oreui.widgets.OreButton;
import dev1503.oreui.widgets.OreTextView;
import androidx.appcompat.app.AlertDialog;
import io.github.rosemoe.sora.lang.EmptyLanguage;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.EditorSearcher;
import io.github.rosemoe.sora.widget.schemes.SchemeVS2019;

/**
 * 竖屏源码编辑器：浏览器式标签页 + 代码补全 + 搜索 + 左右滑动切换文件。
 * 顶部标签栏展示当前文件夹内的文件，点击 / 左右滑动切换；
 * "全部"按钮打开文件树选择文件；"搜索"按钮打开搜索栏。
 */
public class SourceEditorActivity extends Activity {

    public static final String EXTRA_PROJECT_DIR = "project_dir";
    public static final String EXTRA_CURRENT_DIR = "current_dir";
    public static final String EXTRA_FILE_PATH = "file_path";

    private File projectDir;
    private File currentDir;
    private File currentFile;
    private List<File> fileList = new ArrayList<>();
    private int currentIndex = -1;

    private CodeEditor editor;
    private LinearLayout root;
    private LinearLayout tabContainer;
    private HorizontalScrollView tabScroll;
    private LinearLayout searchBar;
    private EditText searchInput;
    private EditorSearcher searcher;
    private String originalContent = "";
    private boolean searchVisible = false;
    private android.view.GestureDetector gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#1E1E1E"));
        Intent intent = getIntent();
        projectDir = new File(intent.getStringExtra(EXTRA_PROJECT_DIR));
        currentDir = new File(intent.getStringExtra(EXTRA_CURRENT_DIR));
        String filePath = intent.getStringExtra(EXTRA_FILE_PATH);
        buildUi();
        loadFolderFiles();
        if (filePath != null) {
            openFile(new File(filePath));
        } else if (!fileList.isEmpty()) {
            openFile(fileList.get(0));
        }
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#1E1E1E"));
        setContentView(root);

        // ===== 顶部工具行：返回 + 全部 + 搜索 =====
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(UiUtils.dp(this, 4), UiUtils.dp(this, 4),
                UiUtils.dp(this, 4), UiUtils.dp(this, 4));
        topBar.setBackgroundColor(Color.parseColor("#252526"));
        OreButton btnBack = new OreButton(this);
        btnBack.setText("返回");
        btnBack.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        topBar.addView(btnBack);
        OreButton btnSave = new OreButton(this);
        btnSave.setText("保存");
        btnSave.setStyleSheet(StyleSheet.STYLE_GREEN);
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                saveCurrent();
                Toast.makeText(SourceEditorActivity.this, "已保存", Toast.LENGTH_SHORT).show();
            }
        });
        topBar.addView(btnSave);
        OreButton btnReset = new OreButton(this);
        btnReset.setText("重置");
        btnReset.setStyleSheet(StyleSheet.STYLE_RED);
        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (currentFile == null || editor == null) return;
                editor.setText(FileUtils.readText(currentFile));
                Toast.makeText(SourceEditorActivity.this, "已重置为磁盘内容", Toast.LENGTH_SHORT).show();
            }
        });
        topBar.addView(btnReset);
        // 中间弹性占位：让 搜索 靠右
        topBar.addView(new View(this), new LinearLayout.LayoutParams(
                0, 1, 1.0f));
        OreButton btnSearch = new OreButton(this);
        btnSearch.setText("搜索");
        btnSearch.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btnSearch.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleSearch(); }
        });
        topBar.addView(btnSearch);
        root.addView(topBar);
        // ===== 标签栏行：浏览器式标签页（单独一行）+ 全部按钮在右侧 =====
        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setGravity(Gravity.CENTER_VERTICAL);
        tabBar.setPadding(UiUtils.dp(this, 4), UiUtils.dp(this, 0),
                UiUtils.dp(this, 4), UiUtils.dp(this, 4));
        tabBar.setBackgroundColor(Color.parseColor("#252526"));
        tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabContainer = new LinearLayout(this);
        tabContainer.setOrientation(LinearLayout.HORIZONTAL);
        tabScroll.addView(tabContainer);
        tabBar.addView(tabScroll, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        OreButton btnAll = new OreButton(this);
        btnAll.setText("全部");
        btnAll.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btnAll.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showFileTreeDialog(); }
        });
        tabBar.addView(btnAll);
        root.addView(tabBar);

        // ===== 搜索栏（默认隐藏）=====
        searchBar = new LinearLayout(this);
        searchBar.setOrientation(LinearLayout.VERTICAL);
        searchBar.setPadding(UiUtils.dp(this, 8), UiUtils.dp(this, 4),
                UiUtils.dp(this, 8), UiUtils.dp(this, 4));
        searchBar.setBackgroundColor(Color.parseColor("#2D2D30"));
        searchInput = new EditText(this);
        searchInput.setHint("搜索关键词");
        searchInput.setTextColor(Color.WHITE);
        searchInput.setHintTextColor(Color.parseColor("#888888"));
        searchInput.setSingleLine(true);
        searchInput.setTextSize(13);
        searchInput.setBackgroundColor(Color.parseColor("#3E3E42"));
        searchInput.setPadding(UiUtils.dp(this, 8), 0, UiUtils.dp(this, 8), 0);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (searcher == null) return;
                String q = s.toString();
                if (q.isEmpty()) {
                    searcher.stopSearch();
                } else {
                    searcher.search(q, new EditorSearcher.SearchOptions(true, false));
                }
            }
        });
        // 第一行：搜索输入框
        searchBar.addView(searchInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiUtils.dp(this, 40)));
        // 第二行：上一个 / 下一个 / 关闭按钮
        LinearLayout searchBtnRow = new LinearLayout(this);
        searchBtnRow.setOrientation(LinearLayout.HORIZONTAL);
        searchBtnRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams btnRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnRowParams.topMargin = UiUtils.dp(this, 6);
        searchBtnRow.setLayoutParams(btnRowParams);
        OreButton btnPrev = new OreButton(this);
        btnPrev.setText("上一个");
        btnPrev.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btnPrev.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (searcher != null && searcher.hasQuery()) searcher.gotoPrevious();
            }
        });
        searchBtnRow.addView(btnPrev, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        OreButton btnNext = new OreButton(this);
        btnNext.setText("下一个");
        btnNext.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (searcher != null && searcher.hasQuery()) searcher.gotoNext();
            }
        });
        searchBtnRow.addView(btnNext, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        OreButton btnCloseSearch = new OreButton(this);
        btnCloseSearch.setText("关闭");
        btnCloseSearch.setStyleSheet(StyleSheet.STYLE_RED);
        btnCloseSearch.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleSearch(); }
        });
        searchBtnRow.addView(btnCloseSearch, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        searchBar.addView(searchBtnRow);
        searchBar.setVisibility(View.GONE);
        root.addView(searchBar);

        // ===== 代码编辑器 =====
        editor = new CodeEditor(this);
        editor.setColorScheme(new UserColorScheme(this));
        editor.setLineNumberEnabled(true);
        editor.setUndoEnabled(true);
        editor.setHighlightCurrentLine(true);
        editor.setHighlightCurrentBlock(true);
        editor.setTypefaceText(Typeface.MONOSPACE);
        editor.setTextSize(11);
        editor.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));
        root.addView(editor);
        searcher = editor.getSearcher();

        // ===== 左右滑动切换文件（不拦截编辑触摸）=====
        gestureDetector = new android.view.GestureDetector(this,
                new android.view.GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                        if (e1 == null || e2 == null) return false;
                        float dx = e2.getX() - e1.getX();
                        float dy = e2.getY() - e1.getY();
                        if (Math.abs(dx) > UiUtils.dp(SourceEditorActivity.this, 60)
                                && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                            if (dx < 0) switchFile(1);      // 左滑 → 下一个
                            else switchFile(-1);            // 右滑 → 上一个
                            return true;
                        }
                        return false;
                    }
                });
        editor.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                gestureDetector.onTouchEvent(event);
                return false; // 不消费事件，编辑器正常处理
            }
        });
    }

    // ==================== 文件列表 / 标签页 ====================
    private void loadFolderFiles() {
        fileList.clear();
        File[] files = currentDir.listFiles();
        if (files != null) {
            for (File f : files) {
                String name = f.getName();
                if (f.isDirectory() && (name.equals(".mcs") || name.endsWith("_back"))) continue;
                if (f.isFile()) fileList.add(f);
            }
        }
        java.util.Collections.sort(fileList);
        tabContainer.removeAllViews();
        for (int i = 0; i < fileList.size(); i++) {
            final File f = fileList.get(i);
            final int idx = i;
            OreButton tab = new OreButton(this);
            tab.setText(f.getName());
            tab.setTextSize(10);
            tab.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(UiUtils.dp(this, 2), 0, UiUtils.dp(this, 2), 0);
            tab.setLayoutParams(lp);
            tab.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { switchToIndex(idx); }
            });
            tabContainer.addView(tab);
        }
    }

    private void refreshTabHighlight() {
        for (int i = 0; i < tabContainer.getChildCount(); i++) {
            View child = tabContainer.getChildAt(i);
            if (child instanceof OreButton) {
                ((OreButton) child).setStyleSheet(
                        i == currentIndex ? StyleSheet.STYLE_GREEN : StyleSheet.STYLE_DARK_GRAY);
            }
        }
        // 滚动标签到可见
        if (currentIndex >= 0 && currentIndex < tabContainer.getChildCount()) {
            View target = tabContainer.getChildAt(currentIndex);
            if (target != null) {
                tabScroll.smoothScrollTo(target.getLeft() - UiUtils.dp(this, 8), 0);
            }
        }
    }

    // ==================== 打开 / 切换文件 ====================
    private void switchFile(int delta) {
        if (fileList.isEmpty()) return;
        int next = currentIndex + delta;
        if (next < 0 || next >= fileList.size()) return;
        switchToIndex(next);
    }

    private void switchToIndex(int index) {
        if (index < 0 || index >= fileList.size()) return;
        saveCurrent();
        openFile(fileList.get(index));
    }

    private void openFile(File file) {
        currentFile = file;
        currentIndex = fileList.indexOf(file);
        String content = FileUtils.readText(file);
        originalContent = content;
        editor.setText(content);
        editor.setSelection(0, 0);
        applyLanguage(file);
        refreshTabHighlight();
        setTitle(file.getName());
    }

    private void applyLanguage(File file) {
        String name = file.getName().toLowerCase();
        SettingsManager settings = SettingsManager.getInstance(this);
        boolean hlPy = settings.getBoolean(HighlightSettingsActivity.KEY_HL_PY, true);
        boolean hlJson = settings.getBoolean(HighlightSettingsActivity.KEY_HL_JSON, true);
        boolean hlLang = settings.getBoolean(HighlightSettingsActivity.KEY_HL_LANG, true);
        boolean hlJs = settings.getBoolean(HighlightSettingsActivity.KEY_HL_JS, true);
        boolean hlMc = settings.getBoolean(HighlightSettingsActivity.KEY_HL_MCFUNCTION, true);
        if (name.endsWith(".py") && hlPy) {
            editor.setEditorLanguage(new SimpleSyntaxLanguage(false));
        } else if (name.endsWith(".json") && hlJson) {
            editor.setEditorLanguage(new SimpleSyntaxLanguage(false));
        } else if (name.endsWith(".lang") && hlLang) {
            editor.setEditorLanguage(new SimpleSyntaxLanguage(false));
        } else if (name.endsWith(".js") && hlJs) {
            editor.setEditorLanguage(new SimpleSyntaxLanguage(false));
        } else if (name.endsWith(".mcfunction") && hlMc) {
            editor.setEditorLanguage(new SimpleSyntaxLanguage(false));
        } else {
            editor.setEditorLanguage(new EmptyLanguage());
        }
    }

    private void saveCurrent() {
        if (currentFile == null || editor == null) return;
        try {
            FileUtils.writeText(currentFile, editor.getText().toString());
        } catch (Exception e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== 搜索 ====================
    private void toggleSearch() {
        searchVisible = !searchVisible;
        searchBar.setVisibility(searchVisible ? View.VISIBLE : View.GONE);
        if (searchVisible) {
            searchInput.requestFocus();
            // 注意：不调用 editor.beginSearchMode()，
            // 否则会同时弹出 sora-editor 原生搜索框（双输入框问题）。
        } else {
            if (searcher != null) searcher.stopSearch();
        }
    }

    // ==================== 文件树（全部按钮）====================
    private void showFileTreeDialog() {
        final OreDialogBuilder builder = new OreDialogBuilder(this);
        final AlertDialog[] dialogHolder = new AlertDialog[1];
        builder.setTitle("选择文件");
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(UiUtils.dp(this, 8), UiUtils.dp(this, 8),
                UiUtils.dp(this, 8), UiUtils.dp(this, 8));
        for (final File f : fileList) {
            OreButton btn = new OreButton(this);
            btn.setText(f.getName());
            btn.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    switchToIndex(fileList.indexOf(f));
                    if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                }
            });
            list.addView(btn);
            UiUtils.addGap(this, list, 4);
        }
        scroll.addView(list);
        content.addView(scroll);
        builder.setView(content);
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
        });
        dialogHolder[0] = builder.show();
    }

    @Override
    protected void onPause() {
        saveCurrent();
        super.onPause();
    }
    @Override
    protected void onDestroy() {
        saveCurrent();
        super.onDestroy();
    }
}