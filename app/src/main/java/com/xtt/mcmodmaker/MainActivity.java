package com.xtt.mcmodmaker;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.json.JSONObject;

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
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.view.KeyEvent;
import android.content.Context;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final String MOD_FOLDER_PATH = "/storage/emulated/0/McMod";
    private static final int REQUEST_MANAGE_STORAGE = 1001;
    private static final int REQUEST_IMPORT_FOLDER = 1002;
    private static final int REQUEST_IMPORT_ZIP = 1003;

    private LinearLayout projectListContainer;
    private LinearLayout aboutContainer;
    private LinearLayout buttonRow;
    private SharedPreferences prefs;
    private ProgressBar importProgressBar;
    private String searchQuery = "";
	private static final String PREFS_LAST_CHECK_DATE = "last_update_check_date";
	private static final String PREFS_UPDATE_DIALOG_SHOWN_TODAY = "update_dialog_shown_today";
	private static final String UPDATE_PAN_URL = "https://1825385503.share.123865.com/123pan/0EQWjv-MafMd?pwd=1379";
	private static final int REQUEST_EXPORT_PROJECT = 1004;  // 导出项目文件选择
	private File pendingExportDir; // 临时保存待导出的项目路径
	private OreEditText searchBox;

    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		try {
			prefs = getSharedPreferences("mcmod_prefs", MODE_PRIVATE);

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				getWindow().setStatusBarColor(Color.parseColor("#1A1A1A"));
				getWindow().setNavigationBarColor(Color.parseColor("#1A1A1A"));
			}

			ScrollView scrollView = new ScrollView(this);
			scrollView.setBackgroundColor(Color.parseColor("#1A1A1A"));

			LinearLayout root = new LinearLayout(this);
			root.setOrientation(LinearLayout.VERTICAL);
			root.setPadding(20, 40, 20, 40);
			// 让根布局抢占焦点，避免输入框自动弹出键盘
			root.setFocusable(true);
			root.setFocusableInTouchMode(true);
			root.requestFocus();
			scrollView.addView(root);

			OreTextView tvTitle = new OreTextView(this);
			tvTitle.setText("模组制作器");
			tvTitle.setTextSize(24);
			tvTitle.setTextColor(Color.WHITE);
			root.addView(tvTitle);
			addGap(root, 16);

			searchBox = new OreEditText(this);
			searchBox.setHint("搜索模组名称,命名空间或ID...");
			searchBox.setTextSize(12);
// 不再限制触摸焦点，用户可正常点击输入
			searchBox.addTextChangedListener(new TextWatcher() {
					@Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
					@Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
					@Override public void afterTextChanged(Editable s) {
						searchQuery = s.toString().trim();
						loadProjects();
					}
				});
// 回车键隐藏键盘并清除焦点
			searchBox.setOnEditorActionListener(new TextView.OnEditorActionListener() {
					@Override
					public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
						InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
						imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
						v.clearFocus();
						return true;
					}
				});

			importProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
			importProgressBar.setMax(100);
			importProgressBar.setProgress(0);
			importProgressBar.setVisibility(View.GONE);
			LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				(int) (6 * getResources().getDisplayMetrics().density));
			barParams.bottomMargin = (int) (16 * getResources().getDisplayMetrics().density);
			importProgressBar.setLayoutParams(barParams);
			importProgressBar.setProgressTintList(ColorStateList.valueOf(Color.WHITE));
			root.addView(importProgressBar);

			final OreTabs tabs = new OreTabs(this);
			OreButton tabDev = new OreButton(this);
			tabDev.setText("开发");
			tabDev.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
			OreButton tabAbout = new OreButton(this);
			tabAbout.setText("关于");
			tabAbout.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
			tabs.addButton(tabDev);
			tabs.addButton(tabAbout);
			tabs.setActiveIndex(0);
			root.addView(tabs);
			addGap(root, 12);

			root.addView(searchBox);
			addGap(root, 12);
			
			projectListContainer = new LinearLayout(this);
			projectListContainer.setOrientation(LinearLayout.VERTICAL);
			root.addView(projectListContainer);

			aboutContainer = new LinearLayout(this);
			aboutContainer.setOrientation(LinearLayout.VERTICAL);
			aboutContainer.setVisibility(View.GONE);
			root.addView(aboutContainer);
			showAboutPage();

			buttonRow = new LinearLayout(this);
			buttonRow.setOrientation(LinearLayout.HORIZONTAL);
			buttonRow.setGravity(Gravity.CENTER);

			OreButton btnCreate = new OreButton(this);
			btnCreate.setText("+ 新建模组");
			btnCreate.setStyleSheet(StyleSheet.STYLE_GREEN);
			btnCreate.setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) {
						if (!checkStoragePermission()) {
							showPermissionRequestDialog();
							return;
						}
						showCreateDialog();
					}
				});
			buttonRow.addView(btnCreate);

			OreButton btnImport = new OreButton(this);
			btnImport.setText("导入模组");
			btnImport.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
			btnImport.setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) {
						if (!checkStoragePermission()) {
							showPermissionRequestDialog();
							return;
						}
						showImportDialog();
					}
				});
			buttonRow.addView(btnImport);
			root.addView(buttonRow);

			tabDev.setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) {
						tabs.setActiveIndex(0);
						projectListContainer.setVisibility(View.VISIBLE);
						aboutContainer.setVisibility(View.GONE);
						buttonRow.setVisibility(View.VISIBLE);
						if (searchBox != null) searchBox.setVisibility(View.VISIBLE);   // 新增
					}
				});

			tabAbout.setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) {
						tabs.setActiveIndex(1);
						projectListContainer.setVisibility(View.GONE);
						aboutContainer.setVisibility(View.VISIBLE);
						buttonRow.setVisibility(View.GONE);
						if (searchBox != null) searchBox.setVisibility(View.GONE);     // 新增
					}
				});

			setContentView(scrollView);

			if (checkStoragePermission()) { 
				cleanTempFolders(); 
				loadProjects(); 
			} else if (prefs.getBoolean("first_run", true)) {
				showPermissionRequestDialog();
			}
			SharedPreferences.Editor editor = prefs.edit();
			editor.putBoolean("first_run", false);
			editor.apply();

		} catch (Exception e) {
			LogUtil.logException(e);
			Toast.makeText(this, "启动失败，请查看日志", Toast.LENGTH_LONG).show();
		}
	}

    // ======================== 权限 ========================
    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) return true;
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                if (intent.resolveActivity(getPackageManager()) != null) { startActivityForResult(intent, REQUEST_MANAGE_STORAGE); return; }
            } catch (Exception e) { LogUtil.logException(e); }
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_MANAGE_STORAGE);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_MANAGE_STORAGE);
        }
    }

    private void showPermissionRequestDialog() {
		OreTextView msgText = new OreTextView(this);
		msgText.setText("请授予存储权限以实现必要功能");
		msgText.setTextColor(Color.WHITE);
		msgText.setTextSize(14);

		LinearLayout layout = new LinearLayout(this);
		layout.setOrientation(LinearLayout.VERTICAL);
		layout.setGravity(Gravity.CENTER);
		int padding = (int) (16 * getResources().getDisplayMetrics().density);
		layout.setPadding(padding, padding, padding, padding);
		layout.addView(msgText);

		OreDialogBuilder builder = new OreDialogBuilder(this);
		builder.setTitle("权限申请");
		builder.setView(layout);
		builder.setPositiveButton("去授权", new DialogInterface.OnClickListener() {
				@Override public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
					requestStoragePermission();      // 这里才弹出系统授权
				}
			});
		builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
				@Override public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
				}
			});
		builder.getNegativeButton().setStyleSheet(StyleSheet.STYLE_RED);
		builder.show();
	}

    @Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		// 存储权限请求返回
		if (requestCode == REQUEST_MANAGE_STORAGE) {
			if (checkStoragePermission()) {
				loadProjects();
			} else {
				Toast.makeText(this, "需要存储权限才能使用", Toast.LENGTH_LONG).show();
			}
			return;
		}

		if (resultCode != RESULT_OK || data == null) return;

		// 文件夹导入
		if (requestCode == REQUEST_IMPORT_FOLDER) {
			Uri treeUri = data.getData();
			if (treeUri != null) {
				getContentResolver().takePersistableUriPermission(
					treeUri,
					Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
				);
				importFromFolder(treeUri);
			}
			return;
		}

		// ZIP导入
		if (requestCode == REQUEST_IMPORT_ZIP) {
			Uri zipUri = data.getData();
			if (zipUri != null) {
				importFromZip(zipUri);
			}
			return;
		}

		// 导出项目
		if (requestCode == REQUEST_EXPORT_PROJECT) {
			final Uri uri = data.getData();
			if (uri != null && pendingExportDir != null) {
				final File projectDirToExport = pendingExportDir;
				pendingExportDir = null;
				showImportProgress(true);
				new Thread(new Runnable() {
						@Override
						public void run() {
							try {
								exportProjectToUri(projectDirToExport, uri);
								showToast("导出成功");
							} catch (final Exception e) {
								showToast("导出失败: " + e.getMessage());
								LogUtil.logException(e);
							} finally {
								hideImportProgress();
							}
						}
					}).start();
			}
		}
	}

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_MANAGE_STORAGE) {
            if (checkStoragePermission()) loadProjects();
            else Toast.makeText(this, "权限被拒绝，无法加载模组", Toast.LENGTH_LONG).show();
        }
    }

    // ======================== 关于页 ========================
    private void showAboutPage() {
		aboutContainer.removeAllViews();
		ImageView appIcon = new ImageView(this);
		appIcon.setImageResource(R.drawable.ic_launcher);
		int iconSize = (int) (80 * getResources().getDisplayMetrics().density);
		LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
		iconParams.gravity = Gravity.CENTER;
		iconParams.bottomMargin = (int) (20 * getResources().getDisplayMetrics().density);
		appIcon.setLayoutParams(iconParams);
		aboutContainer.addView(appIcon);

		OreTextView tvAbout = new OreTextView(this);
		tvAbout.setText("网易模组制作器\nNemo Mobile Mod Studio\n\n为网易基岩版设计\n可视化编辑模组");
		tvAbout.setTextColor(Color.WHITE);
		tvAbout.setTextSize(14);
		tvAbout.setGravity(Gravity.CENTER);
		aboutContainer.addView(tvAbout);

		// 版本号卡片（可点击检查更新）
		OreCard versionCard = new OreCard(this);
		versionCard.setPadding(16, 12, 16, 12);

// 水平布局：左侧版本文字 + 右侧刷新图标
		LinearLayout cardLayout = new LinearLayout(this);
		cardLayout.setOrientation(LinearLayout.HORIZONTAL);
		cardLayout.setGravity(Gravity.CENTER_VERTICAL);

		String versionName = "";
		int versionCode = 1;
		try {
			PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
			versionName = info.versionName;
			versionCode = info.versionCode;
		} catch (Exception e) {
			versionName = "未知";
		}

		OreTextView versionText = new OreTextView(this);
		versionText.setText("当前版本：" + versionName + " (" + versionCode + ")");
		versionText.setTextColor(Color.WHITE);
		versionText.setTextSize(14);
		versionText.setGravity(Gravity.CENTER);

// 点击版本卡片触发检查更新
		versionCard.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					checkForUpdate();
				}
			});

		cardLayout.addView(versionText);
		versionCard.addView(cardLayout);

		LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT,
			LinearLayout.LayoutParams.WRAP_CONTENT);
		cardParams.gravity = Gravity.CENTER;
		cardParams.topMargin = (int) (16 * getResources().getDisplayMetrics().density);
		versionCard.setLayoutParams(cardParams);

		aboutContainer.addView(versionCard);
		
		// ---------- 开源地址卡片 ----------
		OreCard openSourceCard = new OreCard(this);
		openSourceCard.setPadding(16, 12, 16, 12);

		LinearLayout openSourceLayout = new LinearLayout(this);
		openSourceLayout.setOrientation(LinearLayout.VERTICAL);

		OreTextView openSourceTitle = new OreTextView(this);
		openSourceTitle.setText("项目开源地址");
		openSourceTitle.setTextColor(Color.WHITE);
		openSourceTitle.setTextSize(14);
		openSourceLayout.addView(openSourceTitle);

		OreTextView openSourceLink = new OreTextView(this);
		openSourceLink.setText("github.com/xtt-xt/Nemo-Mobile-Mod-Studio");
		openSourceLink.setTextColor(Color.GRAY);
		openSourceLink.setTextSize(11);
		openSourceLayout.addView(openSourceLink);

		openSourceCard.addView(openSourceLayout);

