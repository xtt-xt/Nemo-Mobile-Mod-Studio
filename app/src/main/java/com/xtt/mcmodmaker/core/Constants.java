package com.xtt.mcmodmaker.core;

/** 全局常量。 */
public final class Constants {

    /** 模组项目根目录。 */
    public static final String MOD_FOLDER_PATH = "/storage/emulated/0/McMod";

    /** 项目文件夹存放目录（各项目/备份均在此）。 */
    public static final String PROJECTS_DIR = MOD_FOLDER_PATH + "/mods";

    /** 设置文件路径。 */
    public static final String SETTING_FILE = MOD_FOLDER_PATH + "/settings.json";

    /** WebView 下载保存目录。 */
    public static final String DOWNLOAD_DIR = MOD_FOLDER_PATH + "/Download";

    /** FileProvider authority。 */
    public static final String FILE_PROVIDER_AUTHORITY = "com.xtt.mcmodmaker.fileprovider";

    public static final String GITHUB_API_LATEST =
            "https://api.github.com/repos/xtt-xt/Nemo-Mobile-Mod-Studio/releases/latest";
    public static final String GITHUB_PAGE =
            "https://github.com/xtt-xt/Nemo-Mobile-Mod-Studio";
    public static final String ORE_UI_PAGE =
            "https://github.com/xtt-xt/ore-ui-for-android";
    public static final String UPDATE_PAN_URL =
            "https://1825385503.share.123865.com/123pan/0EQWjv-MafMd?pwd=1379";
    public static final String MC_DEV_WEB =
            "https://mcdev.webapp.163.com/#/square?channel=cnt";
    /** MCDevManager 开源项目地址。 */
    public static final String MC_DEV_MANAGER_PAGE =
            "https://github.com/BitterLemonn/MCDevManager";
    /** MCDev API 文档地址。 */
    public static final String MC_DEV_API_DOCS =
            "https://mcdev-docs.xtt.p8.ink/";

    /** SharedPreferences 名称。 */
    public static final String PREFS_NAME = "mcmod_prefs";

    public static final String PREFS_LAST_CHECK_DATE = "last_update_check_date";
    public static final String PREFS_UPDATE_DIALOG_SHOWN_TODAY = "update_dialog_shown_today";
    public static final String PREFS_FIRST_RUN = "first_run";
    /** MCDev 登录 Cookie。 */
    public static final String PREFS_MC_COOKIE = "mcdev_cookie";
    /** MCDev 登录邮箱。 */
    public static final String PREFS_MC_EMAIL = "mcdev_email";

    private Constants() {
    }
}