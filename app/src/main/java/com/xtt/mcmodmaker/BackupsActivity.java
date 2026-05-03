package com.xtt.mcmodmaker;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import dev1503.oreui.StyleSheet;
import dev1503.oreui.dialog.OreDialogBuilder;
import dev1503.oreui.widgets.OreButton;
import dev1503.oreui.widgets.OreCard;
import dev1503.oreui.widgets.OreEditText;
import dev1503.oreui.widgets.OreTextView;

public class BackupsActivity extends Activity {

    private static final String MOD_FOLDER_PATH = "/storage/emulated/0/McMod";
    private String originalId;
    private LinearLayout backupListContainer;
    private List<File> allBackups;
    private String searchQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        originalId = getIntent().getStringExtra("original_id");
        if (originalId == null) {
            finish();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.parseColor("#1A1A1A"));
            getWindow().setNavigationBarColor(Color.parseColor("#1A1A1A"));
        }

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#1A1A1A"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 40, 20, 40);
        scrollView.addView(root);

        // 获取模组显示名称
        String displayName = originalId;
        File originalProject = new File(MOD_FOLDER_PATH, originalId);
        if (!originalProject.exists()) {
            File backupContainer = new File(MOD_FOLDER_PATH, originalId + "_back");
            File[] firstBackup = backupContainer.listFiles();
            if (firstBackup != null && firstBackup.length > 0) {
                File nameSource = firstBackup[0];
                if (nameSource.isDirectory()) {
                    File studioFile = new File(nameSource, "studio.json");
                    if (studioFile.exists()) {
                        try {
                            FileInputStream fis = new FileInputStream(studioFile);
                            byte[] data = new byte[(int) studioFile.length()];
                            fis.read(data);
                            fis.close();
                            String content = new String(data, "UTF-8");
                            int start = content.indexOf("\"name\"");
                            if (start != -1) {
                                start = content.indexOf("\"", start + 6);
                                if (start != -1) {
                                    int end = content.indexOf("\"", start + 1);
                                    if (end != -1) {
                                        displayName = content.substring(start + 1, end);
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        } else {
            File studioFile = new File(originalProject, "studio.json");
            if (studioFile.exists()) {
                try {
                    FileInputStream fis = new FileInputStream(studioFile);
                    byte[] data = new byte[(int) studioFile.length()];
                    fis.read(data);
                    fis.close();
                    String content = new String(data, "UTF-8");
                    int start = content.indexOf("\"name\"");
                    if (start != -1) {
                        start = content.indexOf("\"", start + 6);
                        if (start != -1) {
                            int end = content.indexOf("\"", start + 1);
                            if (end != -1) {
                                displayName = content.substring(start + 1, end);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        // 标题
        OreTextView title = new OreTextView(this);
        title.setText(displayName + " 的所有备份");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        root.addView(title);
        addGap(root, 16);

        // 搜索框
        final OreEditText searchBox = new OreEditText(this);
        searchBox.setHint("搜索备份时间...");
        searchBox.setTextSize(12);
        searchBox.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    searchQuery = s.toString().trim().toLowerCase();
                    refreshBackupList();
                }
            });
        root.addView(searchBox);
        addGap(root, 12);

        // 返回按钮
        OreButton btnBack = new OreButton(this);
        btnBack.setText("← 返回主页");
        btnBack.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        root.addView(btnBack);
        addGap(root, 12);

        // 备份列表容器
        backupListContainer = new LinearLayout(this);
        backupListContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(backupListContainer);

        setContentView(scrollView);

        loadAllBackups();
    }

    private void loadAllBackups() {
        allBackups = new ArrayList<>();
        File backupContainer = new File(MOD_FOLDER_PATH, originalId + "_back");
        if (backupContainer.exists() && backupContainer.isDirectory()) {
            File[] subDirs = backupContainer.listFiles();
            if (subDirs != null) {
                for (File sub : subDirs) {
                    if (sub.isDirectory()) {
                        allBackups.add(sub);
                    }
                }
            }
        }
        refreshBackupList();
    }

    private void refreshBackupList() {
        backupListContainer.removeAllViews();
        if (allBackups.isEmpty()) {
            OreTextView empty = new OreTextView(this);
            empty.setText("暂无备份");
            empty.setTextColor(Color.GRAY);
            backupListContainer.addView(empty);
            return;
        }

        boolean hasResults = false;
        for (File backupDir : allBackups) {
            String folderName = backupDir.getName();
            String backupTime = "";
            try {
                long timestamp = Long.parseLong(folderName) * 1000L;
                backupTime = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(timestamp));
            } catch (Exception ignored) {
                backupTime = folderName;
            }

            if (!searchQuery.isEmpty()) {
                if (!backupTime.toLowerCase().contains(searchQuery) && !folderName.contains(searchQuery)) {
                    continue;
                }
            }

            hasResults = true;
            OreCard card = createBackupCard(backupDir, backupTime);
            backupListContainer.addView(card);
            addGap(backupListContainer, 8);
        }

        if (!hasResults) {
            OreTextView empty = new OreTextView(this);
            empty.setText("没有找到匹配的备份");
            empty.setTextColor(Color.GRAY);
            backupListContainer.addView(empty);
        }
    }

    private OreCard createBackupCard(final File backupDir, String backupTime) {
        OreCard card = new OreCard(this);
        card.setPadding(16, 12, 16, 12);

        LinearLayout cardLayout = new LinearLayout(this);
        cardLayout.setOrientation(LinearLayout.HORIZONTAL);
        cardLayout.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(android.R.drawable.ic_menu_save);
        icon.setColorFilter(Color.parseColor("#AAAAAA"));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(80, 80);
        iconParams.setMargins(0, 0, 16, 0);
        icon.setLayoutParams(iconParams);
        cardLayout.addView(icon);

        LinearLayout textLayout = new LinearLayout(this);
        textLayout.setOrientation(LinearLayout.VERTICAL);

        String displayName = backupTime;
        File studioFile = new File(backupDir, "studio.json");
        if (studioFile.exists()) {
            try {
                FileInputStream fis = new FileInputStream(studioFile);
                byte[] data = new byte[(int) studioFile.length()];
                fis.read(data);
                fis.close();
                String content = new String(data, "UTF-8");
                int start = content.indexOf("\"name\"");
                if (start != -1) {
                    start = content.indexOf("\"", start + 6);
                    if (start != -1) {
                        int end = content.indexOf("\"", start + 1);
                        if (end != -1) {
                            displayName = content.substring(start + 1, end) + " (副本)";
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        OreTextView nameText = new OreTextView(this);
        nameText.setText(displayName);
        nameText.setTextSize(16);
        nameText.setTextColor(Color.WHITE);
        textLayout.addView(nameText);

        OreTextView timeText = new OreTextView(this);
        timeText.setText("备份保存于: " + backupTime);
        timeText.setTextSize(12);
        timeText.setTextColor(Color.GRAY);
        textLayout.addView(timeText);

        cardLayout.addView(textLayout);
        card.addView(cardLayout);

        card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showBackupMenu(backupDir);
                }
            });

        return card;
    }

    private void showBackupMenu(final File backupDir) {
        OreTextView msg = new OreTextView(this);
        msg.setText("请选择操作");
        msg.setTextColor(Color.WHITE);
        msg.setTextSize(14);

        LinearLayout msgLayout = new LinearLayout(this);
        msgLayout.setOrientation(LinearLayout.VERTICAL);
        msgLayout.setGravity(Gravity.CENTER);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        msgLayout.setPadding(pad, pad, pad, pad);
        msgLayout.addView(msg);

        OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("副本操作");
        builder.setView(msgLayout);

        builder.setPositiveButton("覆盖", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    overwriteOriginal(backupDir);
                    dialog.dismiss();
                }
            });
        builder.getPositiveButton().setStyleSheet(StyleSheet.STYLE_RED);

        builder.setNegativeButton("设置", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    showCopySettings(backupDir);
                }
            });
        builder.getNegativeButton().setStyleSheet(StyleSheet.STYLE_WHITE);

        builder.setNeutralButton("取消", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });

        builder.show();
    }

    private void showCopySettings(final File backupDir) {
        String backupName = backupDir.getName();
        String backupTime = "";
        try {
            long timestamp = Long.parseLong(backupName) * 1000L;
            backupTime = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(timestamp));
        } catch (Exception ignored) {
            backupTime = backupName;
        }

        String displayName = backupTime;
        File studioFile = new File(backupDir, "studio.json");
        if (studioFile.exists()) {
            try {
                FileInputStream fis = new FileInputStream(studioFile);
                byte[] data = new byte[(int) studioFile.length()];
                fis.read(data);
                fis.close();
                String content = new String(data, "UTF-8");
                int start = content.indexOf("\"name\"");
                if (start != -1) {
                    start = content.indexOf("\"", start + 6);
                    if (start != -1) {
                        int end = content.indexOf("\"", start + 1);
                        if (end != -1) {
                            displayName = content.substring(start + 1, end);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setGravity(Gravity.CENTER);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        contentLayout.setPadding(pad, pad, pad, pad);

        OreTextView nameText = new OreTextView(this);
        nameText.setText("副本名称: " + displayName);
        nameText.setTextColor(Color.WHITE);
        nameText.setTextSize(14);
        contentLayout.addView(nameText);
        addGap(contentLayout, 8);

        OreTextView timeText = new OreTextView(this);
        timeText.setText("备份时间: " + backupTime);
        timeText.setTextColor(Color.GRAY);
        timeText.setTextSize(12);
        contentLayout.addView(timeText);

        OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("副本设置");
        builder.setView(contentLayout);

        builder.setPositiveButton("打开文件夹", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    openProjectFolder(backupDir);
                    dialog.dismiss();
                }
            });
        builder.getPositiveButton().setStyleSheet(StyleSheet.STYLE_GREEN);

        builder.setNegativeButton("删除", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    showDeleteConfirm(backupDir);
                }
            });
        builder.getNegativeButton().setStyleSheet(StyleSheet.STYLE_RED);

        builder.setNeutralButton("取消", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });

        builder.show();
    }

    private void showDeleteConfirm(final File projectDir) {
        OreTextView msgText = new OreTextView(this);
        msgText.setText("你确定要删除这个备份吗？\n它会永久消失(真的很久)\n此操作不可恢复！");
        msgText.setTextColor(Color.WHITE);
        msgText.setTextSize(14);

        LinearLayout msgLayout = new LinearLayout(this);
        msgLayout.setOrientation(LinearLayout.VERTICAL);
        msgLayout.setGravity(Gravity.CENTER);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        msgLayout.setPadding(padding, padding, padding, padding);
        msgLayout.addView(msgText);

        OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("确认删除");
        builder.setView(msgLayout);
        builder.setPositiveButton("删除", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (deleteRecursive(projectDir)) {
                        Toast.makeText(BackupsActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                        loadAllBackups();
                    } else {
                        Toast.makeText(BackupsActivity.this, "删除失败", Toast.LENGTH_SHORT).show();
                    }
                    dialog.dismiss();
                }
            });
        builder.getPositiveButton().setStyleSheet(StyleSheet.STYLE_RED);
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
            });
        builder.show();
    }

    private void overwriteOriginal(File backupSubDir) {
        File parentDir = backupSubDir.getParentFile();
        String containerName = parentDir.getName();
        if (!containerName.endsWith("_back")) {
            Toast.makeText(this, "无法识别原项目ID", Toast.LENGTH_SHORT).show();
            return;
        }
        String origId = containerName.substring(0, containerName.length() - 5);
        File originalDir = new File(MOD_FOLDER_PATH, origId);

        if (originalDir.exists()) {
            deleteRecursive(originalDir);
        }

        try {
            copyDirectory(backupSubDir, originalDir);
            Toast.makeText(this, "覆盖成功", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        } catch (IOException e) {
            Toast.makeText(this, "覆盖失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openProjectFolder(File folder) {
        try {
            Uri uri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                uri = androidx.core.content.FileProvider.getUriForFile(
                    this, "com.xtt.mcmodmaker.fileprovider", folder);
            } else {
                uri = Uri.fromFile(folder);
            }
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/x-directory");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            try {
                Uri uri;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    uri = androidx.core.content.FileProvider.getUriForFile(
                        this, "com.xtt.mcmodmaker.fileprovider", folder);
                } else {
                    uri = Uri.fromFile(folder);
                }
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(uri, "resource/folder");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            } catch (Exception e2) {
                Toast.makeText(this, "无法打开文件夹", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return file.delete();
    }

    private void copyDirectory(File source, File dest) throws IOException {
        if (source.isDirectory()) {
            dest.mkdirs();
            File[] children = source.listFiles();
            if (children != null) {
                for (File child : children) {
                    copyDirectory(child, new File(dest, child.getName()));
                }
            }
        } else {
            FileInputStream fis = new FileInputStream(source);
            FileOutputStream fos = new FileOutputStream(dest);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
            fis.close();
            fos.close();
        }
    }

    private void addGap(LinearLayout parent, int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                              LinearLayout.LayoutParams.MATCH_PARENT, h));
        parent.addView(v);
    }

}
