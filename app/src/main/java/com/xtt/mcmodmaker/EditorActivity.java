package com.xtt.mcmodmaker;

import android.app.Activity;
import android.content.ClipData;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import dev1503.oreui.StyleSheet;
import dev1503.oreui.dialog.OreDialogBuilder;
import dev1503.oreui.widgets.OreAccordion;
import dev1503.oreui.widgets.OreButton;
import dev1503.oreui.widgets.OreCard;
import dev1503.oreui.widgets.OreEditText;
import dev1503.oreui.widgets.OreTextView;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import dev1503.oreui.widgets.OreAlert;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import org.json.JSONObject;
import org.json.JSONArray;
import android.widget.GridLayout;
import android.widget.CompoundButton;
import android.app.AlertDialog;

import dev1503.oreui.widgets.OreSwitch;
import android.widget.CompoundButton;

public class EditorActivity extends Activity {

    private static final int MODE_BROWSE = 0;
    private static final int MODE_FOCUS = 1;
    private static final int MODE_EDIT = 2;

    private String projectPath;
    private File projectDir;
    private File currentDir;

    private LinearLayout leftContainer;
    private LinearLayout middleContainer;
    private LinearLayout editorContainer;

    private LinearLayout leftPanel, middlePanel, rightPanel;
    private OreButton btnBrowse, btnFocus, btnEdit;
    private int currentMode = MODE_BROWSE;

    private FileNode rootNode;
    private LinearLayout modeButtonContainer;

    private static final int BLOCK_WIDTH_DP = 110;
    private static final int BLOCK_SPACING_DP = 8;

    private boolean isHighlighting = false;

    private File currentRecipeFile;

    private GridLayout recipeGrid;
    private LinearLayout recipeMappingLayout;

    // 配方可视化编辑器状态（立即初始化避免 NPE）
    private Set<String> selectedTags = new HashSet<String>();
    private JSONObject currentRecipeRoot;
    private String currentRecipeType;
    private OreEditText visualResultItem;
    private OreEditText visualResultCount;
    private OreEditText visualUnlockEdit;
    private LinearLayout recipeContentArea;
    private String currentUnlockContext = "AlwaysUnlocked";   // 当前解锁方式context
	private List<String> unlockItems = new ArrayList<String>();  // 用于PlayerHasManyItems的物品列表
	private List<String> shapelessInputs = new ArrayList<String>();      // 无序合成输入物品列表
	private LinearLayout shapelessInputLayout;          // 输入物品UI容器
	private List<String> furnaceInputs = new ArrayList<String>();  // 熔炉输入物品
	private OreEditText brewingInputItem;     // 酿造输入药水
	private OreEditText brewingReagentItem;   // 酿造试剂
	private OreEditText smithingTemplateItem;   // 锻造模板
	private OreEditText smithingBaseItem;       // 锻造基底
	private OreEditText smithingAdditionItem;   // 锻造添加物

