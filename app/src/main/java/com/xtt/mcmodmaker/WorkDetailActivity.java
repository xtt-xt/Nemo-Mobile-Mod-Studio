package com.xtt.mcmodmaker;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.Spanned;
import android.os.Build;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.xtt.mcmodmaker.core.Constants;
import com.xtt.mcmodmaker.net.McDevApi;
import com.xtt.mcmodmaker.util.SettingsManager;
import com.xtt.mcmodmaker.util.UiUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev1503.oreui.StyleSheet;
import dev1503.oreui.dialog.OreDialogBuilder;
import dev1503.oreui.widgets.OreAccordion;
import dev1503.oreui.widgets.OreButton;
import dev1503.oreui.widgets.OreCard;
import dev1503.oreui.widgets.OreEditText;
import dev1503.oreui.widgets.OreTextView;
import dev1503.oreui.widgets.OreSwitch;
import dev1503.oreui.widgets.OreSwitch;

/**
 * 作品详情页（新增/编辑共用）——完全对齐 MCDevManager（网页版）布局。
 */
public class WorkDetailActivity extends Activity {

    // 文件选择请求码
    private static final int REQ_RES = 1001;
    private static final int REQ_CHANNEL_IMG = 1002;
    private static final int REQ_VIDEO = 1003;
    private static final int REQ_CORP = 1004;
    private static final int REQ_DETAIL_IMG = 1005;
    private static final int REQ_CHANNEL_CROP = 1006;

    private boolean isEdit;
    private String itemId = "";
    private String cookie = "";
    private JSONObject detail;

    private LinearLayout formLayout;
    private OreTextView statusText;
    private OreButton btnSave, btnReview;

    // ===== 基本信息 =====
    private OreEditText etName;
    private OreTextView tvItemId, tvNormalNumber, tvItemVersion; // 只读元数据
    private YesNoBox boxShantou;   // 我的山头
    private YesNoBox boxOriginal;  // 原创
    private YesNoBox boxRelated;   // 关联模组
    private LinearLayout relatedBox;   // 关联模组展开区
    private boolean relatedIsMaster = true;
    private OreEditText etRelatedSearch;
    private OreTextView tvRelatedSel;
    private String relateItemId = "";
    private YesNoBox boxSyncPc;    // 同步 PC
    private LinearLayout corpRow;  // 授权图行
    private OreTextView tvCorpProof;
    private String corpProofImage = "";
    private OreEditText etPrereq;  // 前置模组
    private OreEditText etTags;    // 模组标签
    private OreEditText etActivityDesc; // 活动参与说明

    // ===== PC 基本信息（syncPc）=====
    private LinearLayout pcBasicBox;
    private YesNoBox boxPcIncludeMap;
    private OreEditText etPcTags, etPcBrief;

    // ===== 定价 =====
    private int priceTypeIdx = 2; // 0=免费 1=绿宝石 2=钻石（默认钻石）
    private int priceRank = 0;    // 0-6
    private OreButton btnDiamond, btnPoint, btnFree;
    private LinearLayout rowRank;
    private OreButton[] rankBtns = new OreButton[7];
    private OreEditText etPriceInput;   // 绿宝石输入
    private OreTextView tvPriceRead;    // 钻石/免费只读
    private boolean priceTypeLocked = false;

    // ===== PE 详情 / 更新纪要 =====
    private OreEditText etDetail;
    // 服务端原始 HTML：未编辑时必须原样提交，避免 Android Html.toHtml 改写段落/img 后导致保存失败。
    private String originalDetailHtml = "";
    private boolean detailHtmlDirty = false;
    private boolean settingDetailHtml = false;
    private OreEditText etUpdateSummary;
    private OreTextView tvSummaryCount;
    private static final int SUMMARY_MAX = 200;

    // ===== PC 详情 / PC 上架设置（syncPc）=====
    private LinearLayout pcDetailBox;
    private OreEditText etPcDetail;
    private YesNoBox boxPcWeakOffline;
    private OreEditText etPcWeakOfflineReason;

    // ===== PE 上架设置 =====
    private YesNoBox boxWeakOffline;
    private OreEditText etWeakOfflineReason;

    // ===== PE 资源管理 =====
    private static class MCConst { int id; String title; MCConst(int id, String title) { this.id = id; this.title = title; } }
    private static class ChannelSlot {
        int channelId; String title; int version; String fileJson = ""; String fileName = "";
        String remoteUrl = ""; ImageView preview; Bitmap fullBitmap;
        ChannelSlot(int channelId, String title, int version) { this.channelId = channelId; this.title = title; this.version = version; }
    }
    private List<MCConst> peTypeOptions = new ArrayList<>();
    private List<MCConst> peSubTypeOptions = new ArrayList<>();
    private List<MCConst> peModSecondOptions = new ArrayList<>();
    private int peType = 0, peSubType = 0, peModSecond = 0;
    private OreAccordion accTypeValue, accSubTypeValue, accModSecondValue, accTagPlayValue, accTagThemeValue;
    private List<Integer> recommendTags = new ArrayList<>();
    private YesNoBox boxPlayPlan, boxMount, boxAddVersion;
    private String resFileJson = "";
    private String resFileName = "";
    private OreTextView tvResFile;
    private OreButton btnResRemove;
    private LinearLayout resDropZone, resFileRow;
    // 参考平台仅暴露 modAPI 版本；mc_version 由资源包/已有详情保留，不在表单中编辑。
    private List<String> modApiVersionOptions = new ArrayList<>();
    private String selectedModApiVersion = "";
    private OreCard modApiVersionCard;
    private OreTextView tvModApiVersion;
    private List<MCConst> playTagCache = new ArrayList<>();
    private List<MCConst> themeTagCache = new ArrayList<>();
    private Map<Integer, OreSwitch> recommendTagSwitches = new LinkedHashMap<>();
    private Map<Integer, Integer> recommendTagGroups = new LinkedHashMap<>();
    private static final int MAX_RECOMMEND_TAGS_PER_GROUP = 2;
    // 接口数据回填时 setChecked 会触发监听；该标志避免把已选标签反向移除。
    private boolean syncingRecommendTagSwitches = false;
    // 每次重绘频道行递增；旧页面/旧视图的异步回调不得覆盖新页面。
    private int channelRenderGeneration = 0;
    private Map<Integer, List<MCConst>> subTypeCache = new LinkedHashMap<>();
    // mod_second_type 与 sub_type 一样按 pri_type ID 分组，例如 mod_second_type["2"] 对应 add_ons。
    private Map<Integer, List<MCConst>> modSecondTypeCache = new LinkedHashMap<>();
    private Map<Integer, List<MCConst>> collectSubMap() { return subTypeCache; }

    // ===== 渠道图 =====
    private List<ChannelSlot> channelSlots = new ArrayList<>();
    // 详情与频道配置是并发加载的：先到的详情 URL 必须暂存，等待频道槽位创建后再绑定。
    private Map<Integer, Object> remoteChannelData = new LinkedHashMap<>();
    private LinearLayout rowChannel;

    // ===== 视频 =====
    private String videoJson = "";
    private String videoName = "";
    // 编辑时显式删除远端视频，避免“未修改”与“删除”混淆。
    private boolean videoRemoved = false;
    private OreTextView tvVideo;
    private OreButton btnVideoRemove;

    // 待上传本地文件
    private String pendingResUri = null, pendingResName = null;
    private String pendingCorpUri = null, pendingCorpName = null;
    private Map<Integer, String> pendingChannelUris = new LinkedHashMap<>();
    private Map<Integer, String> pendingChannelNames = new LinkedHashMap<>();
    private String pendingVideoUri = null, pendingVideoName = null;
    private int pendingChannelSel = -1;
    private LinearLayout videoDropView, videoRowView;

    // ===== 内部类：是/否胶囊选择器 =====
    /** 布尔字段统一使用 OreSwitch，避免双选胶囊在窄屏权重布局中错位。 */
    private static class YesNoBox {
        boolean value;
        OreSwitch toggle;
    }

