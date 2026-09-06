package com.xtt.mcmodmaker;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

import com.xtt.mcmodmaker.net.McDevApi;
import com.xtt.mcmodmaker.core.Constants;
import com.xtt.mcmodmaker.core.ProjectManager;
import com.xtt.mcmodmaker.core.UpdateChecker;
import com.xtt.mcmodmaker.util.DateUtils;
import com.xtt.mcmodmaker.util.FileUtils;
import com.xtt.mcmodmaker.util.SettingsManager;
import com.xtt.mcmodmaker.util.UiUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev1503.oreui.StyleSheet;
import dev1503.oreui.dialog.OreDialogBuilder;
import dev1503.oreui.widgets.OreAccordion;
import dev1503.oreui.widgets.OreAlert;
import dev1503.oreui.widgets.OreButton;
import dev1503.oreui.widgets.OreCard;
import dev1503.oreui.widgets.OreEditText;
import dev1503.oreui.widgets.OreSwitch;
import dev1503.oreui.widgets.OreTabs;
import dev1503.oreui.widgets.OreTextView;

/**
 * 主界面：三选项卡（开发/关于/管理）。
 * 重构版：项目业务委托 {@link ProjectManager}，通用逻辑复用 util 包，消除重复代码。
 */
public class MainActivity extends Activity {

    private static final int REQUEST_MANAGE_STORAGE = 1001;
    private static final int REQUEST_IMPORT_FOLDER = 1002;
    private static final int REQUEST_IMPORT_ZIP = 1003;
    private static final int REQUEST_EXPORT_PROJECT = 1004;
    private static final int REQUEST_STORAGE_PERMISSION = 1006;

    private LinearLayout projectListContainer;
    private LinearLayout aboutContainer;
    private LinearLayout buttonRow;
    private FrameLayout contentFrameLayout;
    private ScrollView scrollView;
    private LinearLayout managerContainer;
    private ProgressBar importProgressBar;
    private OreEditText searchBox;
    private ProgressBar downloadProgressBar;
    private File downloadTargetDir;
    private OreAlert storagePermissionAlert;
    private OreCard storagePermissionCard;

    private SettingsManager settings;
    private ProjectManager projectManager;
    private String searchQuery = "";
    private File pendingExportDir;

