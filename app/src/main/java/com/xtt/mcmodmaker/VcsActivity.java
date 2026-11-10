package com.xtt.mcmodmaker;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;

import com.xtt.mcmodmaker.core.GitRepo;
import com.xtt.mcmodmaker.core.GitWorkspace;
import com.xtt.mcmodmaker.net.GitAuth;
import com.xtt.mcmodmaker.util.DateUtils;
import com.xtt.mcmodmaker.util.JsonUtils;
import com.xtt.mcmodmaker.util.UiUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev1503.oreui.StyleSheet;
import dev1503.oreui.dialog.OreDialogBuilder;
import dev1503.oreui.widgets.OreButton;
import dev1503.oreui.widgets.OreCard;
import dev1503.oreui.widgets.OreEditText;
import dev1503.oreui.widgets.OreTextView;

/**
 * 代码管理：一个模组的版本历史。
 *
 * 分两栏 —— 「自动保存」是每次退出编辑器时自动记下的改动，
 * 「版本备份」是手动打标签的节点，两栏都能恢复。
 * 恢复不会甩掉历史：当前状态先存成一个版本，再把目标版本的内容当成一次新改动提交。
 */
public class VcsActivity extends Activity {

    public static final String EXTRA_PROJECT_PATH = "project_path";

    private static final int TAB_AUTO = 0;
    private static final int TAB_TAG = 1;
    private static final int TAB_REMOTE = 2;

    private File projectDir;
    private GitRepo repo;

    private OreButton tabAuto;
    private OreButton tabTag;
    private OreButton tabRemote;
    private OreTextView summary;
    private OreCard remoteCard;
    private OreTextView remoteTitle;
    private OreTextView remoteSub;
    private OreEditText searchBox;
    private String remoteUrl = "";
    /** 本地分支对着远端哪条分支（git 上游配置，没选过就是同名分支）。 */
    private String upstream = "";
    private LinearLayout listContainer;
    private LinearLayout busyRow;
    private OreTextView busyText;

    private boolean busy = false;
    private String busyBase = "";
    private long busyStart = 0;
    private final Runnable busyTick = new Runnable() {
        @Override public void run() {
            if (!busy) return;
            long sec = (System.currentTimeMillis() - busyStart) / 1000L;
            busyText.setText(busyBase + "（" + sec + " 秒）");
            main.postDelayed(this, 1000);
        }
    };

    private int currentTab = TAB_AUTO;
    private String query = "";

    private List<GitRepo.CommitInfo> commits = new ArrayList<>();
    private List<GitRepo.TagInfo> tags = new ArrayList<>();
    /** 远端分支上的提交（拉取时顺带读到的）。 */
    private List<GitRepo.CommitInfo> remoteCommits = new ArrayList<>();
    /** 远端分支当前指向的提交、本地 HEAD，reload 时一并读好，画界面时不再碰磁盘。 */
    private String remoteHeadId = "";
    private String localHeadId = "";