    // ==================== 生命周期 ====================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isEdit = "edit".equals(getIntent().getStringExtra("mode"));
        itemId = getIntent().getStringExtra("item_id") == null ? "" : getIntent().getStringExtra("item_id");
        SettingsManager settings = SettingsManager.getInstance(this);
        cookie = settings.getString(Constants.PREFS_MC_COOKIE, "");
        if (cookie == null || cookie.isEmpty()) {
            UiUtils.toast(this, "请先登录");
            finish();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFocusableInTouchMode(true);
        root.setFocusable(true);
        root.requestFocus();
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        root.setPadding(UiUtils.dp(this, 16), UiUtils.dp(this, 12),
                UiUtils.dp(this, 16), UiUtils.dp(this, 12));
        root.setBackgroundColor(Color.parseColor("#1A1A1A"));
        setContentView(root);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.parseColor("#1A1A1A"));
            getWindow().setNavigationBarColor(Color.parseColor("#1A1A1A"));
        }

        // 顶栏：返回 | 标题 | 保存 | 提审
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        OreButton btnBack = new OreButton(this);
        btnBack.setText("返回");
        btnBack.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        topRow.addView(btnBack);
        OreTextView title = new OreTextView(this);
        title.setText(isEdit ? "作品详情" : "新建作品");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        topRow.addView(title);
        btnSave = new OreButton(this);
        btnSave.setText("保存");
        btnSave.setStyleSheet(StyleSheet.STYLE_GREEN);
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onSave(false); }
        });
        topRow.addView(btnSave);
        addHorizontalGap(topRow, 6);
        btnReview = new OreButton(this);
        btnReview.setText("提审");
        btnReview.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btnReview.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onSave(true); }
        });
        topRow.addView(btnReview);
        root.addView(topRow);
        UiUtils.addGap(this, root, 4);

        statusText = new OreTextView(this);
        statusText.setTextColor(Color.parseColor("#AAAAAA"));
        statusText.setTextSize(12);
        statusText.setVisibility(View.GONE);
        root.addView(statusText);

        ScrollView scroll = new ScrollView(this);
        formLayout = new LinearLayout(this);
        formLayout.setOrientation(LinearLayout.VERTICAL);
        formLayout.setPadding(0, 8, 0, 8);
        scroll.addView(formLayout);
        scroll.setFocusableInTouchMode(true);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        buildForm();
        scroll.requestFocus();
        loadOptions();
        if (isEdit) loadDetail();
    }

    // ==================== UI 构建（MCDevManager 同构）====================
    private LinearLayout currentCard;

    private void buildForm() {
        // ---------- 1. 基本信息 ----------
        addCardSection("基本信息");
        etName = addEdit("资源名称", false);
        // 只读元数据（编辑时显示）
        LinearLayout metaRow = new LinearLayout(this);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        tvItemId = addReadOnlyInto(metaRow, "资源ID", "");
        tvNormalNumber = addReadOnlyInto(metaRow, "模组码", "");
        tvItemVersion = addReadOnlyInto(metaRow, "资源版本", "");
        currentCard.addView(metaRow);
        UiUtils.addGap(this, currentCard, 8);
        // 是/否 选项组（2x2）
        boxShantou = addBinarySelector("是否加入到「我的山头」专区", "是", "否", false);
        boxOriginal = addBinarySelector("是否原创作品", "是", "否", true);
        boxRelated = addBinarySelector("是否为关联模组", "是", "否", false);
        boxSyncPc = addBinarySelector("是否同步生成 PC 模组", "是", "否", false);
        // 授权信息图（非原创时显示）
        corpRow = new LinearLayout(this);
        corpRow.setOrientation(LinearLayout.HORIZONTAL);
        corpRow.setGravity(Gravity.CENTER_VERTICAL);
        OreTextView corpLabel = new OreTextView(this);
        corpLabel.setText("授权信息图：");
        corpLabel.setTextColor(Color.WHITE);
        corpLabel.setTextSize(13);
        corpRow.addView(corpLabel);
        OreButton btnCorp = new OreButton(this);
        btnCorp.setText("选择图片");
        btnCorp.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btnCorp.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickFile(REQ_CORP, "image/*"); }
        });
        corpRow.addView(btnCorp);
        tvCorpProof = new OreTextView(this);
        tvCorpProof.setTextColor(Color.parseColor("#888888"));
        tvCorpProof.setTextSize(12);
        tvCorpProof.setPadding(UiUtils.dp(this, 8), 0, 0, 0);
        corpRow.addView(tvCorpProof);
        currentCard.addView(corpRow);
        corpRow.setVisibility(View.GONE);
        UiUtils.addGap(this, currentCard, 4);
        // 关联模组（选「是」展开）
        relatedBox = new LinearLayout(this);
        relatedBox.setOrientation(LinearLayout.VERTICAL);
        addFieldLabelInto(relatedBox, "当前模组类型", false);
        LinearLayout masterRow = new LinearLayout(this);
        masterRow.setOrientation(LinearLayout.HORIZONTAL);
        masterRow.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        final OreTextView btnMaster = chipButton("主包", true);
        final OreTextView btnSlave = chipButton("副包", false);
        btnMaster.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                relatedIsMaster = true;
                applyChipStyle(btnMaster, true);
                applyChipStyle(btnSlave, false);
            }
        });
        btnSlave.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                relatedIsMaster = false;
                applyChipStyle(btnSlave, true);
                applyChipStyle(btnMaster, false);
            }
        });
        masterRow.addView(btnMaster);
        addHorizontalGap(masterRow, 8);
        masterRow.addView(btnSlave);
        relatedBox.addView(masterRow);
        UiUtils.addGap(this, relatedBox, 6);
        etRelatedSearch = new OreEditText(this);
        etRelatedSearch.setHint("搜索模组名称");
        etRelatedSearch.setTextColor(Color.WHITE);
        etRelatedSearch.setTextSize(13);
        etRelatedSearch.setHintTextColor(Color.parseColor("#888888"));
        relatedBox.addView(etRelatedSearch);
        OreButton btnSearchRel = new OreButton(this);
        btnSearchRel.setText("搜索");
        btnSearchRel.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btnSearchRel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { searchRelated(); }
        });
        relatedBox.addView(btnSearchRel);
        tvRelatedSel = new OreTextView(this);
        tvRelatedSel.setTextColor(Color.parseColor("#4CAF50"));
        tvRelatedSel.setTextSize(12);
        relatedBox.addView(tvRelatedSel);
        currentCard.addView(relatedBox);
        relatedBox.setVisibility(View.GONE);
        UiUtils.addGap(this, currentCard, 4);
        // 前置模组
        etPrereq = addEdit("前置模组", false);
        addHintInto(currentCard, "搜索并选择已上传的私有前置模组，仅能关联一个前置模组");
        // 模组标签
        etTags = addEdit("模组标签", false);
        addHintInto(currentCard, "搜索标签 / 输入自定义标签");
        // 活动参与说明
        etActivityDesc = addEdit("活动参与说明", true);
        addHintInto(currentCard, "用于填写参与官方活动需上传介绍与说明，此处内容不会在游戏端出现");
        // 布尔项使用开关；状态变化时只更新关联区块，不留空白。
        boxOriginal.toggle.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                boxOriginal.value = checked; corpRow.setVisibility(checked ? View.GONE : View.VISIBLE);
            }
        });
        boxRelated.toggle.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                boxRelated.value = checked; relatedBox.setVisibility(checked ? View.VISIBLE : View.GONE);
            }
        });
        boxSyncPc.toggle.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                boxSyncPc.value = checked; showPcBoxes(checked);
            }
        });

        // ---------- 2. PC 基本信息（syncPc 时）----------
        pcBasicBox = addCardSection("PC 基本信息");
        boxPcIncludeMap = addBinarySelectorInto(pcBasicBox, "是否包含地图", "是", "否", false);
        etPcBrief = addEditInto(pcBasicBox, "PC 模组简介", false);
        etPcTags = addEditInto(pcBasicBox, "PC 模组标签", false);
        pcBasicBox.setVisibility(View.GONE);

        // ---------- 3. 定价 ----------
        addCardSection("定价");
        // 定价类型（钻石/绿宝石/免费 胶囊）
        addFieldLabel("定价类型");
        LinearLayout priceTypeRow = new LinearLayout(this);
        priceTypeRow.setOrientation(LinearLayout.HORIZONTAL);
        priceTypeRow.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        btnDiamond = priceTypeButton("钻石");
        btnPoint = priceTypeButton("绿宝石");
        btnFree = priceTypeButton("免费");
        // 连续单选按钮，不留分隔空白，风格与顶部选项卡一致。
        priceTypeRow.addView(btnDiamond);
        priceTypeRow.addView(btnPoint);
        priceTypeRow.addView(btnFree);
        btnDiamond.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setPriceType(2); }
        });
        btnPoint.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setPriceType(1); }
        });
        btnFree.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setPriceType(0); }
        });
        currentCard.addView(priceTypeRow);
        UiUtils.addGap(this, currentCard, 10);
        // 定价档位（钻石）
        addFieldLabel("定价档位");
        rowRank = new LinearLayout(this);
        rowRank.setOrientation(LinearLayout.VERTICAL);
        currentCard.addView(rowRank);
        fillRankGroup();
        UiUtils.addGap(this, currentCard, 10);
        // 定价：钻石只读 / 绿宝石输入 / 免费只读
        addFieldLabel("定价");
        etPriceInput = new OreEditText(this);
        etPriceInput.setHint("价格");
        etPriceInput.setTextColor(Color.WHITE);
        etPriceInput.setTextSize(13);
        etPriceInput.setHintTextColor(Color.parseColor("#888888"));
        etPriceInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        currentCard.addView(etPriceInput);
        tvPriceRead = new OreTextView(this);
        tvPriceRead.setTextColor(Color.WHITE);
        tvPriceRead.setTextSize(13);
        currentCard.addView(tvPriceRead);
        UiUtils.addGap(this, currentCard, 6);
        setPriceType(2); // 默认钻石

        // ---------- 4. PE 详情信息 ----------
        addCardSection("PE 详情信息");
        // 富文本工具栏：统一使用 OreButton，避免普通文本控件的圆形/偏移外观。
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        String[] tools = {"B", "I", "U", "A", "插图"};
        for (final String t : tools) {
            OreButton b = new OreButton(this);
            b.setText(t);
            b.setTextSize(12);
            b.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
            b.setLayoutParams(new LinearLayout.LayoutParams(
                    "插图".equals(t) ? UiUtils.dp(this, 64) : UiUtils.dp(this, 42), UiUtils.dp(this, 38)));
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if ("插图".equals(t)) pickFile(REQ_DETAIL_IMG, "image/*");
                    else toggleRichStyle(etDetail, t);
                }
            });
            toolbar.addView(b);
            addHorizontalGap(toolbar, 3);
        }
        currentCard.addView(toolbar);
        UiUtils.addGap(this, currentCard, 6);
        etDetail = addEdit("作品介绍", true);
        etDetail.setMinLines(8);
        etDetail.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) {
                if (!settingDetailHtml) detailHtmlDirty = true;
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        addHintInto(currentCard, "支持粗体、斜体、下划线、颜色与图片；图片会在编辑区按比例显示。");

        // ---------- 5. PE 更新纪要 ----------
        addCardSection("PE 更新纪要");
        etUpdateSummary = addEdit("更新纪要（不允许空格/换行）", true);
        etUpdateSummary.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                String filtered = s.toString().replaceAll("\\s", "");
                if (filtered.length() > SUMMARY_MAX) filtered = filtered.substring(0, SUMMARY_MAX);
                if (!filtered.equals(s.toString())) {
                    etUpdateSummary.removeTextChangedListener(this);
                    etUpdateSummary.setText(filtered);
                    etUpdateSummary.setSelection(filtered.length());
                    etUpdateSummary.addTextChangedListener(this);
                }
                if (tvSummaryCount != null) tvSummaryCount.setText(filtered.length() + "/" + SUMMARY_MAX);
            }
        });
        tvSummaryCount = new OreTextView(this);
        tvSummaryCount.setText("0/" + SUMMARY_MAX);
        tvSummaryCount.setTextColor(Color.parseColor("#888888"));
        tvSummaryCount.setTextSize(11);
        tvSummaryCount.setGravity(Gravity.RIGHT);
        currentCard.addView(tvSummaryCount);
        UiUtils.addGap(this, currentCard, 4);

        // ---------- 6. PC 详细信息（syncPc 时）----------
        pcDetailBox = addCardSection("PC 详细信息");
        etPcDetail = addEditInto(pcDetailBox, "PC 作品介绍", true);
        addFieldLabelInto(pcDetailBox, "PC 上架设置", false);
        boxPcWeakOffline = addBinarySelectorInto(pcDetailBox, "下架设置", "弱下架", "正常上架", false);
        addHintInto(pcDetailBox, "弱下架后资源不在列表展示，已获取的玩家仍可正常使用");
        etPcWeakOfflineReason = addEditInto(pcDetailBox, "下架理由", false);
        boxPcWeakOffline.toggle.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                boxPcWeakOffline.value = checked;
                etPcWeakOfflineReason.setVisibility(checked ? View.VISIBLE : View.GONE);
            }
        });
        etPcWeakOfflineReason.setVisibility(View.GONE);
        pcDetailBox.setVisibility(View.GONE);

        // ---------- 7. PE 上架设置 ----------
        addCardSection("PE 上架设置");
        boxWeakOffline = addBinarySelector("下架设置", "弱下架", "正常上架", false);
        addHintInto(currentCard, "弱下架后资源不在列表展示，已获取的玩家仍可正常使用");
        etWeakOfflineReason = addEdit("下架理由", false);
        boxWeakOffline.toggle.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                boxWeakOffline.value = checked;
                etWeakOfflineReason.setVisibility(checked ? View.VISIBLE : View.GONE);
            }
        });
        etWeakOfflineReason.setVisibility(View.GONE);

        // ---------- 9. 上传 PE 资源管理 ----------
        addCardSection("上传 PE 资源管理");
        accTypeValue = addDropdownField("资源类别", true);
        accSubTypeValue = addDropdownField("具体类别", true);
        accModSecondValue = addDropdownField("次级分类", true);
        accTagPlayValue = addDropdownField("推荐标签 · 玩法", true);
        accTagThemeValue = addDropdownField("推荐标签 · 主题", true);
        boxPlayPlan = addBinarySelector("加入模组畅玩计划", "是", "否", false);
        boxMount = addBinarySelector("启用坐骑召唤功能", "是", "否", false);
        boxAddVersion = addBinarySelector("提升版本", "是", "否", false);
        // 资源文件（占位框 / 已上传行）
        addFieldLabel("资源文件");
        resDropZone = new LinearLayout(this);
        resDropZone.setOrientation(LinearLayout.VERTICAL);
        resDropZone.setGravity(Gravity.CENTER);
        int p = UiUtils.dp(this, 20);
        resDropZone.setPadding(p, p, p, p);
        GradientDrawable dzBg = new GradientDrawable();
        dzBg.setColor(Color.parseColor("#1E1E1E"));
        dzBg.setCornerRadius(UiUtils.dp(this, 12));
        dzBg.setStroke(1, Color.parseColor("#555555"));
        resDropZone.setBackground(dzBg);
        OreTextView dzTitle = new OreTextView(this);
        dzTitle.setText("点击选择 zip / mcpack 资源文件");
        dzTitle.setTextColor(Color.parseColor("#AAAAAA"));
        dzTitle.setTextSize(13);
        dzTitle.setGravity(Gravity.CENTER);
        resDropZone.addView(dzTitle);
        OreTextView dzSub = new OreTextView(this);
        dzSub.setText("支持拖入文件");
        dzSub.setTextColor(Color.parseColor("#666666"));
        dzSub.setTextSize(11);
        dzSub.setGravity(Gravity.CENTER);
        resDropZone.addView(dzSub);
        resDropZone.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickFile(REQ_RES, "*/*"); }
        });
        currentCard.addView(resDropZone);
        resFileRow = new LinearLayout(this);
        resFileRow.setOrientation(LinearLayout.HORIZONTAL);
        resFileRow.setGravity(Gravity.CENTER_VERTICAL);
        resFileRow.setPadding(UiUtils.dp(this, 12), UiUtils.dp(this, 10), UiUtils.dp(this, 12), UiUtils.dp(this, 10));
        GradientDrawable frBg = new GradientDrawable();
        frBg.setColor(Color.parseColor("#1E1E1E"));
        frBg.setCornerRadius(UiUtils.dp(this, 10));
        resFileRow.setBackground(frBg);
        tvResFile = new OreTextView(this);
        tvResFile.setTextColor(Color.WHITE);
        tvResFile.setTextSize(12);
        tvResFile.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        resFileRow.addView(tvResFile);
        btnResRemove = new OreButton(this);
        btnResRemove.setText("✕");
        btnResRemove.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btnResRemove.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                resFileJson = "";
                resFileName = "";
                pendingResUri = null;
                pendingResName = null;
                refreshResFile();
            }
        });
        resFileRow.addView(btnResRemove);
        currentCard.addView(resFileRow);
        UiUtils.addGap(this, currentCard, 8);
        addModApiVersionCard();

        // ---------- 10. 编辑 PE 图片 ----------
        addCardSection("编辑 PE 图片");
        rowChannel = new LinearLayout(this);
        rowChannel.setOrientation(LinearLayout.VERTICAL);
        currentCard.addView(rowChannel);

        // ---------- 11. 上传视频 ----------
        addCardSection("上传视频");
        addHintInto(currentCard, "要求: 16:9 比例，时长 1:30 以内，50MB 以内，H264 编码");
        videoDropView = new LinearLayout(this);
        videoDropView.setOrientation(LinearLayout.VERTICAL);
        videoDropView.setGravity(Gravity.CENTER);
        int vp = UiUtils.dp(this, 16);
        videoDropView.setPadding(vp, vp, vp, vp);
        GradientDrawable vBg = new GradientDrawable();
        vBg.setColor(Color.parseColor("#1E1E1E"));
        vBg.setCornerRadius(UiUtils.dp(this, 12));
        vBg.setStroke(1, Color.parseColor("#555555"));
        videoDropView.setBackground(vBg);
        OreTextView vText = new OreTextView(this);
        vText.setText("选择视频");
        vText.setTextColor(Color.parseColor("#AAAAAA"));
        vText.setTextSize(13);
        vText.setGravity(Gravity.CENTER);
        videoDropView.addView(vText);
        videoDropView.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickFile(REQ_VIDEO, "video/*"); }
        });
        currentCard.addView(videoDropView);
        videoRowView = new LinearLayout(this);
        videoRowView.setOrientation(LinearLayout.HORIZONTAL);
        videoRowView.setGravity(Gravity.CENTER_VERTICAL);
        videoRowView.setPadding(UiUtils.dp(this, 12), UiUtils.dp(this, 10), UiUtils.dp(this, 12), UiUtils.dp(this, 10));
        GradientDrawable vrBg = new GradientDrawable();
        vrBg.setColor(Color.parseColor("#1E1E1E"));
        vrBg.setCornerRadius(UiUtils.dp(this, 10));
        videoRowView.setBackground(vrBg);
        tvVideo = new OreTextView(this);
        tvVideo.setTextColor(Color.WHITE);
        tvVideo.setTextSize(12);
        tvVideo.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        videoRowView.addView(tvVideo);
        btnVideoRemove = new OreButton(this);
        btnVideoRemove.setText("✕");
        btnVideoRemove.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        btnVideoRemove.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                videoJson = "";
                videoName = "";
                videoRemoved = true;
                pendingVideoUri = null;
                pendingVideoName = null;
                refreshVideo();
            }
        });
        videoRowView.addView(btnVideoRemove);
        currentCard.addView(videoRowView);
        // 统一绑定其余是/否胶囊（修复点击失效）
        refreshResFile();
        refreshVideo();
        UiUtils.addGap(this, formLayout, 12);
    }

    private void showPcBoxes(boolean show) {
        if (pcBasicBox != null) pcBasicBox.setVisibility(show ? View.VISIBLE : View.GONE);
        if (pcDetailBox != null) pcDetailBox.setVisibility(show ? View.VISIBLE : View.GONE);
    }
    private void refreshResFile() {
        boolean has = resFileName != null && !resFileName.isEmpty() || (pendingResName != null && !pendingResName.isEmpty());
        resDropZone.setVisibility(has ? View.GONE : View.VISIBLE);
        resFileRow.setVisibility(has ? View.VISIBLE : View.GONE);
        tvResFile.setText(has ? (pendingResName != null ? pendingResName : resFileName) : "");
    }
    private void refreshVideo() {
        boolean has = videoName != null && !videoName.isEmpty() || (pendingVideoName != null && !pendingVideoName.isEmpty());
        if (videoDropView != null) videoDropView.setVisibility(has ? View.GONE : View.VISIBLE);
        if (videoRowView != null) videoRowView.setVisibility(has ? View.VISIBLE : View.GONE);
        tvVideo.setText(has ? (pendingVideoName != null ? pendingVideoName : videoName) : "");
    }

    // ==================== UI 辅助（MCDevManager 同构）====================

    /** 卡片式区块：标题 + 暗色圆角卡片容器。 */
    private LinearLayout addCardSection(String title) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(UiUtils.dp(this, 14), UiUtils.dp(this, 8),
                UiUtils.dp(this, 14), UiUtils.dp(this, 8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#222222"));
        card.setBackground(bg);
        OreTextView titleTv = new OreTextView(this);
        titleTv.setText(title);
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTextSize(15);
        titleTv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        card.addView(titleTv);
        UiUtils.addGap(this, card, 6);
        formLayout.addView(card);
        UiUtils.addGap(this, formLayout, 8);
        currentCard = card;
        return card;
    }

    private OreEditText addEdit(String label, boolean multiLine) {
        return addEditInto(currentCard, label, multiLine);
    }

    private OreEditText addEditInto(LinearLayout card, String label, boolean multiLine) {
        addFieldLabelInto(card, label, false);
        OreEditText et = new OreEditText(this);
        et.setTextColor(Color.WHITE);
        et.setTextSize(13);
        et.setHintTextColor(Color.parseColor("#888888"));
        if (multiLine) {
            et.setMinLines(3);
            et.setGravity(Gravity.TOP | Gravity.START);
        }
        card.addView(et);
        UiUtils.addGap(this, card, 6);
        return et;
    }

    private void addFieldLabel(String label) {
        addFieldLabelInto(currentCard, label, false);
    }

    private void addFieldLabelInto(LinearLayout card, String label, boolean required) {
        OreTextView tv = new OreTextView(this);
        tv.setText((required ? "* " : "") + label);
        tv.setTextColor(Color.parseColor("#BBBBBB"));
        tv.setTextSize(12);
        card.addView(tv);
        UiUtils.addGap(this, card, 2);
    }

    /** 只读字段（标签在上、值在下），点击值可复制。 */
    private OreTextView addReadOnlyInto(LinearLayout row, String label, String value) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        OreTextView lbl = new OreTextView(this);
        lbl.setText(label);
        lbl.setTextColor(Color.parseColor("#888888"));
        lbl.setTextSize(11);
        col.addView(lbl);
        final OreTextView val = new OreTextView(this);
        val.setText(value == null || value.isEmpty() ? "—" : value);
        val.setTextColor(Color.WHITE);
        val.setTextSize(13);
        val.setPadding(0, UiUtils.dp(this, 2), 0, 0);
        col.addView(val);
        col.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        val.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String t = val.getText().toString();
                if (t == null || t.isEmpty() || "—".equals(t)) return;
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("meta", t));
                    UiUtils.toast(WorkDetailActivity.this, "已复制");
                }
            }
        });
        row.addView(col);
        return val;
    }

    /** 布尔设置行：左侧名称，右侧 OreSwitch。 */
    private YesNoBox addBinarySelector(String label, String yesText, String noText, boolean defYes) {
        return addBinarySelectorInto(currentCard, label, yesText, noText, defYes);
    }

    private YesNoBox addBinarySelectorInto(LinearLayout card, String label, String yesText, String noText, boolean defYes) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, UiUtils.dp(this, 5), 0, UiUtils.dp(this, 5));
        OreTextView title = new OreTextView(this);
        title.setText(label);
        title.setTextColor(Color.parseColor("#BBBBBB"));
        title.setTextSize(13);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(title);
        YesNoBox box = new YesNoBox();
        box.value = defYes;
        box.toggle = new OreSwitch(this);
        box.toggle.setChecked(defYes);
        box.toggle.setContentDescription(label);
        row.addView(box.toggle, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(row);
        UiUtils.addGap(this, card, 4);
        return box;
    }

    private void refreshYesNo(YesNoBox box) {
        if (box != null && box.toggle != null) box.toggle.setChecked(box.value);
    }

    /** 胶囊按钮（选中=主色填充）。 */
    /** 胶囊按钮（OreTextView + 自定义圆角背景，避免 OreButton 在 weight 布局下消失）。 */
    private OreTextView chipButton(String text, boolean selected) {
        OreTextView tv = new OreTextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, UiUtils.dp(this, 40), 1f);
        tv.setLayoutParams(lp);
        applyChipStyle(tv, selected);
        return tv;
    }

    private void applyChipStyle(OreTextView tv, boolean selected) {
        if (tv == null) return;
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(UiUtils.dp(this, 20));
        if (selected) {
            bg.setColor(Color.parseColor("#4CAF50"));
            tv.setTextColor(Color.WHITE);
        } else {
            bg.setColor(Color.parseColor("#333333"));
            tv.setTextColor(Color.parseColor("#CCCCCC"));
        }
        tv.setBackground(bg);
    }

    /** 横向 LinearLayout 的间距不能使用 UiUtils.addGap（其宽度为 MATCH_PARENT）。 */
    private void addHorizontalGap(LinearLayout parent, int widthDp) {
        View gap = new View(this);
        parent.addView(gap, new LinearLayout.LayoutParams(UiUtils.dp(this, widthDp), 1));
    }

    private OreButton priceTypeButton(String text) {
        OreButton button = new OreButton(this);
        button.setText(text);
        button.setTextSize(13);
        button.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        button.setLayoutParams(new LinearLayout.LayoutParams(0, UiUtils.dp(this, 44), 1f));
        return button;
    }

    private void addHintInto(LinearLayout card, String hint) {
        OreTextView tv = new OreTextView(this);
        tv.setText(hint);
        tv.setTextColor(Color.parseColor("#777777"));
        tv.setTextSize(11);
        card.addView(tv);
        UiUtils.addGap(this, card, 6);
    }

    /** 下拉框容器：标签 + 值文本 + 展开箭头，点击弹出菜单（loadOptions 后绑定）。 */
    /** 下拉框（折叠面板）：标题=字段名，副标题=当前值，点击展开/收起选项列表。 */
    private OreAccordion addDropdownField(final String label, boolean required) {
        OreAccordion acc = new OreAccordion(this);
        acc.setTitle((required ? "* " : "") + label);
        acc.setSubtitle("请选择");
        acc.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
        acc.setExpanded(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        // OreAccordion.setContentView() 内部直接 addView；必须给内容根布局全宽参数，
        // 否则会按子项文字的自然宽度测量，导致 OreCard 缩成左侧窄条。
        content.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        content.setPadding(0, 0, 0, 0);
        acc.setContentView(content);
        currentCard.addView(acc);
        UiUtils.addGap(this, currentCard, 6);
        return acc;
    }

    // ==================== 弹窗（OreUI）====================

    private void showSingleDropdown(final List<MCConst> options, final int selectedId,
                                    final String emptyText, final java.util.function.Consumer<Integer> onPick) {
        if (options == null || options.isEmpty()) { UiUtils.toast(this, "暂无可选选项"); return; }
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = UiUtils.dp(this, 4);
        list.setPadding(pad, pad, pad, 0);
        for (final MCConst opt : options) {
            final LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(UiUtils.dp(this, 12), UiUtils.dp(this, 8), UiUtils.dp(this, 12), UiUtils.dp(this, 8));
            GradientDrawable rowBg = new GradientDrawable();
            rowBg.setColor(Color.parseColor("#2A2A2A"));
            row.setBackground(rowBg);
            OreTextView title = new OreTextView(this);
            title.setText(opt.title);
            title.setTextColor(opt.id == selectedId ? Color.WHITE : Color.parseColor("#CCCCCC"));
            title.setTextSize(13);
            title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(title);
            final OreSwitch sw = new OreSwitch(this);
            sw.setChecked(opt.id == selectedId);
            row.addView(sw);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { onPick.accept(opt.id); }
            });
            sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean isChecked) {
                    if (isChecked) onPick.accept(opt.id);
                }
            });
            list.addView(row);
            UiUtils.addGap(this, list, 6);
        }
        final androidx.appcompat.app.AlertDialog dialog = new OreDialogBuilder(this)
                .setTitle("请选择")
                .setView(list)
                .setNegativeButton("取消", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int which) { d.dismiss(); }
                })
                .show();
        // 单击行/开关选中后立即关闭（网页版单选：选完即关）
        for (int i = 0; i < list.getChildCount(); i++) {
            final View child = list.getChildAt(i);
            if (child instanceof LinearLayout) {
                child.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        LinearLayout row = (LinearLayout) child;
                        OreTextView title = (OreTextView) row.getChildAt(0);
                        String t = title.getText().toString();
                        for (MCConst opt : options) {
                            if (opt.title.equals(t)) { onPick.accept(opt.id); break; }
                        }
                        dialog.dismiss();
                    }
                });
            }
        }
    }

    private void showMultiDropdown(String title, String[] items, final boolean[] checked,
                                   final Runnable onChange, final Runnable onDone) {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = UiUtils.dp(this, 4);
        list.setPadding(pad, pad, pad, 0);
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            final LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(UiUtils.dp(this, 12), UiUtils.dp(this, 8), UiUtils.dp(this, 12), UiUtils.dp(this, 8));
            GradientDrawable rowBg = new GradientDrawable();
            rowBg.setColor(Color.parseColor("#2A2A2A"));
            row.setBackground(rowBg);
            final OreTextView label = new OreTextView(this);
            label.setText(items[i]);
            label.setTextColor(checked[i] ? Color.WHITE : Color.parseColor("#CCCCCC"));
            label.setTextSize(13);
            label.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(label);
            final OreSwitch sw = new OreSwitch(this);
            sw.setChecked(checked[i]);
            row.addView(sw);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { sw.setChecked(!sw.isChecked()); }
            });
            sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean isChecked) {
                    checked[idx] = isChecked;
                    label.setTextColor(isChecked ? Color.WHITE : Color.parseColor("#CCCCCC"));
                    if (onChange != null) onChange.run();
                }
            });
            list.addView(row);
            UiUtils.addGap(this, list, 6);
        }
        new OreDialogBuilder(this)
                .setTitle(title)
                .setView(list)
                .setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int which) {
                        if (onDone != null) onDone.run();
                        d.dismiss();
                    }
                })
                .setNegativeButton("取消", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int which) { d.dismiss(); }
                })
                .show();
    }

    private void setAccSubtitle(OreAccordion acc, String text) {
        if (acc != null) acc.setSubtitle(text);
    }

    // ==================== 定价 ====================
    private static final int[] RANK_PRICES = {300, 600, 1000, 2000, 5000, 10000, 20000};
    private static final String[] RANK_TITLES = {"300", "600", "1000", "2000", "5000", "10000", "20000"};

    /** 钻石档位使用与定价类型相同的矩形 OreButton，而非圆角胶囊。 */
    private void fillRankGroup() {
        rowRank.removeAllViews();
        LinearLayout curRow = null;
        for (int i = 0; i < RANK_TITLES.length; i++) {
            if (i % 4 == 0) {
                curRow = new LinearLayout(this);
                curRow.setOrientation(LinearLayout.HORIZONTAL);
                curRow.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                rowRank.addView(curRow);
                if (i > 0) UiUtils.addGap(this, rowRank, 3);
            }
            final int idx = i;
            OreButton b = new OreButton(this);
            b.setText(RANK_TITLES[i] + "钻");
            b.setTextSize(12);
            b.setStyleSheet(priceRank == idx ? StyleSheet.STYLE_GREEN : StyleSheet.STYLE_DARK_GRAY);
            b.setLayoutParams(new LinearLayout.LayoutParams(0, UiUtils.dp(this, 40), 1f));
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    priceRank = idx;
                    applyRank();
                    fillRankGroup();
                }
            });
            rankBtns[i] = b;
            curRow.addView(b);
        }
    }

    private void applyRank() {
        if (priceRank >= 0 && priceRank < RANK_PRICES.length) {
            etPriceInput.setText(String.valueOf(RANK_PRICES[priceRank]));
            if (tvPriceRead != null) tvPriceRead.setText(RANK_PRICES[priceRank] + " 钻石");
        }
    }

    private void setPriceType(int idx) { setPriceType(idx, false); }

    /** force 用于详情回填：已上架作品不能由用户修改，但必须正确显示原定价。 */
    private void setPriceType(int idx, boolean force) {
        if (priceTypeLocked && !force) { UiUtils.toast(this, "已上架作品不可修改定价类型"); return; }
        priceTypeIdx = idx;
        btnFree.setStyleSheet(idx == 0 ? StyleSheet.STYLE_GREEN : StyleSheet.STYLE_DARK_GRAY);
        btnPoint.setStyleSheet(idx == 1 ? StyleSheet.STYLE_GREEN : StyleSheet.STYLE_DARK_GRAY);
        btnDiamond.setStyleSheet(idx == 2 ? StyleSheet.STYLE_GREEN : StyleSheet.STYLE_DARK_GRAY);
        btnFree.setEnabled(!priceTypeLocked);
        btnPoint.setEnabled(!priceTypeLocked);
        btnDiamond.setEnabled(!priceTypeLocked);
        rowRank.setVisibility(idx == 2 ? View.VISIBLE : View.GONE);
        if (idx == 2) {
            applyRank();
            etPriceInput.setVisibility(View.GONE);
            tvPriceRead.setVisibility(View.VISIBLE);
        } else if (idx == 1) {
            etPriceInput.setVisibility(View.VISIBLE);
            tvPriceRead.setVisibility(View.GONE);
        } else {
            etPriceInput.setText("0");
            etPriceInput.setVisibility(View.GONE);
            tvPriceRead.setText("免费");
            tvPriceRead.setVisibility(View.VISIBLE);
        }
    }