    // ==================== 生命周期 ====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = SettingsManager.getInstance(this);
        projectManager = new ProjectManager(this);
        projectManager.setImportUi(new ImportUiImpl());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.parseColor("#1A1A1A"));
            getWindow().setNavigationBarColor(Color.parseColor("#1A1A1A"));
        }

        buildUi();
        setupManagerPage();
        showTab(0);
        showStoragePermissionUiIfNeeded();
    }

    private void buildUi() {
        LinearLayout mainContainer = new LinearLayout(this);
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        mainContainer.setBackgroundColor(Color.parseColor("#1A1A1A"));

        // ===== 标题 =====
        OreTextView tvTitle = new OreTextView(this);
        tvTitle.setText("模组制作器");
        tvTitle.setTextSize(24);
        tvTitle.setTextColor(Color.WHITE);
        mainContainer.addView(tvTitle);
        UiUtils.addGap(this, mainContainer, 16);

        // ===== 选项卡 =====
        final OreTabs tabs = new OreTabs(this);
        OreButton tabDev = tabButton("开发", StyleSheet.STYLE_DARK_GRAY);
        OreButton tabAbout = tabButton("设置", StyleSheet.STYLE_DARK_GRAY);
        OreButton tabManager = tabButton("管理", StyleSheet.STYLE_DARK_GRAY);
        tabs.addButton(tabDev);
        tabs.addButton(tabAbout);
        tabs.addButton(tabManager);
        tabs.setActiveIndex(0);
        mainContainer.addView(tabs);
        UiUtils.addGap(this, mainContainer, 12);

        tabDev.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { tabs.setActiveIndex(0); showTab(0); }
        });
        tabAbout.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { tabs.setActiveIndex(1); showTab(1); }
        });
        tabManager.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { tabs.setActiveIndex(2); showTab(2); }
        });

        // ===== 内容切换区 =====
        contentFrameLayout = new FrameLayout(this);
        contentFrameLayout.setBackgroundColor(Color.parseColor("#1A1A1A"));
        contentFrameLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        mainContainer.addView(contentFrameLayout);

        // ---- 开发/关于页：可滚动 ----
        scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#1A1A1A"));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 40, 20, 40);
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        root.requestFocus();
        scrollView.addView(root);
        contentFrameLayout.addView(scrollView);

        // ---- 管理页容器（初始隐藏）----
        managerContainer = new LinearLayout(this);
        managerContainer.setOrientation(LinearLayout.VERTICAL);
        managerContainer.setVisibility(View.GONE);
        contentFrameLayout.addView(managerContainer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // ===== 进度条 =====
        importProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        importProgressBar.setMax(100);
        importProgressBar.setProgress(0);
        importProgressBar.setVisibility(View.GONE);
        importProgressBar.setProgressTintList(ColorStateList.valueOf(Color.WHITE));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (6 * getResources().getDisplayMetrics().density));
        barParams.bottomMargin = (int) (16 * getResources().getDisplayMetrics().density);
        importProgressBar.setLayoutParams(barParams);
        root.addView(importProgressBar);

        // ===== 搜索框 =====
        searchBox = new OreEditText(this);
        searchBox.setHint("搜索模组名称,命名空间或ID...");
        searchBox.setTextSize(12);
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                searchQuery = s.toString().trim();
                loadProjects();
            }
        });
        searchBox.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                v.clearFocus();
                return true;
            }
        });
        root.addView(searchBox);
        UiUtils.addGap(this, root, 12);

        // 未授予存储权限时显示：红色说明 + 可点击的授权卡片。
        storagePermissionAlert = new OreAlert(this);
        storagePermissionAlert.setText("未授予管理所有文件权限，无法读取和管理本地模组。");
        storagePermissionAlert.setStyleSheet(StyleSheet.STYLE_RED);
        storagePermissionAlert.setVisibility(View.GONE);
        root.addView(storagePermissionAlert);
        UiUtils.addGap(this, root, 8);

        storagePermissionCard = new OreCard(this);
        storagePermissionCard.setPadding(16, 16, 16, 16);
        storagePermissionCard.setVisibility(View.GONE);
        storagePermissionCard.setClickable(true);
        storagePermissionCard.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showPermissionRequestDialog();
            }
        });
        LinearLayout permissionCardContent = new LinearLayout(this);
        permissionCardContent.setOrientation(LinearLayout.VERTICAL);
        OreTextView permissionTitle = new OreTextView(this);
        permissionTitle.setText("授予文件管理权限");
        permissionTitle.setTextColor(Color.WHITE);
        permissionTitle.setTextSize(16);
        permissionCardContent.addView(permissionTitle);
        OreTextView permissionDescription = new OreTextView(this);
        permissionDescription.setText("点击此卡片前往授权，以使用新建、导入和管理模组功能");
        permissionDescription.setTextColor(Color.parseColor("#AAAAAA"));
        permissionDescription.setTextSize(12);
        permissionDescription.setPadding(0, UiUtils.dp(this, 6), 0, 0);
        permissionCardContent.addView(permissionDescription);
        storagePermissionCard.addView(permissionCardContent);
        root.addView(storagePermissionCard);
        UiUtils.addGap(this, root, 12);

        // ===== 项目列表 / 关于页 =====
        projectListContainer = new LinearLayout(this);
        projectListContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(projectListContainer);

        aboutContainer = new LinearLayout(this);
        aboutContainer.setOrientation(LinearLayout.VERTICAL);
        aboutContainer.setVisibility(View.GONE);
        root.addView(aboutContainer);
        showSettingsPage();

        // ===== 底部按钮（新建/导入）=====
        buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER);
        OreButton btnCreate = new OreButton(this);
        btnCreate.setText("+ 新建模组");
        btnCreate.setStyleSheet(StyleSheet.STYLE_GREEN);
        btnCreate.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!checkStoragePermission()) { showPermissionRequestDialog(); return; }
                showCreateDialog();
            }
        });
        buttonRow.addView(btnCreate);

        OreButton btnImport = new OreButton(this);
        btnImport.setText("导入模组");
        btnImport.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btnImport.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!checkStoragePermission()) { showPermissionRequestDialog(); return; }
                showImportDialog();
            }
        });
        buttonRow.addView(btnImport);
        root.addView(buttonRow);

        setContentView(mainContainer);
    }

    private OreButton tabButton(String text, StyleSheet style) {
        OreButton btn = new OreButton(this);
        btn.setText(text);
        btn.setStyleSheet(style);
        return btn;
    }

    // ==================== 权限 ====================

    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= 30) {
            return Environment.isExternalStorageManager();
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
            } catch (Exception e) {
                try {
                    startActivityForResult(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                            REQUEST_MANAGE_STORAGE);
                } catch (Exception e2) {
                    UiUtils.toast(this, "无法打开存储权限设置");
                }
            }
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
        }
    }

    /**
     * 首次启动未授权时自动提示；以后未授权则只显示页面内的提示和授权卡片。
     * 首次提示状态存于应用私有 SharedPreferences，避免依赖尚不可访问的外部存储。
     */
    private void showStoragePermissionUiIfNeeded() {
        boolean granted = checkStoragePermission();
        if (storagePermissionAlert != null) {
            storagePermissionAlert.setVisibility(granted ? View.GONE : View.VISIBLE);
        }
        if (storagePermissionCard != null) {
            storagePermissionCard.setVisibility(granted ? View.GONE : View.VISIBLE);
        }
        if (buttonRow != null) {
            buttonRow.setVisibility(granted ? View.VISIBLE : View.GONE);
        }
        if (granted) return;

        android.content.SharedPreferences prefs =
                getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(Constants.PREFS_FIRST_RUN, false)) {
            prefs.edit().putBoolean(Constants.PREFS_FIRST_RUN, true).apply();
            showPermissionRequestDialog();
        }
    }

    private void showPermissionRequestDialog() {
        OreTextView msg = new OreTextView(this);
        msg.setText("需要存储权限才能管理模组文件");
        msg.setTextColor(Color.WHITE);
        msg.setTextSize(14);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        int pad = UiUtils.dp(this, 16);
        layout.setPadding(pad, pad, pad, pad);
        layout.addView(msg);

        OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("需要权限");
        builder.setView(layout);
        builder.setPositiveButton("去授权", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                requestStoragePermission();
                dialog.dismiss();
            }
        });
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
        });
        builder.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadProjects();
        }
    }

    // ==================== 关于页 / 更新检查 ====================

    // ==================== 设置页 ====================
    private void showSettingsPage() {
        aboutContainer.removeAllViews();
        OreTextView title = new OreTextView(this);
        title.setText("设置");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setGravity(Gravity.CENTER);
        aboutContainer.addView(title);
        UiUtils.addGap(this, aboutContainer, 16);
        // 关于分栏
        aboutContainer.addView(settingsCard("关于", "版本信息、开源地址、开源鸣谢", AboutActivity.class));
        // 代码高亮分栏
        aboutContainer.addView(settingsCard("代码高亮", "文件类型高亮开关、语法颜色配置", HighlightSettingsActivity.class));
    }
    private OreCard settingsCard(String title, String desc, final Class<?> target) {
        OreCard card = new OreCard(this);
        card.setPadding(16, 20, 16, 20);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        OreTextView tvTitle = new OreTextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(14);
        layout.addView(tvTitle, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        OreTextView arrow = new OreTextView(this);
        arrow.setText(">");
        arrow.setTextColor(Color.parseColor("#888888"));
        arrow.setTextSize(16);
        layout.addView(arrow);
        card.addView(layout);
        OreTextView tvDesc = new OreTextView(this);
        tvDesc.setText(desc);
        tvDesc.setTextColor(Color.GRAY);
        tvDesc.setTextSize(11);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descParams.topMargin = UiUtils.dp(this, 4);
        tvDesc.setLayoutParams(descParams);
        card.addView(tvDesc);
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, target));
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = UiUtils.dp(this, 10);
        card.setLayoutParams(params);
        return card;
    }
    private OreCard linkCard(String title, String linkText, final String url) {
        OreCard card = new OreCard(this);
        card.setPadding(16, 12, 16, 12);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        OreTextView t = new OreTextView(this);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(14);
        layout.addView(t);
        OreTextView l = new OreTextView(this);
        l.setText(linkText);
        l.setTextColor(Color.GRAY);
        l.setTextSize(11);
        layout.addView(l);
        card.addView(layout);
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openBrowser(url); }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = UiUtils.dp(this, 16);
        card.setLayoutParams(params);
        return card;
    }

    private void openBrowser(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            UiUtils.toast(this, "无法打开浏览器");
        }
    }

    private String getCurrentVersion() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName;
        } catch (Exception e) {
            return "";
        }
    }

    private void checkForUpdate() {
        UiUtils.toast(this, "正在检查更新...");
        String currentVersion = getCurrentVersion();
        UpdateChecker.checkLatest(currentVersion, new UpdateChecker.Callback() {
            @Override
            public void onResult(final UpdateChecker.ReleaseInfo release) {
                UiUtils.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (release == null) {
                            UiUtils.toast(MainActivity.this, "已经是最新版本");
                        } else {
                            showUpdateDialog(release.version, release.body, release.htmlUrl, false);
                        }
                    }
                });
            }

            @Override
            public void onError(final String message) {
                UiUtils.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        UiUtils.toast(MainActivity.this, "检查更新失败: " + message);
                    }
                });
            }
        });
    }

    private void showUpdateDialog(String newVersion, String body, final String releaseUrl, boolean autoCheck) {
        if (body != null && body.length() > 300) {
            body = body.substring(0, 300) + "...";
        }
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = UiUtils.dp(this, 16);
        layout.setPadding(pad, pad, pad, pad);

        OreTextView versionLabel = new OreTextView(this);
        versionLabel.setText("发现新版本：" + newVersion);
        versionLabel.setTextColor(Color.WHITE);
        versionLabel.setTextSize(16);
        layout.addView(versionLabel);
        UiUtils.addGap(this, layout, 10);

        OreTextView detailText = new OreTextView(this);
        detailText.setText(body == null ? "" : body);
        detailText.setTextColor(Color.GRAY);
        detailText.setTextSize(12);
        layout.addView(detailText);

        OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("更新");
        builder.setView(layout);
        builder.setNeutralButton("网盘下载", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                openBrowser(Constants.UPDATE_PAN_URL);
                dialog.dismiss();
            }
        });
        builder.getNeutralButton().setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        builder.setPositiveButton("GitHub下载", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                openBrowser(releaseUrl);
                dialog.dismiss();
            }
        });
        builder.getPositiveButton().setStyleSheet(StyleSheet.STYLE_GREEN);
        builder.setNegativeButton("稍后", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
        });

        if (autoCheck) {
            settings.putBoolean(Constants.PREFS_UPDATE_DIALOG_SHOWN_TODAY + "_" + DateUtils.today(), true);
        }
        builder.show();
    }

    private void autoCheckForUpdate() {
        final String today = DateUtils.today();
        if (today.equals(settings.getString(Constants.PREFS_LAST_CHECK_DATE, ""))) return;
        settings.putString(Constants.PREFS_LAST_CHECK_DATE, today);

        final String currentVersion = getCurrentVersion();
        if (currentVersion.isEmpty()) return;
        UpdateChecker.checkLatest(currentVersion, new UpdateChecker.Callback() {
            @Override
            public void onResult(final UpdateChecker.ReleaseInfo release) {
                if (release == null) return;
                boolean alreadyShown = settings.getBoolean(Constants.PREFS_UPDATE_DIALOG_SHOWN_TODAY + "_" + today, false);
                if (alreadyShown) return;
                UiUtils.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        showUpdateDialog(release.version, release.body, release.htmlUrl, true);
                    }
                });
            }

            @Override
            public void onError(String message) {
                // 自动检查失败静默处理
            }
        });
    }

    // ==================== 创建模组 ====================

    private boolean isValidNamespace(String ns) {
        return ns.isEmpty() || ns.matches("[a-z_]+");
    }

    private void updateNsValidation(String ns, OreButton positiveBtn, OreAlert alert) {
        if (ns.isEmpty()) {
            alert.setVisibility(View.GONE);
            positiveBtn.setEnabled(true);
            return;
        }
        if (isValidNamespace(ns)) {
            alert.setVisibility(View.GONE);
            positiveBtn.setEnabled(true);
        } else {
            alert.setVisibility(View.VISIBLE);
            positiveBtn.setEnabled(false);
        }
    }

    private void showCreateDialog() {
        final OreEditText inputName = new OreEditText(this);
        inputName.setHint("模组名称");
        final OreEditText inputNamespace = new OreEditText(this);
        inputNamespace.setHint("命名空间（小写字母_）");

        LinearLayout scriptRow = new LinearLayout(this);
        scriptRow.setOrientation(LinearLayout.HORIZONTAL);
        scriptRow.setGravity(Gravity.CENTER_VERTICAL);
        final OreSwitch switchScript = new OreSwitch(this);
        switchScript.setChecked(true);
        scriptRow.addView(switchScript);
        OreTextView scriptLabel = new OreTextView(this);
        scriptLabel.setText("包含脚本");
        scriptLabel.setTextColor(Color.WHITE);
        scriptLabel.setTextSize(12);
        scriptLabel.setPadding(8, 0, 0, 0);
        scriptRow.addView(scriptLabel);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int dialogPadding = UiUtils.dp(this, 16);
        layout.setPadding(dialogPadding, dialogPadding, dialogPadding, dialogPadding);
        layout.addView(inputName);
        layout.addView(inputNamespace);
        layout.addView(scriptRow);

        final OreAlert nsAlert = new OreAlert(this);
        nsAlert.setText("命名空间只能包含小写字母和下划线");
        nsAlert.setStyleSheet(StyleSheet.STYLE_ALERT_YELLOW);
        nsAlert.setVisibility(View.GONE);
        layout.addView(nsAlert);

        OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("新建模组");
        builder.setView(layout);
        builder.setPositiveButton("创建", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                String name = inputName.getText().toString().trim();
                String namespace = inputNamespace.getText().toString().trim();
                if (name.isEmpty()) { UiUtils.toast(MainActivity.this, "名称不能为空"); return; }
                if (namespace.isEmpty()) namespace = "test";
                if (!isValidNamespace(namespace)) { UiUtils.toast(MainActivity.this, "命名空间格式错误"); return; }
                projectManager.createProject(name, namespace, switchScript.isChecked());
                UiUtils.toast(MainActivity.this, "创建成功");
                dialog.dismiss();
                loadProjects();
            }
        });
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
        });
        builder.getNegativeButton().setStyleSheet(StyleSheet.STYLE_RED);

        final OreButton positiveBtn = builder.getPositiveButton();
        updateNsValidation(inputNamespace.getText().toString(), positiveBtn, nsAlert);
        inputNamespace.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                updateNsValidation(s.toString(), positiveBtn, nsAlert);
            }
        });
        builder.show();
    }

        private boolean legacyMigrated = false;

    /** 将旧版 McMod/ 根目录下的项目文件夹迁移到 McMod/mods/（首次加载时执行一次）。 */
    private void migrateLegacyProjects() {
        if (legacyMigrated) return;
        legacyMigrated = true;
        try {
            File root = new File(Constants.MOD_FOLDER_PATH);
            File mods = new File(Constants.PROJECTS_DIR);
            if (!root.exists() || !root.isDirectory()) return;
            if (!mods.exists()) mods.mkdirs();
            File[] entries = root.listFiles();
            if (entries == null) return;
            for (File e : entries) {
                String n = e.getName();
                // 保留系统目录：log 是崩溃日志目录，绝不能作为项目迁移到 mods/log。
                // 其余根目录零散文件（如 setting.json）本身不满足目录条件，不会迁移。
                if (n.equals("mods") || n.equals("Download") || n.equals("log") || n.startsWith("temp_")) continue;
                if (!e.isDirectory()) continue;
                File dest = new File(mods, n);
                if (dest.exists()) continue;
                try {
                    if (!e.renameTo(dest)) {
                        // renameTo 失败（SELinux 等）则复制后删除
                        FileUtils.copyDirectory(e, dest);
                        FileUtils.deleteRecursive(e);
                    }
                    LogUtil.log("已迁移项目目录: " + n + " -> mods/" + n);
                } catch (Exception ex) {
                    LogUtil.logException(ex);
                }
            }
        } catch (Exception e) {
            LogUtil.logException(e);
        }
    }

    // ==================== 项目列表 ====================
    private void loadProjects() {
        try {
            if (!checkStoragePermission()) return;
            migrateLegacyProjects();
            projectListContainer.removeAllViews();

            File modDir = new File(Constants.PROJECTS_DIR);
            if (!modDir.exists()) modDir.mkdirs();

            File[] allEntries = modDir.listFiles(new java.io.FileFilter() {
                @Override public boolean accept(File file) {
                    return file.isDirectory() && !file.getName().startsWith("temp_");
                }
            });
            if (allEntries == null || allEntries.length == 0) {
                projectListContainer.addView(emptyText("暂无模组项目，点击下方按钮创建"));
                return;
            }

            Map<String, File> originalMap = new LinkedHashMap<>();
            Map<String, List<File>> backupSubMap = new LinkedHashMap<>();
            List<File> backupContainers = new ArrayList<>();
            List<File> normalProjects = new ArrayList<>();

            for (File entry : allEntries) {
                if (entry.getName().endsWith("_back")) backupContainers.add(entry);
                else normalProjects.add(entry);
            }
            for (File project : normalProjects) originalMap.put(project.getName(), project);
            for (File container : backupContainers) {
                String originalId = container.getName().substring(0, container.getName().length() - 5);
                File[] subDirs = container.listFiles(new java.io.FileFilter() {
                    @Override public boolean accept(File file) { return file.isDirectory(); }
                });
                List<File> subList = new ArrayList<>();
                if (subDirs != null) for (File sub : subDirs) subList.add(sub);
                backupSubMap.put(originalId, subList);
            }

            boolean hasResults = false;
            Set<String> allIds = new LinkedHashSet<>();
            allIds.addAll(originalMap.keySet());
            allIds.addAll(backupSubMap.keySet());

            for (final String originalId : allIds) {
                File originalProject = originalMap.get(originalId);
                List<File> backups = backupSubMap.get(originalId);
                if (backups == null) backups = new ArrayList<>();
                File nameSource = originalProject != null ? originalProject
                        : (backups.size() > 0 ? backups.get(0) : null);
                String displayName = nameSource == null ? originalId : projectManager.getProjectName(nameSource);
                String namespace = "";
                if (nameSource != null) {
                    namespace = projectManager.getProjectNamespace(nameSource);
                }

                if (!searchQuery.isEmpty()) {
                    boolean match = displayName.toLowerCase().contains(searchQuery.toLowerCase())
                            || originalId.toLowerCase().contains(searchQuery.toLowerCase())
                            || namespace.toLowerCase().contains(searchQuery.toLowerCase());
                    if (!match) {
                        for (File backup : backups) {
                            if (backup.getName().toLowerCase().contains(searchQuery.toLowerCase())) {
                                match = true;
                                break;
                            }
                        }
                    }
                    if (!match) continue;
                }
                hasResults = true;

                if (originalProject != null) projectListContainer.addView(createProjectCard(originalProject));
                if (backups.size() > 0) {
                    int maxDisplay = Math.min(backups.size(), 5);
                    OreAccordion accordion = new OreAccordion(this);
                    accordion.setTitle("备份版本");
                    accordion.setSubtitle("共 " + backups.size() + " 个备份");
                    LinearLayout backupContent = new LinearLayout(this);
                    backupContent.setOrientation(LinearLayout.VERTICAL);
                    backupContent.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                    backupContent.setPadding(0, 8, 0, 8);
                    for (int i = 0; i < maxDisplay; i++) {
                        backupContent.addView(createProjectCard(backups.get(i)));
                        if (i < maxDisplay - 1) UiUtils.addGap(this, backupContent, 8);
                    }
                    if (backups.size() > 5) {
                        UiUtils.addGap(this, backupContent, 8);
                        OreButton viewAllBtn = new OreButton(this);
                        viewAllBtn.setText("查看全部备份 (" + backups.size() + "个)");
                        viewAllBtn.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
                        viewAllBtn.setOnClickListener(new View.OnClickListener() {
                            @Override public void onClick(View v) {
                                Intent i = new Intent(MainActivity.this, BackupsActivity.class);
                                i.putExtra("original_id", originalId);
                                startActivity(i);
                            }
                        });
                        backupContent.addView(viewAllBtn);
                    }
                    accordion.setContentView(backupContent);
                    projectListContainer.addView(accordion);
                }
                UiUtils.addGap(this, projectListContainer, 12);
            }
            if (!hasResults) projectListContainer.addView(emptyText("没有找到匹配的项目"));
        } catch (Exception e) {
            LogUtil.logException(e);
        }
    }

    private OreTextView emptyText(String msg) {
        OreTextView tv = new OreTextView(this);
        tv.setText(msg);
        tv.setTextColor(Color.WHITE);
        return tv;
    }

    private OreCard createProjectCard(final File projectDir) {
        OreCard card = new OreCard(this);
        card.setPadding(16, 12, 16, 12);

        LinearLayout cardLayout = new LinearLayout(this);
        cardLayout.setOrientation(LinearLayout.HORIZONTAL);
        cardLayout.setGravity(Gravity.CENTER_VERTICAL);

        final boolean isCopy = projectDir.getParentFile() != null
                && projectDir.getParentFile().getName().endsWith("_back");

        ImageView icon = new ImageView(this);
        icon.setImageResource(isCopy ? android.R.drawable.ic_menu_save : android.R.drawable.ic_menu_gallery);
        icon.setColorFilter(Color.parseColor("#AAAAAA"));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(80, 80);
        iconParams.setMargins(0, 0, 16, 0);
        icon.setLayoutParams(iconParams);
        cardLayout.addView(icon);

        LinearLayout textLayout = new LinearLayout(this);
        textLayout.setOrientation(LinearLayout.VERTICAL);

        String projectName = projectManager.getProjectName(projectDir);
        if (isCopy) projectName += " (副本)";

        OreTextView nameText = new OreTextView(this);
        nameText.setText(projectName);
        nameText.setTextSize(16);
        nameText.setTextColor(Color.WHITE);
        textLayout.addView(nameText);

        OreTextView timeText = new OreTextView(this);
        if (isCopy) {
            timeText.setText("备份保存于: " + formatBackupTime(projectDir.getName()));
        } else {
            timeText.setText("修改时间: " + DateUtils.formatMillis(projectDir.lastModified()));
        }
        timeText.setTextSize(12);
        timeText.setTextColor(Color.GRAY);
        textLayout.addView(timeText);

        cardLayout.addView(textLayout);
        card.addView(cardLayout);

        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (isCopy) showCopyOptions(projectDir);
                else showProjectOptions(projectDir);
            }
        });
        return card;
    }

    private String formatBackupTime(String folderName) {
        try {
            long timestamp = Long.parseLong(folderName) * 1000L;
            return DateUtils.formatMillis(timestamp);
        } catch (Exception e) {
            return folderName;
        }
    }

    // ==================== 项目操作 ====================

    private LinearLayout centeredMessage(String text) {
        OreTextView msg = new OreTextView(this);
        msg.setText(text);
        msg.setTextColor(Color.WHITE);
        msg.setTextSize(14);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        int pad = UiUtils.dp(this, 16);
        layout.setPadding(pad, pad, pad, pad);
        layout.addView(msg, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return layout;
    }

    private void showProjectOptions(final File projectDir) {
        OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("管理项目");
        builder.setView(centeredMessage("请选择操作"));
        builder.addButton("进入", StyleSheet.STYLE_GREEN, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(MainActivity.this, EditorActivity.class);
                intent.putExtra("project_path", projectDir.getAbsolutePath());
                startActivity(intent);
                dialog.dismiss();
            }
        });
        builder.addButton("设置", StyleSheet.STYLE_WHITE, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                showProjectSettings(projectDir);
                dialog.dismiss();
            }
        });
        builder.addButton("导出", StyleSheet.STYLE_PURPLE, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                if (!checkStoragePermission()) {
                    UiUtils.toast(MainActivity.this, "需要存储权限才能导出");
                    dialog.dismiss();
                    return;
                }
                startExportProject(projectDir);
                dialog.dismiss();
            }
        });
        builder.addButton("取消", null, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
        });
        builder.show();
    }

    private void showProjectSettings(final File projectDir) {
        final String currentNamespace = projectManager.getProjectNamespace(projectDir);

        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setGravity(Gravity.CENTER);
        int padding = UiUtils.dp(this, 16);
        contentLayout.setPadding(padding, padding, padding, padding);

        contentLayout.addView(infoText("项目名: " + projectManager.getProjectName(projectDir), 14, Color.WHITE));
        UiUtils.addGap(this, contentLayout, 8);
        contentLayout.addView(infoText("项目ID: " + projectManager.getProjectId(projectDir), 12, Color.GRAY));
        UiUtils.addGap(this, contentLayout, 4);
        contentLayout.addView(infoText("命名空间: " + currentNamespace, 12, Color.GRAY));
        UiUtils.addGap(this, contentLayout, 8);

        contentLayout.addView(settingButton("修改命名空间", StyleSheet.STYLE_DARK_GRAY, new View.OnClickListener() {
            @Override public void onClick(View v) { showNamespaceDialog(projectDir, currentNamespace); }
        }));
        UiUtils.addGap(this, contentLayout, 8);
        contentLayout.addView(settingButton("重命名", StyleSheet.STYLE_WHITE, new View.OnClickListener() {
            @Override public void onClick(View v) { showRenameDialog(projectDir); }
        }));
        UiUtils.addGap(this, contentLayout, 8);
        contentLayout.addView(settingButton("添加备份", StyleSheet.STYLE_DARK_GRAY, new View.OnClickListener() {
            @Override public void onClick(View v) {
                projectManager.createBackup(projectDir);
                loadProjects();
            }
        }));
        UiUtils.addGap(this, contentLayout, 8);
        contentLayout.addView(settingButton("打开文件夹", StyleSheet.STYLE_GREEN, new View.OnClickListener() {
            @Override public void onClick(View v) { openProjectFolder(projectDir); }
        }));
        UiUtils.addGap(this, contentLayout, 8);
        contentLayout.addView(settingButton("删除", StyleSheet.STYLE_RED, new View.OnClickListener() {
            @Override public void onClick(View v) { showDeleteConfirm(projectDir); }
        }));

        OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("项目设置");
        builder.setView(contentLayout);
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
        });
        builder.show();
    }

    private OreTextView infoText(String text, int size, int color) {
        OreTextView tv = new OreTextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(size);
        return tv;
    }

    private OreButton settingButton(String text, StyleSheet style, View.OnClickListener listener) {
        OreButton btn = new OreButton(this);
        btn.setText(text);
        btn.setStyleSheet(style);
        btn.setOnClickListener(listener);
        return btn;
    }

    private void showNamespaceDialog(final File projectDir, String currentNs) {
        final OreEditText input = new OreEditText(this);
        input.setHint("命名空间（小写字母_）");
        input.setText(currentNs);

        final OreAlert alert = new OreAlert(this);
        alert.setText("命名空间只能包含小写字母和下划线");
        alert.setStyleSheet(StyleSheet.STYLE_ALERT_YELLOW);
        alert.setVisibility(View.GONE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int dialogPadding = UiUtils.dp(this, 16);
        layout.setPadding(dialogPadding, dialogPadding, dialogPadding, dialogPadding);
        layout.addView(input);
        layout.addView(alert);

        OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("修改命名空间");
        builder.setView(layout);
        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                String ns = input.getText().toString().trim();
                if (ns.isEmpty()) ns = "test";
                if (!isValidNamespace(ns)) { UiUtils.toast(MainActivity.this, "命名空间格式错误"); return; }
                projectManager.updateNamespace(projectDir, ns);
                dialog.dismiss();
                loadProjects();
            }
        });
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
        });
        builder.getNegativeButton().setStyleSheet(StyleSheet.STYLE_RED);

        final OreButton positiveBtn = builder.getPositiveButton();
        updateNsValidation(input.getText().toString(), positiveBtn, alert);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { updateNsValidation(s.toString(), positiveBtn, alert); }
        });
        builder.show();
    }

    private void showRenameDialog(final File projectDir) {
        final OreEditText input = new OreEditText(this);
        input.setHint("输入新名称");
        input.setText(projectManager.getProjectName(projectDir));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int dialogPadding = UiUtils.dp(this, 16);
        content.setPadding(dialogPadding, dialogPadding, dialogPadding, dialogPadding);
        content.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("重命名模组");
        builder.setView(content);
        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                String newName = input.getText().toString().trim();
                if (newName.isEmpty()) { UiUtils.toast(MainActivity.this, "名称不能为空"); return; }
                projectManager.renameProject(projectDir, newName);
                dialog.dismiss();
                loadProjects();
            }
        });
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
        });
        builder.getNegativeButton().setStyleSheet(StyleSheet.STYLE_RED);
        builder.show();
    }

    private void showCopyOptions(final File projectDir) {
        OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("副本操作");
        builder.setView(centeredMessage("请选择操作"));
        builder.setPositiveButton("覆盖", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                projectManager.overwriteOriginalFromBackup(projectDir);
                dialog.dismiss();
                loadProjects();
            }
        });
        builder.getPositiveButton().setStyleSheet(StyleSheet.STYLE_RED);
        builder.setNegativeButton("设置", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { showCopySettings(projectDir); }
        });
        builder.getNegativeButton().setStyleSheet(StyleSheet.STYLE_WHITE);
        builder.setNeutralButton("取消", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
        });
        builder.show();
    }

    private void showCopySettings(final File projectDir) {
        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setGravity(Gravity.CENTER);
        int padding = UiUtils.dp(this, 16);
        contentLayout.setPadding(padding, padding, padding, padding);

        contentLayout.addView(infoText("副本名称: " + projectManager.getProjectName(projectDir), 14, Color.WHITE));
        UiUtils.addGap(this, contentLayout, 8);
        contentLayout.addView(infoText("备份时间: " + formatBackupTime(projectDir.getName()), 12, Color.GRAY));

        OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("副本设置");
        builder.setView(contentLayout);
        builder.setPositiveButton("打开文件夹", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                openProjectFolder(projectDir);
                dialog.dismiss();
            }
        });
        builder.getPositiveButton().setStyleSheet(StyleSheet.STYLE_GREEN);
        builder.setNegativeButton("删除", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { showDeleteConfirm(projectDir); }
        });
        builder.getNegativeButton().setStyleSheet(StyleSheet.STYLE_RED);
        builder.setNeutralButton("取消", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
        });
        builder.show();
    }

    private void showDeleteConfirm(final File projectDir) {
        OreTextView msgText = new OreTextView(this);
        msgText.setText("你确定要删除这个模组吗？\n它会永久消失(真的很久)\n此操作不可恢复！");
        msgText.setTextColor(Color.WHITE);
        msgText.setTextSize(14);
        LinearLayout msgLayout = new LinearLayout(this);
        msgLayout.setOrientation(LinearLayout.VERTICAL);
        msgLayout.setGravity(Gravity.CENTER_VERTICAL);
        int padding = UiUtils.dp(this, 16);
        msgLayout.setPadding(padding, padding, padding, padding);
        msgLayout.addView(msgText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("确认删除");
        builder.setView(msgLayout);
        builder.setPositiveButton("删除", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                if (FileUtils.deleteRecursive(projectDir)) UiUtils.toast(MainActivity.this, "项目已删除");
                else UiUtils.toast(MainActivity.this, "删除失败");
                dialog.dismiss();
                recreate();
            }
        });
        builder.getPositiveButton().setStyleSheet(StyleSheet.STYLE_RED);
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
        });
        builder.show();
    }

    private void openProjectFolder(File projectDir) {
        try {
            Uri uri = getUriForFile(projectDir);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/x-directory");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            try {
                Uri uri = getUriForFile(projectDir);
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(uri, "resource/folder");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            } catch (Exception e2) {
                UiUtils.toast(this, "没有可用的文件管理器");
            }
        }
    }

    private Uri getUriForFile(File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return androidx.core.content.FileProvider.getUriForFile(
                    this, Constants.FILE_PROVIDER_AUTHORITY, file);
        }
        return Uri.fromFile(file);
    }

    // ==================== 导出 ====================

    private void startExportProject(File projectDir) {
        pendingExportDir = projectDir;
        String projectName = projectManager.getProjectName(projectDir);
        String safeName = projectName.replaceAll("[^a-zA-Z0-9_\\u4e00-\\u9fa5\\-]", "_");
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, "Cpp_AddOn_" + safeName + ".zip");
        startActivityForResult(intent, REQUEST_EXPORT_PROJECT);
    }

    // ==================== 导入 ====================

    private void showImportDialog() {
        OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("导入模组");
        builder.setView(centeredMessage("请选择导入方式"));
        builder.setPositiveButton("选择ZIP导入", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { openZipPicker(); dialog.dismiss(); }
        });
        builder.getPositiveButton().setStyleSheet(StyleSheet.STYLE_GREEN);
        builder.setNegativeButton("选择文件夹导入", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { openFolderPicker(); dialog.dismiss(); }
        });
        // 暂停“从开发者平台导入”：平台下载的是加密发布包，并非可编辑的 Nemo 源项目 ZIP。
        // 保留实现代码供后续接入官方源项目导出接口时复用。
        /*
        builder.addButton("从开发者平台导入", StyleSheet.STYLE_WHITE, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                showPlatformWorkPicker();
            }
        });
        */
        builder.setNeutralButton("取消", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
        });
        builder.show();
    }

    /** 使用已有的开发者平台作品列表，选择作品后下载资源 ZIP 并复用 ZIP 导入流程。 */
    private void showPlatformWorkPicker() {
        final String cookie = settings.getString(Constants.PREFS_MC_COOKIE, "");
        if (cookie == null || cookie.isEmpty()) {
            UiUtils.toast(this, "请先登录开发者平台");
            return;
        }
        UiUtils.runOnUiThread(new Runnable() {
            @Override public void run() { showImportProgress(true); }
        });
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final List<McDevApi.WorkItem> works = McDevApi.getWorks(cookie);
                    UiUtils.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            showImportProgress(false);
                            if (works == null || works.isEmpty()) {
                                UiUtils.toast(MainActivity.this, works == null
                                        ? "加载开发者平台作品失败" : "开发者平台暂无作品");
                                return;
                            }
                            showPlatformWorkList(works, cookie);
                        }
                    });
                } catch (final Exception e) {
                    UiUtils.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            showImportProgress(false);
                            UiUtils.toast(MainActivity.this, "加载作品失败：" + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    /** 选择列表弹窗：候选作品灰色纵向排列，列表过长时可滚动，取消固定在底部。 */
    private void showPlatformWorkList(final List<McDevApi.WorkItem> works, final String cookie) {
        final LinearLayout optionList = new LinearLayout(this);
        optionList.setOrientation(LinearLayout.VERTICAL);
        int listPadding = UiUtils.dp(this, 8);
        optionList.setPadding(listPadding, listPadding, listPadding, listPadding);

        final ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(optionList);

        final LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        final OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("选择开发者平台模组");
        builder.setView(body);
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
        });
        final android.app.Dialog[] dialogRef = new android.app.Dialog[1];
        for (final McDevApi.WorkItem work : works) {
            OreButton option = new OreButton(this);
            option.setText(work.itemName == null || work.itemName.isEmpty()
                    ? work.itemId : work.itemName);
            option.setTextSize(14);
            option.setSingleLine(false);
            option.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
            option.setPadding(UiUtils.dp(this, 12), 0, UiUtils.dp(this, 12), 0);
            option.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
            // 使用与主页普通操作按钮一致的正常高度；相邻按钮不设置 margin，避免出现缝隙。
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, UiUtils.dp(this, 40));
            optionList.addView(option, params);
            option.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (dialogRef[0] != null) dialogRef[0].dismiss();
                    downloadPlatformWorkZip(work, cookie);
                }
            });
        }
        dialogRef[0] = builder.show();
    }

    /** 获取所选作品详情中的资源 ZIP 地址。详情字段结构可能随平台版本变化，逐层兼容解析。 */
    private void downloadPlatformWorkZip(final McDevApi.WorkItem work, final String cookie) {
        showImportProgress(true);
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    String detail = McDevApi.getWorkDetail(cookie, "pe", work.itemId);
                    if (detail == null || detail.isEmpty()) throw new IOException("无法获取作品详情");
                    JSONObject root = new JSONObject(detail);
                    // 详情接口中 res[0].res_url 是资源标识/占位字段，真正可下载地址在 cdn_url。
                    // 这是开发者平台详情模型 ResourceDetailRes 的字段结构，不能只搜索 res_url。
                    String url = extractZipUrl(root.opt("res"));
                    if (url.isEmpty()) url = extractZipUrl(root.opt("cdn_url"));
                    if (url.isEmpty()) url = extractZipUrl(root.opt("res_url"));
                    if (url.isEmpty()) url = extractZipUrl(root.opt("resource"));
                    if (url.isEmpty()) url = extractZipUrl(root);
                    if (url.isEmpty()) throw new IOException("作品没有可下载的 ZIP 资源（详情中缺少 cdn_url）");

                    // 调试期间保留源 ZIP 到公共下载目录，便于直接检查实际目录结构。
                    File targetDir = new File(Constants.DOWNLOAD_DIR);
                    if (!targetDir.exists() && !targetDir.mkdirs()) throw new IOException("无法创建下载目录");
                    String safeName = sanitizeFileName(work.itemName);
                    final File zipFile = new File(targetDir,
                            "platform_" + work.itemId + "_" + safeName + "_" + System.currentTimeMillis() + ".zip");
                    downloadToFile(url, cookie, zipFile);
                    if (isJsonErrorFile(zipFile)) {
                        zipFile.delete();
                        throw new IOException("平台返回登录错误，请重新登录");
                    }
                    final Uri zipUri = androidx.core.content.FileProvider.getUriForFile(
                            MainActivity.this, Constants.FILE_PROVIDER_AUTHORITY, zipFile);
                    UiUtils.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            showImportProgress(false);
                            importFromDownloadedZip(zipUri, zipFile);
                        }
                    });
                } catch (final Exception e) {
                    UiUtils.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            showImportProgress(false);
                            UiUtils.toast(MainActivity.this, "平台模组导入失败：" + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void importFromDownloadedZip(final Uri zipUri, final File zipFile) {
        showImportProgress(true);
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    projectManager.importFromZip(zipUri, new ProjectManager.ProgressCallback() {
                        @Override public void onProgress(int percent) { updateProgress(percent); }
                    });
                    UiUtils.toast(MainActivity.this, "开发者平台模组导入成功");
                    refreshProjects();
                } catch (final Exception e) {
                    LogUtil.logException(e);
                    UiUtils.toast(MainActivity.this, "ZIP 导入失败：" + e.getMessage());
                } finally {
                    // 源 ZIP 按调试要求保留在 McMod/Download，不在导入结束时删除。
                    hideImportProgress();
                }
            }
        }).start();
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.trim().isEmpty()) return "unnamed";
        String safe = name.trim().replaceAll("[\\\\/:*?\\\"<>|\\r\\n]", "_");
        return safe.length() > 80 ? safe.substring(0, 80) : safe;
    }

    private String extractZipUrl(Object value) {
        if (value == null || value == JSONObject.NULL) return "";
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                String result = extractZipUrl(array.opt(i));
                if (!result.isEmpty()) return result;
            }
            return "";
        }
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            String[] preferred = {"cdn_url", "download_url", "zip_url", "file_url", "resource_url", "url"};
            for (String key : preferred) {
                String result = extractZipUrl(object.opt(key));
                if (!result.isEmpty()) return result;
            }
            String[] nested = {"body", "data", "result", "file", "resource", "res_url"};
            for (String key : nested) {
                String result = extractZipUrl(object.opt(key));
                if (!result.isEmpty()) return result;
            }
            return "";
        }
        String raw = String.valueOf(value).trim().replace("\\/", "/");
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            // cdn_url 可能是无扩展名的签名下载地址，不能仅靠 .zip 后缀判断。
            // 此方法只从资源字段（cdn_url/res 等）调用，因此直接保留 HTTPS 地址。
            return raw;
        }
        try { return extractZipUrl(new JSONObject(raw)); } catch (Exception ignored) { }
        try { return extractZipUrl(new JSONArray(raw)); } catch (Exception ignored) { }
        return "";
    }

    private void downloadToFile(String url, String cookie, File output) throws IOException {
        java.net.HttpURLConnection conn = null;
        java.io.InputStream input = null;
        java.io.FileOutputStream outputStream = null;
        try {
            conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", "NemoModStudio");
            if (cookie != null && !cookie.isEmpty()) conn.setRequestProperty("Cookie", cookie);
            conn.connect();
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
            int total = conn.getContentLength();
            input = conn.getInputStream();
            outputStream = new java.io.FileOutputStream(output);
            byte[] buffer = new byte[8192];
            int length, done = 0;
            while ((length = input.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
                done += length;
                if (total > 0) updateProgress((int) (done * 100L / total));
            }
            outputStream.flush();
        } finally {
            try { if (input != null) input.close(); } catch (Exception ignored) { }
            try { if (outputStream != null) outputStream.close(); } catch (Exception ignored) { }
            if (conn != null) conn.disconnect();
        }
    }

    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_IMPORT_FOLDER);
    }

    private void openZipPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        startActivityForResult(intent, REQUEST_IMPORT_ZIP);
    }

    private void importFromFolder(final Uri treeUri) {
        showImportProgress(true);
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    projectManager.importFromFolder(treeUri);
                    UiUtils.toast(MainActivity.this, "导入成功");
                    refreshProjects();
                } catch (final Exception e) {
                    LogUtil.logException(e);
                    UiUtils.toast(MainActivity.this, "导入失败: " + e.getMessage());
                } finally {
                    hideImportProgress();
                }
            }
        }).start();
    }

    private void importFromZip(final Uri zipUri) {
        showImportProgress(true);
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    projectManager.importFromZip(zipUri, new ProjectManager.ProgressCallback() {
                        @Override public void onProgress(int percent) { updateProgress(percent); }
                    });
                    UiUtils.toast(MainActivity.this, "导入成功");
                    refreshProjects();
                } catch (final Exception e) {
                    LogUtil.logException(e);
                    UiUtils.toast(MainActivity.this, "导入失败: " + e.getMessage());
                } finally {
                    hideImportProgress();
                }
            }
        }).start();
    }

    private void showImportProgress(boolean show) {
        UiUtils.runOnUiThread(new Runnable() {
            @Override public void run() {
                importProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
                if (show) importProgressBar.setProgress(0);
            }
        });
    }

    private void hideImportProgress() {
        showImportProgress(false);
    }

    private void updateProgress(final int progress) {
        UiUtils.runOnUiThread(new Runnable() {
            @Override public void run() {
                if (importProgressBar != null) importProgressBar.setProgress(progress);
            }
        });
    }

    private void refreshProjects() {
        UiUtils.runOnUiThread(new Runnable() {
            @Override public void run() { loadProjects(); }
        });
    }

    // ==================== ProjectManager UI 回调 ====================

    private class ImportUiImpl implements ProjectManager.ImportUi {
        @Override
        public int askConflict(String projectId) {
            final Object lock = new Object();
            final int[] choice = {0};
            UiUtils.runOnUiThread(new Runnable() {
                @Override public void run() {
                    showImportConflictDialog(projectId, new ImportConflictCallback() {
                        @Override public void onChoice(int c) {
                            synchronized (lock) { choice[0] = c; lock.notify(); }
                        }
                    });
                }
            });
            synchronized (lock) {
                try { lock.wait(); } catch (InterruptedException ignored) {}
            }
            return choice[0];
        }

        @Override
        public String[] askNameAndNamespace() {
            final Object lock = new Object();
            final String[] result = {null, null};
            UiUtils.runOnUiThread(new Runnable() {
                @Override public void run() {
                    showNameAndNamespaceDialog(new NameNamespaceCallback() {
                        @Override public void onResult(String name, String namespace) {
                            synchronized (lock) { result[0] = name; result[1] = namespace; lock.notify(); }
                        }
                    });
                }
            });
            synchronized (lock) {
                try { lock.wait(); } catch (InterruptedException ignored) {}
            }
            if (result[0] == null) return null;
            return result;
        }
    }

    private interface ImportConflictCallback {
        void onChoice(int choice);
    }

    private interface NameNamespaceCallback {
        void onResult(String name, String namespace);
    }

    private void showImportConflictDialog(String projectName, final ImportConflictCallback callback) {
        OreTextView msgText = new OreTextView(this);
        msgText.setText("已存在同名项目 \"" + projectName + "\"\n请选择操作");
        msgText.setTextColor(Color.WHITE);
        msgText.setTextSize(14);
        LinearLayout msgLayout = new LinearLayout(this);
        msgLayout.setOrientation(LinearLayout.VERTICAL);
        msgLayout.setGravity(Gravity.CENTER);
        int padding = UiUtils.dp(this, 16);
        msgLayout.setPadding(padding, padding, padding, padding);
        msgLayout.addView(msgText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("项目冲突");
        builder.setView(msgLayout);
        builder.setPositiveButton("覆盖", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { callback.onChoice(1); dialog.dismiss(); }
        });
        builder.getPositiveButton().setStyleSheet(StyleSheet.STYLE_RED);
        builder.setNegativeButton("添加副本", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { callback.onChoice(2); dialog.dismiss(); }
        });
        builder.getNegativeButton().setStyleSheet(StyleSheet.STYLE_GREEN);
        builder.setNeutralButton("取消", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { callback.onChoice(3); dialog.dismiss(); }
        });
        builder.show();
    }

    private void showNameAndNamespaceDialog(final NameNamespaceCallback callback) {
        final OreEditText inputName = new OreEditText(this);
        inputName.setHint("模组名称");
        final OreEditText inputNs = new OreEditText(this);
        inputNs.setHint("命名空间（小写字母_）");

        final OreAlert nsAlert = new OreAlert(this);
        nsAlert.setText("命名空间只能包含小写字母和下划线");
        nsAlert.setStyleSheet(StyleSheet.STYLE_ALERT_YELLOW);
        nsAlert.setVisibility(View.GONE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int dialogPadding = UiUtils.dp(this, 16);
        layout.setPadding(dialogPadding, dialogPadding, dialogPadding, dialogPadding);
        layout.addView(inputName);
        layout.addView(inputNs);
        layout.addView(nsAlert);

        OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("设置模组信息");
        builder.setView(layout);
        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                String name = inputName.getText().toString().trim();
                String ns = inputNs.getText().toString().trim();
                if (name.isEmpty()) name = "未命名模组";
                if (ns.isEmpty()) ns = "test";
                if (!isValidNamespace(ns)) { UiUtils.toast(MainActivity.this, "命名空间格式错误"); return; }
                callback.onResult(name, ns);
                dialog.dismiss();
            }
        });
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                callback.onResult(null, null);
                dialog.dismiss();
            }
        });
        builder.getNegativeButton().setStyleSheet(StyleSheet.STYLE_RED);
        final OreButton positiveBtn = builder.getPositiveButton();
        updateNsValidation(inputNs.getText().toString(), positiveBtn, nsAlert);
        inputNs.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { updateNsValidation(s.toString(), positiveBtn, nsAlert); }
        });
        builder.show();
    }

    // ==================== 管理页（基于 MCDev API） ====================

    private boolean managerInitialized;
    private ScrollView managerScroll;
    private LinearLayout managerListContainer;
    private LinearLayout managerLoginBox;
    private OreEditText emailInput;
    private OreEditText pwdInput;
    private OreTextView managerStatusText;
    private OreButton addWorkBtn;

    private void setupManagerPage() {
        if (managerInitialized) return;
        managerInitialized = true;
        downloadProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        downloadProgressBar.setMax(100);
        downloadProgressBar.setProgress(0);
        downloadProgressBar.setVisibility(View.GONE);
        downloadProgressBar.setProgressTintList(ColorStateList.valueOf(Color.WHITE));
        managerContainer.addView(downloadProgressBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (6 * getResources().getDisplayMetrics().density)));
        downloadTargetDir = new File(Constants.DOWNLOAD_DIR);
        if (!downloadTargetDir.exists()) downloadTargetDir.mkdirs();

        // 顶部标题
        OreTextView title = new OreTextView(this);
        title.setText("开发者内容管理");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        managerContainer.addView(title);
        UiUtils.addGap(this, managerContainer, 4);

        // 登录状态
        managerStatusText = new OreTextView(this);
        managerStatusText.setTextColor(Color.parseColor("#AAAAAA"));
        managerStatusText.setTextSize(12);
        managerContainer.addView(managerStatusText);
        UiUtils.addGap(this, managerContainer, 8);

        // 登录卡片（默认隐藏）
        managerLoginBox = buildLoginBox();
        managerContainer.addView(managerLoginBox);
        // 登录后入口：新建作品与收件箱并列。
        LinearLayout workEntryRow = new LinearLayout(this);
        workEntryRow.setOrientation(LinearLayout.HORIZONTAL);
        addWorkBtn = new OreButton(this);
        addWorkBtn.setText("＋ 新增作品"); addWorkBtn.setStyleSheet(StyleSheet.STYLE_GREEN); addWorkBtn.setVisibility(View.GONE);
        addWorkBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showAddWorkDialog(); } });
        workEntryRow.addView(addWorkBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        OreButton mailboxBtn = new OreButton(this);
        mailboxBtn.setText("收件箱"); mailboxBtn.setStyleSheet(StyleSheet.STYLE_DARK_GRAY); mailboxBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, MailboxActivity.class)); } });
        workEntryRow.addView(mailboxBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        managerContainer.addView(workEntryRow);
        // 作品列表（滚动）
        managerScroll = new ScrollView(this);
        managerListContainer = new LinearLayout(this);
        managerListContainer.setOrientation(LinearLayout.VERTICAL);
        managerListContainer.setPadding(0, 8, 0, 8);
        managerScroll.addView(managerListContainer);
        managerContainer.addView(managerScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // 底部按钮行：刷新 / 浏览器打开 / MCDevManager
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        OreButton refreshBtn = new OreButton(this);
        refreshBtn.setText("刷新");
        refreshBtn.setStyleSheet(StyleSheet.STYLE_WHITE);
        refreshBtn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { refreshWorks(); }
        });
        btnRow.addView(refreshBtn);
        OreButton openBtn = new OreButton(this);
        openBtn.setText("浏览器打开");
        openBtn.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        openBtn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        openBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openBrowser(Constants.MC_DEV_WEB); }
        });
        btnRow.addView(openBtn);
        OreButton managerBtn = new OreButton(this);
        managerBtn.setText("MCDevManager");
        managerBtn.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        managerBtn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        managerBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                // 优先打开已安装的 MCDevManager App，未安装则跳转开源页面
                try {
                    Intent launch = getPackageManager().getLaunchIntentForPackage("com.lemon.mcdevmanagermp");
                    if (launch != null) {
                        startActivity(launch);
                    } else {
                        openBrowser(Constants.MC_DEV_MANAGER_PAGE);
                    }
                } catch (Exception e) {
                    openBrowser(Constants.MC_DEV_MANAGER_PAGE);
                }
            }
        });
        btnRow.addView(managerBtn);
        managerContainer.addView(btnRow);

        refreshLoginState();
    }

    private LinearLayout buildLoginBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, 8, 0, 8);

        OreTextView tip = new OreTextView(this);
        tip.setText("登录网易开发者账号（用于查询/管理你的作品）");
        tip.setTextColor(Color.parseColor("#AAAAAA"));
        tip.setTextSize(12);
        box.addView(tip);
        UiUtils.addGap(this, box, 8);

        emailInput = new OreEditText(this);
        emailInput.setHint("网易邮箱");
        emailInput.setTextSize(14);
        emailInput.setTextColor(Color.WHITE);
        box.addView(emailInput);
        UiUtils.addGap(this, box, 6);

        pwdInput = new OreEditText(this);
        pwdInput.setHint("密码");
        pwdInput.setTextSize(14);
        pwdInput.setTextColor(Color.WHITE);
        pwdInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(pwdInput);
        UiUtils.addGap(this, box, 8);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        OreButton loginBtn = new OreButton(this);
        loginBtn.setText("登录");
        loginBtn.setStyleSheet(StyleSheet.STYLE_WHITE);
        loginBtn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        loginBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doLogin(); }
        });
        btnRow.addView(loginBtn);
        OreButton cookieBtn = new OreButton(this);
        cookieBtn.setText("粘贴Cookie");
        cookieBtn.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        cookieBtn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        cookieBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doCookieLogin(); }
        });
        btnRow.addView(cookieBtn);
        OreButton logoutBtn = new OreButton(this);
        logoutBtn.setText("退出登录");
        logoutBtn.setStyleSheet(StyleSheet.STYLE_RED);
        logoutBtn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        logoutBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doLogout(); }
        });
        btnRow.addView(logoutBtn);
        box.addView(btnRow);
        return box;
    }

    private void refreshLoginState() {
        final String cookie = settings.getString(Constants.PREFS_MC_COOKIE, "");
        final String email = settings.getString(Constants.PREFS_MC_EMAIL, "");
        if (cookie == null || cookie.isEmpty()) {
            managerStatusText.setText("未登录");
            managerLoginBox.setVisibility(View.VISIBLE);
            if (addWorkBtn != null) addWorkBtn.setVisibility(View.GONE);
            managerListContainer.removeAllViews();
            addEmptyTip("登录后查看你的作品");
        } else {
            managerLoginBox.setVisibility(View.GONE);
            if (addWorkBtn != null) addWorkBtn.setVisibility(View.VISIBLE);
            managerStatusText.setText("已登录" + (email != null && !email.isEmpty() ? "：" + email : ""));
            refreshWorks();
        }
    }

    private void doLogin() {
        final String email = emailInput.getText().toString().trim();
        final String pwd = pwdInput.getText().toString();
        if (email.isEmpty() || pwd.isEmpty()) {
            UiUtils.toast(this, "请输入邮箱和密码");
            return;
        }
        managerStatusText.setText("登录中...（可能需要几秒）");
        new Thread(new Runnable() {
            @Override public void run() {
                final String cookie = McDevApi.login(email, pwd);
                UiUtils.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (cookie != null && !cookie.isEmpty()) {
                            settings.putString(Constants.PREFS_MC_COOKIE, cookie);
                                    settings.putString(Constants.PREFS_MC_EMAIL, email);
                            UiUtils.toast(MainActivity.this, "登录成功");
                            refreshLoginState();
                        } else {
                            managerStatusText.setText("登录失败，请检查账号密码或改用粘贴Cookie");
                        }
                    }
                });
            }
        }).start();
    }

    private void doCookieLogin() {
        final OreEditText input = new OreEditText(this);
        input.setHint("粘贴浏览器中的 Cookie（含 NTES_SESS=...）");
        input.setTextSize(13);
        input.setTextColor(Color.WHITE);
        new OreDialogBuilder(this)
                .setTitle("粘贴 Cookie")
                .setMessage("浏览器登录 mcdev.webapp.163.com 后，按 F12 复制 Cookie 粘贴到这里")
                .setView(input)
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { d.dismiss(); }
                })
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        final String cookie = input.getText().toString().trim();
                        if (cookie.isEmpty()) { UiUtils.toast(MainActivity.this, "Cookie 不能为空"); return; }
                        managerStatusText.setText("校验中...");
                        new Thread(new Runnable() {
                            @Override public void run() {
                                final boolean ok = McDevApi.validateCookie(cookie);
                                UiUtils.runOnUiThread(new Runnable() {
                                    @Override public void run() {
                                        if (ok) {
                                            settings.putString(Constants.PREFS_MC_COOKIE, cookie);
                                                    settings.putString(Constants.PREFS_MC_EMAIL, "Cookie 登录");
                                            UiUtils.toast(MainActivity.this, "Cookie 有效，已登录");
                                            refreshLoginState();
                                        } else {
                                            managerStatusText.setText("Cookie 无效或已过期");
                                        }
                                    }
                                });
                            }
                        }).start();
                    }
                })
                .show();
    }

    private void doLogout() {
        settings.remove(Constants.PREFS_MC_COOKIE);
        settings.remove(Constants.PREFS_MC_EMAIL);
        UiUtils.toast(this, "已退出登录");
        refreshLoginState();
    }

    private void refreshWorks() {
        final String cookie = settings.getString(Constants.PREFS_MC_COOKIE, "");
        if (cookie == null || cookie.isEmpty()) return;
        managerStatusText.setText("加载作品中...");
        new Thread(new Runnable() {
            @Override public void run() {
                java.util.List<McDevApi.WorkItem> loadedWorks = null;
                String loadError = null;
                try {
                    loadedWorks = McDevApi.getWorks(cookie);
                } catch (Exception e) {
                    loadError = e.getMessage();
                }
                final java.util.List<McDevApi.WorkItem> works = loadedWorks;
                final String error = loadError;
                UiUtils.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (error != null) {
                            // getWorks 明确报告 Cookie 失效时，清除本地登录状态并恢复登录界面。
                            if (error.contains("Cookie已失效")) {
                                settings.remove(Constants.PREFS_MC_COOKIE);
                                settings.remove(Constants.PREFS_MC_EMAIL);
                                refreshLoginState();
                                managerStatusText.setText("Cookie 已失效，请重新登录");
                            } else {
                                managerStatusText.setText(error.isEmpty() ? "加载作品失败" : error);
                            }
                            return;
                        }
                        managerStatusText.setText("共 " + works.size() + " 个作品");
                        renderWorks(works);
                    }
                });
            }
        }).start();
    }

    private void renderWorks(final java.util.List<McDevApi.WorkItem> works) {
        managerListContainer.removeAllViews();
        if (works.isEmpty()) {
            addEmptyTip("暂无作品");
            return;
        }
        for (final McDevApi.WorkItem item : works) {
            OreCard card = new OreCard(this);
            // 外层仅作为视觉容器：不接受点击，也不显示任何锁定/禁用贴图；
            // 内部操作按钮保持各自可点击。
            card.setClickable(false);
            card.setLongClickable(false);
            card.setFocusable(false);
            // OreCard 默认背景含 pressed 状态；换成静态背景，彻底去掉外层按下反馈。
            GradientDrawable staticCardBg = new GradientDrawable();
            staticCardBg.setColor(Color.parseColor("#4A4A4A"));
            staticCardBg.setStroke(UiUtils.dp(this, 1), Color.parseColor("#777777"));
            card.setBackground(staticCardBg);
            card.setPadding(16, 12, 16, 12);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cp.bottomMargin = UiUtils.dp(this, 8);
            card.setLayoutParams(cp);
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            OreTextView name = new OreTextView(this);
            name.setText(item.itemName);
            name.setTextColor(Color.WHITE);
            name.setTextSize(14);
            col.addView(name);
            OreTextView status = new OreTextView(this);
            status.setText("状态：" + McDevApi.statusLabel(item.status) + "  (ID: " + item.itemId + ")");
            status.setTextColor(Color.parseColor("#AAAAAA"));
            status.setTextSize(11);
            col.addView(status);
            UiUtils.addGap(this, col, 6);
            col.addView(buildActionRow(item));
            card.addView(col);
            managerListContainer.addView(card);
        }
    }

    private LinearLayout buildActionRow(final McDevApi.WorkItem item) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        String status = item.status == null ? "" : item.status;
        if ("init".equals(status)) {
            addActionBtn(row, "自测", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    // ApplySelfTestDTO 只接受 self_test_pass_check；is_check_apply 属于提审 DTO。
                    performAction(item, "self-test-apply", "{\"self_test_pass_check\":false}", false);
                }
            });
            addActionBtn(row, "提交审核", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    performAction(item, "apply_review", "{\"apply_review_text\":\"\",\"conflict_notify\":1,\"conflict_notify_type\":[1],\"is_check_apply\":false}", false);
                }
            });
        } else if ("self_test".equals(status) || "self_test_prepare".equals(status)) {
            addActionBtn(row, "取消自测", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    performAction(item, "cancel_self_test", null, false);
                }
            });
        } else if ("reviewing".equals(status) || "prepare".equals(status)) {
            addActionBtn(row, "取消审核", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    performAction(item, "cancel_review", null, false);
                }
            });
        } else if ("accept".equals(status)) {
            addActionBtn(row, "上架", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    performAction(item, "online", "{\"op_platform\":\"all\"}", false);
                }
            });
        }
        addActionBtn(row, "编辑", new View.OnClickListener() {
            @Override public void onClick(View v) { editWorkDialog(item); }
        });
        addActionBtn(row, "删除", new View.OnClickListener() {
            @Override public void onClick(View v) { confirmDelete(item); }
        });
        return row;
    }

    private void addActionBtn(LinearLayout row, String text, View.OnClickListener listener) {
        OreButton btn = new OreButton(this);
        btn.setText(text);
        btn.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btn.setTextSize(11);
        btn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        btn.setOnClickListener(listener);
        row.addView(btn);
    }

    private void performAction(final McDevApi.WorkItem item, final String action,
                               final String jsonBody, final boolean isDelete) {
        final String cookie = settings.getString(Constants.PREFS_MC_COOKIE, "");
        managerStatusText.setText("操作中...");
        new Thread(new Runnable() {
            @Override public void run() {
                final boolean ok;
                if (isDelete) ok = McDevApi.deleteWork(cookie, "pe", item.itemId);
                else ok = McDevApi.changeStatus(cookie, "pe", item.itemId, action, jsonBody);
                UiUtils.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        String error = McDevApi.getLastError();
                        UiUtils.toast(MainActivity.this, ok ? "操作成功"
                                : (error == null || error.isEmpty() ? "操作失败" : "操作失败：" + error));
                        refreshWorks();
                    }
                });
            }
        }).start();
    }

    private void confirmDelete(final McDevApi.WorkItem item) {
        // 与主页/收件箱确认弹窗保持一致：标题使用原生区域，正文使用标准 16dp 内边距。
        OreTextView bodyText = new OreTextView(this);
        bodyText.setText("确定删除《" + item.itemName + "》？此操作不可恢复！");
        bodyText.setTextColor(Color.WHITE);
        bodyText.setTextSize(14);
        bodyText.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_VERTICAL);
        int padding = UiUtils.dp(this, 16);
        body.setPadding(padding, padding, padding, padding);
        body.addView(bodyText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("删除作品");
        builder.setView(body);
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int w) { d.dismiss(); }
        });
        builder.setPositiveButton("删除", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int w) {
                performAction(item, null, null, true);
            }
        });
        builder.getPositiveButton().setStyleSheet(StyleSheet.STYLE_RED);
        builder.show();
    }

    private void showAddWorkDialog() {
        final String cookie = settings.getString(Constants.PREFS_MC_COOKIE, "");
        if (cookie == null || cookie.isEmpty()) { UiUtils.toast(this, "请先登录"); return; }
        Intent it = new Intent(this, WorkDetailActivity.class);
        it.putExtra("mode", "create");
        startActivityForResult(it, REQ_WORK_SAVE);
    }

    private void editWorkDialog(final McDevApi.WorkItem item) {
        final String cookie = settings.getString(Constants.PREFS_MC_COOKIE, "");
        if (cookie == null || cookie.isEmpty()) { UiUtils.toast(this, "请先登录"); return; }
        Intent it = new Intent(this, WorkDetailActivity.class);
        it.putExtra("mode", "edit");
        it.putExtra("item_id", item.itemId);
        it.putExtra("item_name", item.itemName);
        startActivityForResult(it, REQ_WORK_SAVE);
    }
    // 新增/编辑作品保存成功后刷新列表
    private static final int REQ_WORK_SAVE = 2001;

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void addEmptyTip(String text) {
        OreTextView tip = new OreTextView(this);
        tip.setText(text);
        tip.setTextColor(Color.parseColor("#666666"));
        tip.setTextSize(13);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(0, 24, 0, 24);
        managerListContainer.addView(tip);
    }

    private void showTab(int index) {
        if (scrollView != null) scrollView.setVisibility(index == 2 ? View.GONE : View.VISIBLE);
        if (projectListContainer != null) projectListContainer.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        if (aboutContainer != null) aboutContainer.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        if (managerContainer != null) managerContainer.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        if (buttonRow != null) buttonRow.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        if (searchBox != null) searchBox.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
    }

    private void downloadFile(final String url, final String fileName) {
        UiUtils.runOnUiThread(new Runnable() {
            @Override public void run() {
                downloadProgressBar.setProgress(0);
                downloadProgressBar.setVisibility(View.VISIBLE);
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                java.net.HttpURLConnection conn = null;
                java.io.InputStream is = null;
                java.io.FileOutputStream fos = null;
                final File outFile = new File(downloadTargetDir, fileName);
                try {
                    java.net.URL downloadUrl = new java.net.URL(url);
                    conn = (java.net.HttpURLConnection) downloadUrl.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);
                    conn.setRequestProperty("User-Agent", "NemoModStudio");
                    conn.connect();

                    int totalSize = conn.getContentLength();
                    is = conn.getInputStream();
                    fos = new java.io.FileOutputStream(outFile);
                    byte[] buffer = new byte[8192];
                    int len, downloaded = 0;
                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                        downloaded += len;
                        if (totalSize > 0) {
                            final int progress = (int) (downloaded * 100L / totalSize);
                            UiUtils.runOnUiThread(new Runnable() {
                                @Override public void run() { downloadProgressBar.setProgress(progress); }
                            });
                        }
                    }
                    fos.flush();

                    if (isJsonErrorFile(outFile)) {
                        outFile.delete();
                        UiUtils.toast(MainActivity.this, "下载错误，需要打开浏览器下载");
                    } else {
                        UiUtils.toast(MainActivity.this, "下载完成：" + outFile.getAbsolutePath());
                    }
                } catch (final Exception e) {
                    LogUtil.logException(e);
                    UiUtils.toast(MainActivity.this, "下载失败：" + e.getMessage());
                    if (outFile.exists()) outFile.delete();
                } finally {
                    try { if (is != null) is.close(); } catch (Exception ignored) {}
                    try { if (fos != null) fos.close(); } catch (Exception ignored) {}
                    if (conn != null) conn.disconnect();
                    UiUtils.runOnUiThread(new Runnable() {
                        @Override public void run() { downloadProgressBar.setVisibility(View.GONE); }
                    });
                }
            }
        }).start();
    }

    /** 判断下载文件是否为包含 "status":"no_login" 的 JSON 错误。 */
    private boolean isJsonErrorFile(File file) {
        try {
            byte[] buffer = new byte[Math.min((int) file.length(), 1024)];
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            fis.read(buffer);
            fis.close();
            String content = new String(buffer, "UTF-8").trim();
            if (content.startsWith("{") && content.contains("\"status\"")) {
                JSONObject json = new JSONObject(content);
                return "no_login".equals(json.optString("status"));
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void openDownloadFolder() {
        openProjectFolder(downloadTargetDir);
    }

    // ==================== onActivityResult ====================

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // 系统“管理所有文件”设置页通常不会返回 RESULT_OK，必须独立处理。
        if (requestCode == REQUEST_MANAGE_STORAGE) {
            showStoragePermissionUiIfNeeded();
            if (checkStoragePermission()) loadProjects();
            return;
        }
        if (resultCode != RESULT_OK) return;
        switch (requestCode) {
            case REQ_WORK_SAVE:
                refreshWorks();
                break;
            case REQUEST_IMPORT_FOLDER:
                if (data != null && data.getData() != null) importFromFolder(data.getData());
                break;
            case REQUEST_IMPORT_ZIP:
                if (data != null && data.getData() != null) importFromZip(data.getData());
                break;
            case REQUEST_EXPORT_PROJECT:
                if (pendingExportDir != null && data != null && data.getData() != null) {
                    try {
                        projectManager.exportProject(pendingExportDir, data.getData());
                        UiUtils.toast(this, "导出成功");
                    } catch (IOException e) {
                        LogUtil.logException(e);
                        UiUtils.toast(this, "导出失败: " + e.getMessage());
                    }
                }
                break;
            case REQUEST_MANAGE_STORAGE:
                loadProjects();
                break;
        }
    }

    // ==================== 生命周期 ====================

    @Override
    protected void onResume() {
        super.onResume();
        if (checkStoragePermission()) {
            loadProjects();
            autoCheckForUpdate();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}