// 点击跳转到项目主页
		openSourceCard.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					try {
						Intent intent = new Intent(Intent.ACTION_VIEW,
												   Uri.parse("https://github.com/xtt-xt/Nemo-Mobile-Mod-Studio"));
						startActivity(intent);
					} catch (Exception e) {
						Toast.makeText(MainActivity.this, "无法打开浏览器", Toast.LENGTH_SHORT).show();
					}
				}
			});

		LinearLayout.LayoutParams openSourceCardParams = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT,
			LinearLayout.LayoutParams.WRAP_CONTENT);
		openSourceCardParams.topMargin = (int) (16 * getResources().getDisplayMetrics().density);
		openSourceCard.setLayoutParams(openSourceCardParams);

		aboutContainer.addView(openSourceCard);
		// ---------- Ore UI 致谢卡片 ----------
		OreCard oreUiCard = new OreCard(this);
		oreUiCard.setPadding(16, 12, 16, 12);

		LinearLayout oreUiLayout = new LinearLayout(this);
		oreUiLayout.setOrientation(LinearLayout.VERTICAL);

		OreTextView oreUiTitle = new OreTextView(this);
		oreUiTitle.setText("界面组件库：Ore UI for Android");
		oreUiTitle.setTextColor(Color.WHITE);
		oreUiTitle.setTextSize(14);
		oreUiLayout.addView(oreUiTitle);

		OreTextView oreUiDesc = new OreTextView(this);
		oreUiDesc.setText("基于 OreUI 标准实现的纯 Kotlin 组件库\n使用 Apache License 2.0 开源许可");
		oreUiDesc.setTextColor(Color.GRAY);
		oreUiDesc.setTextSize(11);
		oreUiLayout.addView(oreUiDesc);

		OreTextView oreUiCredit = new OreTextView(this);
		oreUiCredit.setText("感谢原作者 TheChuan1503 及所有贡献者");
		oreUiCredit.setTextColor(Color.parseColor("#AAAAAA"));
		oreUiCredit.setTextSize(10);
		oreUiLayout.addView(oreUiCredit);

		oreUiCard.addView(oreUiLayout);