// ==================== 选项加载 ====================

    private void loadOptions() {
        statusText.setText("加载选项数据中...");
        statusText.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            @Override public void run() {
                final String constsJson = McDevApi.getMCConsts(cookie);
                final String tagJson = McDevApi.getItemTag(cookie);
                UiUtils.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        try {
                            if (constsJson != null) {
                                JSONObject c = new JSONObject(constsJson);
                                // 资源类别 pri_type.pe
                                peTypeOptions.clear();
                                JSONObject priTypeObj = c.optJSONObject("pri_type");
                                JSONArray pt = priTypeObj == null ? null : priTypeObj.optJSONArray("pe");
                                if (pt != null) for (int i = 0; i < pt.length(); i++) {
                                    JSONObject o = pt.getJSONObject(i);
                                    peTypeOptions.add(new MCConst(o.optInt("id"), o.optString("title")));
                                }
                                // 具体类别 sub_type.pe：key=资源类别id
                                JSONObject stWrapper = c.optJSONObject("sub_type");
                                JSONObject stObj = stWrapper == null ? null : stWrapper.optJSONObject("pe");
                                final Map<Integer, List<MCConst>> subMap = new LinkedHashMap<>();
                                if (stObj != null) {
                                    java.util.Iterator<String> keys = stObj.keys();
                                    while (keys.hasNext()) {
                                        String k = keys.next();
                                        JSONArray arr = stObj.optJSONArray(k);
                                        if (arr == null) continue;
                                        List<MCConst> list = new ArrayList<>();
                                        for (int i = 0; i < arr.length(); i++) {
                                            JSONObject o = arr.getJSONObject(i);
                                            list.add(new MCConst(o.optInt("id"), o.optString("title")));
                                        }
                                        try { subMap.put(Integer.parseInt(k), list); } catch (Exception ignored) {}
                                    }
                                }
                                // 次级分类 mod_second_type 按资源类别 ID 分组：mod_second_type["2"] 对应 add_ons。
                                modSecondTypeCache.clear();
                                peModSecondOptions.clear();
                                JSONObject msWrapper = c.optJSONObject("mod_second_type");
                                if (msWrapper != null) {
                                    java.util.Iterator<String> msKeys = msWrapper.keys();
                                    while (msKeys.hasNext()) {
                                        String k = msKeys.next();
                                        JSONArray arr = msWrapper.optJSONArray(k);
                                        if (arr == null) continue;
                                        List<MCConst> list = new ArrayList<>();
                                        for (int i = 0; i < arr.length(); i++) {
                                            JSONObject o = arr.getJSONObject(i);
                                            list.add(new MCConst(o.optInt("id"), o.optString("title")));
                                        }
                                        try { modSecondTypeCache.put(Integer.parseInt(k), list); }
                                        catch (Exception ignored) { }
                                    }
                                }
                                peModSecondOptions = modSecondTypeCache.get(peType);
                                if (peModSecondOptions == null) peModSecondOptions = new ArrayList<>();
                                // 推荐标签 label_type: {1:玩法, 2:主题}
                                final List<MCConst> playTags = new ArrayList<>();
                                final List<MCConst> themeTags = new ArrayList<>();
                                JSONObject lt = c.optJSONObject("label_type");
                                JSONArray p1 = lt == null ? null : lt.optJSONArray("1");
                                JSONArray p2 = lt == null ? null : lt.optJSONArray("2");
                                if (p1 != null) for (int i = 0; i < p1.length(); i++) {
                                    JSONObject o = p1.getJSONObject(i);
                                    playTags.add(new MCConst(o.optInt("id"), o.optString("title")));
                                }
                                if (p2 != null) for (int i = 0; i < p2.length(); i++) {
                                    JSONObject o = p2.getJSONObject(i);
                                    themeTags.add(new MCConst(o.optInt("id"), o.optString("title")));
                                }
                                // 仅展示平台官方提供的 modAPI 版本；mc_version 不在移动端表单中暴露。
                                modApiVersionOptions.clear();
                                JSONArray modVersions = c.optJSONArray("mod_version");
                                if (modVersions != null) for (int i = 0; i < modVersions.length(); i++) {
                                    String version = modVersions.optString(i).trim();
                                    if (!version.isEmpty()) modApiVersionOptions.add(version);
                                }
                                refreshModApiVersionCard();
                                // 渠道图频道 channel.pe + pe_multi
                                channelSlots.clear();
                                JSONObject chObj = c.optJSONObject("channel");
                                JSONArray chPe = chObj == null ? null : chObj.optJSONArray("pe");
                                JSONArray chPeMulti = chObj == null ? null : chObj.optJSONArray("pe_multi");
                                // pe 与 pe_multi 可能包含相同频道；按 channel_id 去重，最多展示平台的 6 个频道。
                                if (chPe != null) for (int i = 0; i < chPe.length() && channelSlots.size() < 6; i++) {
                                    JSONObject o = chPe.getJSONObject(i);
                                    addUniqueChannelSlot(o);
                                }
                                if (chPeMulti != null) for (int i = 0; i < chPeMulti.length() && channelSlots.size() < 6; i++) {
                                    JSONObject o = chPeMulti.getJSONObject(i);
                                    addUniqueChannelSlot(o);
                                }
                                // 详情可能比频道配置更早返回；现在将暂存的 URL 绑定到新建槽位。
                                applyStoredChannelData();
                                // 缓存
                                subTypeCache.clear();
                                subTypeCache.putAll(subMap);
                                playTagCache.clear();
                                playTagCache.addAll(playTags);
                                themeTagCache.clear();
                                themeTagCache.addAll(themeTags);
                                // 绑定下拉框点击
                                bindDropdowns(subMap, playTags, themeTags);
                                // 渠道图 UI
                                buildChannelRows();
                            }
                            // 标签建议
                            if (tagJson != null) {
                                JSONObject t = new JSONObject(tagJson);
                                JSONArray tl = t.optJSONArray("tag_list");
                                if (tl != null && tl.length() > 0) {
                                    StringBuilder sb = new StringBuilder();
                                    for (int i = 0; i < Math.min(tl.length(), 12); i++) {
                                        if (sb.length() > 0) sb.append("、");
                                        sb.append(tl.optString(i));
                                    }
                                    OreTextView hint = new OreTextView(WorkDetailActivity.this);
                                    hint.setText("可用标签：" + sb);
                                    hint.setTextColor(Color.parseColor("#666666"));
                                    hint.setTextSize(11);
                                    formLayout.addView(hint, 2);
                                }
                            }
                            statusText.setVisibility(View.GONE);
                        } catch (Exception e) {
                            statusText.setText("选项加载失败：" + e.getMessage());
                        }
                    }
                });
            }
        }).start();
    }

    /** 下拉框点击绑定：资源类别(联动具体类别)/具体类别/次级分类/推荐标签·玩法/主题/MC版本。 */
    private void bindDropdowns(final Map<Integer, List<MCConst>> subMap,
                               final List<MCConst> playTags, final List<MCConst> themeTags) {
        recommendTagSwitches.clear();
        recommendTagGroups.clear();
        // ---- 资源类别（单选）----
        if (accTypeValue != null) {
            LinearLayout content = (LinearLayout) accTypeValue.getContentView();
            for (final MCConst opt : peTypeOptions) {
                content.addView(makeSelectRow(opt.title, opt.id == peType, new Runnable() {
                    @Override public void run() {
                        peType = opt.id;
                        peSubType = 0;
                        peSubTypeOptions = subMap.get(opt.id);
                        if (peSubTypeOptions == null) peSubTypeOptions = new ArrayList<>();
                        peModSecondOptions = modSecondTypeCache.get(opt.id);
                        if (peModSecondOptions == null) peModSecondOptions = new ArrayList<>();
                        peModSecond = 0;
                        accTypeValue.setSubtitle(titleOf(peTypeOptions, peType));
                        accSubTypeValue.setSubtitle("请选择");
                        rebuildSubTypeContent();
                        rebuildModSecondContent();
                        accTypeValue.toggle();
                    }
                }));
            }
        }
        // ---- 具体类别（单选，联动重建）----
        rebuildSubTypeContent();
        // ---- 次级分类（单选，按资源类别联动）----
        rebuildModSecondContent();
        // ---- 推荐标签 · 玩法（多选）----
        if (accTagPlayValue != null) {
            LinearLayout content = (LinearLayout) accTagPlayValue.getContentView();
            for (final MCConst tag : playTags) {
                content.addView(makeSwitchRow(tag.id, 1, tag.title, recommendTags.contains(tag.id), new Runnable() {
                    @Override public void run() {
                        if (recommendTags.contains(tag.id)) recommendTags.remove((Integer) tag.id);
                        else recommendTags.add(tag.id);
                        refreshTagTexts(playTags, themeTags);
                    }
                }));
            }
        }
        // ---- 推荐标签 · 主题（多选）----
        if (accTagThemeValue != null) {
            LinearLayout content = (LinearLayout) accTagThemeValue.getContentView();
            for (final MCConst tag : themeTags) {
                content.addView(makeSwitchRow(tag.id, 2, tag.title, recommendTags.contains(tag.id), new Runnable() {
                    @Override public void run() {
                        if (recommendTags.contains(tag.id)) recommendTags.remove((Integer) tag.id);
                        else recommendTags.add(tag.id);
                        refreshTagTexts(playTags, themeTags);
                    }
                }));
            }
        }
        syncRecommendTagSwitches();

    }

    /** 重建具体类别下拉内容（资源类别变化后联动）。 */
    private void rebuildSubTypeContent() {
        if (accSubTypeValue == null) return;
        LinearLayout content = (LinearLayout) accSubTypeValue.getContentView();
        content.removeAllViews();
        if (peSubTypeOptions == null || peSubTypeOptions.isEmpty()) {
            accSubTypeValue.setSubtitle("请选择");
            return;
        }
        for (final MCConst opt : peSubTypeOptions) {
            content.addView(makeSelectRow(opt.title, opt.id == peSubType, new Runnable() {
                @Override public void run() {
                    peSubType = opt.id;
                    accSubTypeValue.setSubtitle(titleOf(peSubTypeOptions, peSubType));
                    accSubTypeValue.toggle();
                }
            }));
        }
    }

    /** 重建次级分类内容：mod_second_type 只有对应 pri_type 有数据时才显示。 */
    private void rebuildModSecondContent() {
        if (accModSecondValue == null) return;
        List<MCConst> options = modSecondTypeCache.get(peType);
        if (options == null) options = new ArrayList<>();
        peModSecondOptions = options;
        boolean visible = peType > 0 && !options.isEmpty();
        accModSecondValue.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) {
            peModSecond = 0;
            accModSecondValue.setSubtitle("不适用");
            return;
        }
        LinearLayout content = (LinearLayout) accModSecondValue.getContentView();
        content.removeAllViews();
        accModSecondValue.setSubtitle(titleOf(options, peModSecond));
        for (final MCConst opt : options) {
            content.addView(makeSelectRow(opt.title, opt.id == peModSecond, new Runnable() {
                @Override public void run() {
                    peModSecond = opt.id;
                    accModSecondValue.setSubtitle(titleOf(peModSecondOptions, peModSecond));
                    accModSecondValue.toggle();
                }
            }));
        }
    }

    /** 刷新推荐标签两组副标题。 */
    private void refreshTagTexts(List<MCConst> playTags, List<MCConst> themeTags) {
        if (accTagPlayValue != null) accTagPlayValue.setSubtitle(tagTitles(playTags, recommendTags, "至少选 1 个"));
        if (accTagThemeValue != null) accTagThemeValue.setSubtitle(tagTitles(themeTags, recommendTags, "至少选 1 个"));
    }

    /** 将详情接口回填的标签 ID 同步到已创建的 Switch；不触发用户点击逻辑。 */
    private void syncRecommendTagSwitches() {
        syncingRecommendTagSwitches = true;
        try {
            for (Map.Entry<Integer, OreSwitch> entry : recommendTagSwitches.entrySet()) {
                OreSwitch sw = entry.getValue();
                if (sw != null) sw.setChecked(recommendTags.contains(entry.getKey()));
            }
        } finally {
            syncingRecommendTagSwitches = false;
        }
        refreshRecommendTagSwitchEnabled();
    }

    /** 单选下拉项：整行 OreCard；不显示图标、选中勾选或高亮。 */
    private LinearLayout makeSelectRow(String title, boolean selected, final Runnable onSelect) {
        OreCard card = new OreCard(this);
        // OreCard 必须显式 MATCH_PARENT，否则在 Accordion 内容中会按文字宽度测量。
        card.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        card.setPadding(0, 0, 0, 0);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, 0);
        OreTextView tv = new OreTextView(this);
        tv.setText(title);
        tv.setTextColor(Color.parseColor("#DDDDDD"));
        tv.setTextSize(14);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding(UiUtils.dp(this, 12), 0, UiUtils.dp(this, 12), 0);
        row.addView(tv, new LinearLayout.LayoutParams(
                0, UiUtils.dp(this, 42), 1f));
        card.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiUtils.dp(this, 42)));
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (onSelect != null) onSelect.run(); }
        });
        return card;
    }

    /** 多选下拉项：整行 OreCard，右侧保留 Switch；最多选择两项。 */
    private LinearLayout makeSwitchRow(final int tagId, final int group, String title, boolean checked, final Runnable onChange) {
        OreCard card = new OreCard(this);
        card.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        card.setPadding(0, 0, 0, 0);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        OreTextView tv = new OreTextView(this);
        tv.setText(title);
        tv.setTextColor(Color.parseColor("#DDDDDD"));
        tv.setTextSize(14);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding(UiUtils.dp(this, 12), 0, 0, 0);
        row.addView(tv, new LinearLayout.LayoutParams(0, UiUtils.dp(this, 42), 1f));
        final OreSwitch sw = new OreSwitch(this);
        sw.setChecked(checked);
        sw.setTag(title);
        row.addView(sw, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, UiUtils.dp(this, 42)));
        card.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiUtils.dp(this, 42)));
        recommendTagSwitches.put(tagId, sw);
        recommendTagGroups.put(tagId, group);
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean isChecked) {
                if (syncingRecommendTagSwitches) return;
                // setChecked 已先改变本开关状态，故超限判断中包含当前项。
                if (isChecked && countSelectedRecommendTags(group) > MAX_RECOMMEND_TAGS_PER_GROUP) {
                    b.setChecked(false);
                    return;
                }
                if (onChange != null) onChange.run();
                refreshRecommendTagSwitchEnabled();
            }
        });
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sw.setChecked(!sw.isChecked()); }
        });
        return card;
    }

    private int countSelectedRecommendTags(int group) {
        int count = 0;
        for (Map.Entry<Integer, OreSwitch> e : recommendTagSwitches.entrySet()) {
            Integer g = recommendTagGroups.get(e.getKey());
            if (g != null && g == group && e.getValue() != null && e.getValue().isChecked()) count++;
        }
        return count;
    }

    /** 玩法、主题各自最多两项；达到上限时只锁同一分类中尚未选中的开关。 */
    private void refreshRecommendTagSwitchEnabled() {
        for (Map.Entry<Integer, OreSwitch> e : recommendTagSwitches.entrySet()) {
            OreSwitch sw = e.getValue();
            Integer group = recommendTagGroups.get(e.getKey());
            if (sw == null || group == null) continue;
            boolean full = countSelectedRecommendTags(group) >= MAX_RECOMMEND_TAGS_PER_GROUP;
            sw.setEnabled(!full || sw.isChecked());
        }
    }

    /** modAPI 版本卡片：紧凑单行布局，显示当前选择并以右箭头提示可进入选择器。 */
    private void addModApiVersionCard() {
        modApiVersionCard = new OreCard(this);
        // 入口保持单行、紧凑；仍保留足够的点击高度。
        modApiVersionCard.setPadding(UiUtils.dp(this, 14), UiUtils.dp(this, 1), UiUtils.dp(this, 10), UiUtils.dp(this, 1));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(UiUtils.dp(this, 46));

        OreTextView label = new OreTextView(this);
        label.setText("* modAPI 版本");
        label.setTextColor(Color.WHITE);
        label.setTextSize(15);
        label.setSingleLine(true);
        // 文字“* modAPI 版本”约需 150dp；固定宽度确保不会拆成两行。
        label.setLayoutParams(new LinearLayout.LayoutParams(UiUtils.dp(this, 158), LinearLayout.LayoutParams.WRAP_CONTENT));
        row.addView(label);

        tvModApiVersion = new OreTextView(this);
        tvModApiVersion.setTextColor(Color.parseColor("#BBBBBB"));
        tvModApiVersion.setTextSize(14);
        tvModApiVersion.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        tvModApiVersion.setSingleLine(true);
        tvModApiVersion.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tvModApiVersion);

        OreTextView arrow = new OreTextView(this);
        arrow.setText("›");
        arrow.setTextColor(Color.WHITE);
        arrow.setTextSize(28);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(UiUtils.dp(this, 32), LinearLayout.LayoutParams.MATCH_PARENT));
        modApiVersionCard.addView(row);
        modApiVersionCard.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showModApiVersionPicker(); }
        });
        currentCard.addView(modApiVersionCard);
        UiUtils.addGap(this, currentCard, 5);
        refreshModApiVersionCard();
    }

    private void refreshModApiVersionCard() {
        if (tvModApiVersion != null) tvModApiVersion.setText(
                selectedModApiVersion.isEmpty() ? "点击选择版本" : selectedModApiVersion);
    }

    private void showModApiVersionPicker() {
        if (modApiVersionOptions.isEmpty()) { UiUtils.toast(this, "modAPI 版本列表尚未加载"); return; }
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = UiUtils.dp(this, 8); list.setPadding(pad, pad, pad, pad);
        final ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        // 平台版本列表通常由旧到新返回；选择器按新到旧展示。
        // 每个选项直接采用 OreButton 的灰色样式，避免卡片控件额外的纵向留白。
        for (int reverseIndex = modApiVersionOptions.size() - 1; reverseIndex >= 0; reverseIndex--) {
            final String version = modApiVersionOptions.get(reverseIndex);
            OreButton item = new OreButton(this);
            item.setText(version.equals(selectedModApiVersion) ? version + "  ✓" : version);
            item.setTextSize(15);
            item.setStyleSheet(StyleSheet.STYLE_DARK_GRAY);
            item.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
            item.setPadding(UiUtils.dp(this, 14), 0, UiUtils.dp(this, 14), 0);
            item.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, UiUtils.dp(this, 44)));
            item.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    selectedModApiVersion = version;
                    refreshModApiVersionCard();
                }
            });
            list.addView(item);
            UiUtils.addGap(this, list, 3);
        }
        final androidx.appcompat.app.AlertDialog dialog = new OreDialogBuilder(this)
                .setTitle("选择 modAPI 版本")
                .setView(scroll)
                .setNegativeButton("取消", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) { d.dismiss(); }
                }).show();
        // 每个版本卡片按倒序列表的位置回填，选择后关闭。
        for (int i = 0, optionIndex = modApiVersionOptions.size() - 1; i < list.getChildCount(); i += 2, optionIndex--) {
            final String pickedVersion = modApiVersionOptions.get(optionIndex);
            View item = list.getChildAt(i);
            item.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    selectedModApiVersion = pickedVersion;
                    refreshModApiVersionCard(); dialog.dismiss();
                }
            });
        }
    }

    private void addUniqueChannelSlot(JSONObject o) {
        if (o == null) return;
        String title = o.optString("title");
        String lower = title.toLowerCase();
        // 平台配置可能返回封面图，但 PE 编辑页只展示两类 ICON、pos 机和三张轮播图。
        if (title.contains("封面") || lower.contains("cover")) return;
        int id = o.optInt("id");
        for (ChannelSlot existing : channelSlots) if (existing.channelId == id) return;
        if (channelSlots.size() < 6) channelSlots.add(new ChannelSlot(id, title, o.optInt("version")));
    }

    private void bindRemoteChannelData(ChannelSlot slot, Object rawUrl) {
        if (slot == null) return;
        slot.fileJson = rawUrl == null ? "" : String.valueOf(rawUrl);
        slot.remoteUrl = extractRemoteUrl(rawUrl);
        slot.fileName = slot.fileJson.isEmpty() ? "" : "已上传";
    }

    private void applyStoredChannelData() {
        for (ChannelSlot slot : channelSlots) {
            if (remoteChannelData.containsKey(slot.channelId))
                bindRemoteChannelData(slot, remoteChannelData.get(slot.channelId));
        }
    }

    /** 渠道图行：本地/远端图片均显示缩略预览。 */
    private void buildChannelRows() {
        if (rowChannel == null) return;
        rowChannel.removeAllViews();
        final int renderGeneration = ++channelRenderGeneration;
        for (final ChannelSlot slot : channelSlots) {
            final boolean has = (slot.fileJson != null && !slot.fileJson.isEmpty())
                    || pendingChannelUris.containsKey(slot.channelId);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            // 左侧图框
            // FrameLayout 让“加载中/未选择”文案覆盖在预览上方，不能挤出图片区域。
            FrameLayout thumb = new FrameLayout(this);
            thumb.setForegroundGravity(Gravity.CENTER);
            thumb.setLayoutParams(new LinearLayout.LayoutParams(UiUtils.dp(this, 104), UiUtils.dp(this, 68)));
            GradientDrawable tBg = new GradientDrawable();
            // 图片预览按要求使用直角，不裁切圆角。
            tBg.setCornerRadius(0);
            tBg.setStroke(1, Color.parseColor("#555555"));
            tBg.setColor(Color.parseColor("#1E1E1E"));
            thumb.setBackground(tBg);
            ImageView preview = new ImageView(this);
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            // 只有解码成功后才显示图片，状态层不会再覆盖有效缩略图。
            preview.setVisibility(View.GONE);
            thumb.addView(preview, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            slot.preview = preview;
            slot.fullBitmap = null;
            preview.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (slot.fullBitmap != null) showImageFullscreen(slot.fullBitmap);
                }
            });
            OreTextView tState = new OreTextView(this);
            tState.setText(has ? "加载图片中..." : "未选择");
            tState.setTextColor(has ? Color.parseColor("#AAAAAA") : Color.parseColor("#777777"));
            tState.setTextSize(11); tState.setGravity(Gravity.CENTER);
            thumb.addView(tState, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            if (has) loadChannelPreview(slot, tState, renderGeneration);
            // 右侧：标题 + 规格 + 按钮
            LinearLayout right = new LinearLayout(this);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setPadding(UiUtils.dp(this, 10), 0, 0, 0);
            right.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            OreTextView title = new OreTextView(this);
            title.setText(slot.title);
            title.setTextColor(Color.WHITE);
            title.setTextSize(13);
            right.addView(title);
            OreTextView spec = new OreTextView(this);
            spec.setText(channelSpecText(slot.title));
            spec.setTextColor(Color.parseColor("#777777"));
            spec.setTextSize(10);
            right.addView(spec);
            UiUtils.addGap(this, right, 3);
            LinearLayout btnRow = new LinearLayout(this);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            OreButton btnPick = new OreButton(this);
            btnPick.setText(has ? "更换" : "选图");
            btnPick.setStyleSheet(has ? StyleSheet.STYLE_DARK_GRAY : StyleSheet.STYLE_GREEN);
            btnPick.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    pendingChannelSel = slot.channelId;
                    pickFile(REQ_CHANNEL_IMG, "image/*");
                }
            });
            btnRow.addView(btnPick);
            if (has) {
                UiUtils.addGap(this, btnRow, 6);
                OreButton btnDel = new OreButton(this);
                btnDel.setText("删除");
                btnDel.setStyleSheet(StyleSheet.STYLE_RED);
                btnDel.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        slot.fileJson = "";
                        slot.fileName = "";
                        pendingChannelUris.remove(slot.channelId);
                        pendingChannelNames.remove(slot.channelId);
                        buildChannelRows();
                    }
                });
                btnRow.addView(btnDel);
            }
            right.addView(btnRow);
            row.addView(thumb);
            row.addView(right);
            rowChannel.addView(row);
            UiUtils.addGap(this, rowChannel, 5);
        }
    }

    /** 兼容平台返回的 URL、对象、数组和被二次 JSON 编码的上传结果。 */
    private String extractRemoteUrl(Object value) {
        if (value == null || value == JSONObject.NULL) return "";
        if (value instanceof JSONArray) {
            JSONArray a = (JSONArray) value;
            for (int i = 0; i < a.length(); i++) { String url = extractRemoteUrl(a.opt(i)); if (!url.isEmpty()) return url; }
            return "";
        }
        if (value instanceof JSONObject) {
            JSONObject o = (JSONObject) value;
            String[] keys = {"url", "file_url", "download_url", "image_url", "src", "location", "body", "data", "result"};
            for (String key : keys) { String url = extractRemoteUrl(o.opt(key)); if (!url.isEmpty()) return url; }
            return "";
        }
        String raw = String.valueOf(value).trim().replace("\\/", "/");
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw;
        try { return extractRemoteUrl(new JSONObject(raw)); } catch (Exception ignored) { }
        try { return extractRemoteUrl(new JSONArray(raw)); } catch (Exception ignored) { }
        return "";
    }

    /** 每次进入/重绘都使用新的 generation 请求，旧页面请求不得影响当前缩略图。 */
    private void loadChannelPreview(final ChannelSlot slot, final OreTextView state, final int generation) {
        final String local = pendingChannelUris.get(slot.channelId);
        final String remote = local == null ? slot.remoteUrl : local;
        if (remote == null || remote.isEmpty()) {
            state.setText("已选图片");
            return;
        }
        new Thread(new Runnable() { @Override public void run() {
            InputStream in = null;
            java.net.HttpURLConnection c = null;
            try {
                final Bitmap bitmap;
                if (remote.startsWith("content://")) {
                    in = getContentResolver().openInputStream(Uri.parse(remote));
                    bitmap = BitmapFactory.decodeStream(in);
                } else if (remote.startsWith("file://")) {
                    // 应用内裁剪页输出到缓存并返回 file:// URI；它不是 HTTP 地址。
                    // 直接以文件流解码，确保刚裁剪、尚未保存到服务端的图片也可立即预览。
                    in = new java.io.FileInputStream(new File(Uri.parse(remote).getPath()));
                    bitmap = BitmapFactory.decodeStream(in);
                } else {
                    c = (java.net.HttpURLConnection) new java.net.URL(remote).openConnection();
                    c.setConnectTimeout(15000); c.setReadTimeout(15000); c.setInstanceFollowRedirects(true);
                    c.setUseCaches(false);
                    c.setRequestProperty("Cache-Control", "no-cache");
                    c.setRequestProperty("User-Agent", "NemoModStudio");
                    c.setRequestProperty("Accept", "image/*,*/*;q=0.8");
                    int code = c.getResponseCode();
                    if (code / 100 != 2) throw new java.io.IOException("HTTP " + code);
                    in = c.getInputStream();
                    bitmap = BitmapFactory.decodeStream(in);
                }
                UiUtils.runOnUiThread(new Runnable() { @Override public void run() {
                    if (isFinishing() || generation != channelRenderGeneration || slot.preview == null) return;
                    if (bitmap != null) {
                        slot.fullBitmap = bitmap;
                        slot.preview.setImageBitmap(bitmap);
                        slot.preview.setVisibility(View.VISIBLE);
                        state.setVisibility(View.GONE);
                    } else state.setText("图片加载失败");
                }});
            } catch (final Exception e) {
                UiUtils.runOnUiThread(new Runnable() { @Override public void run() {
                    if (!isFinishing() && generation == channelRenderGeneration) state.setText("图片加载失败");
                }});
            } finally {
                try { if (in != null) in.close(); } catch (Exception ignored) { }
                if (c != null) c.disconnect();
            }
        }}).start();
    }

    /** 点击缩略图后全屏查看：半透明黑色背景，点击任意位置关闭。 */
    private void showImageFullscreen(Bitmap bitmap) {
        if (bitmap == null) return;
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.argb(190, 0, 0, 0));
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImageBitmap(bitmap);
        root.addView(image, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        OreTextView tip = new OreTextView(this);
        tip.setText("点击任意地方关闭");
        tip.setTextColor(Color.WHITE);
        tip.setTextSize(14);
        tip.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tipLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, UiUtils.dp(this, 48), Gravity.BOTTOM);
        root.addView(tip, tipLp);
        root.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); }
        });
        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0f);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }
    }

    private ChannelSlot findChannelSlot(int channelId) {
        for (ChannelSlot slot : channelSlots) if (slot.channelId == channelId) return slot;
        return null;
    }

    private int[] channelCropSize(String title) {
        String t = title == null ? "" : title.toLowerCase();
        if (t.contains("icon") || t.contains("1000")) return new int[] {1000, 1000};
        if (t.contains("post") || t.contains("900")) return new int[] {900, 580};
        return new int[] {992, 558};
    }

    private int countCompleteChannelSlots() {
        int count = 0;
        for (ChannelSlot slot : channelSlots) {
            if (pendingChannelUris.containsKey(slot.channelId)
                    || (slot.fileJson != null && !slot.fileJson.trim().isEmpty())) count++;
        }
        return count;
    }

    /** 新图使用上传回执；旧图使用服务端原始 channel_url，确保整组图片不会被覆盖丢失。 */
    private JSONArray buildCompleteChannelArray(Map<Integer, String> uploads) throws Exception {
        JSONArray result = new JSONArray();
        for (ChannelSlot slot : channelSlots) {
            JSONObject entry = new JSONObject();
            entry.put("channel_id", slot.channelId);
            entry.put("version", slot.version);
            String uploaded = uploads.get(slot.channelId);
            if (uploaded != null && !uploaded.trim().isEmpty()) {
                entry.put("channel_url", new JSONObject(uploaded));
            } else {
                String stored = slot.fileJson == null ? "" : slot.fileJson.trim();
                if (stored.isEmpty()) continue;
                try { entry.put("channel_url", new JSONObject(stored)); }
                catch (Exception ignored) { try { entry.put("channel_url", new JSONArray(stored)); }
                catch (Exception ignoredAgain) { entry.put("channel_url", stored); } }
            }
            result.put(entry);
        }
        return result;
    }

    private String channelSpecText(String title) {
        if (title == null) return "992×558，仅 PNG / JPG / JPEG，<10MB";
        String t = title.toLowerCase();
        if (t.contains("icon") || t.contains("1000")) return "1000×1000，仅 PNG / JPG / JPEG，<10MB";
        if (t.contains("post") || t.contains("900")) return "900×580，仅 PNG / JPG / JPEG，<10MB";
        return "992×558，仅 PNG / JPG / JPEG，<10MB";
    }

    // ==================== 编辑：加载详情预填 ====================

    private void loadDetail() {
        statusText.setText("加载作品详情中...");
        statusText.setVisibility(View.VISIBLE);
        btnSave.setEnabled(false);
        new Thread(new Runnable() {
            @Override public void run() {
                String fetchedDetail = McDevApi.getWorkDetail(cookie, "pe", itemId);
                // 网络偶发返回空时仅重试一次；日志可明确区分两次请求。
                if (fetchedDetail == null) {
                    try { Thread.sleep(600); } catch (InterruptedException ignored) { }
                    fetchedDetail = McDevApi.getWorkDetail(cookie, "pe", itemId);
                }
                final String detailJson = fetchedDetail;
                UiUtils.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        btnSave.setEnabled(true);
                        if (detailJson == null) {
                            statusText.setText("详情加载失败，请检查网络/Cookie");
                            return;
                        }
                        try {
                            detail = new JSONObject(detailJson);
                            prefill(detail);
                            statusText.setVisibility(View.GONE);
                        } catch (Exception e) {
                            statusText.setText("详情解析失败：" + e.getMessage());
                        }
                    }
                });
            }
        }).start();
    }

    private void prefill(JSONObject d) throws Exception {
        // 只读元数据
        if (tvItemId != null) tvItemId.setText(d.optString("item_id"));
        if (tvNormalNumber != null) tvNormalNumber.setText(d.optString("normal_number"));
        if (tvItemVersion != null) tvItemVersion.setText(d.optString("item_version"));
        setText(etName, d.optString("item_name"));
        setYesNo(boxOriginal, d.optBoolean("is_original", true));
        corpRow.setVisibility(boxOriginal.value ? View.GONE : View.VISIBLE);
        setYesNo(boxShantou, d.optInt("is_domain_server_item", 0) == 1);
        boolean rel = false;
        JSONObject dlc = d.optJSONObject("dlc_info");
        if (dlc != null) {
            rel = dlc.optBoolean("dlc_switch", false);
            relatedIsMaster = !"slave".equals(dlc.optString("dlc_type"));
        }
        setYesNo(boxRelated, rel);
        if (relatedBox != null) relatedBox.setVisibility(rel ? View.VISIBLE : View.GONE);
        relateItemId = d.optString("relate_item_id");
        if (!relateItemId.isEmpty()) tvRelatedSel.setText("已选关联模组 ID: " + relateItemId);
        setYesNo(boxSyncPc, d.optBoolean("sync_pc_flag", false));
        showPcBoxes(boxSyncPc.value);
        corpProofImage = d.optString("corp_proof_image");
        if (!corpProofImage.isEmpty()) tvCorpProof.setText("已上传授权图");
        // 标签
        JSONArray tags = d.optJSONArray("tags");
        if (tags != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < tags.length(); i++) {
                JSONObject o = tags.optJSONObject(i);
                String n = o == null ? tags.optString(i) : o.optString("name");
                if (n.isEmpty()) continue;
                if (sb.length() > 0) sb.append(",");
                sb.append(n);
            }
            setText(etTags, sb.toString());
        }
        setText(etActivityDesc, d.optString("activity_desc"));
        // PC 基本信息 / PC 详情 / PC 上架设置（sync_item_info）
        JSONObject si = d.optJSONObject("sync_item_info");
        if (si != null) {
            setYesNo(boxPcIncludeMap, si.optBoolean("include_map", false));
            setText(etPcBrief, si.optString("brief"));
            setText(etPcDetail, si.optString("info"));
            setYesNo(boxPcWeakOffline, si.optBoolean("weak_offline", false));
            setText(etPcWeakOfflineReason, si.optString("weak_offline_reason"));
            JSONArray pcTags = si.optJSONArray("tag");
            if (pcTags != null) {
                StringBuilder pcsb = new StringBuilder();
                for (int i = 0; i < pcTags.length(); i++) {
                    JSONObject o = pcTags.optJSONObject(i);
                    String n = o == null ? pcTags.optString(i) : o.optString("name");
                    if (n.isEmpty()) continue;
                    if (pcsb.length() > 0) pcsb.append(",");
                    pcsb.append(n);
                }
                setText(etPcTags, pcsb.toString());
            }
        }
        // 前置模组（requirement 仅回显名称）
        JSONArray reqArr = d.optJSONArray("requirement");
        if (reqArr != null && reqArr.length() > 0) {
            JSONObject r0 = reqArr.optJSONObject(0);
            setText(etPrereq, r0 == null ? "" : r0.optString("item_name", r0.optString("name")));
        }
        // PE 资源管理
        peType = d.optInt("pri_type", 0);
        peSubType = d.optInt("sub_type", 0);
        peModSecond = d.optInt("mod_second_type", 0);
        JSONArray lt = d.optJSONArray("label_type_list");
        if (lt != null) { recommendTags.clear(); for (int i = 0; i < lt.length(); i++) recommendTags.add(lt.optInt(i)); }
        syncRecommendTagSwitches();
        setYesNo(boxPlayPlan, d.optBoolean("pe_is_add_play_plan", false));
        setYesNo(boxMount, d.optBoolean("mount_call_enabled", false));
        JSONArray res = d.optJSONArray("res");
        if (res != null && res.length() > 0) {
            JSONObject r0 = res.getJSONObject(0);
            resFileName = r0.optString("res_name");
            // 保留完整远端资源条目；未替换时保存请求沿用详情中的 res。
            resFileJson = r0.opt("res_url") == null ? "" : String.valueOf(r0.opt("res_url"));
            setYesNo(boxAddVersion, r0.optBoolean("add_version", false));
        }
        refreshResFile();
        // 更新纪要
        setText(etUpdateSummary, d.optString("update_summary"));
        if (tvSummaryCount != null) {
            tvSummaryCount.setText(d.optString("update_summary").replaceAll("\\s", "").length() + "/" + SUMMARY_MAX);
        }
        // 定价
        String pt = d.optString("price_type");
        if ("diamond".equals(pt)) priceTypeIdx = 2;
        else if ("point".equals(pt)) priceTypeIdx = 1;
        else priceTypeIdx = 0;
        priceRank = d.optInt("price_rank", 0);
        if (priceRank < 0 || priceRank > 6) priceRank = 0;
        priceTypeLocked = !d.optString("first_online_time").isEmpty();
        setPriceType(priceTypeIdx, true);
        setText(etPriceInput, String.valueOf(d.optInt("price")));
        if (tvPriceRead != null) {
            if (priceTypeIdx == 2) tvPriceRead.setText(d.optInt("price") + " 钻石");
            else if (priceTypeIdx == 0) tvPriceRead.setText("免费");
        }
        if (priceTypeLocked) {
            OreTextView lock = new OreTextView(this);
            lock.setText("⚠ 已上架作品不可修改定价类型");
            lock.setTextColor(Color.parseColor("#FF9800"));
            lock.setTextSize(12);
            formLayout.addView(lock);
            UiUtils.addGap(this, formLayout, 4);
        }
        // mc_version 由远端详情原样保留，不在此页面编辑。
        selectedModApiVersion = d.optString("mod_version");
        refreshModApiVersionCard();
        originalDetailHtml = d.optString("info");
        detailHtmlDirty = false;
        setRichHtml(etDetail, originalDetailHtml);
        // 上架设置
        setYesNo(boxWeakOffline, d.optBoolean("weak_offline", false));
        etWeakOfflineReason.setVisibility(boxWeakOffline.value ? View.VISIBLE : View.GONE);
        setText(etWeakOfflineReason, d.optString("weak_offline_reason"));
        // 渠道图
        JSONArray ch = d.optJSONArray("channel");
        if (ch != null) for (int i = 0; i < ch.length(); i++) {
            JSONObject o = ch.getJSONObject(i);
            int cid = o.optInt("channel_id");
            Object rawUrl = o.opt("channel_url");
            // 必须在频道槽位尚未加载时也保存：第二次打开时详情和配置返回顺序不同。
            remoteChannelData.put(cid, rawUrl);
            for (ChannelSlot slot : channelSlots) {
                if (slot.channelId == cid) bindRemoteChannelData(slot, rawUrl);
            }
        }
        // 视频
        JSONArray vids = d.optJSONArray("video_info_list");
        if (vids != null && vids.length() > 0) {
            JSONObject v0 = vids.getJSONObject(0);
            videoJson = v0.toString();
            videoName = "已上传视频";
        }
        refreshVideo();
        // 重绘下拉框文本（选项缓存已就绪）
        setAccSubtitle(accTypeValue, titleOf(peTypeOptions, peType));
        peSubTypeOptions = subTypeCache.get(peType);
        if (peSubTypeOptions == null) peSubTypeOptions = new ArrayList<>();
        setAccSubtitle(accSubTypeValue, titleOf(peSubTypeOptions, peSubType));
        peModSecondOptions = modSecondTypeCache.get(peType);
        if (peModSecondOptions == null) peModSecondOptions = new ArrayList<>();
        rebuildModSecondContent();
        setAccSubtitle(accTagPlayValue, tagTitles(playTagCache, recommendTags, "至少选 1 个"));
        setAccSubtitle(accTagThemeValue, tagTitles(themeTagCache, recommendTags, "至少选 1 个"));
        rebuildSubTypeContent();
        buildChannelRows();
    }

    // ==================== 下拉文本工具 ====================
    private String titleOf(List<MCConst> list, int id) {
        if (list != null) for (MCConst c : list) if (c.id == id) return c.title;
        return "";
    }
    private String tagTitles(List<MCConst> list, List<Integer> selected, String emptyText) {
        if (list == null || list.isEmpty()) return emptyText;
        StringBuilder sb = new StringBuilder();
        for (MCConst c : list) {
            if (selected.contains(c.id)) {
                if (sb.length() > 0) sb.append("、");
                sb.append(c.title);
            }
        }
        return sb.length() == 0 ? emptyText : sb.toString();
    }
    private String tagTitles(List<MCConst> list, boolean[] checked, String emptyText) {
        if (list == null || list.isEmpty()) return emptyText;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i < checked.length && checked[i]) {
                if (sb.length() > 0) sb.append("、");
                sb.append(list.get(i).title);
            }
        }
        return sb.length() == 0 ? emptyText : sb.toString();
    }
    private String join(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String s : list) { if (sb.length() > 0) sb.append("、"); sb.append(s); }
        return sb.toString();
    }

