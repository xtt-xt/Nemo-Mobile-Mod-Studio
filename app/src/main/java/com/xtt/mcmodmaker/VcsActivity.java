package com.xtt.mcmodmaker;

import android.app.Activity;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.xtt.mcmodmaker.core.GitRepo;
import com.xtt.mcmodmaker.core.GitWorkspace;
import com.xtt.mcmodmaker.util.DateUtils;
import com.xtt.mcmodmaker.util.JsonUtils;
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

    private File projectDir;
    private GitRepo repo;

    private OreButton tabAuto;
    private OreButton tabTag;
    private OreTextView summary;
    private OreEditText searchBox;
    private LinearLayout listContainer;

    private int currentTab = TAB_AUTO;
    private String query = "";

    private List<GitRepo.CommitInfo> commits = new ArrayList<>();
    private List<GitRepo.TagInfo> tags = new ArrayList<>();

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
        LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tabAuto.setLayoutParams(tabParams);
        tabTag.setLayoutParams(tabParams);
        tabAuto.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { switchTab(TAB_AUTO); }
        });
        tabTag.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { switchTab(TAB_TAG); }
        });
        tabs.addView(tabAuto);
        tabs.addView(tabTag);
        page.addView(tabs);
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
        if (executor != null) executor.shutdownNow();
        super.onDestroy();
    }

    // ==================== 数据 ====================

    /** 后台读一遍仓库状态（第一次进来自动建仓）。 */
    private void reload() {
        executor.execute(new Runnable() {
            @Override public void run() {
                String error = null;
                try {
                    repo.ensureInit("初始提交");
                    commits = repo.commits(1000);
                    tags = repo.tags();
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
                            render();
                        }
                    }
                });
            }
        });
    }

    private void switchTab(int tab) {
        currentTab = tab;
        tabAuto.setStyleSheet(tab == TAB_AUTO ? StyleSheet.STYLE_GREEN : StyleSheet.STYLE_DARK_GRAY);
        tabTag.setStyleSheet(tab == TAB_TAG ? StyleSheet.STYLE_GREEN : StyleSheet.STYLE_DARK_GRAY);
        render();
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
        titleView.setText((kind == 1 ? "★ " : "") + title);
        titleView.setTextColor(kind == 1 ? Color.parseColor("#7ED321") : Color.WHITE);
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
        UiUtils.toast(this, "正在保存…");
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
        UiUtils.toast(this, "正在恢复…");
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
