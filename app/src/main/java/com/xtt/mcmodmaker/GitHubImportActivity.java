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

import com.xtt.mcmodmaker.core.Constants;
import com.xtt.mcmodmaker.core.GitRepo;
import com.xtt.mcmodmaker.net.GitAuth;
import com.xtt.mcmodmaker.net.GitHubClient;
import com.xtt.mcmodmaker.util.DateUtils;
import com.xtt.mcmodmaker.util.UiUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev1503.oreui.StyleSheet;
import dev1503.oreui.dialog.OreDialogBuilder;
import dev1503.oreui.widgets.OreButton;
import dev1503.oreui.widgets.OreCard;
import dev1503.oreui.widgets.OreEditText;
import dev1503.oreui.widgets.OreTextView;

/**
 * 从 GitHub 导入：列出自家的仓库，选一个克隆成模组。
 *
 * 克隆到 McMod/mods/ 下的一个目录里，目录名取仓库名；同名就在后面加 -2、-3，
 * 不会覆盖已有模组。克隆下来的是完整仓库，历史和分支都在。
 */
public class GitHubImportActivity extends Activity {

    private OreTextView status;
    private OreEditText searchBox;
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

    private String query = "";
    private List<GitHubClient.Repo> repos = new ArrayList<>();

    private final Handler main = new Handler(Looper.getMainLooper());
    private ExecutorService executor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        title.setText("从 GitHub 导入");
        title.setTextColor(Color.WHITE);
        title.setTextSize(17);
        page.addView(title);

        status = new OreTextView(this);
        status.setText("正在读取仓库列表…");
        status.setTextColor(Color.parseColor("#9A9A9A"));
        status.setTextSize(11);
        status.setPadding(0, UiUtils.dp(this, 3), 0, 0);
        page.addView(status);
        UiUtils.addGap(this, page, UiUtils.dp(this, 8));

