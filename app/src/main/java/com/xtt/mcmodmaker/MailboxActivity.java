package com.xtt.mcmodmaker;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.xtt.mcmodmaker.core.Constants;
import com.xtt.mcmodmaker.net.McDevApi;
import com.xtt.mcmodmaker.util.SettingsManager;
import com.xtt.mcmodmaker.util.UiUtils;

import java.util.ArrayList;
import java.util.List;

import dev1503.oreui.StyleSheet;
import dev1503.oreui.dialog.OreDialogBuilder;
import dev1503.oreui.widgets.OreButton;
import dev1503.oreui.widgets.OreCard;
import dev1503.oreui.widgets.OreTextView;

/** 网易开发者平台收件箱。所有会改变服务器消息状态的操作均须用户确认。 */
public class MailboxActivity extends Activity {
    private static final int BG = Color.rgb(26, 26, 26);
    private final String[] typeKeys = {null, "system_notice", "important_notice", "review_notice", "issue_feedback", "notify"};
    private final String[] typeLabels = {"全部", "系统通知", "重要通知", "审核通知", "问题反馈", "提醒"};
    private String selectedType;
    private String cookie;
    private LinearLayout list;
    private OreTextView status;
    private OreButton[] filters;
    private List<McDevApi.MailItem> mails = new ArrayList<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(0); // 深色背景使用浅色系统图标。
        }
        cookie = SettingsManager.getInstance(this).getString(Constants.PREFS_MC_COOKIE, "");
        buildUi();
        if (cookie == null || cookie.isEmpty()) { status.setText("请先登录后查看收件箱"); return; }
        loadMail();
    }

    private int dp(int n) { return UiUtils.dp(this, n); }
    private OreTextView text(String value, float size, int color) { OreTextView v = new OreTextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); return v; }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        root.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        OreButton back = new OreButton(this); back.setText("返回"); back.setTextSize(13); back.setStyleSheet(StyleSheet.STYLE_DARK_GRAY); back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(70), dp(40)));
        OreTextView title = text("消息中心", 20, Color.WHITE); title.setGravity(Gravity.CENTER_VERTICAL); title.setPadding(dp(8), 0, 0, 0);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(40), 1));
        OreButton readAll = new OreButton(this); readAll.setText("全部已读"); readAll.setTextSize(12); readAll.setStyleSheet(StyleSheet.STYLE_DARK_GRAY); readAll.setOnClickListener(v -> confirmMarkAllRead());
        top.addView(readAll, new LinearLayout.LayoutParams(dp(88), dp(40)));
        OreButton refresh = new OreButton(this); refresh.setText("刷新"); refresh.setTextSize(12); refresh.setStyleSheet(StyleSheet.STYLE_DARK_GRAY); refresh.setOnClickListener(v -> loadMail());
        top.addView(refresh, new LinearLayout.LayoutParams(dp(62), dp(40)));
        root.addView(top);
        status = text("", 12, Color.rgb(170,170,170)); status.setPadding(0, dp(5), 0, dp(5)); root.addView(status);

        // 完全复用 SourceEditorActivity 的稳定结构：HorizontalScrollView 的唯一直接子项是横向 LinearLayout，
        // OreButton 直接加入该容器（不包 holder、不强设高度），由 OreUI 按默认测量绘制完整边框背景。
        HorizontalScrollView filterScroll = new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout filterContainer = new LinearLayout(this);
        filterContainer.setOrientation(LinearLayout.HORIZONTAL);
        filterScroll.addView(filterContainer);
        filters = new OreButton[typeKeys.length];
        for (int i = 0; i < typeKeys.length; i++) {
            final int index = i;
            OreButton b = new OreButton(this);
            b.setText(typeLabels[i]);
            b.setTextSize(12);
            b.setStyleSheet(i == 0 ? StyleSheet.STYLE_GREEN : StyleSheet.STYLE_DARK_GRAY);
            b.setOnClickListener(v -> { selectedType = typeKeys[index]; refreshFilters(); loadMail(); });
            filters[i] = b;
            filterContainer.addView(b); // 与 SourceEditorActivity 的 tabContainer.addView(tab) 一致。
        }
        root.addView(filterScroll, new LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT));
        ScrollView scroll = new ScrollView(this); list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(0, dp(7), 0, dp(7)); scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        OreButton deleteRead = new OreButton(this); deleteRead.setText("删除已读"); deleteRead.setTextSize(13); deleteRead.setStyleSheet(StyleSheet.STYLE_RED); deleteRead.setOnClickListener(v -> confirmDeleteRead());
        root.addView(deleteRead, new LinearLayout.LayoutParams(-1, dp(40)));
        setContentView(root); refreshFilters();
    }

    private void refreshFilters() {
        for (int i = 0; i < filters.length; i++) {
            boolean active = selectedType == null ? typeKeys[i] == null : selectedType.equals(typeKeys[i]);
            filters[i].setStyleSheet(active ? StyleSheet.STYLE_GREEN : StyleSheet.STYLE_DARK_GRAY);
            filters[i].requestLayout();
            filters[i].invalidate();
        }
    }
    private void loadMail() { status.setText("加载消息中..."); new Thread(() -> { final List<McDevApi.MailItem> r=McDevApi.getMailbox(cookie,selectedType); final String err=McDevApi.getLastError(); UiUtils.runOnUiThread(()->{ if(r==null){status.setText(err.isEmpty()?"加载消息失败":err);return;} mails=r;status.setText("共 "+r.size()+" 条消息");render();}); }).start(); }
    private void render() { list.removeAllViews(); if(mails.isEmpty()){OreTextView e=text("暂无消息",14,Color.rgb(136,136,136));e.setGravity(Gravity.CENTER);list.addView(e,new LinearLayout.LayoutParams(-1,dp(120)));return;} for(final McDevApi.MailItem m:mails){ OreCard c=new OreCard(this);c.setPadding(dp(14),dp(11),dp(14),dp(11));LinearLayout col=new LinearLayout(this);col.setOrientation(LinearLayout.VERTICAL);OreTextView t=text((m.haveRead?"":"● ")+m.title,16,m.haveRead?Color.rgb(204,204,204):Color.WHITE);col.addView(t);LinearLayout meta=new LinearLayout(this);OreTextView tag=text(typeLabel(m.mailType),11,Color.rgb(159,197,255));meta.addView(tag);OreTextView time=text("  "+formatTime(m.time),11,Color.rgb(153,153,153));meta.addView(time);col.addView(meta);c.addView(col);c.setOnClickListener(v->openMail(m));list.addView(c,new LinearLayout.LayoutParams(-1,-2));UiUtils.addGap(this,list,7);} }
    private String typeLabel(String key){for(int i=0;i<typeKeys.length;i++)if(key!=null&&key.equals(typeKeys[i]))return typeLabels[i];return key==null||key.isEmpty()?"通知":key;}
    private String formatTime(String raw) { if(raw==null)return ""; try { if(raw.length()>=16 && raw.charAt(4)=='-' && raw.charAt(7)=='-') return raw.substring(5,10)+" "+raw.substring(11,16); } catch(Exception ignored){} return raw; }
    private void openMail(final McDevApi.MailItem m){new Thread(()->{final McDevApi.MailContent content=McDevApi.getMailContent(cookie,m.id);final String err=McDevApi.getLastError();UiUtils.runOnUiThread(()->{if(content==null){UiUtils.toast(this,err.isEmpty()?"打开消息失败":err);return;}m.haveRead=true;render();showMailDetail(m,content);});}).start();}
    private void showMailDetail(final McDevApi.MailItem m,McDevApi.MailContent c) {
        LinearLayout b = new LinearLayout(this); b.setOrientation(LinearLayout.VERTICAL);
        int dialogPadding = dp(16);
        b.setPadding(dialogPadding, dp(8), dialogPadding, dp(8));
        OreTextView sender = text("来自：" + (c.sender.isEmpty() ? "—" : c.sender) + "\n" + formatTime(m.time), 13, Color.rgb(180,180,180));
        b.addView(sender, new LinearLayout.LayoutParams(-1, -2));
        OreTextView d = text(Html.fromHtml(c.detail).toString(), 14, Color.WHITE); d.setPadding(0, dp(12), 0, dp(4)); b.addView(d, new LinearLayout.LayoutParams(-1, -2));
        new OreDialogBuilder(this).setTitle(m.title).setView(b)
                .setNegativeButton("关闭", (x,w)->x.dismiss())
                .setPositiveButton("删除", (x,w)->{x.dismiss();confirmDeleteOne(m);}).show();
    }
    private void confirmMarkAllRead(){showConfirm("全部标记为已读","将把收件箱中的未读消息标记为已读，是否继续？","确认",this::markAllRead);}
    private void markAllRead(){new Thread(()->{final boolean ok=McDevApi.readAllMail(cookie);final String err=McDevApi.getLastError();UiUtils.runOnUiThread(()->{UiUtils.toast(this,ok?"已全部标记为已读":(err.isEmpty()?"操作失败":err));if(ok)loadMail();});}).start();}
    private void confirmDeleteRead(){ArrayList<String> ids=new ArrayList<>();for(McDevApi.MailItem m:mails)if(m.haveRead)ids.add(m.id);if(ids.isEmpty()){UiUtils.toast(this,"没有已读消息可删除");return;}final ArrayList<String> finalIds=ids;showConfirm("删除已读消息","确定删除当前列表内 "+ids.size()+" 条已读消息？此操作不可恢复。","删除",()->deleteMails(finalIds));}
    private void confirmDeleteOne(final McDevApi.MailItem m){ArrayList<String> ids=new ArrayList<>();ids.add(m.id);showConfirm("删除消息","确定删除这条消息？此操作不可恢复。","删除",()->deleteMails(ids));}
    // 与主页弹窗保持一致：原生标题 + 自定义正文，正文左对齐并保留统一左右边距。
    private void showConfirm(String heading,String message,String positive,final Runnable action) {
        OreTextView bodyText = text(message, 14, Color.WHITE);
        bodyText.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_VERTICAL);
        int padding = dp(16);
        body.setPadding(padding, padding, padding, padding);
        body.addView(bodyText, new LinearLayout.LayoutParams(-1, -2));

        OreDialogBuilder builder = new OreDialogBuilder(this);
        builder.setTitle(heading);
        builder.setView(body);
        builder.setNegativeButton("取消", (d,w)->d.dismiss());
        builder.setPositiveButton(positive, (d,w)->{d.dismiss();action.run();});
        if ("删除".equals(positive)) builder.getPositiveButton().setStyleSheet(StyleSheet.STYLE_RED);
        builder.show();
    }
    private void deleteMails(final List<String> ids){new Thread(()->{final boolean ok=McDevApi.deleteMails(cookie,ids);final String err=McDevApi.getLastError();UiUtils.runOnUiThread(()->{UiUtils.toast(this,ok?"已删除":(err.isEmpty()?"删除失败":err));if(ok)loadMail();});}).start();}
}