    // 文件节点模型
    private static class FileNode {
        String name;
        File file;
        boolean isDirectory;
        List<FileNode> children;
        FileNode(File file) { this.file = file; this.name = file.getName(); this.isDirectory = file.isDirectory(); children = new ArrayList<FileNode>(); }
    }

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
        rootNode = scanDirectory(projectDir, true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(Color.parseColor("#1A1A1A"));

        leftPanel = new LinearLayout(this);
        leftPanel.setOrientation(LinearLayout.VERTICAL);
        leftPanel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 2f));
        leftPanel.setPadding(8, 8, 8, 8);

        addFunctionAccordion(leftPanel);
        addGap(leftPanel, 8);

        modeButtonContainer = new LinearLayout(this);
        modeButtonContainer.setOrientation(LinearLayout.HORIZONTAL);
        modeButtonContainer.setLayoutParams(new LinearLayout.LayoutParams(
                                                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);

        btnBrowse = new OreButton(this);
        btnBrowse.setText("浏览");
        btnBrowse.setStyleSheet(StyleSheet.STYLE_GREEN);
        btnBrowse.setLayoutParams(btnParams);
        btnBrowse.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { switchMode(MODE_BROWSE); }
            });
        modeButtonContainer.addView(btnBrowse);

        btnFocus = new OreButton(this);
        btnFocus.setText("聚焦");
        btnFocus.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btnFocus.setLayoutParams(btnParams);
        btnFocus.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { switchMode(MODE_FOCUS); }
            });
        modeButtonContainer.addView(btnFocus);

        btnEdit = new OreButton(this);
        btnEdit.setText("编辑");
        btnEdit.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btnEdit.setLayoutParams(btnParams);
        btnEdit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { switchMode(MODE_EDIT); }
            });
        modeButtonContainer.addView(btnEdit);

        leftPanel.addView(modeButtonContainer);
        addGap(leftPanel, 8);

        ScrollView leftScroll = new ScrollView(this);
        leftScroll.setLayoutParams(new LinearLayout.LayoutParams(
                                       LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        leftContainer = new LinearLayout(this);
        leftContainer.setOrientation(LinearLayout.VERTICAL);
        leftScroll.addView(leftContainer);
        leftPanel.addView(leftScroll);

        root.addView(leftPanel);
        View divider1 = new View(this);
        divider1.setLayoutParams(new LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT));
        divider1.setBackgroundColor(Color.parseColor("#333333"));
        root.addView(divider1);

        middlePanel = new LinearLayout(this);
        middlePanel.setOrientation(LinearLayout.VERTICAL);
        middlePanel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 3f));
        middlePanel.setPadding(8, 8, 8, 8);

        LinearLayout middleHeader = new LinearLayout(this);
        middleHeader.setOrientation(LinearLayout.HORIZONTAL);
        middleHeader.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams headerBtnParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);

        OreButton btnRoot = new OreButton(this);
        btnRoot.setText("根目录");
        btnRoot.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btnRoot.setLayoutParams(headerBtnParams);
        btnRoot.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentDir = projectDir;
                    loadFileList(currentDir);
                }
            });
        middleHeader.addView(btnRoot);

        OreButton btnBack = new OreButton(this);
        btnBack.setText("← 返回");
        btnBack.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btnBack.setLayoutParams(headerBtnParams);
        btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (currentDir != null && !currentDir.equals(projectDir)) {
                        File parent = currentDir.getParentFile();
                        if (parent != null && parent.getAbsolutePath().startsWith(projectPath)) {
                            loadFileList(parent);
                        }
                    }
                }
            });
        middleHeader.addView(btnBack);

        OreButton btnNew = new OreButton(this);
        btnNew.setText("新建");
        btnNew.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btnNew.setLayoutParams(headerBtnParams);
        btnNew.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showNewItemDialog();
                }
            });
        middleHeader.addView(btnNew);

        OreButton btnImport = new OreButton(this);
        btnImport.setText("导入");
        btnImport.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btnImport.setLayoutParams(headerBtnParams);
        btnImport.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showImportDialog();
                }
            });
        middleHeader.addView(btnImport);

        middlePanel.addView(middleHeader);
        addGap(middlePanel, 8);

        ScrollView middleScroll = new ScrollView(this);
        middleScroll.setLayoutParams(new LinearLayout.LayoutParams(
                                         LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        middleContainer = new LinearLayout(this);
        middleContainer.setOrientation(LinearLayout.VERTICAL);
        middleScroll.addView(middleContainer);
        middlePanel.addView(middleScroll);

        root.addView(middlePanel);
        View divider2 = new View(this);
        divider2.setLayoutParams(new LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT));
        divider2.setBackgroundColor(Color.parseColor("#333333"));
        root.addView(divider2);

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
        switchMode(MODE_BROWSE);
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
            LinearLayout.LayoutParams vertParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            btnBrowse.setLayoutParams(vertParams); btnFocus.setLayoutParams(vertParams); btnEdit.setLayoutParams(vertParams);
        } else {
            modeButtonContainer.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams horzParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            btnBrowse.setLayoutParams(horzParams); btnFocus.setLayoutParams(horzParams); btnEdit.setLayoutParams(horzParams);
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
        tabContainer.setLayoutParams(new LinearLayout.LayoutParams(
                                         LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        leftContainer.addView(tabContainer);

        final OreButton tabQuick = new OreButton(this);
        tabQuick.setText("快速跳转");
        tabQuick.setStyleSheet(StyleSheet.STYLE_GREEN);
        final OreButton tabTree = new OreButton(this);
        tabTree.setText("文件树");
        tabTree.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);

        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setLayoutParams(new LinearLayout.LayoutParams(
                                   LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        tabQuick.setLayoutParams(tabParams);
        tabTree.setLayoutParams(tabParams);

        tabRow.addView(tabQuick);
        tabRow.addView(tabTree);
        tabContainer.addView(tabRow);

        final LinearLayout subContainer = new LinearLayout(this);
        subContainer.setOrientation(LinearLayout.VERTICAL);
        subContainer.setLayoutParams(new LinearLayout.LayoutParams(
                                         LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
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

    private void showQuickJump(LinearLayout parent) {
		parent.removeAllViews();
		File[] rootFiles = projectDir.listFiles();
		if (rootFiles != null) {
			for (File f : rootFiles) {
				String name = f.getName().toLowerCase();
				if (f.isDirectory()) {
					if (name.startsWith("behavior_pack_") || name.startsWith("behaviour_pack_")) {
						// 行为包文件夹
						OreButton btnBeh = new OreButton(this);
						btnBeh.setText("行为包");
						btnBeh.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
						final File behDir = f;
						btnBeh.setOnClickListener(new View.OnClickListener() {
								@Override public void onClick(View v) { loadFileList(behDir); }
							});
						parent.addView(btnBeh);
						addGap(parent, 4);

						// 行为包下的配方目录 netease_recipes
						File recipesDir = new File(f, "netease_recipes");
						if (recipesDir.exists() && recipesDir.isDirectory()) {
							OreButton btnRecipes = new OreButton(this);
							btnRecipes.setText("配方");
							btnRecipes.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
							final File rDir = recipesDir;
							btnRecipes.setOnClickListener(new View.OnClickListener() {
									@Override public void onClick(View v) { loadFileList(rDir); }
								});
							parent.addView(btnRecipes);
							addGap(parent, 4);
						}

						// 行为包下的脚本目录 Script_NeteaseMod*
						File[] behSubs = f.listFiles();
						if (behSubs != null) {
							for (File sub : behSubs) {
								if (sub.isDirectory() && sub.getName().startsWith("Script_NeteaseMod")) {
									OreButton btnScript = new OreButton(this);
									btnScript.setText("脚本");
									btnScript.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
									final File sDir = sub;
									btnScript.setOnClickListener(new View.OnClickListener() {
											@Override public void onClick(View v) { loadFileList(sDir); }
										});
									parent.addView(btnScript);
									addGap(parent, 4);
									break; // 只显示第一个脚本目录
								}
							}
						}
					} else if (name.startsWith("resource_pack_")) {
						// 资源包文件夹
						OreButton btnRes = new OreButton(this);
						btnRes.setText("资源包");
						btnRes.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
						final File resDir = f;
						btnRes.setOnClickListener(new View.OnClickListener() {
								@Override public void onClick(View v) { loadFileList(resDir); }
							});
						parent.addView(btnRes);
						addGap(parent, 4);

						// 资源包下的纹理目录 textures
						File texDir = new File(f, "textures");
						if (texDir.exists() && texDir.isDirectory()) {
							OreButton btnTex = new OreButton(this);
							btnTex.setText("纹理");
							btnTex.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
							final File tDir = texDir;
							btnTex.setOnClickListener(new View.OnClickListener() {
									@Override public void onClick(View v) { loadFileList(tDir); }
								});
							parent.addView(btnTex);
							addGap(parent, 4);
						}
					}
				}
			}
		}

		// 固定添加快捷入口：清单文件、配置
		OreButton btnManifest = new OreButton(this);
		btnManifest.setText("清单文件");
		btnManifest.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
		btnManifest.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { jumpToCategory("清单文件"); }
			});
		parent.addView(btnManifest);
		addGap(parent, 4);

		OreButton btnConfig = new OreButton(this);
		btnConfig.setText("配置");
		btnConfig.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
		btnConfig.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { jumpToCategory("配置"); }
			});
		parent.addView(btnConfig);
	}

    private void showFileTree(LinearLayout parent) {
        parent.removeAllViews();
        if (rootNode != null && rootNode.children != null) {
            buildTreeView(rootNode, parent);
        } else {
            OreTextView empty = new OreTextView(this);
            empty.setText("无法加载文件树");
            empty.setTextColor(Color.GRAY);
            parent.addView(empty);
        }
    }

    private void buildFocusLeft() {
		leftContainer.removeAllViews();
		File[] rootFiles = projectDir.listFiles();
		if (rootFiles != null) {
			for (File f : rootFiles) {
				String name = f.getName().toLowerCase();
				if (f.isDirectory()) {
					if (name.startsWith("behavior_pack_") || name.startsWith("behaviour_pack_")) {
						// 行为包文件夹
						final File behDir = f;
						OreButton btn = new OreButton(this);
						btn.setText("行为");
						btn.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
						btn.setOnClickListener(new View.OnClickListener() {
								@Override public void onClick(View v) { loadFileList(behDir); }
							});
						leftContainer.addView(btn);
						addGap(leftContainer, 2);

						// 配方目录
						File recipesDir = new File(f, "netease_recipes");
						if (recipesDir.exists() && recipesDir.isDirectory()) {
							final File rDir = recipesDir;
							OreButton btnRecipes = new OreButton(this);
							btnRecipes.setText("配方");
							btnRecipes.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
							btnRecipes.setOnClickListener(new View.OnClickListener() {
									@Override public void onClick(View v) { loadFileList(rDir); }
								});
							leftContainer.addView(btnRecipes);
							addGap(leftContainer, 2);
						}

						// 脚本目录
						File[] behSubs = f.listFiles();
						if (behSubs != null) {
							for (File sub : behSubs) {
								if (sub.isDirectory() && sub.getName().startsWith("Script_NeteaseMod")) {
									final File sDir = sub;
									OreButton btnScript = new OreButton(this);
									btnScript.setText("脚本");
									btnScript.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
									btnScript.setOnClickListener(new View.OnClickListener() {
											@Override public void onClick(View v) { loadFileList(sDir); }
										});
									leftContainer.addView(btnScript);
									addGap(leftContainer, 2);
									break;
								}
							}
						}
					} else if (name.startsWith("resource_pack_")) {
						// 资源包文件夹
						final File resDir = f;
						OreButton btn = new OreButton(this);
						btn.setText("资源");
						btn.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
						btn.setOnClickListener(new View.OnClickListener() {
								@Override public void onClick(View v) { loadFileList(resDir); }
							});
						leftContainer.addView(btn);
						addGap(leftContainer, 2);

						// 纹理目录
						File texDir = new File(f, "textures");
						if (texDir.exists() && texDir.isDirectory()) {
							final File tDir = texDir;
							OreButton btnTex = new OreButton(this);
							btnTex.setText("纹理");
							btnTex.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
							btnTex.setOnClickListener(new View.OnClickListener() {
									@Override public void onClick(View v) { loadFileList(tDir); }
								});
							leftContainer.addView(btnTex);
							addGap(leftContainer, 2);
						}
					}
				}
			}
		}
	}

    private void buildEditLeft() {
        leftContainer.removeAllViews();
        OreTextView placeholder = new OreTextView(this);
        placeholder.setText("已打开文件");
        placeholder.setTextColor(Color.GRAY);
        placeholder.setTextSize(12);
        leftContainer.addView(placeholder);
    }

    private void addFunctionAccordion(LinearLayout panel) {
        OreAccordion funcAccordion = new OreAccordion(this);
        funcAccordion.setTitle("功能");

        LinearLayout funcContent = new LinearLayout(this);
        funcContent.setOrientation(LinearLayout.VERTICAL);
        funcContent.setLayoutParams(new LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        funcContent.setPadding(4, 4, 4, 4);

        OreButton btnExit = new OreButton(this);
        btnExit.setText("退出编辑器");
        btnExit.setStyleSheet(StyleSheet.STYLE_RED);
        btnExit.setLayoutParams(new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        btnExit.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { finish(); }
            });
        funcContent.addView(btnExit);

        addGap(funcContent, 6);

        OreButton btnBackup = new OreButton(this);
        btnBackup.setText("备份当前项目");
        btnBackup.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btnBackup.setLayoutParams(new LinearLayout.LayoutParams(
                                      LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        btnBackup.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { createBackupInEditor(); }
            });
        funcContent.addView(btnBackup);

        funcAccordion.setContentView(funcContent);
        panel.addView(funcAccordion);
    }

    private void createBackupInEditor() {
        if (!projectDir.exists()) {
            Toast.makeText(this, "项目文件夹不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        String backupDirName = projectDir.getName() + "_back";
        File backupContainer = new File(projectDir.getParent(), backupDirName);
        if (!backupContainer.exists()) backupContainer.mkdirs();

        String subName = String.valueOf(System.currentTimeMillis() / 1000);
        File backupSubDir = new File(backupContainer, subName);
        backupSubDir.mkdirs();

        try {
            copyDirectory(projectDir, backupSubDir);
            Toast.makeText(this, "备份成功", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "备份失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void copyDirectory(File source, File dest) throws IOException {
        if (source.isDirectory()) {
            dest.mkdirs();
            File[] children = source.listFiles();
            if (children != null) for (File child : children) copyDirectory(child, new File(dest, child.getName()));
        } else {
            FileInputStream fis = new FileInputStream(source);
            FileOutputStream fos = new FileOutputStream(dest);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) != -1) fos.write(buffer, 0, len);
            fis.close();
            fos.close();
        }
    }

    private FileNode scanDirectory(File dir, boolean isRoot) {
        FileNode node = new FileNode(dir);
        if (!dir.isDirectory()) return node;
        File[] files = dir.listFiles();
        if (files == null) return node;
        for (File f : files) {
            if (f.isDirectory() && f.getName().equals(".mcs")) continue;
            if (f.isDirectory() && f.getName().endsWith("_back")) continue;
            node.children.add(scanDirectory(f, false));
        }
        return node;
    }

    private void loadFileList(File directory) {
        currentDir = directory;
        middleContainer.removeAllViews();

        File[] allFiles = directory.listFiles();
        if (allFiles == null || allFiles.length == 0) {
            OreTextView empty = new OreTextView(this);
            empty.setText("（空）");
            empty.setTextColor(Color.GRAY);
            middleContainer.addView(empty);
            return;
        }

        List<File> folders = new ArrayList<File>();
        List<File> files = new ArrayList<File>();
        for (File f : allFiles) {
            String name = f.getName();
            if (f.isDirectory() && (name.equals(".mcs") || name.endsWith("_back")))
                continue;
            if (f.isDirectory()) folders.add(f);
            else files.add(f);
        }
        final List<File> sorted = new ArrayList<File>();
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
                        listLayout.setLayoutParams(new LinearLayout.LayoutParams(
                                                       LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

                        for (File f : sorted) {
                            OreCard card = createFileCard(f, LinearLayout.LayoutParams.MATCH_PARENT, true);
                            listLayout.addView(card);
                            addGap(listLayout, 6);
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
                    rowsLayout.setLayoutParams(new LinearLayout.LayoutParams(
                                                   LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

                    LinearLayout currentRow = null;
                    for (int i = 0; i < sorted.size(); i++) {
                        if (i % columns == 0) {
                            currentRow = new LinearLayout(EditorActivity.this);
                            currentRow.setOrientation(LinearLayout.HORIZONTAL);
                            currentRow.setLayoutParams(new LinearLayout.LayoutParams(
                                                           LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                            rowsLayout.addView(currentRow);
                        }
                        OreCard card = createFileCard(sorted.get(i), blockWidth, false);
                        currentRow.addView(card);
                    }

                    middleContainer.removeAllViews();
                    middleContainer.addView(rowsLayout);
                }
            });
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
            cardLayout.setLayoutParams(new LinearLayout.LayoutParams(
                                           LinearLayout.LayoutParams.MATCH_PARENT,
                                           LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        boolean isImage = file.getName().toLowerCase().matches(".*\\.(png|jpg|jpeg)$");
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
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
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
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                width, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(0, 0, 8, 8);
            card.setLayoutParams(cardParams);
        }

        card.addView(cardLayout);
        card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
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

    private void openFileInEditor(File file) {
        editorContainer.removeAllViews();

        if (file.getParentFile() != null && file.getParentFile().getName().equals("netease_recipes")
            && file.getName().toLowerCase().endsWith(".json")) {
            buildRecipeEditor(file);
            return;
        }

        String lowerName = file.getName().toLowerCase();
        if (lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            buildImagePreview(file);
            return;
        }

        buildCodeEditor(file);
    }

    private void jumpToCategory(String category) {
        File targetDir = null;
        File[] rootFiles = projectDir.listFiles();

        if ("脚本".equals(category)) {
            if (rootFiles != null) {
                for (File f : rootFiles) {
                    if (f.isDirectory() && f.getName().toLowerCase().startsWith("behavior_pack_")) {
                        File[] behContents = f.listFiles();
                        if (behContents != null) {
                            for (File sub : behContents) {
                                if (sub.isDirectory() && sub.getName().startsWith("Script_NeteaseMod")) {
                                    targetDir = sub;
                                    break;
                                }
                            }
                        }
                        if (targetDir != null) break;
                    }
                }
            }
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
                if (category.equals("行为包") && f.isDirectory() &&
                    (name.startsWith("behavior_pack_") || name.startsWith("behaviour_pack_"))) {
                    targetDir = f; break;
                } else if (category.equals("资源包") && f.isDirectory() && name.startsWith("resource_pack_")) {
                    targetDir = f; break;
                }
            }
        }

        if ("纹理".equals(category)) {
            if (rootFiles != null) {
                for (File f : rootFiles) {
                    if (f.isDirectory() && f.getName().toLowerCase().startsWith("resource_pack_")) {
                        File texDir = new File(f, "textures");
                        if (texDir.exists()) { targetDir = texDir; break; }
                    }
                }
            }
        } else if ("清单文件".equals(category)) {
            middleContainer.removeAllViews();
            if (rootFiles != null) {
                for (File f : rootFiles) {
                    if (f.isDirectory() && f.getName().toLowerCase().startsWith("behavior_pack_"))
                        showSpecificFile(new File(f, "manifest.json"), "行为包 manifest.json");
                    else if (f.isDirectory() && f.getName().toLowerCase().startsWith("resource_pack_"))
                        showSpecificFile(new File(f, "manifest.json"), "资源包 manifest.json");
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

        android.widget.ImageView icon = new android.widget.ImageView(this);
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
                @Override
                public void onClick(View v) { openFileInEditor(file); }
            });
        middleContainer.addView(card);
        addGap(middleContainer, 6);
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
                addGap(parent, 2);
            }
        }
    }

    private void addGap(LinearLayout parent, int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                              LinearLayout.LayoutParams.MATCH_PARENT, h));
        parent.addView(v);
    }

    private void buildImagePreview(final File imageFile) {
        LinearLayout previewLayout = new LinearLayout(this);
        previewLayout.setOrientation(LinearLayout.VERTICAL);
        previewLayout.setLayoutParams(new LinearLayout.LayoutParams(
                                          LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout actionBar = new LinearLayout(this);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);
        actionBar.setPadding(0, 0, 0, 8);

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);

        OreButton btnRename = new OreButton(this);
        btnRename.setText("重命名");
        btnRename.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btnRename.setLayoutParams(btnParams);
        btnRename.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showRenameDialog(imageFile);
                }
            });
        actionBar.addView(btnRename);

        OreButton btnDelete = new OreButton(this);
        btnDelete.setText("删除");
        btnDelete.setStyleSheet(StyleSheet.STYLE_RED);
        btnDelete.setLayoutParams(btnParams);
        btnDelete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showDeleteFileConfirm(imageFile);
                }
            });
        actionBar.addView(btnDelete);

        OreButton btnCopyPath = new OreButton(this);
        btnCopyPath.setText("复制纹理路径");
        btnCopyPath.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btnCopyPath.setLayoutParams(btnParams);
        btnCopyPath.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String fullPath = imageFile.getAbsolutePath();
                    int index = fullPath.indexOf("textures");
                    if (index != -1) {
                        String relativePath = fullPath.substring(index);
                        android.content.ClipboardManager clipboard = 
                            (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        android.content.ClipData clip = android.content.ClipData.newPlainText("path", relativePath);
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(EditorActivity.this, "路径已复制: " + relativePath, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(EditorActivity.this, "未找到 textures 目录", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        actionBar.addView(btnCopyPath);

        previewLayout.addView(actionBar);

        final ImageView imageView = new ImageView(this);
        imageView.setLayoutParams(new LinearLayout.LayoutParams(
                                      LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        imageView.setBackgroundColor(Color.parseColor("#222222"));
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        new Thread(new Runnable() {
                @Override
                public void run() {
                    final Bitmap bitmap = loadBitmapSafe(imageFile, 2048, 2048);
                    runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
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

    private void showDeleteFileConfirm(final File file) {
        OreTextView msg = new OreTextView(this);
        msg.setText("确定要删除文件 \"" + file.getName() + "\" 吗？");
        msg.setTextColor(Color.WHITE);
        msg.setTextSize(14);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        layout.addView(msg);

        OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("删除文件");
        builder.setView(layout);
        builder.setPositiveButton("删除", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (file.delete()) {
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
                @Override
                public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
            });
        builder.show();
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

    private void showImportDialog() {
        OreTextView msgText = new OreTextView(this);
        msgText.setText("选择导入类型");
        msgText.setTextColor(Color.WHITE);
        msgText.setTextSize(14);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        layout.addView(msgText);

        OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("导入文件");
        builder.setView(layout);

        builder.setPositiveButton("导入图片", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    openImagePicker();
                    dialog.dismiss();
                }
            });
        builder.getPositiveButton().setStyleSheet(StyleSheet.STYLE_GREEN);

        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
        builder.getNegativeButton().setStyleSheet(StyleSheet.STYLE_RED);
        builder.show();
    }

    private static final int REQUEST_IMPORT_IMAGE = 2001;

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
            List<Uri> uris = new ArrayList<Uri>();
            if (data.getClipData() != null) {
                ClipData clipData = data.getClipData();
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    uris.add(clipData.getItemAt(i).getUri());
                }
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }

            for (Uri uri : uris) {
                importImageToTextures(uri);
            }
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

            InputStream is = getContentResolver().openInputStream(imageUri);
            FileOutputStream os = new FileOutputStream(destFile);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
            os.close();
            is.close();

            Toast.makeText(this, "图片导入成功", Toast.LENGTH_SHORT).show();
            loadFileList(currentDir);
        } catch (Exception e) {
            Toast.makeText(this, "导入失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private File findTexturesDir() {
        if (projectDir == null) return null;
        File[] rootFiles = projectDir.listFiles();
        if (rootFiles != null) {
            for (File f : rootFiles) {
                if (f.isDirectory() && f.getName().toLowerCase().startsWith("resource_pack_")) {
                    File texDir = new File(f, "textures");
                    if (texDir.exists() && texDir.isDirectory()) {
                        return texDir;
                    }
                }
            }
        }
        return null;
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
        if (name == null) {
            name = uri.getLastPathSegment();
        }
        return name;
    }

    private void showRenameDialog(final File file) {
        final OreEditText input = new OreEditText(this);
        input.setHint("输入新名称");
        input.setText(file.getName());

        OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("重命名文件");
        builder.setView(input);
        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String newName = input.getText().toString().trim();
                    if (newName.isEmpty()) {
                        Toast.makeText(EditorActivity.this, "名称不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    File newFile = new File(file.getParent(), newName);
                    if (newFile.exists()) {
                        Toast.makeText(EditorActivity.this, "文件名已存在", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    boolean success = file.renameTo(newFile);
                    if (success) {
                        Toast.makeText(EditorActivity.this, "重命名成功", Toast.LENGTH_SHORT).show();
                        loadFileList(currentDir);
                        buildImagePreview(newFile);
                    } else {
                        Toast.makeText(EditorActivity.this, "重命名失败", Toast.LENGTH_SHORT).show();
                    }
                    dialog.dismiss();
                }
            });
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
            });
        builder.getNegativeButton().setStyleSheet(StyleSheet.STYLE_RED);
        builder.show();
    }

    private OreEditText codeEditText;
    private String originalContent = "";

    private void buildCodeEditor(final File file, final LinearLayout parent) {
        LinearLayout editorLayout = new LinearLayout(this);
        editorLayout.setOrientation(LinearLayout.VERTICAL);
        editorLayout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

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
                    codeEditText.setSelection(codeEditText.getText().length());
                }
            });
        toolbar.addView(btnReset);

        editorLayout.addView(toolbar);

        codeEditText = new OreEditText(this);
        codeEditText.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        codeEditText.setGravity(Gravity.TOP);
        codeEditText.setTypeface(Typeface.MONOSPACE);
        codeEditText.setTextSize(10);
        codeEditText.setBackgroundColor(Color.parseColor("#2B2B2B"));
        codeEditText.setTextColor(Color.parseColor("#FFFFFF"));
        codeEditText.setHorizontallyScrolling(true);

        String content = loadFileContent(file);
        originalContent = content;
        codeEditText.setText(content);
        codeEditText.setSelection(0);

        String fileName = file.getName().toLowerCase();
        boolean enableHighlight = fileName.endsWith(".json") || fileName.endsWith(".js") ||
            fileName.endsWith(".mcfunction") || fileName.endsWith(".py");
        final boolean highlight = enableHighlight;

        if (highlight) {
            codeEditText.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                    @Override public void afterTextChanged(Editable s) {
                        if (isHighlighting) return;
                        if (highlight) applySyntaxHighlight(s);
                    }
                });
            applySyntaxHighlight(codeEditText.getText());
        }

        editorLayout.addView(codeEditText);
        parent.addView(editorLayout);
    }

    private void buildCodeEditor(final File file) {
        buildCodeEditor(file, editorContainer);
    }

    private String loadFileContent(File file) {
        if (!file.exists()) return "";
        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();
            return new String(data, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private void saveFileContent(File file, String content) {
        try {
            FileWriter fw = new FileWriter(file);
            fw.write(content);
            fw.close();
            originalContent = content;
            Toast.makeText(EditorActivity.this, "文件已保存", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(EditorActivity.this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void applySyntaxHighlight(Editable s) {
        if (isHighlighting) return;
        isHighlighting = true;
        try {
            String text = s.toString();
            int length = text.length();
            SpannableStringBuilder spannable = new SpannableStringBuilder(text);
            spannable.clearSpans();

            boolean inString = false;
            boolean inLineComment = false;
            boolean inBlockComment = false;
            int stringStart = -1;
            int commentStart = -1;
            char stringChar = 0;

            for (int i = 0; i < length; i++) {
                char c = text.charAt(i);
                char prev = i > 0 ? text.charAt(i - 1) : 0;

                if (inBlockComment && c == '/' && prev == '*' && i - 2 >= commentStart) {
                    spannable.setSpan(new ForegroundColorSpan(Color.parseColor("#6A9955")), commentStart, i + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    inBlockComment = false;
                    continue;
                }

                if (!inLineComment && !inBlockComment && (c == '"' || c == '\'') && prev != '\\') {
                    if (!inString) {
                        inString = true;
                        stringStart = i;
                        stringChar = c;
                    } else if (c == stringChar) {
                        inString = false;
                        spannable.setSpan(new ForegroundColorSpan(Color.parseColor("#CE9178")), stringStart, i + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    continue;
                }

                if (!inString && !inBlockComment && c == '#' && (i == 0 || prev != '\\')) {
                    inLineComment = true;
                    commentStart = i;
                    continue;
                }

                if (!inString && !inLineComment && c == '/' && i + 1 < length && text.charAt(i + 1) == '*') {
                    inBlockComment = true;
                    commentStart = i;
                    continue;
                }

                if (inLineComment && c == '\n') {
                    spannable.setSpan(new ForegroundColorSpan(Color.parseColor("#6A9955")), commentStart, i + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    inLineComment = false;
                    continue;
                }

                if (inString || inLineComment || inBlockComment) continue;

                if (Character.isDigit(c) && (i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1)))) {
                    int numStart = i;
                    while (i < length && (Character.isDigit(text.charAt(i)) || text.charAt(i) == '.')) i++;
                    spannable.setSpan(new ForegroundColorSpan(Color.parseColor("#B5CEA8")), numStart, i, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i--;
                    continue;
                }

                String[] keywords = {
                    "true", "false", "null",
                    "def", "class", "import", "from", "return", "pass",
                    "if", "else", "elif", "for", "while", "in", "not", "and", "or",
                    "try", "except", "finally", "with", "as", "yield", "lambda"
                };
                for (String kw : keywords) {
                    if (i + kw.length() <= length && text.startsWith(kw, i) &&
                        (i == 0 || !Character.isLetter(text.charAt(i - 1))) &&
                        (i + kw.length() == length || !Character.isLetterOrDigit(text.charAt(i + kw.length())))) {
                        spannable.setSpan(new ForegroundColorSpan(Color.parseColor("#569CD6")), i, i + kw.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        i += kw.length() - 1;
                        break;
                    }
                }
            }

            if (inLineComment && commentStart >= 0)
                spannable.setSpan(new ForegroundColorSpan(Color.parseColor("#6A9955")), commentStart, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            else if (inBlockComment && commentStart >= 0)
                spannable.setSpan(new ForegroundColorSpan(Color.parseColor("#6A9955")), commentStart, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            else if (inString && stringStart >= 0)
                spannable.setSpan(new ForegroundColorSpan(Color.parseColor("#CE9178")), stringStart, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            s.replace(0, length, spannable);
        } finally {
            isHighlighting = false;
        }
    }

    private void showNewItemDialog() {
        OreTextView msg = new OreTextView(this);
        msg.setText("新建类型");
        msg.setTextColor(Color.WHITE);
        msg.setTextSize(14);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        layout.addView(msg);

        OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("新建");
        builder.setView(layout);

        builder.setPositiveButton("新建文件", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Toast.makeText(EditorActivity.this, "新建文件功能开发中", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }
            });
        builder.getPositiveButton().setStyleSheet(StyleSheet.STYLE_GREEN);

        builder.setNegativeButton("新建配方", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    showNewRecipeDialog();
                    dialog.dismiss();
                }
            });

        builder.setNeutralButton("取消", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
            });

        builder.show();
    }

    private void showNewRecipeDialog() {
        final OreEditText inputName = new OreEditText(this);
        inputName.setHint("配方名称（英文）");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        layout.addView(inputName);
        addGap(layout, 12);

        final String[] types = {"有序合成", "无序合成", "熔炉", "酿造", "锻造"};
        final String[] typeIds = {"recipe_shaped", "recipe_shapeless", "recipe_furnace", "recipe_brewing_mix", "recipe_smithing_transform"};

        final OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("新建配方");
        builder.setView(layout);

        // 用一个数组来持有 Dialog 对象，方便在内部类中访问
        final android.app.Dialog[] dialogRef = new android.app.Dialog[1];

        for (int i = 0; i < types.length; i++) {
            final int index = i;
            OreButton typeBtn = new OreButton(this);
            typeBtn.setText(types[i]);
            typeBtn.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
            typeBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String name = inputName.getText().toString().trim();
                        if (name.isEmpty()) {
                            Toast.makeText(EditorActivity.this, "名称不能为空", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String namespace = "test";
                        if (projectDir != null) {
                            File studioFile = new File(projectDir, "studio.json");
                            if (studioFile.exists()) {
                                try {
                                    FileInputStream fis = new FileInputStream(studioFile);
                                    byte[] data = new byte[(int) studioFile.length()];
                                    fis.read(data); fis.close();
                                    String content = new String(data, "UTF-8");
                                    JSONObject obj = new JSONObject(content);
                                    namespace = obj.optString("namespace", "test");
                                } catch (Exception ignored) {}
                            }
                        }
                        // 关闭对话框
                        if (dialogRef[0] != null) {
                            dialogRef[0].dismiss();
                        }
                        createNewRecipe(name, namespace, typeIds[index]);
                    }
                });
            layout.addView(typeBtn);
            addGap(layout, 6);
        }

        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
            });
        builder.getNegativeButton().setStyleSheet(StyleSheet.STYLE_RED);

        // 显示并保存 Dialog 引用
        dialogRef[0] = builder.show();
    }

    private void createNewRecipe(String name, String namespace, String recipeType) {
        File behDir = null;
        File[] rootFiles = projectDir.listFiles();
        if (rootFiles != null) {
            for (File f : rootFiles) {
                if (f.isDirectory() && f.getName().startsWith("behavior_pack_")) {
                    behDir = f; break;
                }
            }
        }
        if (behDir == null) { Toast.makeText(this, "未找到行为包", Toast.LENGTH_SHORT).show(); return; }
        File recipesDir = new File(behDir, "netease_recipes");
        if (!recipesDir.exists()) recipesDir.mkdirs();

        String fileName = namespace + "_" + name + ".json";
        File recipeFile = new File(recipesDir, fileName);
        if (recipeFile.exists()) { Toast.makeText(this, "配方已存在", Toast.LENGTH_SHORT).show(); return; }

        String template = "";
        if (recipeType.equals("recipe_shaped")) {
            template = "{\n  \"format_version\": \"1.20.10\",\n  \"minecraft:recipe_shaped\": {\n    \"description\": {\"identifier\": \"" + namespace + ":" + name + "\"},\n    \"key\": {},\n    \"pattern\": [],\n    \"result\": {\"count\": 1, \"item\": \"minecraft:apple\"},\n    \"tags\": [\"crafting_table\"],\n    \"unlock\": {\"context\": \"AlwaysUnlocked\"}\n  }\n}";
        } else if (recipeType.equals("recipe_shapeless")) {
            template = "{\n  \"format_version\": \"1.20.10\",\n  \"minecraft:recipe_shapeless\": {\n    \"description\": {\"identifier\": \"" + namespace + ":" + name + "\"},\n    \"ingredients\": [],\n    \"result\": {\"count\": 1, \"item\": \"minecraft:apple\"},\n    \"tags\": [\"crafting_table\"],\n    \"unlock\": {\"context\": \"AlwaysUnlocked\"}\n  }\n}";
        } else if (recipeType.equals("recipe_furnace")) {
            template = "{\n  \"format_version\": \"1.20.10\",\n  \"minecraft:recipe_furnace\": {\n    \"description\": {\"identifier\": \"" + namespace + ":" + name + "\"},\n    \"input\": {\"count\": 1, \"item\": \"minecraft:apple\"},\n    \"output\": {\"count\": 1, \"item\": \"minecraft:apple\"},\n    \"tags\": [\"furnace\"],\n    \"unlock\": {\"context\": \"AlwaysUnlocked\"}\n  }\n}";
        } else if (recipeType.equals("recipe_brewing_mix")) {
            template = "{\n  \"format_version\": \"1.20.10\",\n  \"minecraft:recipe_brewing_mix\": {\n    \"description\": {\"identifier\": \"" + namespace + ":" + name + "\"},\n    \"input\": \"minecraft:potion:4\",\n    \"output\": \"minecraft:potion:31\",\n    \"reagent\": \"minecraft:blaze_powder:0\",\n    \"tags\": [\"brewing_stand\"]\n  }\n}";
        } else if (recipeType.equals("recipe_smithing_transform")) {
            template = "{\n  \"format_version\": \"1.20.10\",\n  \"minecraft:recipe_smithing_transform\": {\n    \"description\": {\"identifier\": \"" + namespace + ":" + name + "\"},\n    \"template\": \"minecraft:netherite_upgrade_smithing_template\",\n    \"base\": \"minecraft:diamond_boots\",\n    \"addition\": \"minecraft:netherite_ingot\",\n    \"result\": \"minecraft:netherite_boots\",\n    \"tags\": [\"smithing_table\"]\n  }\n}";
        }

        try {
            FileWriter fw = new FileWriter(recipeFile);
            fw.write(template);
            fw.close();
            Toast.makeText(this, "配方创建成功", Toast.LENGTH_SHORT).show();
            loadFileList(recipesDir);
        } catch (IOException e) { Toast.makeText(this, "创建失败", Toast.LENGTH_SHORT).show(); }
    }

    private boolean recipeDirty = false;

    private void buildRecipeEditor(final File recipeFile) {
		editorContainer.removeAllViews();

		LinearLayout modeBar = new LinearLayout(this);
		modeBar.setOrientation(LinearLayout.HORIZONTAL);
		modeBar.setPadding(0, 0, 0, 8);

		final OreButton btnVisual = new OreButton(this);
		btnVisual.setText("可视化编辑");
		btnVisual.setStyleSheet(StyleSheet.STYLE_GREEN);
		final OreButton btnSource = new OreButton(this);
		btnSource.setText("编辑源码");
		btnSource.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);

		// 删除按钮
		final OreButton btnDelete = new OreButton(this);
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

		final LinearLayout contentArea = new LinearLayout(this);
		contentArea.setOrientation(LinearLayout.VERTICAL);
		contentArea.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
		editorContainer.addView(contentArea);

		showRecipeVisualEditor(contentArea, recipeFile);

		btnVisual.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					btnVisual.setStyleSheet(StyleSheet.STYLE_GREEN);
					btnSource.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
					contentArea.removeAllViews();
					showRecipeVisualEditor(contentArea, recipeFile);
				}
			});
		btnSource.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					btnSource.setStyleSheet(StyleSheet.STYLE_GREEN);
					btnVisual.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
					contentArea.removeAllViews();
					buildCodeEditor(recipeFile, contentArea);
				}
			});
		btnDelete.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					showDeleteRecipeConfirm(recipeFile);
				}
			});
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

    private void showRecipeVisualEditor(LinearLayout container, final File recipeFile) {
		currentRecipeFile = recipeFile;
		recipeContentArea = container;

		ScrollView scroll = new ScrollView(this);
		LinearLayout layout = new LinearLayout(this);
		layout.setOrientation(LinearLayout.VERTICAL);
		layout.setPadding(8, 8, 8, 8);

		// 读取 JSON
		String jsonStr = loadFileContent(recipeFile);
		JSONObject root = null;
		try {
			root = new JSONObject(jsonStr);
		} catch (Exception e) {
			e.printStackTrace();
			OreTextView errText = new OreTextView(this);
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
		if (root.has("minecraft:recipe_shaped")) {
			recipeType = "recipe_shaped";
			body = root.optJSONObject("minecraft:recipe_shaped");
		} else if (root.has("minecraft:recipe_shapeless")) {
			recipeType = "recipe_shapeless";
			body = root.optJSONObject("minecraft:recipe_shapeless");
		} else if (root.has("minecraft:recipe_furnace")) {
			recipeType = "recipe_furnace";
			body = root.optJSONObject("minecraft:recipe_furnace");
		} else if (root.has("minecraft:recipe_brewing_mix")) {
			recipeType = "recipe_brewing_mix";
			body = root.optJSONObject("minecraft:recipe_brewing_mix");
		} else if (root.has("minecraft:recipe_smithing_transform")) {
			recipeType = "recipe_smithing_transform";
			body = root.optJSONObject("minecraft:recipe_smithing_transform");
		}
		currentRecipeType = recipeType;
		if (body == null) {
			OreTextView errText = new OreTextView(this);
			errText.setText("不支持的配方类型");
			errText.setTextColor(Color.WHITE);
			layout.addView(errText);
			scroll.addView(layout);
			container.addView(scroll);
			currentRecipeRoot = null;
			return;
		}

		// 类型与名称
		OreTextView typeLabel = new OreTextView(this);
		String typeDisplay = recipeType.equals("recipe_shaped") ? "有序合成"
            : recipeType.equals("recipe_shapeless") ? "无序合成"
            : recipeType.equals("recipe_furnace") ? "熔炉"
            : recipeType.equals("recipe_brewing_mix") ? "酿造" : "锻造";
		typeLabel.setText("类型: " + typeDisplay);
		typeLabel.setTextColor(Color.WHITE);
		layout.addView(typeLabel);

		String identifier = body.optJSONObject("description").optString("identifier", "unknown");
		String localName = identifier.contains(":") ? identifier.split(":")[1] : identifier;
		OreTextView nameText = new OreTextView(this);
		nameText.setText("名称: " + localName);
		nameText.setTextColor(Color.WHITE);
		layout.addView(nameText);

		// ★ 解锁方式解析提前（防止自动保存覆盖）
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

		LinearLayout tagGroup = new LinearLayout(this);
		tagGroup.setOrientation(LinearLayout.HORIZONTAL);
		String[] availableTags = {};
		if (recipeType.equals("recipe_shaped"))
			availableTags = new String[]{"crafting_table"};
		else if (recipeType.equals("recipe_shapeless"))
			availableTags = new String[]{"crafting_table", "stonecutter", "cartography_table"};
		else if (recipeType.equals("recipe_furnace"))
			availableTags = new String[]{"furnace", "blast_furnace", "smoker", "campfire"};
		else if (recipeType.equals("recipe_brewing_mix"))
			availableTags = new String[]{"brewing_stand"};
		else if (recipeType.equals("recipe_smithing_transform"))
			availableTags = new String[]{"smithing_table"};

		for (String tag : availableTags) {
			LinearLayout tagRow = new LinearLayout(this);
			tagRow.setOrientation(LinearLayout.HORIZONTAL);
			tagRow.setGravity(Gravity.CENTER_VERTICAL);
			final OreSwitch sw = new OreSwitch(this);
			sw.setChecked(selectedTags.contains(tag));
			final String finalTag = tag;
			sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
						if (selectedTags == null) return;
						if (isChecked) selectedTags.add(finalTag);
						else selectedTags.remove(finalTag);
						autoSaveRecipe();
					}
				});
			tagRow.addView(sw);
			OreTextView tagLabel = new OreTextView(this);
			tagLabel.setText(getTagDisplayName(tag));
			tagLabel.setTextColor(Color.WHITE);
			tagLabel.setTextSize(10);
			tagLabel.setPadding(4, 0, 8, 0);
			tagRow.addView(tagLabel);
			tagGroup.addView(tagRow);
		}
		layout.addView(tagGroup);

		// ===== 无序合成输入物品 =====
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
			OreTextView inputLabel = new OreTextView(this);
			inputLabel.setText("输入物品");
			inputLabel.setTextColor(Color.WHITE);
			inputLabel.setTextSize(12);
			layout.addView(inputLabel);
			buildShapelessInputUI(layout);
		}

		// ===== 有序合成专属：3×3 网格 + 映射列表 =====
		if (recipeType.equals("recipe_shaped")) {
			recipeGrid = new GridLayout(this);
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
					final OreButton cell = new OreButton(this);
					cell.setText(letter);
					cell.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
					final int r = row, c = col;
					cell.setOnClickListener(new View.OnClickListener() {
							@Override public void onClick(View v) { showLetterInputDialog(cell); }
						});
					recipeGrid.addView(cell, new GridLayout.LayoutParams(
										   GridLayout.spec(row), GridLayout.spec(col)));
				}
			}
			layout.addView(recipeGrid);
			recipeMappingLayout = new LinearLayout(this);
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

		// 熔炉输入物品
		if (recipeType.equals("recipe_furnace")) {
		furnaceInputs.clear();
		JSONObject inputObj = body.optJSONObject("input");
		if (inputObj != null) {
        String itemStr = inputObj.optString("item", "");
        int data = inputObj.optInt("data", -1);
        if (data >= 0) itemStr = itemStr + ":" + data;
        furnaceInputs.add(itemStr);
		}
		if (furnaceInputs.isEmpty()) {
        furnaceInputs.add("minecraft:apple");
		}
		OreTextView furnaceInputLabel = new OreTextView(this);
		furnaceInputLabel.setText("熔炉输入");
		furnaceInputLabel.setTextColor(Color.WHITE);
			furnaceInputLabel.setTextSize(12);
			layout.addView(furnaceInputLabel);
			buildFurnaceInputUI(layout);
		}
		
		// 酿造配方专属：输入药水、试剂、输出药水
		if (recipeType.equals("recipe_brewing_mix")) {
		// 输入药水
		OreTextView brewInputLabel = new OreTextView(this);
		brewInputLabel.setText("输入药水");
		brewInputLabel.setTextColor(Color.WHITE);
		brewInputLabel.setTextSize(12);
		layout.addView(brewInputLabel);

		brewingInputItem = new OreEditText(this);
		brewingInputItem.setHint("输入药水 (如 minecraft:potion:0)");
		String inputVal = body.optString("input", "");
		brewingInputItem.setText(inputVal);
		brewingInputItem.addTextChangedListener(new TextWatcher() {
        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
			@Override public void afterTextChanged(Editable s) { autoSaveRecipe(); }
			});
			layout.addView(brewingInputItem);

		// 试剂
		OreTextView brewReagentLabel = new OreTextView(this);
		brewReagentLabel.setText("试剂");
		brewReagentLabel.setTextColor(Color.WHITE);
			brewReagentLabel.setTextSize(12);
			layout.addView(brewReagentLabel);

				brewingReagentItem = new OreEditText(this);
			brewingReagentItem.setHint("试剂 (如 minecraft:blaze_powder:0)");
		String reagentVal = body.optString("reagent", "");
			brewingReagentItem.setText(reagentVal);
			brewingReagentItem.addTextChangedListener(new TextWatcher() {
        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
			@Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
			@Override public void afterTextChanged(Editable s) { autoSaveRecipe(); }
		});
		layout.addView(brewingReagentItem);
		}
		
		// 锻造配方专属：模板、基底、添加物
		if (recipeType.equals("recipe_smithing_transform")) {
			// 提示（使用库中的 OreAlert）
			OreAlert smithingWarning = new OreAlert(this);
			smithingWarning.setText("由于不知名原因，锻造配方可能无法正常使用");
			smithingWarning.setStyleSheet(StyleSheet.STYLE_ALERT_YELLOW);
			layout.addView(smithingWarning);

			// 模板
			OreTextView templateLabel = new OreTextView(this);
			templateLabel.setText("锻造模板");
			templateLabel.setTextColor(Color.WHITE);
			templateLabel.setTextSize(12);
			layout.addView(templateLabel);

			smithingTemplateItem = new OreEditText(this);
			smithingTemplateItem.setHint("模板 (如 minecraft:netherite_upgrade_smithing_template)");
			String templateVal = body.optString("template", "");
			smithingTemplateItem.setText(templateVal);
			smithingTemplateItem.addTextChangedListener(new TextWatcher() {
					@Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
					@Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
					@Override public void afterTextChanged(Editable s) { autoSaveRecipe(); }
				});
			layout.addView(smithingTemplateItem);

			// 基底
			OreTextView baseLabel = new OreTextView(this);
			baseLabel.setText("基底");
			baseLabel.setTextColor(Color.WHITE);
			baseLabel.setTextSize(12);
			layout.addView(baseLabel);

			smithingBaseItem = new OreEditText(this);
			smithingBaseItem.setHint("基底 (如 minecraft:diamond_boots)");
			String baseVal = body.optString("base", "");
			smithingBaseItem.setText(baseVal);
			smithingBaseItem.addTextChangedListener(new TextWatcher() {
					@Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
					@Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
					@Override public void afterTextChanged(Editable s) { autoSaveRecipe(); }
				});
			layout.addView(smithingBaseItem);

			// 添加物
			OreTextView additionLabel = new OreTextView(this);
			additionLabel.setText("添加物");
			additionLabel.setTextColor(Color.WHITE);
			additionLabel.setTextSize(12);
			layout.addView(additionLabel);

			smithingAdditionItem = new OreEditText(this);
			smithingAdditionItem.setHint("添加物 (如 minecraft:netherite_ingot)");
			String additionVal = body.optString("addition", "");
			smithingAdditionItem.setText(additionVal);
			smithingAdditionItem.addTextChangedListener(new TextWatcher() {
					@Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
					@Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
					@Override public void afterTextChanged(Editable s) { autoSaveRecipe(); }
				});
			layout.addView(smithingAdditionItem);
		}
		
		// 产出物品
		OreTextView resultLabel = new OreTextView(this);
		resultLabel.setText("产出物品");
		resultLabel.setTextColor(Color.WHITE);
		resultLabel.setTextSize(12);
		layout.addView(resultLabel);

		LinearLayout resultLayout = new LinearLayout(this);
		resultLayout.setOrientation(LinearLayout.HORIZONTAL);

		visualResultItem = new OreEditText(this);
		visualResultItem.setHint("产出物品ID");
		visualResultCount = new OreEditText(this);
		visualResultCount.setHint("数量");

		TextWatcher resultWatcher = new TextWatcher() {
			@Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
			@Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
			@Override public void afterTextChanged(Editable s) { autoSaveRecipe(); }
				};
			visualResultItem.addTextChangedListener(resultWatcher);
		visualResultCount.addTextChangedListener(resultWatcher);

		if (recipeType.equals("recipe_shaped") || recipeType.equals("recipe_shapeless") || recipeType.equals("recipe_furnace")) {
		JSONObject resultObj = body.optJSONObject("result");
		if (resultObj != null) {
			visualResultItem.setText(resultObj.optString("item", ""));
			visualResultCount.setText(String.valueOf(resultObj.optInt("count", 1)));
				}
				visualResultCount.setVisibility(View.VISIBLE);
			} else if (recipeType.equals("recipe_brewing_mix")) {
			String out = body.optString("output", "");
		visualResultItem.setText(out);
			visualResultItem.setHint("输出药水 (如 minecraft:potion:31)");
			visualResultCount.setVisibility(View.GONE);
			} else if (recipeType.equals("recipe_smithing_transform")) {
			String res = body.optString("result", "");
		visualResultItem.setText(res);
			visualResultItem.setHint("产物 (如 minecraft:netherite_boots)");
			visualResultCount.setVisibility(View.GONE);
			}
			resultLayout.addView(visualResultItem);
			resultLayout.addView(visualResultCount);
			layout.addView(resultLayout);

		// 解锁方式按钮与物品列表
		OreButton unlockSelectBtn = new OreButton(this);
		unlockSelectBtn.setText(getUnlockDisplayName(currentUnlockContext));
		unlockSelectBtn.setTag("unlock_select_btn");
		unlockSelectBtn.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
		unlockSelectBtn.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { showUnlockSelectDialog(); }
			});
		layout.addView(unlockSelectBtn);
		if (currentUnlockContext.equals("PlayerHasManyItems")) {
			buildUnlockItemsUI(layout);
		}

		scroll.addView(layout);
		container.addView(scroll);
	}

    private void updateRecipeFromGrid() {
		if (currentRecipeRoot == null || currentRecipeType == null) return;
		try {
			JSONObject body;
			if (!currentRecipeRoot.has("minecraft:" + currentRecipeType)) return;
			body = currentRecipeRoot.getJSONObject("minecraft:" + currentRecipeType);

			// 1. 有序合成
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

			// 无序合成：输入物品
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
								} catch (NumberFormatException ignored) {}
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
							} catch (NumberFormatException ignored) {}
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

			// 2. tags
			JSONArray tagsArray = new JSONArray();
			if (selectedTags != null) for (String tag : selectedTags) tagsArray.put(tag);
			body.put("tags", tagsArray);

			// 3. 产出 + 酿造 / 锻造特有字段
			if (currentRecipeType.equals("recipe_brewing_mix")) {
				// 输入药水
				if (brewingInputItem != null) {
					String inputVal = brewingInputItem.getText().toString().trim();
					if (!inputVal.isEmpty()) body.put("input", inputVal);
					else body.remove("input");
				}
				// 试剂
				if (brewingReagentItem != null) {
					String reagentVal = brewingReagentItem.getText().toString().trim();
					if (!reagentVal.isEmpty()) body.put("reagent", reagentVal);
					else body.remove("reagent");
				}
				// 输出药水
				if (visualResultItem != null) {
					String outVal = visualResultItem.getText().toString().trim();
					if (!outVal.isEmpty()) body.put("output", outVal);
					else body.remove("output");
				}
			} else if (currentRecipeType.equals("recipe_smithing_transform")) {
				// 产物
				if (visualResultItem != null) {
					String resultVal = visualResultItem.getText().toString().trim();
					if (!resultVal.isEmpty()) body.put("result", resultVal);
					else body.remove("result");
				}
				// 模板
				if (smithingTemplateItem != null) {
					String val = smithingTemplateItem.getText().toString().trim();
					if (!val.isEmpty()) body.put("template", val);
					else body.remove("template");
				}
				// 基底
				if (smithingBaseItem != null) {
					String val = smithingBaseItem.getText().toString().trim();
					if (!val.isEmpty()) body.put("base", val);
					else body.remove("base");
				}
				// 添加物
				if (smithingAdditionItem != null) {
					String val = smithingAdditionItem.getText().toString().trim();
					if (!val.isEmpty()) body.put("addition", val);
					else body.remove("addition");
				}
			} else if (visualResultItem != null && visualResultCount != null) {
				String item = visualResultItem.getText().toString().trim();
				String countStr = visualResultCount.getText().toString().trim();
				int count = 1;
				try { count = Integer.parseInt(countStr); } catch (NumberFormatException ignored) {}

				if (currentRecipeType.equals("recipe_shaped") || currentRecipeType.equals("recipe_shapeless")
                    || currentRecipeType.equals("recipe_furnace")) {
					JSONObject result = new JSONObject();
					result.put("item", item);
					result.put("count", count);
					body.put("result", result);
				}
			}

			// 4. 解锁
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
									} catch (NumberFormatException ignored) {}
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
            FileWriter fw = new FileWriter(file);
            fw.write(recipeJson.toString(2));
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (org.json.JSONException e) {
            e.printStackTrace();
        }
    }

    private void addMappingRow(LinearLayout mappingLayout, String letter, JSONObject itemObj) {
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 4, 0, 4);

        final OreEditText letterEdit = new OreEditText(this);
        letterEdit.setHint("字母");
        if (letter != null) letterEdit.setText(letter);
        letterEdit.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(letterEdit);

        final OreEditText itemEdit = new OreEditText(this);
        itemEdit.setHint("物品ID");     // 缩短提示文字
        if (itemObj != null) itemEdit.setText(itemObj.optString("item", ""));
        itemEdit.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f));
        row.addView(itemEdit);

        OreButton deleteBtn = new OreButton(this);
        deleteBtn.setText("×");
        deleteBtn.setStyleSheet(StyleSheet.STYLE_RED);
        deleteBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    View parent = (View) row.getParent();
                    if (parent instanceof LinearLayout) {
                        ((LinearLayout) parent).removeView(row);
                    }
                    autoSaveRecipe();
                }
            });
        row.addView(deleteBtn);

        TextWatcher tw = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                autoSaveRecipe();
            }
        };
        letterEdit.addTextChangedListener(tw);
        itemEdit.addTextChangedListener(tw);

        mappingLayout.addView(row);
    }

    private void autoSaveRecipe() {
        if (currentRecipeFile == null || currentRecipeRoot == null) return;
        updateRecipeFromGrid();
        saveRecipeToFile(currentRecipeFile, currentRecipeRoot);
    }
    
    /**
     * 检查字母是否已存在映射行，若不存在则自动添加一行（物品为空）
     */
    private void ensureMappingExists(String letter) {
        if (recipeMappingLayout == null || letter == null || letter.trim().isEmpty()) return;

        // 防止重复添加
        for (int i = 0; i < recipeMappingLayout.getChildCount(); i++) {
            View child = recipeMappingLayout.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout row = (LinearLayout) child;
                if (row.getChildCount() > 0) {
                    OreEditText letterEdit = (OreEditText) row.getChildAt(0);
                    if (letterEdit != null && letter.equals(letterEdit.getText().toString().trim())) {
                        return;   // 已存在
                    }
                }
            }
        }

        // 不存在则添加
        addMappingRow(recipeMappingLayout, letter, null);
        // 注意：addMappingRow 内部会为 letterEdit 设置文本 letter，这里不再需要额外处理
    }
    
    private void showUnlockSelectDialog() {
		final String[] contexts = {"AlwaysUnlocked", "PlayerHasManyItems", "PlayerInWater"};
		final String[] chineseNames = new String[contexts.length];
		for (int i = 0; i < contexts.length; i++) {
			chineseNames[i] = getUnlockDisplayName(contexts[i]);
		}

		LinearLayout layout = new LinearLayout(this);
		layout.setOrientation(LinearLayout.VERTICAL);
		int pad = (int) (12 * getResources().getDisplayMetrics().density);
		layout.setPadding(pad, pad, pad, pad);

		final OreDialogBuilder builder = new OreDialogBuilder(this);
		builder.setTitle("选择解锁方式");
		builder.setView(layout);
		final android.app.Dialog[] dialogRef = new android.app.Dialog[1];

		for (int i = 0; i < contexts.length; i++) {
			final int index = i;
			OreButton btn = new OreButton(this);
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
							autoSaveRecipe();          // 保存到文件

							// 动态刷新解锁区域，不重建整个界面
							View unlockBtn = recipeContentArea.findViewWithTag("unlock_select_btn");
							if (unlockBtn instanceof OreButton) {
								((OreButton) unlockBtn).setText(getUnlockDisplayName(currentUnlockContext));
							}
							// 移除旧的物品编辑容器
							View oldItems = recipeContentArea.findViewWithTag("unlock_items_container");
							if (oldItems != null && recipeContentArea instanceof android.view.ViewGroup) {
								((android.view.ViewGroup) recipeContentArea).removeView(oldItems);
							}
							// 如果是物品解锁，重新构建物品列表
							if (currentUnlockContext.equals("PlayerHasManyItems") && recipeContentArea instanceof LinearLayout) {
								buildUnlockItemsUI((LinearLayout) recipeContentArea);
							}
						}
						if (dialogRef[0] != null) dialogRef[0].dismiss();
					}
				});
			layout.addView(btn);
			addGap(layout, 6);
		}

		builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
			});
		dialogRef[0] = builder.show();
	}

	private void buildUnlockItemsUI(final LinearLayout parent) {
		// 先移除旧的物品编辑区域（通过 tag 标识）
		View oldContainer = parent.findViewWithTag("unlock_items_container");
		if (oldContainer != null) {
			parent.removeView(oldContainer);
		}

		LinearLayout container = new LinearLayout(this);
		container.setOrientation(LinearLayout.VERTICAL);
		container.setTag("unlock_items_container");

		for (int i = 0; i < unlockItems.size(); i++) {
			final int index = i;
			String itemStr = unlockItems.get(i);

			final OreEditText itemEdit = new OreEditText(this);
			itemEdit.setHint("物品ID");
			itemEdit.setText(itemStr);

			// 删除按钮：只移除自己的行，然后局部刷新
			OreButton delBtn = new OreButton(this);
			delBtn.setText("×");
			delBtn.setStyleSheet(StyleSheet.STYLE_RED);
			delBtn.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						unlockItems.remove(index);
						autoSaveRecipe();           // 先保存
						// 局部刷新整个物品列表（不重建配方视图）
						buildUnlockItemsUI(parent);
					}
				});

			LinearLayout row = new LinearLayout(this);
			row.setOrientation(LinearLayout.HORIZONTAL);
			LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
			itemEdit.setLayoutParams(itemParams);
			row.addView(itemEdit);
			row.addView(delBtn);
			container.addView(row);
			addGap(container, 4);

			// 文本变化监听：只更新数据并保存，不刷新界面（避免输入时跳动）
			itemEdit.addTextChangedListener(new TextWatcher() {
					@Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
					@Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
					@Override public void afterTextChanged(Editable s) {
						if (index < unlockItems.size()) {
							unlockItems.set(index, s.toString().trim());
						}
						autoSaveRecipe();
					}
				});
		}

		// 添加按钮：添加默认物品，保存后局部刷新
		OreButton addBtn = new OreButton(this);
		addBtn.setText("+ 添加物品");
		addBtn.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
		addBtn.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					unlockItems.add("minecraft:apple");
					autoSaveRecipe();
					buildUnlockItemsUI(parent);   // 只有物品列表区域重建
				}
			});
		container.addView(addBtn);

		parent.addView(container);
	}
	
	private void showLetterInputDialog(final OreButton cell) {
		final OreEditText letterInput = new OreEditText(this);
		letterInput.setHint("输入字母 (A-Z)");
		letterInput.setText(cell.getText().toString());

		OreDialogBuilder builder = new OreDialogBuilder(this);
		builder.setTitle("输入字母");
		builder.setView(letterInput);
		builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					String letter = letterInput.getText().toString().trim().toUpperCase();
					if (letter.length() == 1 && letter.charAt(0) >= 'A' && letter.charAt(0) <= 'Z') {
						cell.setText(letter);
						ensureMappingExists(letter);
						autoSaveRecipe();
					} else if (letter.isEmpty()) {
						cell.setText("");
						autoSaveRecipe();
					} else {
						Toast.makeText(EditorActivity.this, "请输入单个字母", Toast.LENGTH_SHORT).show();
					}
					dialog.dismiss();
				}
			});
		builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
			});
		builder.show();
	}
	
	private void buildShapelessInputUI(final LinearLayout parent) {
		// 移除旧的输入物品容器
		View oldContainer = parent.findViewWithTag("shapeless_inputs_container");
		if (oldContainer != null) {
			parent.removeView(oldContainer);
		}

		LinearLayout container = new LinearLayout(this);
		container.setOrientation(LinearLayout.VERTICAL);
		container.setTag("shapeless_inputs_container");

		for (int i = 0; i < shapelessInputs.size(); i++) {
			final int index = i;
			String itemStr = shapelessInputs.get(i);

			final OreEditText itemEdit = new OreEditText(this);
			itemEdit.setHint("物品ID");
			itemEdit.setText(itemStr);

			OreButton delBtn = new OreButton(this);
			delBtn.setText("×");
			delBtn.setStyleSheet(StyleSheet.STYLE_RED);
			delBtn.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						shapelessInputs.remove(index);
						autoSaveRecipe();
						// 重建整个配方视图（安全刷新）
						if (recipeContentArea != null && currentRecipeFile != null) {
							recipeContentArea.removeAllViews();
							showRecipeVisualEditor(recipeContentArea, currentRecipeFile);
						}
					}
				});

			LinearLayout row = new LinearLayout(this);
			row.setOrientation(LinearLayout.HORIZONTAL);
			LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
			itemEdit.setLayoutParams(itemParams);
			row.addView(itemEdit);
			row.addView(delBtn);
			container.addView(row);
			addGap(container, 4);

			itemEdit.addTextChangedListener(new TextWatcher() {
					@Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
					@Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
					@Override public void afterTextChanged(Editable s) {
						if (index < shapelessInputs.size()) {
							shapelessInputs.set(index, s.toString().trim());
						}
						autoSaveRecipe();
					}
				});
		}

		// 添加按钮
		OreButton addBtn = new OreButton(this);
		addBtn.setText("+ 添加物品");
		addBtn.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
		addBtn.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					shapelessInputs.add("minecraft:apple");
					autoSaveRecipe();
					if (recipeContentArea != null && currentRecipeFile != null) {
						recipeContentArea.removeAllViews();
						showRecipeVisualEditor(recipeContentArea, currentRecipeFile);
					}
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

		LinearLayout container = new LinearLayout(this);
		container.setOrientation(LinearLayout.VERTICAL);
		container.setTag("furnace_input_container");

		for (int i = 0; i < furnaceInputs.size(); i++) {
			final int index = i;
			String itemStr = furnaceInputs.get(i);

			final OreEditText itemEdit = new OreEditText(this);
			itemEdit.setHint("物品ID");
			itemEdit.setText(itemStr);

			OreButton delBtn = new OreButton(this);
			delBtn.setText("×");
			delBtn.setStyleSheet(StyleSheet.STYLE_RED);
			delBtn.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						furnaceInputs.remove(index);
						autoSaveRecipe();
						if (recipeContentArea != null && currentRecipeFile != null) {
							recipeContentArea.removeAllViews();
							showRecipeVisualEditor(recipeContentArea, currentRecipeFile);
						}
					}
				});

			LinearLayout row = new LinearLayout(this);
			row.setOrientation(LinearLayout.HORIZONTAL);
			LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
			itemEdit.setLayoutParams(itemParams);
			row.addView(itemEdit);
			row.addView(delBtn);
			container.addView(row);
			addGap(container, 4);

			itemEdit.addTextChangedListener(new TextWatcher() {
					@Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
					@Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
					@Override public void afterTextChanged(Editable s) {
						if (index < furnaceInputs.size()) {
							furnaceInputs.set(index, s.toString().trim());
						}
						autoSaveRecipe();
					}
				});
		}

		OreButton addBtn = new OreButton(this);
		addBtn.setText("+ 添加物品");
		addBtn.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
		addBtn.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					furnaceInputs.add("minecraft:apple");
					autoSaveRecipe();
					if (recipeContentArea != null && currentRecipeFile != null) {
						recipeContentArea.removeAllViews();
						showRecipeVisualEditor(recipeContentArea, currentRecipeFile);
					}
				}
			});
		container.addView(addBtn);

		parent.addView(container);
	}
	
	private void showDeleteRecipeConfirm(final File recipeFile) {
		OreTextView msg = new OreTextView(this);
		msg.setText("确定要删除配方 \"" + recipeFile.getName() + "\" 吗？\n此操作不可恢复！");
		msg.setTextColor(Color.WHITE);
		msg.setTextSize(14);

		LinearLayout layout = new LinearLayout(this);
		layout.setOrientation(LinearLayout.VERTICAL);
		layout.setGravity(Gravity.CENTER);
		int pad = (int) (16 * getResources().getDisplayMetrics().density);
		layout.setPadding(pad, pad, pad, pad);
		layout.addView(msg);

		OreDialogBuilder builder = new OreDialogBuilder(this);
		builder.setTitle("删除配方");
		builder.setView(layout);
		builder.setPositiveButton("删除", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					if (recipeFile.delete()) {
						Toast.makeText(EditorActivity.this, "配方已删除", Toast.LENGTH_SHORT).show();
					} else {
						Toast.makeText(EditorActivity.this, "删除失败", Toast.LENGTH_SHORT).show();
					}
					editorContainer.removeAllViews();
					loadFileList(currentDir);   // 刷新文件列表
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

}