        // 列表读取和克隆都可能要等十几秒，给一条看得见的进度
        busyRow = new LinearLayout(this);
        busyRow.setOrientation(LinearLayout.HORIZONTAL);
        busyRow.setGravity(Gravity.CENTER_VERTICAL);
        busyRow.setVisibility(View.GONE);
        ProgressBar spinner = new ProgressBar(this);
        spinner.setIndeterminate(true);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(Color.parseColor("#7ED321")));
        busyRow.addView(spinner, new LinearLayout.LayoutParams(
                UiUtils.dp(this, 18), UiUtils.dp(this, 18)));
        busyText = new OreTextView(this);
        busyText.setTextColor(Color.parseColor("#9A9A9A"));
        busyText.setTextSize(11);
        busyText.setPadding(UiUtils.dp(this, 8), 0, 0, 0);
        busyRow.addView(busyText);
        page.addView(busyRow);
        UiUtils.addGap(this, page, UiUtils.dp(this, 2));

        searchBox = new OreEditText(this);
        searchBox.setHint("搜索仓库名");
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

        ScrollView scroll = new ScrollView(this);
        page.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(0, 0, 0, UiUtils.dp(this, 16));
        scroll.addView(listContainer, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        load();
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacks(busyTick);
        if (executor != null) executor.shutdownNow();
        super.onDestroy();
    }

    // ==================== 进度 ====================

    /** 显示/隐藏列表上方的进度条；忙的时候搜索框和卡片都点不动。 */
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
                if (searchBox != null) searchBox.setEnabled(!on);
                if (listContainer != null) {
                    listContainer.setEnabled(!on);
                    listContainer.setAlpha(on ? 0.7f : 1f);
                }
            }
        });
    }

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

    /** 传给 GitRepo 的进度回调。 */
    private GitRepo.Progress progress() {
        return new GitRepo.Progress() {
            @Override public void onStep(String text) {
                setBusyText(GitRepo.zhStep(text));
            }
        };
    }

    // ==================== 数据 ====================

    private void load() {
        if (!GitAuth.hasToken(this)) {
            status.setText("还没有填 GitHub 令牌");
            listContainer.removeAllViews();
            OreCard card = new OreCard(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(UiUtils.dp(this, 14), UiUtils.dp(this, 12),
                    UiUtils.dp(this, 14), UiUtils.dp(this, 12));
            OreTextView t = new OreTextView(this);
            t.setText("填写 GitHub 令牌");
            t.setTextColor(Color.WHITE);
            t.setTextSize(13);
            card.addView(t);
            OreTextView d = new OreTextView(this);
            d.setText("列仓库、克隆私有仓库都要用令牌，勾 repo 权限");
            d.setTextColor(Color.parseColor("#9A9A9A"));
            d.setTextSize(10);
            d.setPadding(0, UiUtils.dp(this, 3), 0, 0);
            card.addView(d);
            card.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showTokenDialog(); }
            });
            listContainer.addView(card);
            return;
        }

        status.setText("正在读取仓库列表…");
        listContainer.removeAllViews();
        setBusy(true, "正在读取仓库列表…");
        final String token = GitAuth.token(this);
        executor.execute(new Runnable() {
            @Override public void run() {
                String error = null;
                List<GitHubClient.Repo> list = null;
                try {
                    list = GitHubClient.listRepos(token);
                } catch (Exception e) {
                    error = String.valueOf(e.getMessage());
                    LogUtil.logException(e);
                }
                final String err = error;
                final List<GitHubClient.Repo> result = list;
                main.post(new Runnable() {
                    @Override public void run() {
                        setBusy(false, null);
                        if (err != null) {
                            status.setText("读取失败：" + err);
                            return;
                        }
                        repos = result == null ? new ArrayList<GitHubClient.Repo>() : result;
                        status.setText("共 " + repos.size() + " 个仓库");
                        render();
                    }
                });
            }
        });
    }

    // ==================== 界面 ====================

    private void render() {
        if (listContainer == null) return;
        listContainer.removeAllViews();

        int shown = 0;
        for (final GitHubClient.Repo repo : repos) {
            if (!matches(repo)) continue;
            listContainer.addView(repoCard(repo));
            UiUtils.addGap(this, listContainer, UiUtils.dp(this, 6));
            shown++;
        }
        if (shown == 0) {
            OreTextView empty = new OreTextView(this);
            empty.setText(repos.isEmpty() ? "没有仓库" : "没有匹配的仓库");
            empty.setTextColor(Color.parseColor("#9A9A9A"));
            empty.setTextSize(12);
            empty.setPadding(0, UiUtils.dp(this, 12), 0, 0);
            listContainer.addView(empty);
        }
    }

    private boolean matches(GitHubClient.Repo repo) {
        if (query.isEmpty()) return true;
        String q = query.toLowerCase();
        return repo.name.toLowerCase().contains(q)
                || repo.fullName.toLowerCase().contains(q)
                || (repo.description != null && repo.description.toLowerCase().contains(q));
    }

    private OreCard repoCard(final GitHubClient.Repo repo) {
        OreCard card = new OreCard(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(UiUtils.dp(this, 14), UiUtils.dp(this, 12),
                UiUtils.dp(this, 14), UiUtils.dp(this, 12));

        OreTextView name = new OreTextView(this);
        name.setText(repo.name + (repo.isPrivate ? "　（私有）" : ""));
        name.setTextColor(Color.WHITE);
        name.setTextSize(13);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(name);

        OreTextView desc = new OreTextView(this);
        String text = repo.description == null || repo.description.trim().isEmpty()
                ? repo.fullName : repo.description;
        desc.setText(text);
        desc.setTextColor(Color.parseColor("#9A9A9A"));
        desc.setTextSize(10);
        desc.setMaxLines(2);
        desc.setEllipsize(android.text.TextUtils.TruncateAt.END);
        desc.setPadding(0, UiUtils.dp(this, 3), 0, 0);
        card.addView(desc);

        OreTextView time = new OreTextView(this);
        time.setText("更新于 " + formatTime(repo.updatedAt));
        time.setTextColor(Color.parseColor("#7A7A7A"));
        time.setTextSize(9);
        time.setPadding(0, UiUtils.dp(this, 3), 0, 0);
        card.addView(time);

        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmClone(repo); }
        });
        return card;
    }

    /** GitHub 给的是 2026-11-08T08:00:00Z，转成本地时间。 */
    private String formatTime(String iso) {
        try {
            java.text.SimpleDateFormat in = new java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US);
            in.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            java.util.Date d = in.parse(iso);
            return DateUtils.formatMillis(d.getTime());
        } catch (Exception e) {
            return iso == null ? "" : iso;
        }
    }

    // ==================== 克隆 ====================

    private void confirmClone(final GitHubClient.Repo repo) {
        final File target = targetDir(repo.name);
        OreTextView msg = new OreTextView(this);
        msg.setText("把 " + repo.fullName + " 克隆到\n" + target.getName() + " 这个模组目录？");
        msg.setTextColor(Color.WHITE);
        msg.setTextSize(13);
        int pad = UiUtils.dp(this, 8);
        msg.setPadding(pad, pad, pad, pad);

        OreDialogBuilder b = new OreDialogBuilder(this);
        b.setTitle("克隆仓库");
        b.setView(msg);
        b.setPositiveButton("克隆", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                d.dismiss();
                cloneRepo(repo, target);
            }
        });
        b.getPositiveButton().setStyleSheet(StyleSheet.STYLE_GREEN);
        b.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) { d.dismiss(); }
        });
        b.getNegativeButton().setStyleSheet(StyleSheet.STYLE_WHITE);
        b.show();
    }

    /** 目标目录：仓库名，重名就加 -2、-3。 */
    private File targetDir(String repoName) {
        String safe = repoName.replaceAll("[^a-zA-Z0-9_\\-\\u4e00-\\u9fa5.]", "_");
        if (safe.isEmpty()) safe = "repo";
        File mods = new File(Constants.PROJECTS_DIR);
        File dir = new File(mods, safe);
        int n = 2;
        while (dir.exists()) {
            dir = new File(mods, safe + "-" + n);
            n++;
        }
        return dir;
    }

    private void cloneRepo(final GitHubClient.Repo repo, final File target) {
        if (busy) return;
        status.setText("正在克隆 " + repo.name + "…");
        setBusy(true, "正在克隆 " + repo.name + "…");
        final String token = GitAuth.token(this);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                String error = null;
                try {
                    GitRepo.cloneInto(target, repo.cloneUrl, token, progress());
                } catch (Exception e) {
                    error = String.valueOf(e.getMessage());
                    LogUtil.logException(e);
                    // 克隆失败别留半个目录
                    try {
                        com.xtt.mcmodmaker.util.FileUtils.deleteRecursive(target);
                    } catch (Exception ignored) {
                    }
                }
                final String err = error;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        setBusy(false, null);
                        if (err != null) {
                            status.setText("克隆失败：" + err);
                            UiUtils.toast(GitHubImportActivity.this, "克隆失败：" + err);
                            return;
                        }
                        UiUtils.toast(GitHubImportActivity.this, "已导入为模组：" + target.getName());
                        finish();
                    }
                });
            }
        });
    }

    /** 没有令牌时在这里补一个。 */
    private void showTokenDialog() {
        final OreEditText input = new OreEditText(this);
        input.setHint("ghp_… 或 github_pat_…");
        input.setSingleLine(true);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = UiUtils.dp(this, 8);
        box.setPadding(pad, pad, pad, pad);
        box.addView(input);

        OreDialogBuilder b = new OreDialogBuilder(this);
        b.setTitle("GitHub 令牌");
        b.setView(box);
        b.setPositiveButton("保存", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                String token = input.getText().toString().trim();
                d.dismiss();
                if (token.isEmpty()) return;
                GitAuth.save(GitHubImportActivity.this, token);
                load();
            }
        });
        b.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) { d.dismiss(); }
        });
        b.getNegativeButton().setStyleSheet(StyleSheet.STYLE_WHITE);
        b.show();
    }
}
