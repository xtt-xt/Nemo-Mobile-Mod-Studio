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
 * 关于页面。
 *
 * 版式统一成一套：顶部应用标识居中，下面全是左对齐的卡片，
 * 每张卡片内边距一致、卡间距一致。之前混用了居中与左对齐、像素与 dp 两种单位，
 * 排出来就散了。
 */
public class AboutActivity extends Activity {

    /** 卡片内边距（dp）。 */
    private static final int PAD_H = 14;
    private static final int PAD_V = 12;
    /** 卡片之间的间距（dp）。 */
    private static final int GAP = 10;

    private OreTextView versionText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.parseColor("#1E1E1E"));
        setContentView(page);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.parseColor("#1E1E1E"));
            getWindow().setNavigationBarColor(Color.parseColor("#1E1E1E"));
        }

        OreButton btnBack = new OreButton(this);
        btnBack.setText("返回");
        btnBack.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btnBack.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        page.addView(btnBack);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        page.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padH = UiUtils.dp(this, 16);
        root.setPadding(padH, UiUtils.dp(this, 16), padH, UiUtils.dp(this, 20));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        addHeader(root);
        root.addView(buildVersionCard());
        root.addView(buildDataCard());
        root.addView(buildAccountCard());
        root.addView(buildLinkCard());
        root.addView(buildCreditsCard());
    }

    // ==================== 顶部应用标识 ====================

    private void addHeader(LinearLayout root) {
        ImageView appIcon = new ImageView(this);
        appIcon.setImageResource(R.drawable.ic_launcher);
        int iconSize = UiUtils.dp(this, 72);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        appIcon.setLayoutParams(iconParams);
        root.addView(appIcon);

        OreTextView appName = new OreTextView(this);
        appName.setText("网易模组制作器");
        appName.setTextColor(Color.WHITE);
        appName.setTextSize(17);
        appName.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = UiUtils.dp(this, 10);
        appName.setLayoutParams(nameParams);
        root.addView(appName);

        OreTextView appSub = new OreTextView(this);
        appSub.setText("Nemo Mobile Mod Studio");
        appSub.setTextColor(Color.parseColor("#9A9A9A"));
        appSub.setTextSize(12);
        appSub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = UiUtils.dp(this, 3);
        subParams.bottomMargin = UiUtils.dp(this, 16);
        appSub.setLayoutParams(subParams);
        root.addView(appSub);
    }

    // ==================== 卡片骨架 ====================

    /** 统一规格的卡片：内边距一致、下方留同样的间距。 */
    private OreCard baseCard() {
        OreCard card = new OreCard(this);
        // OreCard 继承 LinearLayout，默认是横向的：不显式设成纵向，
        // 往里加的标题和正文会并排成一行
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(UiUtils.dp(this, PAD_H), UiUtils.dp(this, PAD_V),
                UiUtils.dp(this, PAD_H), UiUtils.dp(this, PAD_V));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UiUtils.dp(this, GAP);
        card.setLayoutParams(lp);
        return card;
    }

    /** 卡片标题。 */
    private OreTextView cardTitle(String text) {
        OreTextView t = new OreTextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(14);
        return t;
    }

    /** 卡片里的小字提示（例如「点击检查更新」），比正文更淡。 */
    private OreTextView cardHint(String text) {
        OreTextView t = new OreTextView(this);
        t.setText(text);
        t.setTextColor(Color.parseColor("#7A7A7A"));
        t.setTextSize(10);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = UiUtils.dp(this, 4);
        t.setLayoutParams(lp);
        return t;
    }

    /** 卡片正文，灰色小字，带一点行距。 */
    private OreTextView cardBody(String text) {
        OreTextView t = new OreTextView(this);
        t.setText(text);
        t.setTextColor(Color.parseColor("#A8A8A8"));
        t.setTextSize(11);
        t.setLineSpacing(UiUtils.dp(this, 2), 1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = UiUtils.dp(this, 4);
        t.setLayoutParams(lp);
        return t;
    }

    // ==================== 各张卡片 ====================

    /** 版本卡片：整块点一下就查更新，不再单独放按钮。 */
    private OreCard buildVersionCard() {
        OreCard card = baseCard();
        card.addView(cardTitle("当前版本"));

        versionText = new OreTextView(this);
        versionText.setTextColor(Color.WHITE);
        versionText.setTextSize(14);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        vp.topMargin = UiUtils.dp(this, 2);
        versionText.setLayoutParams(vp);
        card.addView(versionText);

        card.addView(cardHint("点击检查更新"));

        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { checkForUpdate(); }
        });
        refreshVersionText();
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
        versionText.setText(versionName + "  (" + versionCode + ")");
    }

    private OreCard buildDataCard() {
        OreCard card = baseCard();
        card.addView(cardTitle("内置数据版本"));
        card.addView(cardBody("物品库：基岩版 " + ItemRegistry.VANILLA_VERSION
                + "（网易 " + ItemRegistry.NETEASE_VERSION + "）\n"
                + "原版方块与物品 " + ItemRegistry.vanillaCount(this) + " 项\n"
                + "方块图标：内置 " + com.xtt.mcmodmaker.core.BlockTextureLib.blockCount(this)
                + " 种方块贴图，按六面实时渲染\n"
                + "数据来源：Mojang bedrock-samples、BedrockData"));
        return card;
    }

    private OreCard buildAccountCard() {
        final OreCard card = baseCard();
        card.addView(cardTitle("已保存的账号"));

        final OreTextView state = cardBody("");
        card.addView(state);
        card.addView(cardHint("点击清除密码并退出登录"));

        updateAccountCard(state, card);

        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                DevAuth auth = DevAuth.load(AboutActivity.this);
                if (auth == null) {
                    UiUtils.toast(AboutActivity.this, "没有保存的账号");
                    return;
                }
                new OreDialogBuilder(AboutActivity.this)
                        .setTitle("清除密码并退出登录")
                        .setMessage("会删除保存的账号、密码和登录状态，之后要重新输入邮箱和密码。")
                        .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) { d.dismiss(); }
                        })
                        .setPositiveButton("清除", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                // 密码一清就没法自动重登了，登录态跟着一起退出，
                                // 免得留一个「登录着但续不了期」的半吊子状态
                                DevAuth.clear(AboutActivity.this);
                                UiUtils.toast(AboutActivity.this, "已清除账号并退出登录");
                                updateAccountCard(state, card);
                                d.dismiss();
                            }
                        })
                        .show();
            }
        });
        return card;
    }

    private void updateAccountCard(OreTextView state, OreCard card) {
        DevAuth auth = DevAuth.load(this);
        boolean hasAccount = auth != null && auth.email != null && !auth.email.isEmpty();
        boolean hasPwd = auth != null && auth.password != null && !auth.password.isEmpty();
        boolean hasCookie = auth != null && auth.cookie != null && !auth.cookie.isEmpty();

        StringBuilder sb = new StringBuilder();
        if (!hasAccount && !hasCookie) {
            sb.append("未保存任何账号，登录后会自动保存");
            card.setEnabled(false);
        } else {
            sb.append("账号：").append(hasAccount ? auth.email : "未保存");
            sb.append('\n').append("密码：").append(hasPwd ? "已保存，登录失效会自动重新登录" : "未保存");
            sb.append('\n').append("状态：").append(hasCookie ? "已登录" : "未登录");
            card.setEnabled(true);
        }
        state.setText(sb.toString());
    }

    private OreCard buildLinkCard() {
        OreCard card = baseCard();
        card.addView(cardTitle("项目开源地址"));
        card.addView(cardBody("github.com/xtt-xt/Nemo-Mobile-Mod-Studio"));
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openBrowser(Constants.GITHUB_PAGE); }
        });
        return card;
    }

    private OreCard buildCreditsCard() {
        OreCard card = baseCard();
        card.addView(cardTitle("开源鸣谢"));
        card.addView(cardBody("MCDev API 文档、MCDevManager、Ore UI、sora-editor"));
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(AboutActivity.this, CreditsActivity.class));
            }
        });
        return card;
    }

    // ==================== 检查更新 ====================

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

    private void openBrowser(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse(url.startsWith("http") ? url : "https://" + url));
            startActivity(intent);
        } catch (Exception e) {
            // 无浏览器
        }
    }
}