// ==================== 关联模组搜索 ====================

    private void searchRelated() {
        final String q = etRelatedSearch.getText().toString().trim();
        if (q.isEmpty()) { UiUtils.toast(this, "请输入搜索关键词"); return; }
        statusText.setText("搜索中...");
        statusText.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            @Override public void run() {
                final java.util.List<McDevApi.WorkItem> results = McDevApi.searchWorks(cookie, "pe", q, 1);
                UiUtils.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        statusText.setVisibility(View.GONE);
                        if (results == null || results.isEmpty()) {
                            UiUtils.toast(WorkDetailActivity.this, "未找到匹配作品");
                            return;
                        }
                        String[] names = new String[results.size()];
                        for (int i = 0; i < results.size(); i++) names[i] = results.get(i).itemName;
                        new OreDialogBuilder(WorkDetailActivity.this)
                                .setTitle("选择关联模组")
                                .setItems(names, new android.content.DialogInterface.OnClickListener() {
                                    @Override public void onClick(android.content.DialogInterface dlg, int which) {
                                        McDevApi.WorkItem w = results.get(which);
                                        relateItemId = w.itemId;
                                        tvRelatedSel.setText("已选: " + w.itemName + " (ID " + w.itemId + ")");
                                        dlg.dismiss();
                                    }
                                })
                                .setNegativeButton("取消", new android.content.DialogInterface.OnClickListener() {
                                    @Override public void onClick(android.content.DialogInterface dlg, int which) { dlg.dismiss(); }
                                })
                                .show();
                    }
                });
            }
        }).start();
    }

    /** 图片按示例 APP 的 image 文件上传流程上传，成功后插入当前光标。 */
    private void uploadDetailImageAndInsert(final Uri uri, final String name) {
        if (etDetail == null || uri == null) return;
        statusText.setText("上传介绍图片中...");
        statusText.setVisibility(View.VISIBLE);
        new Thread(new Runnable() { @Override public void run() {
            try {
                final String info = uploadByUri(uri.toString(), name, "image", "image/*");
                JSONObject o = new JSONObject(info);
                // 平台富文本使用上传结果 body 作为 img src；兼容不同上传响应字段。
                String url = extractRemoteUrl(o.opt("body"));
                if (url.isEmpty()) url = extractRemoteUrl(o);
                if (url.isEmpty()) throw new Exception("上传成功但未取得图片地址");
                final String tag = "<img src=\"" + url + "\" />";
                UiUtils.runOnUiThread(new Runnable() { @Override public void run() {
                    // 将现有富文本序列化后插入，再整体按 HTML 重建；新 img 立即成为 ImageSpan。
                    String base = getRichHtml(etDetail);
                    if (base.isEmpty()) base = "<p></p>";
                    // Android 文本光标无法可靠映射到 HTML 索引，图片追加在当前内容末尾，避免破坏标签结构。
                    originalDetailHtml = normalizeRichHtml(base + tag);
                    detailHtmlDirty = true;
                    setRichHtml(etDetail, originalDetailHtml);
                    etDetail.setSelection(etDetail.length());
                    statusText.setVisibility(View.GONE);
                }});
            } catch (final Exception e) {
                UiUtils.runOnUiThread(new Runnable() { @Override public void run() {
                    statusText.setText("介绍图片上传失败：" + e.getMessage());
                }});
            }
        }}).start();
    }

    /** HTML 实时显示为富文本；img 以可自动加载的 Drawable 呈现真实图片。 */
    @SuppressWarnings("deprecation")
    private void setRichHtml(final OreEditText edit, String html) {
        if (edit == null) return;
        if (html == null || html.isEmpty()) { edit.setText(""); return; }
        settingDetailHtml = true;
        try {
            Html.ImageGetter getter = new Html.ImageGetter() {
                @Override public Drawable getDrawable(String source) {
                    return new RichImageDrawable(edit, source);
                }
            };
            Spanned rich;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // COMPACT 避免 <p> 在每次回显后被转换成额外空白行。
                rich = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT, getter, null);
            } else rich = Html.fromHtml(html, getter, null);
            edit.setText(rich);
        } catch (Exception e) { edit.setText(html); }
        finally { settingDetailHtml = false; }
    }

    /** 使用连续行模式保存，规范化空段落，避免打开/保存后不断多出换行。 */
    @SuppressWarnings("deprecation")
    private String getRichHtml(OreEditText edit) {
        if (edit == null) return "";
        // 未编辑时绝对保留接口原文，避免 Html.toHtml 把已有 p/img 重写造成服务端校验失败。
        if (!detailHtmlDirty && edit == etDetail) return originalDetailHtml == null ? "" : originalDetailHtml;
        try {
            String html = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    ? Html.toHtml(edit.getText(), Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)
                    : Html.toHtml(edit.getText());
            // Android Html 会将尾部换行转成空 p；去掉无内容段落和重复 br。
            html = html.replaceAll("(?i)<p>\\s*(?:<br\\s*/?>)?\\s*</p>", "");
            html = html.replaceAll("(?i)(<br\\s*/?>\\s*){3,}", "<br>");
            return normalizeRichHtml(html);
        } catch (Exception e) { return edit.getText().toString(); }
    }

    /** 对齐示例 APP：img 仅保留 src，清除 Android 自动补出的尺寸和多余空段落。 */
    private String normalizeRichHtml(String html) {
        if (html == null) return "";
        // Android Html.toHtml 会附加 dir="ltr"，网易富文本白名单不允许该属性。
        html = html.replaceAll("(?i)\\s+dir\\s*=\\s*(['\"]).*?\\1", "");
        // 段落仅保留标签本身，避免编辑器自动附加的对齐/方向属性。
        html = html.replaceAll("(?i)<p\\s+[^>]*>", "<p>");
        html = html.replaceAll("(?is)<img\\s+([^>]*?)\\s*(?:width|height)\\s*=\\s*(['\"]).*?\\2", "<img $1");
        html = html.replaceAll("(?i)<img\\s+([^>]*?)\\s*/?>", "<img $1>");
        html = html.replaceAll("(?i)<p>\\s*(?:<br\\s*/?>)?\\s*</p>", "");
        html = html.replaceAll("(?i)(<br\\s*/?>\\s*){3,}", "<br>");
        return html.trim();
    }

    /** 真实图片 Drawable：先占用一行高度，后台下载后按编辑区宽度等比显示并刷新 OreEditText。 */
    private final class RichImageDrawable extends Drawable {
        private final OreEditText host;
        private final String source;
        private final Paint fallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Bitmap bitmap;
        RichImageDrawable(OreEditText host, String source) {
            this.host = host; this.source = source;
            fallbackPaint.setColor(Color.parseColor("#AAAAAA"));
            fallbackPaint.setTextSize(UiUtils.dp(WorkDetailActivity.this, 12));
            setBounds(0, 0, Math.max(UiUtils.dp(WorkDetailActivity.this, 140), host.getWidth()), UiUtils.dp(WorkDetailActivity.this, 42));
            load();
        }
        private void load() {
            new Thread(new Runnable() { @Override public void run() {
                InputStream in = null; java.net.HttpURLConnection c = null;
                try {
                    if (source.startsWith("content://")) in = getContentResolver().openInputStream(Uri.parse(source));
                    else {
                        c = (java.net.HttpURLConnection) new java.net.URL(source).openConnection();
                        c.setConnectTimeout(15000); c.setReadTimeout(15000); c.setUseCaches(false);
                        c.setRequestProperty("User-Agent", "NemoModStudio");
                        in = c.getInputStream();
                    }
                    final Bitmap loaded = BitmapFactory.decodeStream(in);
                    UiUtils.runOnUiThread(new Runnable() { @Override public void run() {
                        if (loaded == null || isFinishing()) return;
                        bitmap = loaded;
                        int available = host.getWidth() - host.getPaddingLeft() - host.getPaddingRight();
                        if (available <= 0) available = UiUtils.dp(WorkDetailActivity.this, 260);
                        int width = Math.max(UiUtils.dp(WorkDetailActivity.this, 80), available);
                        int height = Math.max(UiUtils.dp(WorkDetailActivity.this, 48),
                                Math.round(loaded.getHeight() * (width / (float) Math.max(1, loaded.getWidth()))));
                        setBounds(0, 0, width, height);
                        // TextView 对已有 ImageSpan 不一定会重新计算行高；重新设置同一 Spanned 强制完整测量。
                        CharSequence current = host.getText();
                        int cursor = host.getSelectionStart();
                        settingDetailHtml = true;
                        host.setText(current);
                        settingDetailHtml = false;
                        if (cursor >= 0 && cursor <= host.length()) host.setSelection(cursor);
                        host.requestLayout(); host.invalidate();
                    }});
                } catch (Exception ignored) {
                    // 失败时 draw() 显示完整 img 标签文本，不影响其余富文本编辑。
                } finally {
                    try { if (in != null) in.close(); } catch (Exception ignored) { }
                    if (c != null) c.disconnect();
                }
            }}).start();
        }
        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (bitmap != null) {
                canvas.drawBitmap(bitmap, null, b, null);
            } else {
                String text = "<img src=\"" + source + "\" />";
                canvas.save(); canvas.clipRect(b);
                canvas.drawText(text, b.left, b.top + UiUtils.dp(WorkDetailActivity.this, 22), fallbackPaint);
                canvas.restore();
            }
        }
        @Override public void setAlpha(int alpha) { fallbackPaint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter colorFilter) { fallbackPaint.setColorFilter(colorFilter); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    // ==================== 文件选择 ====================

    private void pickFile(int req, String mime) {
        Intent it = new Intent(Intent.ACTION_GET_CONTENT);
        it.setType(mime);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(it, "选择文件"), req);
        } catch (Exception e) {
            UiUtils.toast(this, "无法打开文件选择器");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == REQ_CHANNEL_CROP) {
            String croppedUri = data.getStringExtra(ChannelCropActivity.RESULT_CROPPED_URI);
            if (croppedUri == null || pendingChannelSel < 0) return;
            pendingChannelUris.put(pendingChannelSel, croppedUri);
            pendingChannelNames.put(pendingChannelSel, data.getStringExtra(ChannelCropActivity.RESULT_CROPPED_NAME));
            buildChannelRows();
            return;
        }
        if (data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_CHANNEL_IMG || requestCode == REQ_CORP || requestCode == REQ_RES || requestCode == REQ_VIDEO || requestCode == REQ_DETAIL_IMG) {
            try { getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
        }
        String name = queryDisplayName(uri);
        if (requestCode == REQ_DETAIL_IMG) {
            uploadDetailImageAndInsert(uri, name);
        } else if (requestCode == REQ_RES) {
            // 选择资源文件只更新资源文件槽位；不得重置分类、标签、定价或其他表单状态。
            pendingResUri = uri.toString();
            pendingResName = name;
            refreshResFile();
        } else if (requestCode == REQ_CORP) {
            pendingCorpUri = uri.toString();
            pendingCorpName = name;
            tvCorpProof.setText("待上传: " + name);
        } else if (requestCode == REQ_CHANNEL_IMG) {
            // 渠道图必须先经固定比例裁剪；原图只读，裁剪结果写入应用缓存。
            ChannelSlot slot = findChannelSlot(pendingChannelSel);
            if (slot == null) { UiUtils.toast(this, "未找到渠道图片位"); return; }
            int[] size = channelCropSize(slot.title);
            Intent crop = new Intent(this, ChannelCropActivity.class);
            crop.putExtra(ChannelCropActivity.EXTRA_SOURCE_URI, uri.toString());
            crop.putExtra(ChannelCropActivity.EXTRA_WIDTH, size[0]);
            crop.putExtra(ChannelCropActivity.EXTRA_HEIGHT, size[1]);
            crop.putExtra(ChannelCropActivity.EXTRA_NAME, name);
            startActivityForResult(crop, REQ_CHANNEL_CROP);
        } else if (requestCode == REQ_VIDEO) {
            pendingVideoUri = uri.toString();
            pendingVideoName = name;
            refreshVideo();
        }
    }

    private String queryDisplayName(Uri uri) {
        try {
            android.database.Cursor c = getContentResolver().query(uri, null, null, null, null);
            if (c != null) {
                int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0 && c.moveToFirst()) {
                    String n = c.getString(idx);
                    c.close();
                    return n;
                }
                c.close();
            }
        } catch (Exception ignored) {
        }
        String p = uri.getPath();
        return p == null ? "file" : p.substring(p.lastIndexOf('/') + 1);
    }

    // ==================== 提交（isReview=true 提审）====================

    private void onSave(final boolean isReview) {
        final String name = etName.getText().toString().trim();
        String validationError = validateBeforeSave(name);
        if (validationError != null) { UiUtils.toast(this, validationError); return; }
        statusText.setText("上传文件中...");
        statusText.setVisibility(View.VISIBLE);
        btnSave.setEnabled(false);
        btnReview.setEnabled(false);
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    String resJson = resFileJson;
                    if (pendingResUri != null) resJson = uploadByUri(pendingResUri, pendingResName, "zip_package", "application/octet-stream");
                    String corpUrl = corpProofImage;
                    if (pendingCorpUri != null) corpUrl = uploadByUri(pendingCorpUri, pendingCorpName, "image", "image/*");
                    Map<Integer, String> channelJsonMap = new LinkedHashMap<>();
                    for (ChannelSlot slot : channelSlots) {
                        if (pendingChannelUris.containsKey(slot.channelId)) {
                            channelJsonMap.put(slot.channelId, uploadByUri(pendingChannelUris.get(slot.channelId),
                                    pendingChannelNames.get(slot.channelId), "image", "image/*"));
                        }
                    }
                    String vidJson = videoJson;
                    if (pendingVideoUri != null) vidJson = uploadByUri(pendingVideoUri, pendingVideoName, "video", "video/*");
                    final JSONObject body = buildPayload(name, resJson, corpUrl, channelJsonMap, vidJson, isReview);
                    if (isEdit && detail != null) {
                        LogUtil.log("[SAVE_DEBUG] DETAIL keys=" + detail.names() + " length=" + detail.toString().length());
                        LogUtil.log("[SAVE_DEBUG] PAYLOAD keys=" + body.names() + " length=" + body.toString().length());
                    }
                    boolean saved;
                    String createdId = itemId;
                    if (isEdit) {
                        saved = McDevApi.updateWork(cookie, "pe", itemId, body.toString());
                    } else {
                        createdId = McDevApi.createWorkJson(cookie, "pe", body.toString());
                        saved = createdId != null;
                    }
                    final boolean ok = saved;
                    final String saveError = isEdit ? McDevApi.getLastError() : "";
                    // 新建接口有时不返回 item_id；此时由创建请求的 is_check_apply 完成提审。
                    final boolean reviewOk = !isReview || (isEdit
                            ? (saved && McDevApi.applyReview(cookie, "pe", createdId))
                            : (!"ok".equals(createdId) ? McDevApi.applyReview(cookie, "pe", createdId) : true));
                    final boolean shouldFinish = saved && (!isReview || reviewOk);
                    UiUtils.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            btnSave.setEnabled(true);
                            btnReview.setEnabled(true);
                            if (shouldFinish) {
                                UiUtils.toast(WorkDetailActivity.this, isReview ? "已保存并提审" : (isEdit ? "已保存" : "创建成功"));
                                setResult(RESULT_OK);
                                finish();
                            } else if (ok && isReview) {
                                statusText.setText("已保存，但提审失败；可返回管理页后重试提审");
                                setResult(RESULT_OK);
                            } else {
                                statusText.setText(isEdit ? ("保存失败：" + (saveError.isEmpty() ? "请检查网络/Cookie" : saveError)) : "创建失败，请检查网络/Cookie");
                            }
                        }
                    });
                } catch (final Exception e) {
                    UiUtils.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            btnSave.setEnabled(true);
                            btnReview.setEnabled(true);
                            statusText.setText("提交失败：" + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    /** 保存前在本地给出明确提示，避免仅收到服务端的笼统失败。 */
    private String validateBeforeSave(String name) {
        if (name == null || name.isEmpty()) return "资源名称不能为空";
        if (!boxOriginal.value && pendingCorpUri == null && corpProofImage.isEmpty()) return "非原创作品必须上传授权信息图";
        if (etTags.getText().toString().trim().isEmpty()) return "请至少添加一个模组标签";
        if (getRichHtml(etDetail).trim().isEmpty()) return "请填写 PE 详情信息";
        if (peType <= 0) return "请选择 PE 资源类别";
        if (peSubTypeOptions != null && !peSubTypeOptions.isEmpty() && peSubType <= 0) return "请选择 PE 具体类别";
        if (recommendTags.isEmpty()) return "请至少选择推荐标签";
        if (selectedModApiVersion.isEmpty()) return "请选择 modAPI 版本";
        if ((resFileName == null || resFileName.isEmpty()) && (pendingResName == null || pendingResName.isEmpty())) return "请上传 PE 资源文件";
        if (channelSlots.size() != 6) return "渠道图片位尚未加载完成，请稍后重试";
        int completeChannels = countCompleteChannelSlots();
        if (completeChannels != channelSlots.size()) return "请先上传全部 6 张渠道图片（当前已完成 " + completeChannels + "/6）";
        if (priceTypeIdx == 1 && parseInt(etPriceInput.getText().toString()) <= 0) return "绿宝石价格必须大于 0";
        if (priceTypeIdx == 2 && (videoRemoved || ((videoName == null || videoName.isEmpty()) && (pendingVideoName == null || pendingVideoName.isEmpty())))) return "钻石定价作品必须上传视频";
        if (boxWeakOffline.value && etWeakOfflineReason.getText().toString().trim().isEmpty()) return "弱下架时请填写下架理由";
        return null;
    }

    /** 上传 content Uri 指向的文件，返回 FileInfoDTO JSON 字符串。 */
    private String uploadByUri(String uriStr, String fileName, String fileType, String mimeType) throws Exception {
        Uri uri = Uri.parse(uriStr);
        File tmp = new File(getCacheDir(), "upload_" + System.currentTimeMillis() + "_" + (fileName == null ? "file" : fileName));
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            FileOutputStream fos = new FileOutputStream(tmp);
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) fos.write(buf, 0, n);
            fos.flush(); fos.close(); is.close();
            if ("video".equals(fileType) && tmp.length() > 50L * 1024 * 1024) {
                throw new Exception("视频文件大小不能超过 50MB");
            }
            String token = McDevApi.getFileToken(cookie, fileType);
            if (token == null) throw new Exception("获取上传 token 失败");
            String realMime = "image/*".equals(mimeType) ? guessImageMime(fileName) : mimeType;
            com.xtt.mcmodmaker.net.UploadHelper.UploadResult r =
                    com.xtt.mcmodmaker.net.UploadHelper.uploadFile(token, tmp, fileName == null ? "file" : fileName, realMime);
            if (r.sign == null || r.sign.isEmpty()) throw new Exception("上传失败，文件签名为空");
            JSONObject fileInfo = new JSONObject();
            fileInfo.put("body", r.body);
            fileInfo.put("file_type", fileType);
            fileInfo.put("sign", r.sign);
            return fileInfo.toString();
        } finally {
            tmp.delete();
        }
    }

    private String guessImageMime(String name) {
        String n = name == null ? "" : name.toLowerCase();
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    // ==================== 构造提交 body ====================

    /**
     * 将详情对象投影为更新接口允许的 WorkUpdateDTO 字段。
     * 只复制白名单中的服务端原值，避免 detail API 的状态/统计/展示字段导致整包保存被拒绝。
     */
    private JSONObject buildCleanUpdateBody(JSONObject source) throws Exception {
        JSONObject out = new JSONObject();
        if (source == null) source = new JSONObject();
        String[] keys = {
                "pre_review_video", "item_id", "item_version", "normal_number", "mc_version",
                "online_platform", "rarity", "multi_tags", "requirement", "mod_id", "java_version",
                "current_change_log", "pe_game_introduction", "body_type", "vanity_number",
                "remove_domain_server_reason", "ios_price", "ios_price_type", "ios_jelly_id",
                "android_price", "android_price_type", "adv_obtain_num", "force_encrypt", "pure",
                "claim_item_enabled", "exchange_currency", "exchange_currency_type", "decompose_currency",
                "decompose_currency_type", "discount", "charge_type", "charge_desc", "lobby_min_num",
                "lobby_max_num", "lobby_force_max_num", "lobby_tags", "lobby_camps", "lobby_player_num",
                "lobby_normal_mode", "lobby_reconnect_time", "is_lobby_competitive", "is_asymmetric",
                "lobby_commercialize", "pe_item_id", "pe_emotes_id", "pe_mc_item_id", "pe_home_cash",
                "pe_frame_id", "pe_furniture_id", "pe_six_month_vip_id", "pe_passport_ten_id",
                "pe_user_background_id", "pe_activity_coupon", "pe_chat_bubble_id", "pe_one_month_vip_id",
                "pe_lottery_chance", "subject_id", "is_sync", "vip_only", "is_season_mod",
                "activity_only", "searchable", "anti_cheat_enable", "version_compatible_enable",
                "is_ea", "achievement_enabled", "achievement_configs", "achievement_background_url",
                "is_spigot", "banner_pic", "season_begin", "is_lottery_reward", "item_update_push",
                "lottery_id", "is_persona", "is_recommend", "need_method_uuid", "need_behaviour_uuid",
                "dyeing_origin", "is_vip_benefit", "is_test_server", "is_access_by_uid", "is_can_comment",
                "is_quick_upload", "running_status", "main_city", "dyeing_relation", "dyeing",
                "persona_mtypeid", "persona_stypeid", "prerequisite_item_ids", "prerequisite_items",
                "relate_item_weak_offline", "white_list", "silent_white_list", "is_joint_activity",
                "joint_activity_detail", "joint_activity_name", "joint_activity_tag", "collection_id",
                "collection_name", "is_official_item", "openbeta_time", "commercial_time", "guide_list",
                "res", "channel", "video_info_list", "sync_item_info"
        };
        for (String key : keys) {
            if (source.has(key) && !source.isNull(key)) out.put(key, source.get(key));
        }
        // subject_id 详情接口会返回空字符串，但更新接口只接受 null/数值对象。
        if (out.has("subject_id") && (out.isNull("subject_id") || out.optString("subject_id").trim().isEmpty())) {
            out.remove("subject_id");
        }
        // WorkUpdateDTO 中必须稳定存在的默认字段。
        if (!out.has("item_id")) out.put("item_id", itemId);
        if (!out.has("item_version")) out.put("item_version", "");
        if (!out.has("normal_number")) out.put("normal_number", "");
        if (!out.has("mc_version")) out.put("mc_version", new JSONArray());
        if (!out.has("online_platform")) out.put("online_platform", new JSONArray());
        if (!out.has("multi_tags")) out.put("multi_tags", "0");
        if (!out.has("requirement")) out.put("requirement", new JSONArray());
        if (!out.has("pre_review_video")) out.put("pre_review_video", "{}");
        return out;
    }

    private JSONObject buildPayload(String name, String resJson, String corpUrl,
                                    Map<Integer, String> channelJsonMap, String vidJson,
                                    boolean isReview) throws Exception {
        // 标签
        JSONArray tagArr = new JSONArray();
        for (String s : etTags.getText().toString().split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) tagArr.put(new JSONObject().put("name", t));
        }
        // label_type_list
        JSONArray labelArr = new JSONArray();
        for (Integer id : recommendTags) labelArr.put(id);
        // 价格
        int price;
        String priceType;
        if (priceTypeIdx == 2) { priceType = "diamond"; price = RANK_PRICES[priceRank]; }
        else if (priceTypeIdx == 1) { priceType = "point"; price = parseInt(etPriceInput.getText().toString()); }
        else { priceType = "free"; price = 0; }
        int trial = detail == null ? 0 : detail.optInt("trial_duration", 0);
        // dlc_info
        JSONObject dlc = new JSONObject();
        boolean rel = boxRelated.value;
        dlc.put("dlc_switch", rel);
        dlc.put("dlc_type", !rel ? "off" : (relatedIsMaster ? "master" : "slave"));
        if (isEdit) {
            // ---- 对齐示例 App 的 ResourceDetailVO -> WorkUpdateDTO：
            // 更新接口只接受白名单 DTO 字段，不能把详情 API 的展示/只读字段整体回传。
            JSONObject body = buildCleanUpdateBody(detail);
            body.put("item_name", name);
            body.put("item_version", body.optString("item_version"));
            body.put("is_domain_server_item", boxShantou.value ? 1 : 0);
            body.put("is_original", boxOriginal.value);
            // WorkUpdateDTO 仅使用 tag；不要覆盖详情中的 tags 扩展字段，
            // 该字段服务端类型与 tag 不同，覆盖会导致未编辑时也被拒绝。
            body.put("tag", tagArr);
            // 更新 DTO 对 corp_proof_image 不接受 JSON null：仅非原创且确有授权图时发送该字段。
            // 原创作品（以及尚未上传授权图的非原创作品）必须完全省略该字段。
            if (!boxOriginal.value && corpUrl != null && !corpUrl.trim().isEmpty()) {
                body.put("corp_proof_image", corpUrl.trim());
            } else {
                body.remove("corp_proof_image");
            }
            body.put("activity_desc", etActivityDesc.getText().toString().trim());
            body.put("info", getRichHtml(etDetail).trim());
            body.put("update_summary", etUpdateSummary.getText().toString().replaceAll("\\s", ""));
            body.put("pri_type", peType);
            body.put("sub_type", peSubType);
            body.put("mod_second_type", peModSecond);
            body.put("label_type_list", labelArr);
            body.put("pe_is_add_play_plan", boxPlayPlan.value);
            body.put("mount_call_enabled", boxMount.value);
            body.put("sync_pc_flag", boxSyncPc.value);
            body.put("price_type", priceType);
            body.put("price_rank", priceTypeIdx == 2 ? priceRank : (priceTypeIdx == 1 ? -5 : -4));
            body.put("price", price);
            body.put("trial_duration", trial);
            // mc_version 保留详情值，不由表单覆盖。
            body.put("mod_version", selectedModApiVersion);
            body.put("weak_offline", boxWeakOffline.value);
            body.put("weak_offline_reason", etWeakOfflineReason.getText().toString().trim());
            body.put("dlc_info", dlc);
            body.put("relate_item_id", rel ? relateItemId : 0);
            body.put("is_check_apply", false);
            // PC 区块（sync_item_info）
            if (boxSyncPc.value) {
                JSONObject si = body.optJSONObject("sync_item_info");
                if (si == null) si = new JSONObject();
                si.put("brief", etPcBrief.getText().toString().trim());
                si.put("info", etPcDetail.getText().toString().trim());
                si.put("include_map", boxPcIncludeMap.value);
                si.put("weak_offline", boxPcWeakOffline.value);
                si.put("weak_offline_reason", etPcWeakOfflineReason.getText().toString().trim());
                JSONArray pcTagArr = new JSONArray();
                for (String s : etPcTags.getText().toString().split(",")) {
                    String t = s.trim();
                    if (!t.isEmpty()) pcTagArr.put(new JSONObject().put("name", t));
                }
                si.put("tag", pcTagArr);
                body.put("sync_item_info", si);
            }
            // 编辑未替换资源时保留服务器详情；只有本次新上传才覆盖资源数组。
            if (pendingResUri != null && resJson != null && !resJson.isEmpty()) {
                JSONArray resArr = new JSONArray();
                JSONObject r = new JSONObject();
                r.put("res_url", new JSONObject(resJson));
                r.put("res_name", pendingResName == null ? resFileName : pendingResName);
                // 新上传资源不伪造 mc_version，平台根据资源包解析。
                r.put("add_version", boxAddVersion.value);
                resArr.put(r);
                body.put("res", resArr);
            }
            // 渠道图为整体替换数组：必须同时带上未修改的远端图，不能只发送本次新上传的图片。
            body.put("channel", buildCompleteChannelArray(channelJsonMap));
            // 视频：未修改时保留详情；显式删除才写空数组；新上传才替换。
            if (videoRemoved) {
                body.put("video_info_list", new JSONArray());
            } else if (pendingVideoUri != null && vidJson != null && !vidJson.isEmpty()) {
                JSONArray vArr = new JSONArray();
                vArr.put(new JSONObject(vidJson));
                body.put("video_info_list", vArr);
            }
            return body;
        } else {
            // ---- WorkCreateDTO ----
            JSONObject body = new JSONObject();
            body.put("item_name", name);
            body.put("item_version", "0.1");
            body.put("online_platform", new JSONArray());
            body.put("label_type_list", labelArr);
            body.put("pri_type", peType).put("sub_type", peSubType).put("mod_second_type", peModSecond);
            body.put("info", getRichHtml(etDetail).trim());
            body.put("rarity", 0);
            body.put("tag", tagArr).put("multi_tags", "");
            JSONArray requirement = new JSONArray();
            String prereq = etPrereq.getText().toString().trim();
            if (!prereq.isEmpty()) requirement.put(new JSONObject().put("name", prereq));
            body.put("requirement", requirement).put("mod_id", 0).put("available_scope", "");
            body.put("force_encrypt", false);
            body.put("price_type", priceType).put("price_rank", priceTypeIdx == 2 ? priceRank : (priceTypeIdx == 1 ? -5 : -4));
            body.put("trial_duration", trial).put("price", price);
            body.put("ios_price", 0).put("ios_price_type", "").put("ios_jelly_id", "");
            body.put("android_price", 0).put("android_price_type", "").put("adv_obtain_num", 0);
            body.put("brief", "");
            body.put("pe_game_introduction", getRichHtml(etDetail).trim());
            body.put("game_host", "").put("pure", false);
            body.put("java_version", "");
            body.put("activity_desc", etActivityDesc.getText().toString().trim());
            body.put("claim_item_enabled", false);
            body.put("body_type", "").put("current_change_log", "");
            body.put("mod_version", selectedModApiVersion);
            body.put("searchable", true);
            body.put("is_original", boxOriginal.value);
            body.put("anti_cheat_enable", 0);
            body.put("is_ea", 0);
            body.put("achievement_enabled", 0);
            body.put("item_update_push", true);
            body.put("is_can_comment", false);
            body.put("include_map", boxPcIncludeMap.value && boxSyncPc.value);
            body.put("lobby_min_num", 0).put("lobby_max_num", 0).put("lobby_force_max_num", 10);
            body.put("lobby_tags", new JSONArray());
            body.put("is_domain_server_item", boxShantou.value ? 1 : 0);
            body.put("pe_is_add_play_plan", boxPlayPlan.value);
            body.put("mount_call_enabled", boxMount.value);
            body.put("update_summary", etUpdateSummary.getText().toString().replaceAll("\\s", ""));
            body.put("sync_pc_flag", boxSyncPc.value);
            body.put("dlc_info", dlc);
            body.put("relate_item_id", rel ? relateItemId : 0);
            body.put("exchange_currency", 0).put("exchange_currency_type", "ordinary");
            body.put("decompose_currency", 0).put("decompose_currency_type", "ordinary");
            body.put("need_method_uuid", false).put("need_behaviour_uuid", false);
            body.put("dyeing_origin", false);
            body.put("is_vip_benefit", false).put("is_test_server", false).put("is_access_by_uid", false);
            body.put("is_check_apply", isReview);
            body.put("weak_offline", boxWeakOffline.value);
            body.put("weak_offline_reason", etWeakOfflineReason.getText().toString().trim());
            // PC 区块（sync_item_info）
            if (boxSyncPc.value) {
                JSONObject si = new JSONObject();
                si.put("brief", etPcBrief.getText().toString().trim());
                si.put("info", etPcDetail.getText().toString().trim());
                si.put("include_map", boxPcIncludeMap.value);
                si.put("weak_offline", boxPcWeakOffline.value);
                si.put("weak_offline_reason", etPcWeakOfflineReason.getText().toString().trim());
                JSONArray pcTagArr = new JSONArray();
                for (String s : etPcTags.getText().toString().split(",")) {
                    String t = s.trim();
                    if (!t.isEmpty()) pcTagArr.put(new JSONObject().put("name", t));
                }
                si.put("tag", pcTagArr);
                body.put("sync_item_info", si);
            }
            // 资源
            if (resJson != null && !resJson.isEmpty()) {
                JSONArray resArr = new JSONArray();
                JSONObject r = new JSONObject();
                r.put("res_url", new JSONObject(resJson));
                r.put("res_name", pendingResName == null ? "resource" : pendingResName);
                // 新建资源的 mc_version 由平台解析，不由用户选择。
                r.put("add_version", boxAddVersion.value);
                resArr.put(r);
                body.put("res", resArr);
            }
            // 渠道图为整体替换数组：必须同时带上未修改的远端图，不能只发送本次新上传的图片。
            body.put("channel", buildCompleteChannelArray(channelJsonMap));
            // 视频
            if (vidJson != null && !vidJson.isEmpty()) {
                JSONArray vArr = new JSONArray();
                vArr.put(new JSONObject(vidJson));
                body.put("video_info_list", vArr);
            }
            return body;
        }
    }

    // ==================== 工具 ====================

    private static String bumpVersion(String version) {
        if (version == null || version.isEmpty()) return "0.1";
        String[] parts = version.split("\\.");
        int major = 0, minor = 0;
        try { major = Integer.parseInt(parts[0]); } catch (Exception ignored) {}
        if (parts.length > 1) { try { minor = Integer.parseInt(parts[1]); } catch (Exception ignored) {} }
        minor++;
        return minor > 9 ? (major + 1) + ".0" : major + "." + minor;
    }

    private int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    private void setText(OreEditText et, String s) {
        if (et != null) et.setText(s == null ? "" : s);
    }

    private void setYesNo(YesNoBox box, boolean b) {
        if (box != null) { box.value = b; refreshYesNo(box); }
    }

    /** 保留统一入口；开关监听器会自行处理具体动态区域。 */
    private void bindYesNo(final YesNoBox box, final Runnable onChanged) {
        if (box == null || box.toggle == null) return;
        box.toggle.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                box.value = checked;
                if (onChanged != null) onChanged.run();
            }
        });
    }

    /** 富文本：对选区（无选区=全文）切换 加粗/斜体/下划线/颜色。 */
    private void toggleRichStyle(OreEditText et, String tool) {
        if (et == null) return;
        Editable e = et.getText();
        if (e == null) return;
        int start = et.getSelectionStart();
        int end = et.getSelectionEnd();
        if (start < 0) start = 0;
        if (end < 0) end = e.length();
        if (start == end) { start = 0; end = e.length(); }
        if (start > end) { int t = start; start = end; end = t; }
        if (end <= start) { UiUtils.toast(this, "请先输入内容"); return; }
        if ("B".equals(tool)) {
            boolean has = false;
            android.text.style.StyleSpan[] ss = e.getSpans(start, end, android.text.style.StyleSpan.class);
            for (android.text.style.StyleSpan s : ss) {
                if (s.getStyle() == android.graphics.Typeface.BOLD) { e.removeSpan(s); has = true; }
            }
            if (!has) e.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if ("I".equals(tool)) {
            boolean has = false;
            android.text.style.StyleSpan[] ss = e.getSpans(start, end, android.text.style.StyleSpan.class);
            for (android.text.style.StyleSpan s : ss) {
                if (s.getStyle() == android.graphics.Typeface.ITALIC) { e.removeSpan(s); has = true; }
            }
            if (!has) e.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.ITALIC), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if ("U".equals(tool)) {
            boolean has = false;
            android.text.style.UnderlineSpan[] ss = e.getSpans(start, end, android.text.style.UnderlineSpan.class);
            for (android.text.style.UnderlineSpan s : ss) { e.removeSpan(s); has = true; }
            if (!has) e.setSpan(new android.text.style.UnderlineSpan(), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if ("A".equals(tool)) {
            boolean has = false;
            android.text.style.ForegroundColorSpan[] ss = e.getSpans(start, end, android.text.style.ForegroundColorSpan.class);
            for (android.text.style.ForegroundColorSpan s : ss) { e.removeSpan(s); has = true; }
            if (!has) e.setSpan(new android.text.style.ForegroundColorSpan(Color.parseColor("#FF5252")), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

}