    private final Handler main = new Handler(Looper.getMainLooper());
    private ExecutorService executor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String path = getIntent().getStringExtra(EXTRA_PROJECT_PATH);
        if (path == null) {
            finish();
            return;
        }
        projectDir = new File(path);
        repo = GitWorkspace.repo(this, projectDir);
        executor = Executors.newSingleThreadExecutor();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.parseColor("#1E1E1E"));
        int padH = UiUtils.dp(this, 16);
        page.setPadding(padH, UiUtils.dp(this, 16), padH, UiUtils.dp(this, 12));
        setContentView(page);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.parseColor("#1E1E1E"));
            getWindow().setNavigationBarColor(Color.parseColor("#1E1E1E"));
        }

        OreButton btnBack = new OreButton(this);
        btnBack.setText("返回");
        btnBack.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        page.addView(btnBack);
        UiUtils.addGap(this, page, UiUtils.dp(this, 12));

        OreTextView title = new OreTextView(this);
        title.setText("代码管理");
        title.setTextColor(Color.WHITE);
        title.setTextSize(17);
        page.addView(title);

        OreTextView modName = new OreTextView(this);
        modName.setText(JsonUtils.getProjectName(projectDir));
        modName.setTextColor(Color.parseColor("#9A9A9A"));
        modName.setTextSize(11);
        modName.setPadding(0, UiUtils.dp(this, 3), 0, 0);
        modName.setMaxLines(1);
        modName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        page.addView(modName);

        summary = new OreTextView(this);
        summary.setText("正在读取…");
        summary.setTextColor(Color.parseColor("#9A9A9A"));
        summary.setTextSize(11);
        summary.setPadding(0, UiUtils.dp(this, 2), 0, 0);
        page.addView(summary);
        UiUtils.addGap(this, page, UiUtils.dp(this, 10));

        // 远端仓库入口
        remoteCard = new OreCard(this);
        remoteCard.setOrientation(LinearLayout.VERTICAL);
        remoteCard.setPadding(UiUtils.dp(this, 14), UiUtils.dp(this, 12),
                UiUtils.dp(this, 14), UiUtils.dp(this, 12));
        remoteTitle = new OreTextView(this);
        remoteTitle.setTextColor(Color.WHITE);
        remoteTitle.setTextSize(13);
        remoteCard.addView(remoteTitle);
        remoteSub = new OreTextView(this);
        remoteSub.setTextColor(Color.parseColor("#9A9A9A"));
        remoteSub.setTextSize(10);
        remoteSub.setMaxLines(2);
        remoteSub.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        remoteSub.setPadding(0, UiUtils.dp(this, 3), 0, 0);
        remoteCard.addView(remoteSub);
        remoteCard.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onRemoteCardClick(); }
        });
        page.addView(remoteCard);
        UiUtils.addGap(this, page, UiUtils.dp(this, 10));

        searchBox = new OreEditText(this);
        searchBox.setHint("搜索说明、时间或版本号");
        searchBox.setSingleLine(true);
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().trim();
                render();
            }
        });
        page.addView(searchBox);
        UiUtils.addGap(this, page, UiUtils.dp(this, 10));

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabAuto = new OreButton(this);
        tabAuto.setText("自动保存");
        tabTag = new OreButton(this);
        tabTag.setText("版本备份");
        tabRemote = new OreButton(this);
        tabRemote.setText("远端");
        LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tabAuto.setLayoutParams(tabParams);
        tabTag.setLayoutParams(tabParams);
        tabRemote.setLayoutParams(tabParams);
        tabAuto.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (!busy) switchTab(TAB_AUTO); }
        });
        tabTag.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (!busy) switchTab(TAB_TAG); }
        });
        tabRemote.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (!busy) switchTab(TAB_REMOTE); }
        });
        tabs.addView(tabAuto);
        tabs.addView(tabTag);
        tabs.addView(tabRemote);
        // 没关联远端时这一栏没用，先藏着；关联之后才出现
        tabRemote.setVisibility(View.GONE);
        page.addView(tabs);
        UiUtils.addGap(this, page, UiUtils.dp(this, 8));

        // 网络操作期间显示的一条进度：转圈 + 当前在干什么，免得看起来像卡死
        busyRow = new LinearLayout(this);
        busyRow.setOrientation(LinearLayout.HORIZONTAL);
        busyRow.setGravity(Gravity.CENTER_VERTICAL);
        busyRow.setVisibility(View.GONE);
        ProgressBar spinner = new ProgressBar(this);
        spinner.setIndeterminate(true);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(Color.parseColor("#7ED321")));
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                UiUtils.dp(this, 18), UiUtils.dp(this, 18));
        busyRow.addView(spinner, spinnerParams);
        busyText = new OreTextView(this);
        busyText.setTextColor(Color.parseColor("#9A9A9A"));
        busyText.setTextSize(11);
        busyText.setPadding(UiUtils.dp(this, 8), 0, 0, 0);
        busyRow.addView(busyText);
        page.addView(busyRow);
        UiUtils.addGap(this, page, UiUtils.dp(this, 8));

        ScrollView scroll = new ScrollView(this);
        page.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(0, 0, 0, UiUtils.dp(this, 16));
        scroll.addView(listContainer, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        switchTab(TAB_AUTO);
        reload();
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacks(busyTick);
        if (executor != null) executor.shutdownNow();
        super.onDestroy();
    }

    // ==================== 数据 ====================

    /** 后台读一遍仓库状态（第一次进来自动建仓）。 */
    private void reload() {
        setBusy(true, "正在读取版本记录…");
        executor.execute(new Runnable() {
            @Override public void run() {
                String error = null;
                boolean needDetect = false;
                try {
                    repo.ensureInit("初始提交");
                    commits = repo.commits(1000);
                    tags = repo.tags();
                    remoteUrl = repo.remoteUrl();
                    upstream = repo.upstreamBranch();
                    // 关联了远端却还没选过分支：下面单独去问，别把页面卡在这上面
                    needDetect = repo.hasRemote() && !repo.hasUpstream();
                    // 远端提交直接用上次 fetch 的结果，不用每次都联网
                    remoteCommits = repo.remoteCommits(300);
                    String rh = repo.remoteHeadId();
                    remoteHeadId = rh == null ? "" : rh;
                    String lh = repo.headId();
                    localHeadId = lh == null ? "" : lh;
                } catch (Exception e) {
                    error = String.valueOf(e.getMessage());
                    LogUtil.logException(e);
                }
                final String err = error;
                main.post(new Runnable() {
                    @Override public void run() {
                        if (err != null) {
                            summary.setText("读取版本历史失败：" + err);
                        } else {
                            summary.setText("共 " + commits.size() + " 次自动保存 · "
                                    + tags.size() + " 个版本备份");
                            renderRemoteCard();
                            render();
                        }
                        setBusy(false, null);
                    }
                });
                if (needDetect) detectUpstream();
            }
        });
    }

    /**
     * 关联过远端但没选过分支时，问一下远端默认分支是哪条。
     *
     * 只记「对着哪条分支」，不改本地分支名；问不出来就先用同名分支顶着，
     * 用户在远程卡片里挑一次之后就不会再问。
     */
    private void detectUpstream() {
        // 单独开线程：连不上时这里要等重试，别堵住后面排队的操作
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    GitRepo.RemoteBranches branches = repo.remoteBranches(GitAuth.token(VcsActivity.this));
                    if (branches.defaultBranch == null || branches.defaultBranch.isEmpty()) return;
                    repo.setUpstream(branches.defaultBranch);
                    upstream = branches.defaultBranch;
                    remoteCommits = repo.remoteCommits(300);
                    String rh = repo.remoteHeadId();
                    remoteHeadId = rh == null ? "" : rh;
                    main.post(new Runnable() {
                        @Override public void run() {
                            renderRemoteCard();
                            render();
                        }
                    });
                } catch (Exception e) {
                    // 网络不通就先用同名分支，用户可以在卡片里自己选
                    LogUtil.logException(e);
                }
            }
        }, "git-detect-branch").start();
    }

    private void switchTab(int tab) {
        currentTab = tab;
        if (tab == TAB_REMOTE && !hasRemote()) tab = TAB_AUTO;
        currentTab = tab;
        tabAuto.setStyleSheet(tab == TAB_AUTO ? StyleSheet.STYLE_GREEN : StyleSheet.STYLE_DARK_GRAY);
        tabTag.setStyleSheet(tab == TAB_TAG ? StyleSheet.STYLE_GREEN : StyleSheet.STYLE_DARK_GRAY);
        tabRemote.setStyleSheet(tab == TAB_REMOTE ? StyleSheet.STYLE_GREEN : StyleSheet.STYLE_DARK_GRAY);
        render();
    }

    private boolean hasRemote() {
        return remoteUrl != null && !remoteUrl.trim().isEmpty();
    }

    /** 远端栏只在关联仓库之后出现。 */
    private void refreshTabVisibility() {
        if (tabRemote != null) {
            tabRemote.setVisibility(hasRemote() ? View.VISIBLE : View.GONE);
        }
    }

    // ==================== 进度 / 忙碌状态 ====================

    /**
     * 显示或隐藏列表上方的进度条。
     *
     * 网络操作可能要十几秒，中间又没有回调，只 toast 一句的话看着就像卡死了。
     * 忙的时候分页、卡片、搜索框都点不动，防止重复发请求。
     */
    private void setBusy(final boolean on, final String text) {
        UiUtils.runOnUiThread(new Runnable() {
            @Override public void run() {
                if (busyRow == null) return;
                busy = on;
                main.removeCallbacks(busyTick);
                if (!on) {
                    busyRow.setVisibility(View.GONE);
                    busyBase = "";
                } else {
                    busyBase = text == null || text.isEmpty() ? "处理中…" : text;
                    busyStart = System.currentTimeMillis();
                    busyRow.setVisibility(View.VISIBLE);
                    busyText.setText(busyBase);
                    main.postDelayed(busyTick, 1000);
                }
                setInputsEnabled(!on);
            }
        });
    }

    /** 操作途中换一句说明（JGit 的进度回调从后台线程来）。 */
    private void setBusyText(final String text) {
        if (text == null || text.isEmpty()) return;
        UiUtils.runOnUiThread(new Runnable() {
            @Override public void run() {
                if (!busy) return;
                busyBase = text;
                busyText.setText(text);
            }
        });
    }

    private void setInputsEnabled(boolean enabled) {
        OreButton[] tabs = {tabAuto, tabTag, tabRemote};
        for (OreButton b : tabs) {
            if (b == null) continue;
            b.setEnabled(enabled);
            b.setAlpha(enabled ? 1f : 0.45f);
        }
        if (remoteCard != null) {
            remoteCard.setEnabled(enabled);
            remoteCard.setAlpha(enabled ? 1f : 0.7f);
        }
        if (searchBox != null) searchBox.setEnabled(enabled);
    }

    /**
     * 把 JGit 的英文阶段名换成看得懂的说法，数字照留（实现在 GitRepo 里共用）。
     */
    private static String zhStep(String raw) {
        return GitRepo.zhStep(raw);
    }

    /** 传给 GitRepo 的进度回调：翻成人话丢给上面的进度条。 */
    private GitRepo.Progress progress() {
        return new GitRepo.Progress() {
            @Override public void onStep(String text) {
                setBusyText(zhStep(text));
            }
        };
    }

    // ==================== 远端 ====================

    private void renderRemoteCard() {
        if (remoteCard == null) return;
        refreshTabVisibility();
        if (!hasRemote() && currentTab == TAB_REMOTE) {
            currentTab = TAB_AUTO;
            switchTab(TAB_AUTO);
        }
        if (!hasRemote()) {
            remoteTitle.setText("关联远端仓库");
            remoteTitle.setTextColor(Color.WHITE);
            remoteSub.setText("还没有关联，点这里填一个仓库地址");
        } else {
            String br = upstream == null || upstream.isEmpty() ? "未选分支" : upstream;
            remoteTitle.setText("已关联远端仓库 · " + br);
            remoteTitle.setTextColor(Color.parseColor("#7ED321"));
            remoteSub.setText(remoteUrl);
        }
    }

    private void onRemoteCardClick() {
        if (busy) return;
        if (remoteUrl == null || remoteUrl.trim().isEmpty()) {
            showRemoteDialog();
            return;
        }
        OreDialogBuilder b = new OreDialogBuilder(this);
        b.setTitle("远端仓库");
        b.setButtonOrientation(LinearLayout.VERTICAL);
        b.addButton("推送", StyleSheet.STYLE_GREEN, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                d.dismiss();
                doPush();
            }
        });
        b.addButton("拉取", StyleSheet.STYLE_WHITE, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                d.dismiss();
                doPull();
            }
        });
        String br = upstream == null || upstream.isEmpty() ? "未选" : upstream;
        b.addButton("切换远端分支（当前 " + br + "）", StyleSheet.STYLE_DARK_GRAY,
                new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        showBranchPicker();
                    }
                });
        b.addButton("修改地址", StyleSheet.STYLE_DARK_GRAY, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                d.dismiss();
                showRemoteDialog();
            }
        });
        b.addButton("解除关联", StyleSheet.STYLE_RED, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                d.dismiss();
                confirmRemoveRemote();
            }
        });
        b.addButton("取消", StyleSheet.STYLE_WHITE, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) { d.dismiss(); }
        });
        b.show();
    }

    /**
     * 选远端分支。
     *
     * 本地分支叫什么不重要，推拉都对着这里选中的那条 ——
     * 本地初始化的分支叫 master、远端默认是 main 时，默认取 main（远端 HEAD 指的）。
     */
    private void showBranchPicker() {
        if (busy) return;
        setBusy(true, "正在读取远端分支…");
        final String token = GitAuth.token(this);
        executor.execute(new Runnable() {
            @Override public void run() {
                String error = null;
                GitRepo.RemoteBranches result = null;
                try {
                    result = repo.remoteBranches(token);
                } catch (Exception e) {
                    error = describe(e);
                    LogUtil.logException(e);
                }
                final String err = error;
                final GitRepo.RemoteBranches list = result;
                main.post(new Runnable() {
                    @Override public void run() {
                        setBusy(false, null);
                        if (err != null) {
                            UiUtils.toast(VcsActivity.this, "读取分支失败：" + err);
                            return;
                        }
                        if (list == null || list.branches.isEmpty()) {
                            UiUtils.toast(VcsActivity.this, "远端还没有分支，先推送一次");
                            return;
                        }
                        showBranchList(list);
                    }
                });
            }
        });
    }

    private void showBranchList(final GitRepo.RemoteBranches list) {
        OreDialogBuilder b = new OreDialogBuilder(this);
        b.setTitle("选择远端分支");
        b.setButtonOrientation(LinearLayout.VERTICAL);
        for (final String name : list.branches) {
            String label = name;
            if (name.equals(list.defaultBranch)) label = label + "（默认）";
            if (name.equals(upstream)) label = "✓ " + label;
            b.addButton(label,
                    name.equals(upstream) ? StyleSheet.STYLE_GREEN : StyleSheet.STYLE_WHITE,
                    new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int which) {
                            d.dismiss();
                            applyBranch(name, list.defaultBranch);
                        }
                    });
        }
        b.addButton("取消", StyleSheet.STYLE_DARK_GRAY, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) { d.dismiss(); }
        });
        b.show();
    }

    private void applyBranch(final String name, final String defaultBranch) {
        setBusy(true, "正在切换分支…");
        executor.execute(new Runnable() {
            @Override public void run() {
                String error = null;
                try {
                    repo.setUpstream(name);
                    upstream = name;
                } catch (Exception e) {
                    error = String.valueOf(e.getMessage());
                    LogUtil.logException(e);
                }
                final String err = error;
                main.post(new Runnable() {
                    @Override public void run() {
                        setBusy(false, null);
                        if (err != null) {
                            UiUtils.toast(VcsActivity.this, "切换失败：" + err);
                            return;
                        }
                        UiUtils.toast(VcsActivity.this,
                                "远端分支已切到 " + name + (name.equals(defaultBranch) ? "（默认）" : ""));
                        renderRemoteCard();
                        // 换了分支，远端提交要按新分支重新读
                        reload();
                    }
                });
            }
        });
    }

    /** 填远端地址。 */
    private void showRemoteDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = UiUtils.dp(this, 8);
        box.setPadding(pad, pad, pad, pad);

        final OreEditText input = new OreEditText(this);
        input.setHint("https://github.com/用户名/仓库.git");
        input.setSingleLine(true);
        if (remoteUrl != null) input.setText(remoteUrl);
        box.addView(input);

        OreTextView tip = new OreTextView(this);
        tip.setText("需要先推送一次，仓库才会出现在 GitHub 上；空仓库也可以");
        tip.setTextColor(Color.parseColor("#9A9A9A"));
        tip.setTextSize(10);
        tip.setPadding(0, UiUtils.dp(this, 8), 0, 0);
        box.addView(tip);

        OreDialogBuilder b = new OreDialogBuilder(this);
        b.setTitle("远端仓库地址");
        b.setView(box);
        b.setPositiveButton("保存", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                String url = input.getText().toString().trim();
                d.dismiss();
                if (url.isEmpty()) return;
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    UiUtils.toast(VcsActivity.this, "只支持 http(s) 地址");
                    return;
                }
                saveRemote(url);
            }
        });
        b.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) { d.dismiss(); }
        });
        b.getNegativeButton().setStyleSheet(StyleSheet.STYLE_WHITE);
        b.show();
    }

    private void saveRemote(final String url) {
        setBusy(true, "正在保存远端地址…");
        executor.execute(new Runnable() {
            @Override public void run() {
                String error = null;
                try {
                    repo.ensureInit("初始提交");
                    repo.setRemote(url);
                    remoteUrl = url;
                    upstream = repo.upstreamBranch();
                } catch (Exception e) {
                    error = String.valueOf(e.getMessage());
                    LogUtil.logException(e);
                }
                final String err = error;
                main.post(new Runnable() {
                    @Override public void run() {
                        setBusy(false, null);
                        if (err != null) {
                            UiUtils.toast(VcsActivity.this, "保存失败：" + err);
                        } else {
                            UiUtils.toast(VcsActivity.this, "已关联远端仓库");
                            renderRemoteCard();
                            // 顺带问一下这条远端的默认分支，好让「远端」栏读对分支
                            reload();
                        }
                    }
                });
            }
        });
    }

    private void confirmRemoveRemote() {
        OreTextView msg = new OreTextView(this);
        msg.setText("只解除本机的关联，GitHub 上的仓库和内容都不动。");
        msg.setTextColor(Color.WHITE);
        msg.setTextSize(13);
        int pad = UiUtils.dp(this, 8);
        msg.setPadding(pad, pad, pad, pad);

        OreDialogBuilder b = new OreDialogBuilder(this);
        b.setTitle("解除关联");
        b.setView(msg);
        b.setPositiveButton("解除", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                d.dismiss();
                executor.execute(new Runnable() {
                    @Override public void run() {
                        try {
                            repo.removeRemote();
                            repo.clearUpstream();
                            remoteUrl = "";
                            upstream = "";
                        } catch (Exception e) {
                            LogUtil.logException(e);
                        }
                        main.post(new Runnable() {
                            @Override public void run() { renderRemoteCard(); }
                        });
                    }
                });
            }
        });
        b.getPositiveButton().setStyleSheet(StyleSheet.STYLE_RED);
        b.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) { d.dismiss(); }
        });
        b.getNegativeButton().setStyleSheet(StyleSheet.STYLE_WHITE);
        b.show();
    }

    /** 推送前先确认令牌：没填就直接把他引到设置页。 */
    private boolean ensureToken() {
        if (GitAuth.hasToken(this)) return true;
        OreDialogBuilder b = new OreDialogBuilder(this);
        b.setTitle("需要 GitHub 令牌");
        OreTextView msg = new OreTextView(this);
        msg.setText("推送和拉取私有仓库都要用令牌，先去设置页填一个。");
        msg.setTextColor(Color.WHITE);
        msg.setTextSize(13);
        int pad = UiUtils.dp(this, 8);
        msg.setPadding(pad, pad, pad, pad);
        b.setView(msg);
        b.setPositiveButton("去设置", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                d.dismiss();
                UiUtils.toast(VcsActivity.this, "在「设置 → GitHub 令牌」里填");
            }
        });
        b.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) { d.dismiss(); }
        });
        b.getNegativeButton().setStyleSheet(StyleSheet.STYLE_WHITE);
        b.show();
        return false;
    }

    /** 推送前先写一句说明，作为这一版提交的标题。 */
    private void doPush() {
        if (busy) return;
        if (!ensureToken()) return;
        showPushDialog();
    }

    private void showPushDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = UiUtils.dp(this, 8);
        box.setPadding(pad, pad, pad, pad);

        final OreEditText input = new OreEditText(this);
        input.setHint("这次改了什么（可以不填）");
        input.setSingleLine(true);
        box.addView(input);

        OreDialogBuilder b = new OreDialogBuilder(this);
        b.setTitle("推送到远端");
        b.setView(box);
        b.setPositiveButton("推送", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                String desc = input.getText().toString().trim();
                d.dismiss();
                pushNow(desc);
            }
        });
        b.getPositiveButton().setStyleSheet(StyleSheet.STYLE_GREEN);
        b.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) { d.dismiss(); }
        });
        b.getNegativeButton().setStyleSheet(StyleSheet.STYLE_WHITE);
        b.show();
    }

    /**
     * 真正推送。
     *
     * @param description 用户填的说明；填了就作为提交信息，不填就退回自动保存那句
     */
    private void pushNow(final String description) {
        setBusy(true, "正在推送…");
        final String token = GitAuth.token(this);
        executor.execute(new Runnable() {
            @Override public void run() {
                String error = null;
                String branch = null;
                try {
                    repo.ensureInit("初始提交");
                    long now = System.currentTimeMillis();
                    String message = description == null || description.isEmpty()
                            ? GitWorkspace.autoMessage(now) : description;
                    repo.commitIfChanged(message);
                    branch = repo.push(token, progress());
                } catch (Exception e) {
                    error = describe(e);
                    LogUtil.logException(e);
                }
                final String err = error;
                final String br = branch;
                main.post(new Runnable() {
                    @Override public void run() {
                        setBusy(false, null);
                        if (err != null) UiUtils.toast(VcsActivity.this, "推送失败：" + err);
                        else UiUtils.toast(VcsActivity.this, "已推送到远端 " + br);
                        reload();
                    }
                });
            }
        });
    }

    private void doPull() {
        if (busy) return;
        // 公开仓库不用令牌也能拉；私有仓库会由服务端拒绝，错误提示里会说清
        setBusy(true, "正在检查远端…");
        final String token = GitAuth.token(this);
        executor.execute(new Runnable() {
            @Override public void run() {
                String error = null;
                GitRepo.PullInfo info = null;
                try {
                    repo.ensureInit("初始提交");
                    info = repo.fetchAndInspect(token, progress());
                } catch (Exception e) {
                    error = describe(e);
                    LogUtil.logException(e);
                }
                final String err = error;
                final GitRepo.PullInfo result = info;
                main.post(new Runnable() {
                    @Override public void run() {
                        setBusy(false, null);
                        if (err != null) {
                            UiUtils.toast(VcsActivity.this, "拉取失败：" + err);
                            return;
                        }
                        handlePull(result);
                    }
                });
            }
        });
    }

    private void handlePull(GitRepo.PullInfo info) {
        switch (info.state) {
            case GitRepo.PULL_SAME:
                UiUtils.toast(this, "已经和远端一样了");
                break;
            case GitRepo.PULL_REMOTE_AHEAD:
                applyPull(info.remoteId, false);
                break;
            case GitRepo.PULL_LOCAL_AHEAD:
                askPushNow();
                break;
            case GitRepo.PULL_NO_REMOTE_BRANCH:
                askPushNow();
                break;
            default:
                askDiverged(info);
                break;
        }
    }

    private void askPushNow() {
        OreDialogBuilder b = new OreDialogBuilder(this);
        b.setTitle("本地比远端新");
        OreTextView msg = new OreTextView(this);
        msg.setText("远端没有需要拉取的内容，要把本地的改动推上去吗？");
        msg.setTextColor(Color.WHITE);
        msg.setTextSize(13);
        int pad = UiUtils.dp(this, 8);
        msg.setPadding(pad, pad, pad, pad);
        b.setView(msg);
        b.setPositiveButton("推送", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                d.dismiss();
                doPush();
            }
        });
        b.getPositiveButton().setStyleSheet(StyleSheet.STYLE_GREEN);
        b.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) { d.dismiss(); }
        });
        b.getNegativeButton().setStyleSheet(StyleSheet.STYLE_WHITE);
        b.show();
    }

    /** 分叉：让用户选保留哪边。 */
    private void askDiverged(final GitRepo.PullInfo info) {
        OreDialogBuilder b = new OreDialogBuilder(this);
        b.setTitle("本地和远端都有新改动");
        b.setButtonOrientation(LinearLayout.VERTICAL);
        b.addButton("以远端为准", StyleSheet.STYLE_GREEN, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                d.dismiss();
                applyPull(info.remoteId, true);
            }
        });
        b.addButton("保留本地，放弃这次拉取", StyleSheet.STYLE_WHITE, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) { d.dismiss(); }
        });
        b.show();
    }

    /**
     * 把工作区切到远端那个提交。
     *
     * @param byRemote 分叉时选「以远端为准」：先给现在的内容打一个标签再覆盖，
     *                 免得本地那版直接没了
     */
    private void applyPull(final String remoteCommit, final boolean byRemote) {
        setBusy(true, byRemote ? "正在用远端内容覆盖本地…" : "正在更新到远端版本…");
        executor.execute(new Runnable() {
            @Override public void run() {
                String error = null;
                try {
                    if (byRemote) {
                        repo.commitIfChanged(GitWorkspace.autoMessage(System.currentTimeMillis()));
                        repo.createTag(repo.uniqueTagName("remote-before"),
                                "拉取覆盖前的本地自动备份");
                    }
                    repo.hardResetTo(remoteCommit);
                } catch (Exception e) {
                    error = describe(e);
                    LogUtil.logException(e);
                }
                final String err = error;
                main.post(new Runnable() {
                    @Override public void run() {
                        setBusy(false, null);
                        if (err != null) UiUtils.toast(VcsActivity.this, "拉取失败：" + err);
                        else UiUtils.toast(VcsActivity.this, "已更新到远端版本");
                        reload();
                    }
                });
            }
        });
    }

    /** 把 JGit 的一长串异常压成一句能看的话。 */
    private String describe(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return e.getClass().getSimpleName();
        if (msg.contains("not authorized") || msg.contains("Authentication is required")
                || msg.contains("Authentication not supported")) {
            return "仓库地址可能写错了，或令牌没有这个仓库的权限";
        }
        if (msg.contains("rejected") || msg.contains("non-fast-forward")) {
            return "远端有本地没有的改动，先拉取";
        }
        if (msg.contains("not found") || msg.contains("Repository not found")) {
            return "仓库不存在，或令牌没有权限";
        }
        // 连不上 GitHub 是这台机器上最常见的一类：给个能动手的说明，别丢英文原文
        String lower = msg.toLowerCase();
        if (lower.contains("connection failed") || lower.contains("connection abort")
                || lower.contains("failed to connect") || lower.contains("econnreset")
                || lower.contains("timeout") || lower.contains("timed out")
                || lower.contains("unknownhost") || lower.contains("unable to resolve")) {
            return "连不上 GitHub（网络被重置或超时），换个网络或稍后再试";
        }
        return msg;
    }

    // ==================== 界面 ====================

    private void render() {
        if (listContainer == null) return;
        listContainer.removeAllViews();

        if (currentTab == TAB_TAG) {
            OreButton mark = new OreButton(this);
            mark.setText("把当前状态标记为版本");
            mark.setStyleSheet(StyleSheet.STYLE_GREEN);
            mark.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            mark.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showCreateTagDialog(); }
            });
            listContainer.addView(mark);
            UiUtils.addGap(this, listContainer, UiUtils.dp(this, 8));
        }

        if (currentTab == TAB_REMOTE) {
            renderRemoteTab();
            return;
        }

        int shown = 0;
        if (currentTab == TAB_AUTO) {
            for (final GitRepo.CommitInfo c : commits) {
                String title = DateUtils.formatMillis(c.timeMillis);
                String detail = c.shortId() + "　" + c.message;
                if (!matches(title, detail)) continue;
                listContainer.addView(versionCard(title, detail, 0,
                        new View.OnClickListener() {
                            @Override public void onClick(View v) {
                                confirmRestore(c.id, DateUtils.formatMillis(c.timeMillis));
                            }
                        }));
                UiUtils.addGap(this, listContainer, UiUtils.dp(this, 6));
                shown++;
            }
        } else {
            for (final GitRepo.TagInfo t : tags) {
                String title = t.message.isEmpty() ? "版本备份" : t.message;
                String detail = DateUtils.formatMillis(t.timeMillis) + "　" + t.shortCommit();
                if (!matches(title, detail)) continue;
                listContainer.addView(versionCard(title, detail, 1,
                        new View.OnClickListener() {
                            @Override public void onClick(View v) {
                                showTagMenu(t);
                            }
                        }));
                UiUtils.addGap(this, listContainer, UiUtils.dp(this, 6));
                shown++;
            }
        }

        if (shown == 0) {
            OreTextView empty = new OreTextView(this);
            empty.setText(query.isEmpty() ? "还没有记录" : "没有找到匹配的版本");
            empty.setTextColor(Color.parseColor("#9A9A9A"));
            empty.setTextSize(12);
            empty.setPadding(0, UiUtils.dp(this, 12), 0, 0);
            listContainer.addView(empty);
        }
    }

    /**
     * 远端栏：远端分支上的提交。
     *
     * fetch 过才有内容，所以这里给两个操作——刷新（只取不下）、拉取（取下来用）。
     */
    private void renderRemoteTab() {
        OreButton refresh = new OreButton(this);
        refresh.setText("刷新远端列表");
        refresh.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        refresh.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { fetchRemoteList(); }
        });
        listContainer.addView(refresh);
        UiUtils.addGap(this, listContainer, UiUtils.dp(this, 8));

        if (remoteCommits.isEmpty()) {
            OreTextView empty = new OreTextView(this);
            empty.setText("还没有取过远端记录，点上面的「刷新远端列表」");
            empty.setTextColor(Color.parseColor("#9A9A9A"));
            empty.setTextSize(12);
            empty.setPadding(0, UiUtils.dp(this, 4), 0, 0);
            listContainer.addView(empty);
            return;
        }

        int shown = 0;
        for (final GitRepo.CommitInfo c : remoteCommits) {
            String title = DateUtils.formatMillis(c.timeMillis);
            boolean isHead = !remoteHeadId.isEmpty() && remoteHeadId.equals(c.id);
            boolean sameAsLocal = !localHeadId.isEmpty() && localHeadId.equals(c.id);
            String detail = c.shortId() + "　" + c.message;
            if (!matches(title, detail)) continue;
            // 本地这边已经有了的标个勾，一眼能看出还差几条没拉
            String mark = sameAsLocal ? "✓ " : (isHead ? "◆ " : "");
            listContainer.addView(versionCard(mark + title, detail, sameAsLocal ? 2 : 1,
                    new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            confirmRestore(c.id, "远端 " + DateUtils.formatMillis(c.timeMillis));
                        }
                    }));
            UiUtils.addGap(this, listContainer, UiUtils.dp(this, 6));
            shown++;
        }
        if (shown == 0) {
            OreTextView empty = new OreTextView(this);
            empty.setText("没有找到匹配的提交");
            empty.setTextColor(Color.parseColor("#9A9A9A"));
            empty.setTextSize(12);
            listContainer.addView(empty);
        }
    }

    /** 只 fetch 不改工作区，把远端提交列出来。 */
    private void fetchRemoteList() {
        if (busy) return;
        setBusy(true, "正在读取远端…");
        final String token = GitAuth.token(this);
        executor.execute(new Runnable() {
            @Override public void run() {
                String error = null;
                try {
                    repo.ensureInit("初始提交");
                    repo.fetchRemote(token, progress());
                    remoteCommits = repo.remoteCommits(300);
                } catch (Exception e) {
                    error = describe(e);
                    LogUtil.logException(e);
                }
                final String err = error;
                main.post(new Runnable() {
                    @Override public void run() {
                        setBusy(false, null);
                        if (err != null) UiUtils.toast(VcsActivity.this, "读取远端失败：" + err);
                        render();
                    }
                });
            }
        });
    }

    private boolean matches(String title, String detail) {
        if (query.isEmpty()) return true;
        String q = query.toLowerCase();
        return title.toLowerCase().contains(q) || detail.toLowerCase().contains(q);
    }

    /**
     * 一条版本记录。
     *
     * @param kind 0 = 自动保存，1 = 版本备份（两类用颜色区分）
     */
    private OreCard versionCard(String title, String detail, int kind, View.OnClickListener onClick) {
        OreCard card = new OreCard(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(UiUtils.dp(this, 14), UiUtils.dp(this, 12),
                UiUtils.dp(this, 14), UiUtils.dp(this, 12));

        OreTextView titleView = new OreTextView(this);
        int color = Color.WHITE;
        if (kind == 1) color = Color.parseColor("#7ED321");
        else if (kind == 2) color = Color.parseColor("#9A9A9A");
        titleView.setText(title);
        titleView.setTextColor(color);
        titleView.setTextSize(13);
        titleView.setMaxLines(1);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(titleView);

        OreTextView detailView = new OreTextView(this);
        detailView.setText(detail);
        detailView.setTextColor(Color.parseColor("#9A9A9A"));
        detailView.setTextSize(10);
        detailView.setMaxLines(1);
        detailView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        detailView.setPadding(0, UiUtils.dp(this, 3), 0, 0);
        card.addView(detailView);

        card.setOnClickListener(onClick);
        return card;
    }

    // ==================== 打版本 ====================

    private void showCreateTagDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = UiUtils.dp(this, 8);
        box.setPadding(pad, pad, pad, pad);

        OreTextView tip = new OreTextView(this);
        tip.setText("给当前内容写一句说明，方便以后认出来");
        tip.setTextColor(Color.parseColor("#9A9A9A"));
        tip.setTextSize(10);
        box.addView(tip);

        final OreEditText input = new OreEditText(this);
        input.setHint("例如：配方调好了，先存一版");
        input.setSingleLine(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = UiUtils.dp(this, 8);
        input.setLayoutParams(lp);
        box.addView(input);

        OreDialogBuilder b = new OreDialogBuilder(this);
        b.setTitle("标记为版本");
        b.setView(box);
        b.setPositiveButton("保存", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                d.dismiss();
                createTag(input.getText().toString().trim());
            }
        });
        b.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) { d.dismiss(); }
        });
        b.getNegativeButton().setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        b.show();
    }

    private void createTag(final String description) {
        if (busy) return;
        setBusy(true, "正在保存为版本…");
        executor.execute(new Runnable() {
            @Override public void run() {
                String error = null;
                try {
                    repo.ensureInit("初始提交");
                    long now = System.currentTimeMillis();
                    // 先把没提交的改动存下来，否则标签指向的不是眼前这份内容
                    repo.commitIfChanged(GitWorkspace.autoMessage(now));
                    String message = description.isEmpty()
                            ? "备份 " + DateUtils.formatMillis(now) : description;
                    repo.createTag(repo.uniqueTagName("backup"), message);
                } catch (Exception e) {
                    error = String.valueOf(e.getMessage());
                    LogUtil.logException(e);
                }
                final String err = error;
                main.post(new Runnable() {
                    @Override public void run() {
                        setBusy(false, null);
                        if (err != null) {
                            UiUtils.toast(VcsActivity.this, "保存失败：" + err);
                        } else {
                            UiUtils.toast(VcsActivity.this, "已保存为一个版本");
                            switchTab(TAB_TAG);
                            reload();
                        }
                    }
                });
            }
        });
    }

    // ==================== 恢复 / 删除 ====================

    private void confirmRestore(final String commitId, final String label) {
        OreTextView msg = new OreTextView(this);
        msg.setText("回到 " + label + " 的内容？\n当前内容会先自动存成一个版本备份，随时能回来。");
        msg.setTextColor(Color.WHITE);
        msg.setTextSize(13);
        int pad = UiUtils.dp(this, 8);
        msg.setPadding(pad, pad, pad, pad);

        OreDialogBuilder b = new OreDialogBuilder(this);
        b.setTitle("恢复版本");
        b.setView(msg);
        b.setPositiveButton("恢复", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                d.dismiss();
                doRestore(commitId, label);
            }
        });
        b.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) { d.dismiss(); }
        });
        b.getNegativeButton().setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        b.show();
    }

    private void doRestore(final String commitId, final String label) {
        if (busy) return;
        setBusy(true, "正在恢复到 " + label + "…");
        executor.execute(new Runnable() {
            @Override public void run() {
                String error = null;
                boolean same = false;
                try {
                    repo.ensureInit("初始提交");
                    // 没落盘的手改先记下来，免得恢复动作把眼前这版弄丢
                    repo.commitIfChanged(GitWorkspace.autoMessage(System.currentTimeMillis()));
                    GitRepo.CommitInfo c = repo.restore(commitId, "恢复到 " + label,
                            "restore-before", "恢复「" + label + "」前的自动备份");
                    same = c == null;
                } catch (Exception e) {
                    error = String.valueOf(e.getMessage());
                    LogUtil.logException(e);
                }
                final String err = error;
                final boolean unchanged = same;
                main.post(new Runnable() {
                    @Override public void run() {
                        setBusy(false, null);
                        if (err != null) UiUtils.toast(VcsActivity.this, "恢复失败：" + err);
                        else if (unchanged) UiUtils.toast(VcsActivity.this, "这就是当前版本");
                        else UiUtils.toast(VcsActivity.this, "已恢复到 " + label);
                        reload();
                    }
                });
            }
        });
    }

    private void showTagMenu(final GitRepo.TagInfo tag) {
        OreDialogBuilder b = new OreDialogBuilder(this);
        b.setTitle(tag.message.isEmpty() ? "版本备份" : tag.message);
        b.setButtonOrientation(LinearLayout.VERTICAL);
        b.addButton("恢复到这个版本", StyleSheet.STYLE_GREEN, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                d.dismiss();
                confirmRestore(tag.commitId,
                        tag.message.isEmpty() ? DateUtils.formatMillis(tag.timeMillis) : tag.message);
            }
        });
        b.addButton("删除这个版本", StyleSheet.STYLE_RED, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                d.dismiss();
                confirmDeleteTag(tag);
            }
        });
        b.addButton("取消", StyleSheet.STYLE_DARK_GRAY, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) { d.dismiss(); }
        });
        b.show();
    }

    private void confirmDeleteTag(final GitRepo.TagInfo tag) {
        OreTextView msg = new OreTextView(this);
        msg.setText("删除后这个备份点就不在列表里了，里面的内容本身还在。");
        msg.setTextColor(Color.WHITE);
        msg.setTextSize(13);
        int pad = UiUtils.dp(this, 8);
        msg.setPadding(pad, pad, pad, pad);

        OreDialogBuilder b = new OreDialogBuilder(this);
        b.setTitle("删除版本");
        b.setView(msg);
        b.setPositiveButton("删除", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                d.dismiss();
                executor.execute(new Runnable() {
                    @Override public void run() {
                        String error = null;
                        try {
                            repo.deleteTag(tag.name);
                        } catch (Exception e) {
                            error = String.valueOf(e.getMessage());
                            LogUtil.logException(e);
                        }
                        final String err = error;
                        main.post(new Runnable() {
                            @Override public void run() {
                                if (err != null) UiUtils.toast(VcsActivity.this, "删除失败：" + err);
                                reload();
                            }
                        });
                    }
                });
            }
        });
        b.getPositiveButton().setStyleSheet(StyleSheet.STYLE_RED);
        b.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) { d.dismiss(); }
        });
        b.getNegativeButton().setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        b.show();
    }
}
