package com.xtt.mcmodmaker;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.xtt.mcmodmaker.core.Constants;
import com.xtt.mcmodmaker.core.ItemRegistry;
import com.xtt.mcmodmaker.core.UpdateChecker;
import com.xtt.mcmodmaker.net.DevAuth;
import com.xtt.mcmodmaker.util.UiUtils;

import dev1503.oreui.StyleSheet;
import dev1503.oreui.dialog.OreDialogBuilder;
import dev1503.oreui.widgets.OreButton;
import dev1503.oreui.widgets.OreCard;
import dev1503.oreui.widgets.OreTextView;

/**
 * 关于页面：应用介绍、版本（点击检查更新）、内置数据版本、账号密码、开源地址。
 *
 * 开源鸣谢条目多，单独放进 {@link CreditsActivity}，这里只留一个入口。
 */
public class AboutActivity extends Activity {

    private OreTextView versionText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.parseColor("#1E1E1E"));
        setContentView(page);
        // 状态栏 / 导航栏与页面背景一致
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.parseColor("#1E1E1E"));
            getWindow().setNavigationBarColor(Color.parseColor("#1E1E1E"));
        }
        // 返回按钮
        OreButton btnBack = new OreButton(this);
        btnBack.setText("返回");
        btnBack.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        page.addView(btnBack);

        // 内容区独立滚动，避免较小屏幕下内容被截断。
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.parseColor("#1E1E1E"));
        page.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiUtils.dp(this, 16), UiUtils.dp(this, 10),
                UiUtils.dp(this, 16), UiUtils.dp(this, 16));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        ImageView appIcon = new ImageView(this);
        appIcon.setImageResource(R.drawable.ic_launcher);
        int iconSize = UiUtils.dp(this, 80);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconParams.gravity = Gravity.CENTER;
        iconParams.bottomMargin = UiUtils.dp(this, 12);
        appIcon.setLayoutParams(iconParams);
        root.addView(appIcon);

        OreTextView tvAbout = new OreTextView(this);
        tvAbout.setText("网易模组制作器\nNemo Mobile Mod Studio\n\n为网易基岩版设计\n可视化编辑模组");
        tvAbout.setTextColor(Color.WHITE);
        tvAbout.setTextSize(14);
        tvAbout.setGravity(Gravity.CENTER);
        root.addView(tvAbout);

        // 版本卡片：整块可点，点了就查更新
        OreCard versionCard = buildVersionCard();
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = UiUtils.dp(this, 8);
        versionCard.setLayoutParams(cardParams);
        root.addView(versionCard);

        // 内置数据版本：说明物品库对应哪一版游戏，免得和实际工程对不上
        OreCard dataCard = new OreCard(this);
        dataCard.setPadding(16, 12, 16, 12);
        LinearLayout dataLayout = new LinearLayout(this);
        dataLayout.setOrientation(LinearLayout.VERTICAL);

        OreTextView dataTitle = new OreTextView(this);
        dataTitle.setText("内置数据版本");
        dataTitle.setTextColor(Color.WHITE);
        dataTitle.setTextSize(14);
        dataLayout.addView(dataTitle);

        OreTextView dataBody = new OreTextView(this);
        dataBody.setText("物品库：基岩版 " + ItemRegistry.VANILLA_VERSION
                + "（网易 " + ItemRegistry.NETEASE_VERSION + "）\n"
                + "包含原版方块与物品共 " + ItemRegistry.vanillaCount(this) + " 项\n"
                + "方块图标：内置 " + com.xtt.mcmodmaker.core.BlockTextureLib.blockCount(this)
                + " 种方块的原版贴图，按六面贴图实时渲染\n"
                + "数据来源：Mojang 官方 bedrock-samples 与 BedrockData");
        dataBody.setTextColor(Color.parseColor("#AAAAAA"));
        dataBody.setTextSize(11);
        dataBody.setPadding(0, UiUtils.dp(this, 6), 0, 0);
        dataLayout.addView(dataBody);

        dataCard.addView(dataLayout);
        LinearLayout.LayoutParams dataParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dataParams.topMargin = UiUtils.dp(this, 8);
        dataCard.setLayoutParams(dataParams);
        root.addView(dataCard);

        // 账号：开发者平台保存的密码可以在这里清掉
        root.addView(buildAccountCard());

        // 开源地址
        root.addView(linkCard("项目开源地址",
                "github.com/xtt-xt/Nemo-Mobile-Mod-Studio", Constants.GITHUB_PAGE));
        // 开源鸣谢（单独一页）
        OreCard creditsCard = plainCard("开源鸣谢", "MCDev API 文档、MCDevManager、Ore UI、sora-editor");
        creditsCard.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(AboutActivity.this, CreditsActivity.class));
            }
        });
        root.addView(creditsCard);
    }

    /** 版本卡片：显示版本号，整块点一下就去 GitHub 查有没有新版本。 */
    private OreCard buildVersionCard() {
        OreCard card = new OreCard(this);
        card.setPadding(16, 12, 16, 12);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        versionText = new OreTextView(this);
        versionText.setTextColor(Color.WHITE);
        versionText.setTextSize(14);
        versionText.setGravity(Gravity.CENTER);
        layout.addView(versionText);

        OreTextView hint = new OreTextView(this);
        hint.setText("点击检查更新");
        hint.setTextColor(Color.parseColor("#8A8A8A"));
        hint.setTextSize(10);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, UiUtils.dp(this, 3), 0, 0);
        layout.addView(hint);

        card.addView(layout);
        refreshVersionText();
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { checkForUpdate(); }
        });
        return card;
    }

    private void refreshVersionText() {
        if (versionText == null) return;
        String versionName = "未知";
        int versionCode = 1;
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = info.versionName;
            versionCode = info.versionCode;
        } catch (Exception ignored) {
        }
        versionText.setText("当前版本：" + versionName + " (" + versionCode + ")");
    }

    private void checkForUpdate() {
        UiUtils.toast(this, "正在检查更新...");
        String currentVersion = "";
        try {
            currentVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        UpdateChecker.checkLatest(currentVersion, new UpdateChecker.Callback() {
            @Override
            public void onResult(final UpdateChecker.ReleaseInfo release) {
                UiUtils.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (release == null) {
                            UiUtils.toast(AboutActivity.this, "已经是最新版本");
                        } else {
                            showUpdateDialog(release.version, release.body, release.htmlUrl);
                        }
                    }
                });
            }

            @Override
            public void onError(final String message) {
                UiUtils.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        UiUtils.toast(AboutActivity.this, "检查更新失败：" + message);
                    }
                });
            }
        });
    }

    private void showUpdateDialog(String newVersion, String body, final String releaseUrl) {
        if (body != null && body.length() > 300) body = body.substring(0, 300) + "...";

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
        builder.show();
    }

    /** 账号卡片：显示存没存密码，并提供清除按钮。 */
    private OreCard buildAccountCard() {
        OreCard card = new OreCard(this);
        card.setPadding(16, 12, 16, 12);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        OreTextView title = new OreTextView(this);
        title.setText("已保存的账号密码");
        title.setTextColor(Color.WHITE);
        title.setTextSize(14);
        layout.addView(title);

        final OreTextView state = new OreTextView(this);
        state.setTextColor(Color.parseColor("#AAAAAA"));
        state.setTextSize(11);
        state.setPadding(0, UiUtils.dp(this, 4), 0, 0);
        layout.addView(state);

        final OreButton clearBtn = new OreButton(this);
        clearBtn.setText("清除已保存的密码");
        clearBtn.setStyleSheet(StyleSheet.STYLE_RED);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.topMargin = UiUtils.dp(this, 8);
        clearBtn.setLayoutParams(btnParams);
        layout.addView(clearBtn);

        card.addView(layout);

        updateAccountCard(state, clearBtn);
        clearBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                DevAuth auth = DevAuth.load(AboutActivity.this);
                if (auth == null || auth.password == null || auth.password.isEmpty()) {
                    UiUtils.toast(AboutActivity.this, "没有保存密码");
                    return;
                }
                new OreDialogBuilder(AboutActivity.this)
                        .setTitle("清除已保存的密码")
                        .setMessage("清除后会同时退出登录，之后需要重新输入邮箱和密码才能继续管理作品。")
                        .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) { d.dismiss(); }
                        })
                        .setPositiveButton("清除", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                // 密码一清就没法自动重登了，登录态跟着一起退出，
                                // 免得留着一个「登录着但续不了期」的半吊子状态
                                DevAuth.clear(AboutActivity.this);
                                UiUtils.toast(AboutActivity.this, "已清除保存的密码并退出登录");
                                updateAccountCard(state, clearBtn);
                                d.dismiss();
                            }
                        })
                        .show();
            }
        });
        return card;
    }

    private void updateAccountCard(OreTextView state, OreButton clearBtn) {
        DevAuth auth = DevAuth.load(this);
        boolean hasPwd = auth != null && auth.password != null && !auth.password.isEmpty();
        boolean hasCookie = auth != null && auth.cookie != null && !auth.cookie.isEmpty();
        StringBuilder sb = new StringBuilder();
        if (auth != null && auth.email != null && !auth.email.isEmpty()) {
            sb.append("账号：").append(auth.email).append('\n');
        } else {
            sb.append("账号：未保存\n");
        }
        sb.append("密码：").append(hasPwd ? "已保存，登录失效会自动重新登录" : "未保存");
        if (!hasCookie) sb.append("\n当前没有登录");
        state.setText(sb.toString());
        clearBtn.setEnabled(hasPwd);
    }

    /** 只有标题和一行说明的卡片，不带箭头。 */
    private OreCard plainCard(String title, String desc) {
        OreCard card = new OreCard(this);
        card.setPadding(16, 12, 16, 12);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        OreTextView tvTitle = new OreTextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(14);
        layout.addView(tvTitle);

        OreTextView tvDesc = new OreTextView(this);
        tvDesc.setText(desc);
        tvDesc.setTextColor(Color.GRAY);
        tvDesc.setTextSize(11);
        layout.addView(tvDesc);

        card.addView(layout);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = UiUtils.dp(this, 6);
        card.setLayoutParams(params);
        return card;
    }

    private OreCard linkCard(String title, String linkText, final String url) {
        OreCard card = new OreCard(this);
        card.setPadding(16, 12, 16, 12);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        OreTextView tvTitle = new OreTextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(14);
        layout.addView(tvTitle);
        OreTextView tvLink = new OreTextView(this);
        tvLink.setText(linkText);
        tvLink.setTextColor(Color.parseColor("#AAAAAA"));
        tvLink.setTextSize(11);
        layout.addView(tvLink);
        card.addView(layout);
        if (url != null) {
            card.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { openBrowser(url); }
            });
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = UiUtils.dp(this, 6);
        card.setLayoutParams(params);
        return card;
    }

    private void openBrowser(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url.startsWith("http") ? url : "https://" + url));
            startActivity(intent);
        } catch (Exception e) {
            // 无浏览器
        }
    }
}
