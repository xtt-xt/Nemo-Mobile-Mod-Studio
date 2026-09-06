package com.xtt.mcmodmaker;

import android.app.Activity;
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
import com.xtt.mcmodmaker.util.UiUtils;

import dev1503.oreui.StyleSheet;
import dev1503.oreui.widgets.OreButton;
import dev1503.oreui.widgets.OreCard;
import dev1503.oreui.widgets.OreTextView;

/** 关于页面：应用介绍、版本、开源地址、开源鸣谢。 */
public class AboutActivity extends Activity {

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

        // 内容区独立滚动，避免较小屏幕下鸣谢卡片被截断。
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

        // 版本卡片
        OreCard versionCard = new OreCard(this);
        versionCard.setPadding(16, 12, 16, 12);
        OreTextView versionText = new OreTextView(this);
        String versionName = "未知";
        int versionCode = 1;
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = info.versionName;
            versionCode = info.versionCode;
        } catch (Exception ignored) {
        }
        versionText.setText("当前版本：" + versionName + " (" + versionCode + ")");
        versionText.setTextColor(Color.WHITE);
        versionText.setTextSize(14);
        versionText.setGravity(Gravity.CENTER);
        versionCard.addView(versionText);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.gravity = Gravity.CENTER;
        cardParams.topMargin = UiUtils.dp(this, 8);
        versionCard.setLayoutParams(cardParams);
        root.addView(versionCard);

        // 开源地址
        root.addView(linkCard("项目开源地址",
                "github.com/xtt-xt/Nemo-Mobile-Mod-Studio", Constants.GITHUB_PAGE));
        // MCDev API 文档致谢（本 App 管理页 API 实现参考）
        root.addView(creditCard("MCDev API 接口文档",
                "网易我的世界开发者平台非官方 API 接口文档\nGitHub 开源 + 在线部署（mcdev-docs.xtt.p8.ink）",
                "作者 xtt-xt", "https://github.com/xtt-xt/mcdev-api-docs"));
        // MCDevManager 致谢（登录加密 / 作品管理 API 参考来源）
        root.addView(creditCard("MCDevManager",
                "网易我的世界开发者内容管理工具（Kotlin Multiplatform）\n本 App 的开发者平台 API 实现参考其开源源码",
                "感谢 BitterLemonn 及所有贡献者",
                "https://github.com/BitterLemonn/MCDevManager"));
        // Ore UI 致谢
        root.addView(creditCard("界面组件库：Ore UI for Android",
                "基于 OreUI 标准实现的纯 Kotlin 组件库\n使用 Apache License 2.0 开源许可",
                "感谢原作者 TheChuan1503 及所有贡献者", Constants.ORE_UI_PAGE));
        // 文件编辑器致谢（sora-editor）
        root.addView(creditCard("代码编辑器：sora-editor",
                "高性能开源代码编辑器（语法高亮 / 代码补全 / 搜索）\n使用 Apache License 2.0 开源许可",
                "感谢作者 Rosemoe 及所有贡献者",
                "https://github.com/Rosemoe/sora-editor"));
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

    private OreCard creditCard(String title, String desc, String credit, final String url) {
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
        OreTextView tvCredit = new OreTextView(this);
        tvCredit.setText(credit);
        tvCredit.setTextColor(Color.parseColor("#AAAAAA"));
        tvCredit.setTextSize(10);
        layout.addView(tvCredit);
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