// 点击卡片跳转到你引用的 Ore UI 项目地址
		oreUiCard.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					try {
						Intent intent = new Intent(Intent.ACTION_VIEW,
												   Uri.parse("https://github.com/xtt-xt/ore-ui-for-android"));
						startActivity(intent);
					} catch (Exception e) {
						Toast.makeText(MainActivity.this, "无法打开浏览器", Toast.LENGTH_SHORT).show();
					}
				}
			});

		LinearLayout.LayoutParams cardParams2 = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT,
			LinearLayout.LayoutParams.WRAP_CONTENT);
		cardParams2.topMargin = (int) (16 * getResources().getDisplayMetrics().density);
		oreUiCard.setLayoutParams(cardParams2);

		aboutContainer.addView(oreUiCard);
	}

	private void checkForUpdate() {
		Toast.makeText(this, "正在检查更新...", Toast.LENGTH_SHORT).show();

		new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						URL url = new URL("https://api.github.com/repos/xtt-xt/Nemo-Mobile-Mod-Studio/releases/latest");
						HttpURLConnection conn = (HttpURLConnection) url.openConnection();
						conn.setRequestMethod("GET");
						conn.setConnectTimeout(10000);
						conn.setReadTimeout(10000);
						conn.setRequestProperty("User-Agent", "NemoModStudio");
						conn.setRequestProperty("Accept", "application/json");

						InputStream is = conn.getInputStream();
						BufferedReader reader = new BufferedReader(new InputStreamReader(is));
						StringBuilder response = new StringBuilder();
						String line;
						while ((line = reader.readLine()) != null) {
							response.append(line);
						}
						reader.close();
						conn.disconnect();

						String responseStr = response.toString();

						// 检查响应是否是 JSON
						if (!responseStr.trim().startsWith("{") && !responseStr.trim().startsWith("[")) {
							runOnUiThread(new Runnable() {
									@Override public void run() {
										Toast.makeText(MainActivity.this, "检查更新失败：服务器返回异常，请稍后再试", Toast.LENGTH_LONG).show();
									}
								});
							return;
						}

						// 解析 JSON
						JSONObject json = new JSONObject(responseStr);
						final String latestTag = json.optString("tag_name", "");
						final String body = json.optString("body", "");
						final String releaseUrl = json.optString("html_url", "");

						// 去掉 tag_name 前面的 "v"
						final String latestVersion = latestTag.startsWith("v") ? latestTag.substring(1) : latestTag;

						// 获取当前版本
						final String currentVersion;
						try {
							PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
							currentVersion = info.versionName;
						} catch (Exception e) {
							runOnUiThread(new Runnable() {
									@Override public void run() {
										Toast.makeText(MainActivity.this, "获取当前版本失败", Toast.LENGTH_SHORT).show();
									}
								});
							return;
						}

						// 比较版本（简单字符串比较）
						if (!latestVersion.equals(currentVersion)) {
							final String finalBody = body;
							runOnUiThread(new Runnable() {
									@Override public void run() {
										// 手动检查，不记录自动弹窗标记（传入 false）
										showUpdateDialog(latestVersion, finalBody, releaseUrl, false);
									}
								});
						} else {
							runOnUiThread(new Runnable() {
									@Override public void run() {
										Toast.makeText(MainActivity.this, "已经是最新版本", Toast.LENGTH_LONG).show();
									}
								});
						}
					} catch (final Exception e) {
						runOnUiThread(new Runnable() {
								@Override public void run() {
									Toast.makeText(MainActivity.this, "检查更新失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
								}
							});
					}
				}
			}).start();
	}
	
	private void showUpdateDialog(final String newVersion, String body, final String releaseUrl, final boolean autoCheck) {
		if (body.length() > 300) {
			body = body.substring(0, 300) + "...";
		}

		LinearLayout layout = new LinearLayout(this);
		layout.setOrientation(LinearLayout.VERTICAL);
		int pad = (int) (16 * getResources().getDisplayMetrics().density);
		layout.setPadding(pad, pad, pad, pad);

		OreTextView versionLabel = new OreTextView(this);
		versionLabel.setText("发现新版本：" + newVersion);
		versionLabel.setTextColor(Color.WHITE);
		versionLabel.setTextSize(16);
		layout.addView(versionLabel);
		addGap(layout, 10);

		OreTextView detailText = new OreTextView(this);
		detailText.setText(body);
		detailText.setTextColor(Color.GRAY);
		detailText.setTextSize(12);
		layout.addView(detailText);

		OreDialogBuilder builder = new OreDialogBuilder(this);
		builder.setTitle("更新");
		builder.setView(layout);

		// 网盘下载（Neutral 按钮）
		builder.setNeutralButton("网盘下载", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					try {
						Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(UPDATE_PAN_URL));
						startActivity(intent);
					} catch (Exception e) {
						Toast.makeText(MainActivity.this, "无法打开浏览器", Toast.LENGTH_SHORT).show();
					}
					dialog.dismiss();
				}
			});
		builder.getNeutralButton().setStyleSheet(StyleSheet.STYLE_DARK_GRAY);

		// GitHub 下载（Positive 按钮）
		builder.setPositiveButton("GitHub下载", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					try {
						Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl));
						startActivity(intent);
					} catch (Exception e) {
						Toast.makeText(MainActivity.this, "无法打开浏览器", Toast.LENGTH_SHORT).show();
					}
					dialog.dismiss();
				}
			});
		builder.getPositiveButton().setStyleSheet(StyleSheet.STYLE_GREEN);

		// 稍后（Negative 按钮）
		builder.setNegativeButton("稍后", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
			});

		// 如果是自动检查弹窗，标记今天已弹窗
		if (autoCheck) {
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
			String today = sdf.format(new Date());
			SharedPreferences.Editor editor = prefs.edit();
			editor.putBoolean(PREFS_UPDATE_DIALOG_SHOWN_TODAY + "_" + today, true);
			editor.apply();
		}

		builder.show();
	}
	
    // ======================== 命名空间验证 ========================
    private boolean isValidNamespace(String ns) {
        if (ns.isEmpty()) return true;
        return ns.matches("[a-z_]+");
    }

    private void updateNsValidation(String ns, OreButton positiveBtn, OreAlert alert) {
        if (ns.isEmpty()) { alert.setVisibility(View.GONE); positiveBtn.setEnabled(true); return; }
        if (isValidNamespace(ns)) { alert.setVisibility(View.GONE); positiveBtn.setEnabled(true); }
        else { alert.setVisibility(View.VISIBLE); positiveBtn.setEnabled(false); }
    }

    // ======================== 创建模组 ========================
    private void showCreateDialog() {
        final OreEditText inputName = new OreEditText(this);
        inputName.setHint("模组名称");

        final OreEditText inputNamespace = new OreEditText(this);
        inputNamespace.setHint("命名空间（小写字母_）");

        // 开关 + 文字标签水平排列
        LinearLayout scriptRow = new LinearLayout(this);
        scriptRow.setOrientation(LinearLayout.HORIZONTAL);
        scriptRow.setGravity(Gravity.CENTER_VERTICAL);

        final OreSwitch switchScript = new OreSwitch(this);
        switchScript.setChecked(true); // 默认开启
        scriptRow.addView(switchScript);

        OreTextView scriptLabel = new OreTextView(this);
        scriptLabel.setText("包含脚本");
        scriptLabel.setTextColor(Color.WHITE);
        scriptLabel.setTextSize(12);
        scriptLabel.setPadding(8, 0, 0, 0);
        scriptRow.addView(scriptLabel);

        // 主布局（竖向）
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(inputName);
        layout.addView(inputNamespace);
        layout.addView(scriptRow);   // 把开关行加进去

        final OreAlert nsAlert = new OreAlert(this);
        nsAlert.setText("命名空间只能包含小写字母和下划线");
        nsAlert.setStyleSheet(StyleSheet.STYLE_ALERT_YELLOW);
        nsAlert.setVisibility(View.GONE);
        layout.addView(nsAlert);

        OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("新建模组");
        builder.setView(layout);

        builder.setPositiveButton("创建", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String name = inputName.getText().toString().trim();
                    String namespace = inputNamespace.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(MainActivity.this, "名称不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (namespace.isEmpty()) namespace = "test";
                    if (!isValidNamespace(namespace)) {
                        Toast.makeText(MainActivity.this, "命名空间格式错误", Toast.LENGTH_LONG).show();
                        return;
                    }
                    boolean withScript = switchScript.isChecked();
                    createNewProject(name, namespace, withScript);
                    dialog.dismiss();
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

    private void createNewProject(String editName, String namespace, boolean withScript) {
        String projectUUID = UUID.randomUUID().toString().replace("-", "");
        File projectDir = new File(MOD_FOLDER_PATH, projectUUID);
        projectDir.mkdirs();

        String behSuffix = randomString(8);
        String resSuffix = randomString(8);

        File behDir = new File(projectDir, "behavior_pack_" + behSuffix);
        behDir.mkdirs();
        new File(behDir, "entities").mkdirs();

        // 如果选择包含脚本，在内创建脚本文件夹
        if (withScript) {
            String scriptSuffix = randomString(8);
            File scriptDir = new File(behDir, "Script_NeteaseMod" + scriptSuffix);
            scriptDir.mkdirs();
            // 写入 __init__.py (空文件)
            writeStringToFile(new File(scriptDir, "__init__.py"), "");
            // 写入 modMain.py（模板）
            String modMainContent = 
                "# -*- coding: utf-8 -*-\n\n" +
                "from mod.common.mod import Mod\n\n\n" +
                "@Mod.Binding(name=\"Script_NeteaseMod" + scriptSuffix + "\", version=\"0.0.1\")\n" +
                "class Script_NeteaseMod" + scriptSuffix + "(object):\n\n" +
                "    def __init__(self):\n" +
                "        pass\n\n" +
                "    @Mod.InitServer()\n" +
                "    def Script_NeteaseMod" + scriptSuffix + "ServerInit(self):\n" +
                "        pass\n\n" +
                "    @Mod.DestroyServer()\n" +
                "    def Script_NeteaseMod" + scriptSuffix + "ServerDestroy(self):\n" +
                "        pass\n\n" +
                "    @Mod.InitClient()\n" +
                "    def Script_NeteaseMod" + scriptSuffix + "ClientInit(self):\n" +
                "        pass\n\n" +
                "    @Mod.DestroyClient()\n" +
                "    def Script_NeteaseMod" + scriptSuffix + "ClientDestroy(self):\n" +
                "        pass\n";
            writeStringToFile(new File(scriptDir, "modMain.py"), modMainContent);
        }

        File resDir = new File(projectDir, "resource_pack_" + resSuffix);
        resDir.mkdirs();
        new File(resDir, "textures").mkdirs();

        // 行为包 manifest.json
        String behHeaderUUID = UUID.randomUUID().toString();
        String behModuleUUID = UUID.randomUUID().toString();
        writeStringToFile(new File(behDir, "manifest.json"),
                          "{\n    \"format_version\": 1,\n    \"header\": {\n        \"min_engine_version\": [1,18,0],\n        \"uuid\": \"" + behHeaderUUID + "\",\n        \"version\": [0,0,1]\n    },\n    \"modules\": [{\n        \"type\": \"data\",\n        \"uuid\": \"" + behModuleUUID + "\",\n        \"version\": [0,0,1]\n    }]\n}");

        // 资源包 manifest.json
        String resHeaderUUID = UUID.randomUUID().toString();
        String resModuleUUID = UUID.randomUUID().toString();
        writeStringToFile(new File(resDir, "manifest.json"),
                          "{\n    \"format_version\": 1,\n    \"header\": {\n        \"min_engine_version\": [1,18,0],\n        \"uuid\": \"" + resHeaderUUID + "\",\n        \"version\": [0,0,1]\n    },\n    \"modules\": [{\n        \"type\": \"resources\",\n        \"uuid\": \"" + resModuleUUID + "\",\n        \"version\": [0,0,1]\n    }]\n}");

        // studio.json
		writeStringToFile(new File(projectDir, "studio.json"),
						  "{\n" +
						  "  \"id\": \"" + projectUUID + "\",\n" +
						  "  \"name\": \"" + editName + "\",\n" +
						  "  \"namespace\": \"" + namespace + "\",\n" +
						  "  \"about\": \"" + editName + "模组，使用网易模组制作器 (Nemo Mobile Mod Studio) 生成。\\n开源地址: https://github.com/xtt-xt/Nemo-Mobile-Mod-Studio\",\n" +
						  "  \"disclaimer\": \"本模组与Mojang及网易无任何关联，模组制作器不承担因模组产生的任何后果\"\n" +
						  "}");
    }

    // ======================== 读取方法 ========================
    private String getProjectName(File projectDir) {
        File studioFile = new File(projectDir, "studio.json");
        if (!studioFile.exists()) return projectDir.getName();
        try {
            FileInputStream fis = new FileInputStream(studioFile);
            byte[] data = new byte[(int) studioFile.length()];
            fis.read(data); fis.close();
            String content = new String(data, "UTF-8");
            int start = content.indexOf("\"name\"");
            if (start != -1) {
                start = content.indexOf("\"", start + 6);
                if (start != -1) {
                    int end = content.indexOf("\"", start + 1);
                    if (end != -1) return content.substring(start + 1, end);
                }
            }
        } catch (Exception ignored) {}
        return projectDir.getName();
    }

    private String getProjectId(File projectDir) {
        File studioFile = new File(projectDir, "studio.json");
        if (!studioFile.exists()) return projectDir.getName();
        try {
            FileInputStream fis = new FileInputStream(studioFile);
            byte[] data = new byte[(int) studioFile.length()];
            fis.read(data); fis.close();
            String content = new String(data, "UTF-8");
            int start = content.indexOf("\"id\"");
            if (start != -1) {
                start = content.indexOf("\"", start + 4);
                if (start != -1) {
                    int end = content.indexOf("\"", start + 1);
                    if (end != -1) return content.substring(start + 1, end);
                }
            }
        } catch (Exception ignored) {}
        return projectDir.getName();
    }

    private String getProjectNamespace(File projectDir) {
        File studioFile = new File(projectDir, "studio.json");
        if (!studioFile.exists()) return "test";
        try {
            FileInputStream fis = new FileInputStream(studioFile);
            byte[] data = new byte[(int) studioFile.length()];
            fis.read(data); fis.close();
            String content = new String(data, "UTF-8");
            int start = content.indexOf("\"namespace\"");
            if (start != -1) {
                start = content.indexOf("\"", start + 12);
                if (start != -1) {
                    int end = content.indexOf("\"", start + 1);
                    if (end != -1) return content.substring(start + 1, end);
                }
            }
        } catch (Exception ignored) {}
        return "test";
    }

    // ======================== 项目设置 ========================
    private void showProjectSettings(final File projectDir) {
        String currentName = getProjectName(projectDir);
        String projectId = getProjectId(projectDir);
        final String currentNamespace = getProjectNamespace(projectDir);

        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setGravity(Gravity.CENTER);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        contentLayout.setPadding(padding, padding, padding, padding);

        OreTextView nameText = new OreTextView(this);
        nameText.setText("项目名: " + currentName);
        nameText.setTextColor(Color.WHITE);
        nameText.setTextSize(14);
        contentLayout.addView(nameText);
        addGap(contentLayout, 8);

        OreTextView idText = new OreTextView(this);
        idText.setText("项目ID: " + projectId);
        idText.setTextColor(Color.GRAY);
        idText.setTextSize(12);
        contentLayout.addView(idText);
        addGap(contentLayout, 4);

        OreTextView nsText = new OreTextView(this);
        nsText.setText("命名空间: " + currentNamespace);
        nsText.setTextColor(Color.GRAY);
        nsText.setTextSize(12);
        contentLayout.addView(nsText);
        addGap(contentLayout, 8);

        OreButton btnChangeNs = new OreButton(this);
        btnChangeNs.setText("修改命名空间");
        btnChangeNs.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btnChangeNs.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showNamespaceDialog(projectDir, currentNamespace); }
            });
        contentLayout.addView(btnChangeNs);
        addGap(contentLayout, 8);

        OreButton btnRename = new OreButton(this);
        btnRename.setText("重命名");
        btnRename.setStyleSheet(StyleSheet.STYLE_WHITE);
        btnRename.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showRenameDialog(projectDir); }
            });
        contentLayout.addView(btnRename);
        addGap(contentLayout, 8);

        OreButton btnBackup = new OreButton(this);
        btnBackup.setText("添加备份");
        btnBackup.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btnBackup.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { createBackup(projectDir); }
            });
        contentLayout.addView(btnBackup);
        addGap(contentLayout, 8);

        OreButton btnOpen = new OreButton(this);
        btnOpen.setText("打开文件夹");
        btnOpen.setStyleSheet(StyleSheet.STYLE_GREEN);
        btnOpen.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { openProjectFolder(projectDir); }
            });
        contentLayout.addView(btnOpen);
        addGap(contentLayout, 8);

        OreButton btnDelete = new OreButton(this);
        btnDelete.setText("删除");
        btnDelete.setStyleSheet(StyleSheet.STYLE_RED);
        btnDelete.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showDeleteConfirm(projectDir); }
            });
        contentLayout.addView(btnDelete);

        OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("项目设置");
        builder.setView(contentLayout);
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
            });
        builder.show();
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
        layout.addView(input);
        layout.addView(alert);

        OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("修改命名空间");
        builder.setView(layout);

        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    String ns = input.getText().toString().trim();
                    if (ns.isEmpty()) ns = "test";
                    if (!isValidNamespace(ns)) { Toast.makeText(MainActivity.this, "命名空间格式错误", Toast.LENGTH_LONG).show(); return; }
                    updateStudioNamespace(projectDir, ns);
                    dialog.dismiss();
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

    private void updateStudioNamespace(File projectDir, String newNs) {
        File studioFile = new File(projectDir, "studio.json");
        if (!studioFile.exists()) return;
        try {
            FileInputStream fis = new FileInputStream(studioFile);
            byte[] data = new byte[(int) studioFile.length()];
            fis.read(data); fis.close();
            String content = new String(data, "UTF-8");

            int start = content.indexOf("\"namespace\"");
            if (start != -1) {
                start = content.indexOf("\"", start + 12);
                if (start != -1) {
                    int end = content.indexOf("\"", start + 1);
                    if (end != -1) {
                        String before = content.substring(0, start + 1);
                        String after = content.substring(end);
                        content = before + newNs + after;
                    }
                }
            } else {
                content = content.replace("}", ",\"namespace\": \"" + newNs + "\"\n}");
            }

            FileWriter fw = new FileWriter(studioFile);
            fw.write(content);
            fw.close();
            Toast.makeText(this, "命名空间已更新", Toast.LENGTH_SHORT).show();
            loadProjects();
        } catch (Exception e) {
            Toast.makeText(this, "更新失败", Toast.LENGTH_SHORT).show();
        }
    }

    // ======================== 重命名 ========================
    private void showRenameDialog(final File projectDir) {
        String currentName = getProjectName(projectDir);
        final OreEditText input = new OreEditText(this);
        input.setHint("输入新名称");
        input.setText(currentName);

        OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("重命名模组");
        builder.setView(input);
        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    String newName = input.getText().toString().trim();
                    if (newName.isEmpty()) { Toast.makeText(MainActivity.this, "名称不能为空", Toast.LENGTH_SHORT).show(); return; }
                    renameProject(projectDir, newName);
                    dialog.dismiss();
                }
            });
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
            });
        builder.getNegativeButton().setStyleSheet(StyleSheet.STYLE_RED);
        builder.show();
    }

    private void renameProject(File projectDir, String newName) {
        try {
            File studioFile = new File(projectDir, "studio.json");
            if (studioFile.exists()) {
                FileInputStream fis = new FileInputStream(studioFile);
                byte[] data = new byte[(int) studioFile.length()];
                fis.read(data); fis.close();
                String content = new String(data, "UTF-8");
                int start = content.indexOf("\"name\"");
                if (start != -1) {
                    start = content.indexOf("\"", start + 6);
                    if (start != -1) {
                        int end = content.indexOf("\"", start + 1);
                        if (end != -1) {
                            String before = content.substring(0, start + 1);
                            String after = content.substring(end);
                            content = before + newName + after;
                        }
                    }
                }
                FileWriter fw = new FileWriter(studioFile);
                fw.write(content);
                fw.close();
            }
            Toast.makeText(this, "重命名成功", Toast.LENGTH_SHORT).show();
            loadProjects();
        } catch (Exception e) {
            Toast.makeText(this, "重命名失败", Toast.LENGTH_SHORT).show();
            LogUtil.logException(e);
        }
    }

    // ======================== 项目卡片 ========================
    private OreCard createProjectCard(final File projectDir) {
        OreCard card = new OreCard(this);
        card.setPadding(16, 12, 16, 12);

        LinearLayout cardLayout = new LinearLayout(this);
        cardLayout.setOrientation(LinearLayout.HORIZONTAL);
        cardLayout.setGravity(Gravity.CENTER_VERTICAL);

        final boolean isCopy = projectDir.getParentFile() != null && projectDir.getParentFile().getName().endsWith("_back");

        ImageView icon = new ImageView(this);
        if (isCopy) {
            icon.setImageResource(android.R.drawable.ic_menu_save);
        } else {
            icon.setImageResource(android.R.drawable.ic_menu_gallery);
        }
        icon.setColorFilter(Color.parseColor("#AAAAAA"));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(80, 80);
        iconParams.setMargins(0, 0, 16, 0);
        icon.setLayoutParams(iconParams);
        cardLayout.addView(icon);

        LinearLayout textLayout = new LinearLayout(this);
        textLayout.setOrientation(LinearLayout.VERTICAL);

        String projectName = getProjectName(projectDir);
        if (isCopy) projectName += " (副本)";

        String copyTime = "";
        if (isCopy) {
            try {
                long timestamp = Long.parseLong(projectDir.getName()) * 1000L;
                copyTime = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(timestamp));
            } catch (Exception ignored) {}
        }

        OreTextView nameText = new OreTextView(this);
        nameText.setText(projectName);
        nameText.setTextSize(16);
        nameText.setTextColor(Color.WHITE);
        textLayout.addView(nameText);

        if (isCopy && !copyTime.isEmpty()) {
            OreTextView timeText = new OreTextView(this);
            timeText.setText("备份保存于: " + copyTime);
            timeText.setTextSize(12);
            timeText.setTextColor(Color.GRAY);
            textLayout.addView(timeText);
        } else {
            String lastModified = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(new Date(projectDir.lastModified()));
            OreTextView timeText = new OreTextView(this);
            timeText.setText("修改时间: " + lastModified);
            timeText.setTextSize(12);
            timeText.setTextColor(Color.GRAY);
            textLayout.addView(timeText);
        }

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

    // ======================== 选项菜单 ========================
    private void showProjectOptions(final File projectDir) {
		OreTextView msgText = new OreTextView(this);
		msgText.setText("请选择操作");
		msgText.setTextColor(Color.WHITE);
		msgText.setTextSize(14);

		LinearLayout msgLayout = new LinearLayout(this);
		msgLayout.setOrientation(LinearLayout.VERTICAL);
		msgLayout.setGravity(Gravity.CENTER);
		int padding = (int) (16 * getResources().getDisplayMetrics().density);
		msgLayout.setPadding(padding, padding, padding, padding);
		msgLayout.addView(msgText);

		final OreDialogBuilder builder = new OreDialogBuilder(this);
		builder.setTitle("管理项目");
		builder.setView(msgLayout);

		// 进入
		builder.addButton("进入", StyleSheet.STYLE_GREEN, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					Intent intent = new Intent(MainActivity.this, EditorActivity.class);
					intent.putExtra("project_path", projectDir.getAbsolutePath());
					startActivity(intent);
					dialog.dismiss();
				}
			});

		// 设置
		builder.addButton("设置", StyleSheet.STYLE_WHITE, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					showProjectSettings(projectDir);
					dialog.dismiss();
				}
			});

		// 导出
		builder.addButton("导出", StyleSheet.STYLE_PURPLE, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					if (!checkStoragePermission()) {
						Toast.makeText(MainActivity.this, "需要存储权限才能导出", Toast.LENGTH_SHORT).show();
						dialog.dismiss();
						return;
					}
					startExportProject(projectDir);
					dialog.dismiss();
				}
			});

		// 取消
		builder.addButton("取消", null, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
				}
			});

		builder.show();
	}
	
	private void startExportProject(File projectDir) {
		// 保存待导出目录供 onActivityResult 使用
		pendingExportDir = projectDir;

		// 获取模组显示名称，用于生成默认文件名
		String projectName = getProjectName(projectDir);
		// 过滤掉文件名中不允许的字符（保留字母、数字、中文、下划线、短横线）
		String safeName = projectName.replaceAll("[^a-zA-Z0-9_\\u4e00-\\u9fa5\\-]", "_");
		String defaultFileName = "Cpp_AddOn_" + safeName + ".zip";

		// 创建保存文件意图
		Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("application/zip");
		intent.putExtra(Intent.EXTRA_TITLE, defaultFileName);
		startActivityForResult(intent, REQUEST_EXPORT_PROJECT);
	}
	
	private void exportProjectToUri(File projectDir, Uri destUri) throws IOException {
		OutputStream os = getContentResolver().openOutputStream(destUri);
		if (os == null) throw new IOException("无法打开输出流");
		ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(os));

		try {
			zipDirectory(projectDir, projectDir, zos);
		} finally {
			zos.close();
		}
	}

	private void zipDirectory(File rootDir, File sourceDir, ZipOutputStream zos) throws IOException {
		File[] files = sourceDir.listFiles();
		if (files == null) return;
		byte[] buffer = new byte[8192];
		for (File file : files) {
			String entryName = rootDir.toURI().relativize(file.toURI()).getPath();
			if (file.isDirectory()) {
				if (!entryName.endsWith("/")) entryName += "/";
				ZipEntry entry = new ZipEntry(entryName);
				zos.putNextEntry(entry);
				zos.closeEntry();
				zipDirectory(rootDir, file, zos);
			} else {
				FileInputStream fis = new FileInputStream(file);
				try {
					ZipEntry entry = new ZipEntry(entryName);
					zos.putNextEntry(entry);
					int len;
					while ((len = fis.read(buffer)) != -1) {
						zos.write(buffer, 0, len);
					}
					zos.closeEntry();
				} finally {
					fis.close();
				}
			}
		}
	}

    private void showCopyOptions(final File projectDir) {
        OreTextView msgText = new OreTextView(this);
        msgText.setText("请选择操作");
        msgText.setTextColor(Color.WHITE);
        msgText.setTextSize(14);

        LinearLayout msgLayout = new LinearLayout(this);
        msgLayout.setOrientation(LinearLayout.VERTICAL);
        msgLayout.setGravity(Gravity.CENTER);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        msgLayout.setPadding(padding, padding, padding, padding);
        msgLayout.addView(msgText);

        OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("副本操作");
        builder.setView(msgLayout);

        builder.setPositiveButton("覆盖", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) { overwriteOriginalFromBackup(projectDir); dialog.dismiss(); }
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
        String displayName = getProjectName(projectDir);
        String backupTime = "";
        try {
            long timestamp = Long.parseLong(projectDir.getName()) * 1000L;
            backupTime = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(timestamp));
        } catch (Exception ignored) {}

        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setGravity(Gravity.CENTER);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        contentLayout.setPadding(padding, padding, padding, padding);

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
                @Override public void onClick(DialogInterface dialog, int which) { openProjectFolder(projectDir); dialog.dismiss(); }
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

    // ======================== 导入相关 ========================
    private void showImportDialog() {
        OreTextView msgText = new OreTextView(this);
        msgText.setText("请选择导入方式");
        msgText.setTextColor(Color.WHITE);
        msgText.setTextSize(14);

        LinearLayout msgLayout = new LinearLayout(this);
        msgLayout.setOrientation(LinearLayout.VERTICAL);
        msgLayout.setGravity(Gravity.CENTER);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        msgLayout.setPadding(padding, padding, padding, padding);
        msgLayout.addView(msgText);

        OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle("导入模组");
        builder.setView(msgLayout);

        builder.setPositiveButton("选择ZIP导入", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) { openZipPicker(); dialog.dismiss(); }
            });
        builder.getPositiveButton().setStyleSheet(StyleSheet.STYLE_GREEN);

        builder.setNegativeButton("选择文件夹导入", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) { openFolderPicker(); dialog.dismiss(); }
            });

        builder.setNeutralButton("取消", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
            });

        builder.show();
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
                        final DocumentFile rootDoc = DocumentFile.fromTreeUri(MainActivity.this, treeUri);
                        if (rootDoc == null) { showToast("无法读取文件夹"); return; }

                        boolean[] valid = {false, false};
                        DocumentFile[] children = rootDoc.listFiles();
                        if (children != null) {
                            for (DocumentFile child : children) {
                                String name = child.getName();
                                if (name != null) {
                                    String lower = name.toLowerCase();
                                    if (lower.startsWith("behavior_pack_") || lower.startsWith("behaviour_pack_")) valid[0] = true;
                                    else if (lower.startsWith("resource_pack_")) valid[1] = true;
                                }
                            }
                        }
                        if (!valid[0] || !valid[1]) { showToast("必须包含行为包和资源包文件夹"); return; }

                        String tempUUID = readStudioId(rootDoc);
                        if (tempUUID == null || tempUUID.isEmpty()) tempUUID = UUID.randomUUID().toString().replace("-", "");
                        final String projectUUID = tempUUID;

                        File projectDir = new File(MOD_FOLDER_PATH, projectUUID);
                        File targetDir = null;
                        if (projectDir.exists()) {
                            final Object lock = new Object();
                            final int[] choice = {0};
                            showConflictAndWait(projectUUID, choice, lock);
                            if (choice[0] == 3) { showToast("已取消导入"); return; }
                            else if (choice[0] == 2) {
                                File backupContainer = new File(MOD_FOLDER_PATH, projectUUID + "_back");
                                backupContainer.mkdirs();
                                targetDir = new File(backupContainer, String.valueOf(System.currentTimeMillis() / 1000));
                            } else {
                                deleteRecursive(projectDir);
                                targetDir = new File(MOD_FOLDER_PATH, projectUUID);
                            }
                        } else {
                            targetDir = projectDir;
                        }
                        targetDir.mkdirs();

                        copyDocumentTreeWithProgress(rootDoc, targetDir);
                        normalizeImportedProject(targetDir);
                        handleImportStudioJson(targetDir, targetDir.getName());

                        showToast("导入成功");
                        refreshProjects();
                    } catch (final Exception e) {
                        showToast("导入失败");
                        LogUtil.logException(e);
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
                        int totalFiles = 0;
                        InputStream countIs = getContentResolver().openInputStream(zipUri);
                        ZipInputStream countZis = new ZipInputStream(new BufferedInputStream(countIs));
                        ZipEntry countEntry;
                        while ((countEntry = countZis.getNextEntry()) != null) {
                            if (!countEntry.isDirectory()) totalFiles++;
                            countZis.closeEntry();
                        }
                        countZis.close(); countIs.close();

                        InputStream is = getContentResolver().openInputStream(zipUri);
                        if (is == null) { showToast("无法读取文件"); return; }

                        final File tempDir = new File(MOD_FOLDER_PATH, "temp_" + System.currentTimeMillis());
                        tempDir.mkdirs();

                        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is));
                        ZipEntry entry;
                        int processedFiles = 0;
                        while ((entry = zis.getNextEntry()) != null) {
                            String name = entry.getName();
                            File outFile = new File(tempDir, name);
                            if (entry.isDirectory()) {
                                outFile.mkdirs();
                            } else {
                                File parent = outFile.getParentFile();
                                if (parent != null && !parent.exists()) parent.mkdirs();
                                FileOutputStream fos = new FileOutputStream(outFile);
                                byte[] buffer = new byte[8192];
                                int len;
                                while ((len = zis.read(buffer)) != -1) { fos.write(buffer, 0, len); try { Thread.sleep(1); } catch (InterruptedException e) {} }
                                fos.close();
                                processedFiles++;
                                final int progress = totalFiles > 0 ? (processedFiles * 100 / totalFiles) : 0;
                                updateProgress(progress);
                            }
                            zis.closeEntry();
                            try { Thread.sleep(2); } catch (InterruptedException e) {}
                        }
                        zis.close(); is.close();

                        String tempUUID = null;
                        File studioJsonFile = new File(tempDir, "studio.json");
                        if (studioJsonFile.exists()) {
                            try {
                                FileInputStream fis = new FileInputStream(studioJsonFile);
                                byte[] data = new byte[(int) studioJsonFile.length()];
                                fis.read(data); fis.close();
                                String content = new String(data, "UTF-8");
                                int start = content.indexOf("\"Id\"");
                                if (start == -1) start = content.indexOf("\"id\"");
                                if (start != -1) {
                                    int colon = content.indexOf(":", start);
                                    if (colon != -1) {
                                        int q1 = content.indexOf("\"", colon);
                                        if (q1 != -1) {
                                            int q2 = content.indexOf("\"", q1 + 1);
                                            if (q2 != -1) tempUUID = content.substring(q1 + 1, q2);
                                        }
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                        if (tempUUID == null || tempUUID.isEmpty()) tempUUID = UUID.randomUUID().toString().replace("-", "");
                        final String projectUUID = tempUUID;

                        File projectDir = new File(MOD_FOLDER_PATH, projectUUID);
                        File targetDir = null;
                        if (projectDir.exists()) {
                            final Object lock = new Object();
                            final int[] choice = {0};
                            showConflictAndWait(projectUUID, choice, lock);
                            if (choice[0] == 3) { deleteRecursive(tempDir); showToast("已取消导入"); return; }
                            else if (choice[0] == 2) {
                                File backupContainer = new File(MOD_FOLDER_PATH, projectUUID + "_back");
                                backupContainer.mkdirs();
                                targetDir = new File(backupContainer, String.valueOf(System.currentTimeMillis() / 1000));
                            } else {
                                deleteRecursive(projectDir);
                                targetDir = new File(MOD_FOLDER_PATH, projectUUID);
                            }
                        } else {
                            targetDir = projectDir;
                        }

                        organizeZipImportedFiles(tempDir, targetDir);
                        deleteRecursive(tempDir);
                        handleImportStudioJson(targetDir, targetDir.getName());

                        showToast("导入成功");
                        refreshProjects();
                    } catch (final Exception e) {
                        cleanTempFolders();
                        showToast("导入失败");
                        LogUtil.logException(e);
                    } finally {
                        hideImportProgress();
                    }
                }
            }).start();
    }

    private void showConflictAndWait(final String projectUUID, final int[] choice, final Object lock) {
        MainActivity.this.runOnUiThread(new Runnable() { public void run() {
                    showImportConflictDialog(projectUUID, new ImportConflictCallback() {
                            @Override public void onChoice(int c) { synchronized (lock) { choice[0] = c; lock.notify(); } }
                        });
                }});
        synchronized (lock) { try { lock.wait(); } catch (InterruptedException e) {} }
    }

    private void updateProgress(final int progress) {
        MainActivity.this.runOnUiThread(new Runnable() { public void run() {
                    if (importProgressBar != null) importProgressBar.setProgress(progress);
                }});
    }

    private void showToast(final String msg) {
        MainActivity.this.runOnUiThread(new Runnable() { public void run() {
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                }});
    }

    private void refreshProjects() {
        MainActivity.this.runOnUiThread(new Runnable() { public void run() {
                    loadProjects();
                }});
    }

    private void showImportConflictDialog(String projectName, final ImportConflictCallback callback) {
        OreTextView msgText = new OreTextView(this);
        msgText.setText("已存在同名项目 \"" + projectName + "\"\n请选择操作");
        msgText.setTextColor(Color.WHITE);
        msgText.setTextSize(14);

        LinearLayout msgLayout = new LinearLayout(this);
        msgLayout.setOrientation(LinearLayout.VERTICAL);
        msgLayout.setGravity(Gravity.CENTER);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        msgLayout.setPadding(padding, padding, padding, padding);
        msgLayout.addView(msgText);

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

    private String readStudioId(DocumentFile rootDoc) {
        DocumentFile studioFile = rootDoc.findFile("studio.json");
        if (studioFile == null || !studioFile.exists() || studioFile.length() <= 0) return null;
        try {
            InputStream is = getContentResolver().openInputStream(studioFile.getUri());
            byte[] data = new byte[(int) studioFile.length()];
            is.read(data); is.close();
            String content = new String(data, "UTF-8");
            int start = content.indexOf("\"Id\"");
            if (start == -1) start = content.indexOf("\"id\"");
            if (start != -1) {
                start = content.indexOf("\"", start + 4);
                if (start != -1) {
                    int end = content.indexOf("\"", start + 1);
                    if (end != -1) return content.substring(start + 1, end);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void handleImportStudioJson(final File targetDir, final String projectId) {
        File existingStudio = new File(targetDir, "studio.json");
        String importName = "未知模组";
        String importNamespace = "test";

        if (existingStudio.exists()) {
            try {
                FileInputStream fis = new FileInputStream(existingStudio);
                byte[] data = new byte[(int) existingStudio.length()];
                fis.read(data); fis.close();
                String content = new String(data, "UTF-8");

                int nameStart = content.indexOf("\"EditName\"");
                if (nameStart == -1) nameStart = content.indexOf("\"name\"");
                if (nameStart != -1) {
                    int colon = content.indexOf(":", nameStart);
                    if (colon != -1) {
                        int q1 = content.indexOf("\"", colon);
                        if (q1 != -1) {
                            int q2 = content.indexOf("\"", q1 + 1);
                            if (q2 != -1) importName = content.substring(q1 + 1, q2);
                        }
                    }
                }

                int nsStart = content.indexOf("\"NameSpace\"");
                if (nsStart == -1) nsStart = content.indexOf("\"namespace\"");
                if (nsStart != -1) {
                    int colon = content.indexOf(":", nsStart);
                    if (colon != -1) {
                        int q1 = content.indexOf("\"", colon);
                        if (q1 != -1) {
                            int q2 = content.indexOf("\"", q1 + 1);
                            if (q2 != -1) { importNamespace = content.substring(q1 + 1, q2); if (importNamespace.isEmpty()) importNamespace = "test"; }
                        }
                    }
                }
            } catch (Exception ignored) {}

            writeStringToFile(existingStudio,
                              "{\n  \"id\": \"" + projectId + "\",\n  \"name\": \"" + importName + "\",\n  \"namespace\": \"" + importNamespace + "\"\n}");
        } else {
            final Object lock = new Object();
            final String[] result = {null, null};
            MainActivity.this.runOnUiThread(new Runnable() { public void run() {
                        showNameAndNamespaceDialog(new NameNamespaceCallback() {
                                @Override public void onResult(String name, String namespace) {
                                    synchronized (lock) { result[0] = name; result[1] = namespace; lock.notify(); }
                                }
                            });
                    }});
            synchronized (lock) { try { lock.wait(); } catch (InterruptedException e) {} }
            if (result[0] == null) return;
            writeStringToFile(new File(targetDir, "studio.json"),
                              "{\n  \"id\": \"" + projectId + "\",\n  \"name\": \"" + result[0] + "\",\n  \"namespace\": \"" + result[1] + "\"\n}");
        }
    }

    private interface NameNamespaceCallback {
        void onResult(String name, String namespace);
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
                    if (!isValidNamespace(ns)) { Toast.makeText(MainActivity.this, "命名空间格式错误", Toast.LENGTH_LONG).show(); return; }
                    callback.onResult(name, ns);
                    dialog.dismiss();
                }
            });
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) { callback.onResult(null, null); dialog.dismiss(); }
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

    // ======================== 备份 / 删除 / 通用 ========================
    private void showDeleteConfirm(final File projectDir) {
        OreTextView msgText = new OreTextView(this);
        msgText.setText("你确定要删除这个模组吗？\n它会永久消失(真的很久)\n此操作不可恢复！");
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
                @Override public void onClick(DialogInterface dialog, int which) { deleteProject(projectDir); dialog.dismiss(); recreate(); }
            });
        builder.getPositiveButton().setStyleSheet(StyleSheet.STYLE_RED);
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
            });
        builder.show();
    }

    private void deleteProject(File projectDir) {
        if (deleteRecursive(projectDir)) Toast.makeText(this, "项目已删除", Toast.LENGTH_SHORT).show();
        else Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
    }

    private boolean deleteRecursive(File file) {
        if (file.isDirectory()) { File[] children = file.listFiles(); if (children != null) for (File child : children) deleteRecursive(child); }
        return file.delete();
    }

    private void createBackup(File projectDir) {
        try {
            if (projectDir.getParentFile() != null && projectDir.getParentFile().getName().endsWith("_back")) {
                Toast.makeText(this, "备份中的项目不能再备份", Toast.LENGTH_SHORT).show(); return;
            }
            String originalId = projectDir.getName();
            if (originalId.contains("_back")) originalId = originalId.substring(0, originalId.lastIndexOf("_back"));

            File backupContainer = new File(MOD_FOLDER_PATH, originalId + "_back");
            backupContainer.mkdirs();
            String backupSubName = String.valueOf(System.currentTimeMillis() / 1000);
            File backupSubDir = new File(backupContainer, backupSubName);
            copyDirectory(projectDir, backupSubDir);
            Toast.makeText(this, "备份成功", Toast.LENGTH_SHORT).show();
            loadProjects();
        } catch (Exception e) { Toast.makeText(this, "备份失败: " + e.getMessage(), Toast.LENGTH_SHORT).show(); }
    }

    private void overwriteOriginalFromBackup(File backupSubDir) {
        File parentDir = backupSubDir.getParentFile();
        String containerName = parentDir.getName();
        if (!containerName.endsWith("_back")) { Toast.makeText(this, "无法识别原项目ID", Toast.LENGTH_SHORT).show(); return; }
        String originalId = containerName.substring(0, containerName.length() - 5);
        File originalDir = new File(MOD_FOLDER_PATH, originalId);
        if (originalDir.exists()) deleteRecursive(originalDir);
        try {
            copyDirectory(backupSubDir, originalDir);
            Toast.makeText(this, "覆盖成功", Toast.LENGTH_SHORT).show();
            loadProjects();
        } catch (Exception e) { Toast.makeText(this, "覆盖失败: " + e.getMessage(), Toast.LENGTH_SHORT).show(); }
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
            fis.close(); fos.close();
        }
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
            } catch (Exception e2) { Toast.makeText(this, "没有可用的文件管理器", Toast.LENGTH_SHORT).show(); }
        }
    }

    private Uri getUriForFile(File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        } else { return Uri.fromFile(file); }
    }

    // ======================== 进度 / 扫描等 ========================
    private void showImportProgress(boolean show) {
        importProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) importProgressBar.setProgress(0);
    }

    private void hideImportProgress() {
        MainActivity.this.runOnUiThread(new Runnable() { public void run() { showImportProgress(false); }});
    }

    private void copyDocumentTreeWithProgress(DocumentFile source, File dest) throws IOException {
        final int totalFiles = countFiles(source);
        final int[] copiedFiles = {0};
        copyDocumentTreeWithCount(source, dest, totalFiles, copiedFiles);
    }

    private int countFiles(DocumentFile root) {
        int count = 0;
        if (root.isDirectory()) { DocumentFile[] children = root.listFiles(); if (children != null) for (DocumentFile child : children) count += countFiles(child); }
        else count = 1;
        return count;
    }

    private void copyDocumentTreeWithCount(DocumentFile source, File dest, final int total, final int[] copied) throws IOException {
        if (source.isDirectory()) {
            dest.mkdirs();
            DocumentFile[] children = source.listFiles();
            if (children != null) for (DocumentFile child : children) copyDocumentTreeWithCount(child, new File(dest, child.getName()), total, copied);
        } else {
            InputStream is = getContentResolver().openInputStream(source.getUri());
            if (is != null) {
                FileOutputStream os = new FileOutputStream(dest);
                byte[] buffer = new byte[8192];
                int len;
                while ((len = is.read(buffer)) != -1) { os.write(buffer, 0, len); try { Thread.sleep(1); } catch (InterruptedException e) {} }
                os.close(); is.close();
                copied[0]++;
                final int progress = total > 0 ? (copied[0] * 100 / total) : 0;
                updateProgress(progress);
            }
        }
    }

    private void organizeZipImportedFiles(File tempDir, File targetProjectDir) {
        File[] entries = tempDir.listFiles();
        if (entries == null) return;

        File behPack = null, resPack = null, studioJson = null, workMcscfg = null;
        for (File entry : entries) {
            String name = entry.getName().toLowerCase();
            if (name.startsWith("behavior_pack_") || name.startsWith("behaviour_pack_")) behPack = entry;
            else if (name.startsWith("resource_pack_")) resPack = entry;
            else if (name.equals("studio.json")) studioJson = entry;
            else if (name.equals("work.mcscfg")) workMcscfg = entry;
        }
        if (behPack == null || resPack == null) throw new RuntimeException("ZIP包必须包含behavior_pack和resource_pack文件夹");

        targetProjectDir.mkdirs();
        try {
            moveFile(behPack, new File(targetProjectDir, behPack.getName()));
            moveFile(resPack, new File(targetProjectDir, resPack.getName()));
            if (studioJson != null) moveFile(studioJson, new File(targetProjectDir, "studio.json"));
            if (workMcscfg != null) moveFile(workMcscfg, new File(targetProjectDir, "work.mcscfg"));
        } catch (Exception e) { LogUtil.logException(e); throw new RuntimeException("文件移动失败"); }
        normalizeImportedProject(targetProjectDir);
    }

    private void normalizeImportedProject(File projectDir) {
        File[] entries = projectDir.listFiles();
        if (entries == null) return;
        for (File entry : entries) {
            String name = entry.getName();
            if (name.startsWith("behavior_pack_") && entry.isDirectory()) {
                File entitiesDir = new File(entry, "entities");
                if (!entitiesDir.exists()) entitiesDir.mkdirs();
                File manifestFile = new File(entry, "manifest.json");
                if (!manifestFile.exists()) {
                    String uuid1 = UUID.randomUUID().toString();
                    String uuid2 = UUID.randomUUID().toString();
                    writeStringToFile(manifestFile, "{\n    \"format_version\": 1,\n    \"header\": {\n        \"min_engine_version\": [1, 18, 0],\n        \"uuid\": \"" + uuid1 + "\",\n        \"version\": [0, 0, 1]\n    },\n    \"modules\": [{\n        \"type\": \"data\",\n        \"uuid\": \"" + uuid2 + "\",\n        \"version\": [0, 0, 1]\n    }]\n}");
                }
            } else if (name.startsWith("resource_pack_") && entry.isDirectory()) {
                File texturesDir = new File(entry, "textures");
                if (!texturesDir.exists()) texturesDir.mkdirs();
                File manifestFile = new File(entry, "manifest.json");
                if (!manifestFile.exists()) {
                    String uuid1 = UUID.randomUUID().toString();
                    String uuid2 = UUID.randomUUID().toString();
                    writeStringToFile(manifestFile, "{\n    \"format_version\": 1,\n    \"header\": {\n        \"min_engine_version\": [1, 18, 0],\n        \"uuid\": \"" + uuid1 + "\",\n        \"version\": [0, 0, 1]\n    },\n    \"modules\": [{\n        \"type\": \"resources\",\n        \"uuid\": \"" + uuid2 + "\",\n        \"version\": [0, 0, 1]\n    }]\n}");
                }
            }
        }
    }

    private void loadProjects() {
        try {
            if (!checkStoragePermission()) return;
            projectListContainer.removeAllViews();

            File modDir = new File(MOD_FOLDER_PATH);
            if (!modDir.exists()) modDir.mkdirs();

            File[] allEntries = modDir.listFiles(new java.io.FileFilter() {
                    @Override public boolean accept(File file) { return file.isDirectory() && !file.getName().startsWith("temp_"); }
                });
            if (allEntries == null || allEntries.length == 0) { projectListContainer.addView(emptyText("暂无模组项目，点击下方按钮创建")); return; }

            Map<String, File> originalMap = new LinkedHashMap<>();
            Map<String, File> backupContainerMap = new LinkedHashMap<>();
            Map<String, List<File>> backupSubMap = new LinkedHashMap<>();
            List<File> backupContainers = new ArrayList<>(), normalProjects = new ArrayList<>();

            for (File entry : allEntries) {
                if (entry.getName().endsWith("_back")) backupContainers.add(entry);
                else normalProjects.add(entry);
            }
            for (File project : normalProjects) originalMap.put(project.getName(), project);
            for (File container : backupContainers) {
                String originalId = container.getName().substring(0, container.getName().length() - 5);
                backupContainerMap.put(originalId, container);
                File[] subDirs = container.listFiles(new java.io.FileFilter() { @Override public boolean accept(File file) { return file.isDirectory(); } });
                List<File> subList = new ArrayList<>();
                if (subDirs != null) for (File sub : subDirs) subList.add(sub);
                backupSubMap.put(originalId, subList);
            }

            boolean hasResults = false;
            Set<String> allIds = new LinkedHashSet<>(); allIds.addAll(originalMap.keySet()); allIds.addAll(backupContainerMap.keySet());

            for (final String originalId : allIds) {
                File originalProject = originalMap.get(originalId);
                List<File> backups = backupSubMap.get(originalId);
                if (backups == null) backups = new ArrayList<>();
                String displayName = getProjectName(originalProject != null ? originalProject : (backups.size() > 0 ? backups.get(0) : null));

// 获取命名空间
				String namespace = "";
				if (originalProject != null) {
					namespace = getProjectNamespace(originalProject);
				} else if (backups.size() > 0) {
					namespace = getProjectNamespace(backups.get(0));   // 从首个副本中读取
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
                    backupContent.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                    backupContent.setPadding(0, 8, 0, 8);
                    for (int i = 0; i < maxDisplay; i++) { backupContent.addView(createProjectCard(backups.get(i))); if (i < maxDisplay - 1) addGap(backupContent, 8); }
                    if (backups.size() > 5) {
                        addGap(backupContent, 8);
                        OreButton viewAllBtn = new OreButton(this);
                        viewAllBtn.setText("查看全部备份 (" + backups.size() + "个)");
                        viewAllBtn.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
                        viewAllBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { Intent i = new Intent(MainActivity.this, BackupsActivity.class); i.putExtra("original_id", originalId); startActivity(i); } });
                        backupContent.addView(viewAllBtn);
                    }
                    accordion.setContentView(backupContent);
                    projectListContainer.addView(accordion);
                }
                addGap(projectListContainer, 12);
            }
            if (!hasResults) projectListContainer.addView(emptyText("没有找到匹配的项目"));
        } catch (Exception e) { LogUtil.logException(e); }
    }

    private OreTextView emptyText(String msg) {
        OreTextView tv = new OreTextView(this);
        tv.setText(msg); tv.setTextColor(Color.WHITE); return tv;
    }

    @Override
	protected void onResume() {
		super.onResume();
		if (checkStoragePermission()) {
			loadProjects();
			// 每天首次打开时自动检查更新（静默，仅在有新版本时弹窗）
			autoCheckForUpdate();
		}
	}
	
	private void autoCheckForUpdate() {
		// 获取今天的日期字符串
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
		final String today = sdf.format(new Date());

		String lastCheckDate = prefs.getString(PREFS_LAST_CHECK_DATE, "");
		if (today.equals(lastCheckDate)) {
			// 今天已经检查过，不再重复检查
			return;
		}

		// 记录今天已检查
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(PREFS_LAST_CHECK_DATE, today);
		editor.apply();

		new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						URL url = new URL("https://api.github.com/repos/xtt-xt/Nemo-Mobile-Mod-Studio/releases/latest");
						HttpURLConnection conn = (HttpURLConnection) url.openConnection();
						conn.setRequestMethod("GET");
						conn.setConnectTimeout(10000);
						conn.setReadTimeout(10000);
						conn.setRequestProperty("User-Agent", "NemoModStudio");
						conn.setRequestProperty("Accept", "application/json");

						InputStream is = conn.getInputStream();
						BufferedReader reader = new BufferedReader(new InputStreamReader(is));
						StringBuilder response = new StringBuilder();
						String line;
						while ((line = reader.readLine()) != null) {
							response.append(line);
						}
						reader.close();
						conn.disconnect();

						String responseStr = response.toString();
						if (!responseStr.trim().startsWith("{") && !responseStr.trim().startsWith("[")) {
							return; // 不是合法的JSON，静默失败
						}

						JSONObject json = new JSONObject(responseStr);
						final String latestTag = json.optString("tag_name", "");
						final String latestVersion = latestTag.startsWith("v") ? latestTag.substring(1) : latestTag;
						final String body = json.optString("body", "");
						final String releaseUrl = json.optString("html_url", "");

						// 获取当前版本
						final String currentVersion;
						try {
							PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
							currentVersion = info.versionName;
						} catch (Exception e) {
							return;
						}

						// 比较版本号（简单字符串比较，可根据需要改为数字拆分比较）
						if (!latestVersion.equals(currentVersion)) {
							// 检查今天是否已经弹过窗
							boolean alreadyShown = prefs.getBoolean(PREFS_UPDATE_DIALOG_SHOWN_TODAY + "_" + today, false);
							if (!alreadyShown) {
								final String finalBody = body;
								runOnUiThread(new Runnable() {
										@Override
										public void run() {
											showUpdateDialog(latestVersion, finalBody, releaseUrl, true);
										}
									});
							}
						}
					} catch (Exception e) {
						// 自动检查失败，静默处理，不打扰用户
					}
				}
			}).start();
	}

    // ======================== 工具方法 ========================
    private void writeStringToFile(File file, String content) {
        try { FileWriter fw = new FileWriter(file); fw.write(content); fw.close(); } catch (IOException e) { e.printStackTrace(); }
    }
    private String randomString(int length) { String chars = "abcdefghijklmnopqrstuvwxyz0123456789"; StringBuilder sb = new StringBuilder(); for (int i = 0; i < length; i++) sb.append(chars.charAt((int)(Math.random()*chars.length()))); return sb.toString(); }
    private void moveFile(File source, File dest) throws IOException {
        if (source.isDirectory()) { dest.mkdirs(); File[] children = source.listFiles(); if (children != null) for (File child : children) moveFile(child, new File(dest, child.getName())); source.delete(); }
        else { FileInputStream fis = new FileInputStream(source); FileOutputStream fos = new FileOutputStream(dest); byte[] buffer = new byte[8192]; int len; while ((len = fis.read(buffer)) != -1) fos.write(buffer, 0, len); fis.close(); fos.close(); source.delete(); }
    }
    private void cleanTempFolders() { File modDir = new File(MOD_FOLDER_PATH); File[] temps = modDir.listFiles(new java.io.FileFilter() { @Override public boolean accept(File file) { return file.isDirectory() && file.getName().startsWith("temp_"); } }); if (temps != null) for (File tmp : temps) deleteRecursive(tmp); }
    private void addGap(LinearLayout parent, int h) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h)); parent.addView(v); }

    private interface ImportConflictCallback { void onChoice(int choice); }
    private interface NameInputCallback { void onName(String name); }
}


