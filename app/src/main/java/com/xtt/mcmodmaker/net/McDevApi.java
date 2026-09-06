package com.xtt.mcmodmaker.net;

import com.xtt.mcmodmaker.LogUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * MCDev 内容管理 API 客户端（参考 BitterLemonn/MCDevManager）。
 * 支持：邮箱密码登录（SM4+RSA+VDF）、Cookie 登录、作品列表、状态操作、删除。
 */
public final class McDevApi {

    public static final String BASE = "https://mc-launcher.webapp.163.com/";
    public static final String LOGIN_BASE = "https://dl.reg.163.com/";
    public static final String NETEASE_TOP_URL = "https://mcdev.webapp.163.com/#/login/";

    // 网易加密常量（来自 MCDevManager ConstantValue.kt）
    private static final String PKID = "kBSLIYY";
    private static final String PD = "x19_developer";
    private static final String PKHT = "mcdev.webapp.163.com";
    private static final int CHANNEL = 0;
    private static final String SM4_KEY = "BC60B8B9E4FFEFFA219E5AD77F11F9E2";
    private static final String RSA_KEY =
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC5gsH+AA4XWONB5TDcUd+xCz7ejOFHZKlcZDx+pF1i7Gsvi1vjyJoQhRtRSn950x498VUkx7rUxg1/ScBVfrRxQOZ8xFBye3pjAzfb22+RCuYApSVpJ3OO3KsEuKExftz9oFBv3ejxPlYc5yq7YiBO8XlTnQN0Sa4R4qhPO3I2MQIDAQAB";

    private static volatile String lastError = "";
    private McDevApi() {}

    /** 最近一次 API 请求失败原因，供界面显示；不含 Cookie 等敏感信息。 */
    public static String getLastError() { return lastError; }

    /** 作品项。 */
    public static class WorkItem {
        public String itemId;
        public String itemName;
        public String status;
        public String createTime;
        public String priceType;
        public int price;

        public WorkItem(String itemId, String itemName, String status, String createTime,
                        String priceType, int price) {
            this.itemId = itemId;
            this.itemName = itemName;
            this.status = status;
            this.createTime = createTime;
            this.priceType = priceType;
            this.price = price;
        }
    }

    /** 作品状态中文名。 */
    public static String statusLabel(String status) {
        if (status == null) return "未知";
        switch (status) {
            case "init": return "待提交审核";
            case "self_test": return "自测中";
            case "self_test_prepare": return "自测准备中";
            case "reviewing": return "审核中";
            case "prepare": return "系统准备中";
            case "rejected": return "审核未通过";
            case "accept": return "待上架";
            case "online": return "已上架";
            case "offline": return "已下架";
            case "system_offline": return "系统下架";
            case "online_preparing": return "上架准备中";
            default: return status;
        }
    }

    // ==================== HTTP 工具 ====================

    private static final int TIMEOUT = 20000;

    private static String http(String method, String url, String cookie, String jsonBody,
                               boolean captureCookie) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(TIMEOUT);
        conn.setReadTimeout(TIMEOUT);
        conn.setRequestMethod(method);
        conn.setUseCaches(false);
        conn.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0");
        conn.setRequestProperty("Pragma", "no-cache");
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36");
        if (cookie != null && !cookie.isEmpty()) {
            conn.setRequestProperty("Cookie", cookie);
        }
        if (jsonBody != null) {
            // JSON 必须 UTF-8 编码；DataOutputStream.writeBytes 会截断中文，
            // 导致服务端将 item_name/info/channel 等必填字段解析为“空”。
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);
            byte[] jsonBytes = jsonBody.getBytes("UTF-8");
            conn.setFixedLengthStreamingMode(jsonBytes.length);
            DataOutputStream out = new DataOutputStream(conn.getOutputStream());
            out.write(jsonBytes);
            out.flush();
            out.close();
        }
        int code = conn.getResponseCode();
        InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String body = readStream(is);
        if (captureCookie) {
            java.util.List<String> setCookies = conn.getHeaderFields().get("Set-Cookie");
            if (setCookies != null && !setCookies.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (String sc : setCookies) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(sc);
                }
                return "COOKIE|" + sb + "\nBODY|" + body;
            }
        }
        return body;
    }

    private static String readStream(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        return sb.toString();
    }

    private static String enc(String json) {
        return McDevCrypto.sm4Encrypt(json, SM4_KEY);
    }

    // ==================== 登录 ====================

    /**
     * 邮箱密码登录。成功返回 Cookie 字符串（含 NTES_SESS），失败返回 null。
     */
    public static String login(String email, String password) {
        try {
            String topUrl = NETEASE_TOP_URL;
            String cookieJar = ""; // 累积登录会话 Cookie（前几步 Set-Cookie 需带到最后一步）
            // 1. init（注意：topURL 传空字符串，与 MCDevManager GetCapIdDTO(topURL = "") 一致）
            JSONObject initReq = new JSONObject();
            initReq.put("pd", PD).put("pkid", PKID).put("pkht", PKHT)
                    .put("channel", CHANNEL).put("topURL", "")
                    .put("rtid", McDevCrypto.randomTid());
            PostResult initR = postLogin("dl/zj/mail/ini", cookieJar, enc(initReq.toString()));
            cookieJar = initR.cookie;
            JSONObject initResp = new JSONObject(initR.body);
            if (!initResp.has("ret") || initResp.optInt("ret") != 201) {
                return null;
            }

            // 2. getPower（获取 VDF 参数）
            JSONObject powerReq = new JSONObject();
            powerReq.put("pkid", PKID).put("pd", PD).put("un", email)
                    .put("channel", CHANNEL).put("topURL", topUrl)
                    .put("rtid", McDevCrypto.randomTid());
            PostResult powerR = postLogin("dl/zj/mail/powGetP", cookieJar, enc(powerReq.toString()));
            cookieJar = powerR.cookie;
            JSONObject powerResp = new JSONObject(powerR.body);
            JSONObject pvInfo = powerResp.optJSONObject("pVInfo");
            if (powerResp.optInt("ret") != 201 || pvInfo == null) {
                return null;
            }
            JSONObject pvArgs = pvInfo.optJSONObject("args");
            McDevCrypto.VdfResult vdf = McDevCrypto.computeVdf(
                    pvArgs.optString("puzzle"),
                    pvArgs.optString("mod"),
                    pvArgs.optString("x"),
                    pvArgs.optInt("t"),
                    pvInfo.optInt("minTime"),
                    pvInfo.optInt("maxTime"));
            JSONObject pvParam = new JSONObject();
            pvParam.put("maxTime", pvInfo.optInt("maxTime"))
                    .put("puzzle", pvArgs.optString("puzzle"))
                    .put("spendTime", vdf.spendTime)
                    .put("runTimes", vdf.runTimes)
                    .put("sid", pvInfo.optString("sid"))
                    .put("args", vdf.args);

            // 3. getTicket
            JSONObject ticketReq = new JSONObject();
            ticketReq.put("un", email).put("pd", PD).put("pkid", PKID)
                    .put("channel", CHANNEL).put("topURL", topUrl)
                    .put("rtid", McDevCrypto.randomTid());
            PostResult ticketR = postLogin("dl/zj/mail/gt", cookieJar, enc(ticketReq.toString()));
            cookieJar = ticketR.cookie;
            JSONObject ticketResp = new JSONObject(ticketR.body);
            if (ticketResp.optInt("ret") != 201 || !ticketResp.has("tk")) {
                return null;
            }
            String tk = ticketResp.optString("tk");

            // 4. safeLogin（密码 RSA 加密，携带累积的会话 Cookie）
            String encPw = McDevCrypto.rsaEncrypt(password, RSA_KEY);
            JSONObject loginReq = new JSONObject();
            loginReq.put("un", email).put("pw", encPw).put("pd", PD)
                    .put("l", 0).put("d", 10)
                    .put("t", System.currentTimeMillis())
                    .put("tk", tk).put("pwdKeyUp", 1)
                    .put("pkid", PKID).put("domains", "")
                    .put("pvParam", pvParam)
                    .put("channel", CHANNEL).put("topURL", topUrl)
                    .put("rtid", McDevCrypto.randomTid());
            String result = http("POST", LOGIN_BASE + "dl/zj/mail/l", cookieJar,
                    "{\"encParams\":\"" + enc(loginReq.toString()) + "\"}", true);
            if (result.startsWith("COOKIE|")) {
                String cookiePart = result.substring(7, result.indexOf("\nBODY|"));
                String bodyPart = result.substring(result.indexOf("\nBODY|") + 6);
                // 成功判定：服务端 ret==201（网易登录成功码），或已下发 NTES_SESS 会话 Cookie
                boolean hasNtesSess = cookiePart.contains("NTES_SESS");
                try {
                    JSONObject loginResp = new JSONObject(bodyPart);
                    int ret = loginResp.optInt("ret");
                    if (!hasNtesSess && ret != 201 && ret != 200) {
                        return null;
                    }
                } catch (Exception ignored) {
                }
                // 合并前几步累积的会话 Cookie，确保身份 Cookie（NTES_SESS/NTES_PASSPORT 等）完整
                return mergeCookies(cookieJar, extractCookies(cookiePart));
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 登录中间步骤：POST encParams 并累积 Set-Cookie。 */
    private static PostResult postLogin(String path, String cookie, String encJson) throws Exception {
        String r = http("POST", LOGIN_BASE + path, cookie,
                "{\"encParams\":\"" + encJson + "\"}", true);
        PostResult pr = new PostResult();
        if (r.startsWith("COOKIE|")) {
            String cookiePart = r.substring(7, r.indexOf("\nBODY|"));
            pr.body = r.substring(r.indexOf("\nBODY|") + 6);
            pr.cookie = mergeCookies(cookie, extractCookies(cookiePart));
        } else {
            pr.body = r;
            pr.cookie = cookie;
        }
        return pr;
    }

    /** 合并两段 cookie 字符串（按 key 去重，后者覆盖前者）。 */
    private static String mergeCookies(String existing, String newCookies) {
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        addCookies(map, existing);
        addCookies(map, newCookies);
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, String> e : map.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(e.getKey()).append("=").append(e.getValue());
        }
        return sb.toString();
    }

    private static void addCookies(java.util.Map<String, String> map, String cookieStr) {
        if (cookieStr == null || cookieStr.isEmpty()) return;
        for (String part : cookieStr.split(";")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            int idx = p.indexOf('=');
            if (idx > 0) map.put(p.substring(0, idx).trim(), p.substring(idx + 1).trim());
        }
    }

    /** 登录中间步骤结果：响应体 + 累积后的 Cookie。 */
    private static class PostResult {
        String body;
        String cookie;
    }



    /** 从 Set-Cookie 响应头提取 cookie 键值对。
     *  注意：http() 会将多个 Set-Cookie 头用 "\n" 拼接，这里必须先按行分割，
     *  否则第二行起的 cookie 键名会被上一行的属性字段污染（如 "HttpOnly\nNTES_PASSPORT"），
     *  导致关键身份 Cookie（NTES_PASSPORT 等）丢失、作品列表请求被服务端判为未登录。 */
    private static String extractCookies(String setCookieHeader) {
        StringBuilder sb = new StringBuilder();
        for (String line : setCookieHeader.split("\n")) {
            for (String part : line.split(";")) {
                String p = part.trim();
                if (p.isEmpty()) continue;
                String[] kv = p.split("=", 2);
                if (kv.length == 2) {
                    String key = kv[0].trim();
                    String val = kv[1].trim();
                    // 跳过过期/属性字段
                    if (key.equalsIgnoreCase("Path") || key.equalsIgnoreCase("Domain")
                            || key.equalsIgnoreCase("Expires") || key.equalsIgnoreCase("Max-Age")
                            || key.equalsIgnoreCase("SameSite") || key.equalsIgnoreCase("Secure")
                            || key.equalsIgnoreCase("HttpOnly")) {
                        continue;
                    }
                    if (sb.length() > 0) sb.append("; ");
                    sb.append(key).append("=").append(val);
                }
            }
        }
        return sb.toString();
    }

    /** 校验 Cookie 是否有效（请求用户信息）。 */
    public static boolean validateCookie(String cookie) {
        try {
            String body = http("GET", BASE + "users/me", cookie, null, false);
            JSONObject obj = new JSONObject(body);
            return "ok".equals(obj.optString("status"));
        } catch (Exception e) {
            return false;
        }
    }

    /** 获取用户昵称。 */
    public static String getUserName(String cookie) {
        try {
            String body = http("GET", BASE + "users/me", cookie, null, false);
            JSONObject obj = new JSONObject(body);
            JSONObject data = obj.optJSONObject("data");
            if (data != null) {
                String nick = data.optString("nick_name");
                if (!nick.isEmpty()) return nick;
                String email = data.optString("email");
                if (!email.isEmpty()) return email;
                return String.valueOf(data.optLong("uid"));
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 作品管理 ====================

    /** 获取作品列表。 */
    public static List<WorkItem> getWorks(String cookie) throws Exception {
        // 先验证 Cookie 是否有效
        if (!validateCookie(cookie)) {
            throw new Exception("Cookie已失效，请重新登录");
        }
        List<WorkItem> list = new ArrayList<>();
        try {
            String body = http("GET", BASE + "items/categories/pe?start=0&span=100",
                    cookie, null, false);
            JSONObject obj = new JSONObject(body);
            JSONObject data = obj.optJSONObject("data");
            if (data == null) return list;
            JSONArray items = data.optJSONArray("item");
            if (items == null) return list;
            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.getJSONObject(i);
                list.add(new WorkItem(
                        it.optString("item_id"),
                        it.optString("item_name"),
                        it.optString("status"),
                        it.optString("create_time"),
                        it.optString("price_type"),
                        it.optInt("price")));
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    /**
     * 作品状态操作（自测/取消自测/审核/上架等）。
     * 请求与响应均写入本地崩溃日志用于定位平台校验失败，绝不记录 Cookie。
     */
    public static boolean changeStatus(String cookie, String platform, String itemId,
                                       String status, String jsonBody) {
        lastError = "";
        String url = BASE + "items/categories/" + platform + "/" + itemId + "/" + status;
        try {
            LogUtil.log("[ACTION_DEBUG] START item=" + itemId + " action=" + status
                    + " url=" + url + " body=" + (jsonBody == null ? "<empty>" : jsonBody));
            String body = http("PUT", url, cookie, jsonBody, false);
            LogUtil.log("[ACTION_DEBUG] RESPONSE item=" + itemId + " action=" + status
                    + " body=" + body);
            JSONObject result = new JSONObject(body);
            if ("ok".equals(result.optString("status"))) {
                LogUtil.log("[ACTION_DEBUG] SUCCESS item=" + itemId + " action=" + status);
                return true;
            }
            lastError = result.optString("message", result.optString("msg", ""));
            if (lastError.isEmpty()) {
                JSONObject errors = result.optJSONObject("errors");
                lastError = errors == null ? "服务端拒绝操作" : errors.toString();
            }
            LogUtil.log("[ACTION_DEBUG] REJECT item=" + itemId + " action=" + status
                    + " error=" + lastError);
            return false;
        } catch (Exception e) {
            lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            LogUtil.log("[ACTION_DEBUG] EXCEPTION item=" + itemId + " action=" + status
                    + " error=" + e);
            LogUtil.logException(e);
            return false;
        }
    }

    /** 收件箱列表项。 */
    public static class MailItem { public String id,title,mailType,time; public boolean haveRead;
        public MailItem(String id,String title,String mailType,String time,boolean haveRead){this.id=id;this.title=title;this.mailType=mailType;this.time=time;this.haveRead=haveRead;} }
    /** 收件箱详情。 */
    public static class MailContent { public String sender,detail; public MailContent(String sender,String detail){this.sender=sender;this.detail=detail;} }
    public static List<MailItem> getMailbox(String cookie, String mailType) {
        lastError=""; try { String url=BASE+"mailbox?start=0&span=50&initLoad=true"+(mailType==null?"":"&mail_type="+java.net.URLEncoder.encode(mailType,"UTF-8")); String raw=http("GET",url,cookie,null,false); JSONObject root=new JSONObject(raw); if(!"ok".equals(root.optString("status"))){lastError=root.optString("message",root.optString("msg","加载消息失败"));return null;} JSONObject data=root.optJSONObject("data"); JSONArray a=data==null?null:data.optJSONArray("mail"); List<MailItem> out=new ArrayList<>(); if(a!=null)for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null)out.add(new MailItem(o.optString("_id"),o.optString("title"),o.optString("mail_type"),o.optString("time"),o.optBoolean("have_read",true)));} return out;
        } catch(Exception e){lastError=e.getMessage()==null?"加载消息失败":e.getMessage();LogUtil.log("[MAILBOX] LIST error="+e);return null;} }
    public static MailContent getMailContent(String cookie,String id) { lastError="";try{String raw=http("GET",BASE+"mailbox/"+java.net.URLEncoder.encode(id,"UTF-8"),cookie,null,false);JSONObject root=new JSONObject(raw);if(!"ok".equals(root.optString("status"))){lastError=root.optString("message",root.optString("msg","打开消息失败"));return null;}JSONObject d=root.optJSONObject("data");return d==null?new MailContent("",""):new MailContent(d.optString("sender"),d.optString("detail"));}catch(Exception e){lastError=e.getMessage()==null?"打开消息失败":e.getMessage();return null;} }
    public static boolean readAllMail(String cookie) { JSONObject b=new JSONObject(); try{b.put("mail_id_list",new JSONArray());b.put("read_all",true);}catch(Exception ignored){} return mailboxPost(cookie,"mailbox/read_mail/",b); }
    public static boolean deleteMails(String cookie,List<String> ids) { JSONObject b=new JSONObject();try{JSONArray array=new JSONArray();for(String id:ids)array.put(id);b.put("mail_id_list",array);}catch(Exception ignored){}return mailboxPost(cookie,"mailbox/delete_many/",b); }
    /** 邮箱 POST 接口要求 URL 保留末尾 /；否则服务端重定向至 HTML 页导致 JSON 解析错误。 */
    private static boolean mailboxPost(String cookie,String path,JSONObject body){lastError="";try{String raw=http("POST",BASE+path,cookie,body.toString(),false);LogUtil.log("[MAILBOX] POST path="+path+" response="+raw);String trimmed=raw==null?"":raw.trim();if(!trimmed.startsWith("{")){lastError="服务端返回非 JSON 响应，请重新登录后重试";return false;}JSONObject r=new JSONObject(trimmed);if("ok".equals(r.optString("status")))return true;lastError=r.optString("message",r.optString("msg","操作失败"));return false;}catch(Exception e){lastError=e.getMessage()==null?"操作失败":e.getMessage();LogUtil.log("[MAILBOX] POST error="+e);return false;}}

    /** 保存成功后单独发起提审，避免把提审状态混入 update 请求。 */
    public static boolean applyReview(String cookie, String platform, String itemId) {
        try {
            JSONObject body = new JSONObject();
            body.put("apply_review_text", "");
            body.put("conflict_notify", 1);
            body.put("conflict_notify_type", new JSONArray().put(1));
            body.put("is_check_apply", false);
            String url = BASE + "items/categories/" + platform + "/" + itemId + "/apply_review";
            String response = http("PUT", url, cookie, body.toString(), false);
            return "ok".equals(new JSONObject(response).optString("status"));
        } catch (Exception e) {
            return false;
        }
    }

    /** 删除作品。 */
    public static boolean deleteWork(String cookie, String platform, String itemId) {
        try {
            String url = BASE + "items/categories/" + platform + "/" + itemId;
            String body = http("DELETE", url, cookie, null, false);
            return new JSONObject(body).optString("status").equals("ok");
        } catch (Exception e) {
            return false;
        }
    }

    /** 获取作品详情（GET items/categories/pe/{itemId}）。成功返回 data 对象 JSON，失败返回 null。 */
    public static String getWorkDetail(String cookie, String platform, String itemId) {
        try {
            // 使用时间戳与无缓存头，避免部分网络环境第二次返回缓存的空详情。
            String url = BASE + "items/categories/" + platform + "/" + itemId
                    + "?_=" + System.currentTimeMillis();
            String body = http("GET", url, cookie, null, false);
            JSONObject obj = new JSONObject(body);
            if (!"ok".equals(obj.optString("status"))) return null;
            JSONObject data = obj.optJSONObject("data");
            return data == null ? null : data.toString();
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 作品详情页选项数据 ====================

    /** 获取常量配置（资源类别/具体类别/次级分类/推荐标签/频道/mc版本等）。返回 data JSON，失败 null。 */
    public static String getMCConsts(String cookie) {
        return getJsonData(cookie, BASE + "items/mc_consts");
    }

    /** 获取默认模组标签（搜索建议）。返回 data JSON（{tag_list:[...]}），失败 null。 */
    public static String getItemTag(String cookie) {
        return getJsonData(cookie, BASE + "item-tag");
    }

    /** 搜索可作前置的模组（comp requirements）。返回 data JSON，失败 null。 */
    public static String getRequirements(String cookie, String query) {
        try {
            return getJsonData(cookie, BASE + "items/categories/comp/requirements?query_str="
                    + java.net.URLEncoder.encode(query, "UTF-8"));
        } catch (Exception e) {
            return null;
        }
    }

    /** 搜索作品（关联模组 pe / PC 前置 comp，mcStatus=1 表示已上架）。返回作品列表。 */
    public static List<WorkItem> searchWorks(String cookie, String platform, String itemName, int mcStatus) {
        List<WorkItem> list = new ArrayList<>();
        try {
            String url = BASE + "items/categories/" + platform + "?start=0&span=20";
            if (itemName != null && !itemName.isEmpty()) {
                url += "&item_name=" + java.net.URLEncoder.encode(itemName, "UTF-8");
            }
            if (mcStatus >= 0) url += "&mc_status=" + mcStatus;
            String body = http("GET", url, cookie, null, false);
            JSONObject obj = new JSONObject(body);
            JSONObject data = obj.optJSONObject("data");
            if (data == null) return list;
            JSONArray items = data.optJSONArray("item");
            if (items == null) return list;
            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.getJSONObject(i);
                list.add(new WorkItem(
                        it.optString("item_id"),
                        it.optString("item_name"),
                        it.optString("status"),
                        it.optString("create_time"),
                        it.optString("price_type"),
                        it.optInt("price")));
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    /** 获取文件上传 token（filepicker/file_token）。返回 data.token，失败 null。 */
    public static String getFileToken(String cookie, String fileType) {
        try {
            String body = http("GET", BASE + "filepicker/file_token?file_type=" + fileType
                    + "&secure=false", cookie, null, false);
            JSONObject obj = new JSONObject(body);
            if (!"ok".equals(obj.optString("status"))) return null;
            JSONObject data = obj.optJSONObject("data");
            return data == null ? null : data.optString("token");
        } catch (Exception e) {
            return null;
        }
    }

    private static String getJsonData(String cookie, String url) {
        try {
            String body = http("GET", url, cookie, null, false);
            JSONObject obj = new JSONObject(body);
            if (!"ok".equals(obj.optString("status"))) return null;
            JSONObject data = obj.optJSONObject("data");
            return data == null ? null : data.toString();
        } catch (Exception e) {
            return null;
        }
    }
    /**
     * 新建作品（完整 body 版本，POST items/categories/{platform}/upload）。
     * 成功返回 item_id（若响应含），否则返回 "ok"；失败返回 null。
     */
    public static String createWorkJson(String cookie, String platform, String jsonBody) {
        try {
            String url = BASE + "items/categories/" + platform + "/upload";
            String resp = http("POST", url, cookie, jsonBody, false);
            JSONObject obj = new JSONObject(resp);
            if (!"ok".equals(obj.optString("status"))) return null;
            JSONObject data = obj.optJSONObject("data");
            if (data != null && data.has("item_id")) return data.optString("item_id");
            return "ok";
        } catch (Exception e) {
            return null;
        }
    }
    /**
     * 编辑已有作品（POST items/categories/{platform}/{itemId}/update）。
     * jsonBody 可传完整 WorkUpdateDTO（item_name/mc_version/price_type 等），
     * 也可传部分字段（如仅改名称：{"item_name":"新名称"}）。
     */
    public static boolean updateWork(String cookie, String platform, String itemId,
                                     String jsonBody) {
        lastError = "";
        String url = BASE + "items/categories/" + platform + "/" + itemId + "/update";
        try {
            JSONObject request = new JSONObject(jsonBody);
            // 不记录 Cookie；作品内容仅用于定位服务器字段校验失败。
            LogUtil.log("[SAVE_DEBUG] START item=" + itemId + " url=" + url
                    + " keys=" + request.names() + " length=" + jsonBody.length());
            LogUtil.log("[SAVE_DEBUG] BODY=" + jsonBody);
            String body = http("POST", url, cookie, jsonBody, false);
            LogUtil.log("[SAVE_DEBUG] RESPONSE item=" + itemId + " body=" + body);
            JSONObject result = new JSONObject(body);
            if ("ok".equals(result.optString("status"))) {
                LogUtil.log("[SAVE_DEBUG] SUCCESS item=" + itemId);
                return true;
            }
            lastError = result.optString("message", result.optString("msg", "服务端拒绝保存"));
            if (lastError == null || lastError.isEmpty()) lastError = "服务端拒绝保存";
            LogUtil.log("[SAVE_DEBUG] REJECT item=" + itemId + " error=" + lastError);
            return false;
        } catch (Exception e) {
            lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            LogUtil.log("[SAVE_DEBUG] EXCEPTION item=" + itemId + " error=" + e);
            LogUtil.logException(e);
            return false;
        }
    }

    /**
     * 新建作品（POST items/categories/{platform}/upload）。
     * 成功返回 item_id（若响应含），否则返回 "ok"；失败返回 null。
     */
    public static String createWork(String cookie, String platform, String name, String brief) {
        try {
            JSONObject body = new JSONObject();
            body.put("item_name", name == null ? "" : name);
            body.put("mc_version", new JSONArray());
            body.put("online_platform", new JSONArray());
            body.put("label_type_list", new JSONArray());
            body.put("pri_type", 0).put("sub_type", 0).put("info", "").put("rarity", 0);
            body.put("lobby_min_num", 0).put("lobby_max_num", 0).put("lobby_force_max_num", 10);
            body.put("lobby_tags", new JSONArray());
            body.put("include_map", false).put("tag", new JSONArray()).put("multi_tags", "");
            body.put("requirement", new JSONArray()).put("mod_id", 0).put("available_scope", "");
            body.put("force_encrypt", false).put("price_type", "free").put("price_rank", 0);
            body.put("trial_duration", 0).put("price", 0);
            body.put("ios_price", 0).put("ios_price_type", "").put("ios_jelly_id", "");
            body.put("android_price", 0).put("android_price_type", "").put("adv_obtain_num", 0);
            body.put("brief", brief == null ? "" : brief);
            body.put("pe_game_introduction", "").put("game_host", "").put("pure", false);
            body.put("java_version", "").put("activity_desc", "").put("claim_item_enabled", false);
            body.put("body_type", "").put("current_change_log", "").put("mod_version", "");
            body.put("searchable", true).put("is_original", true).put("item_update_push", true);
            body.put("exchange_currency", 0).put("exchange_currency_type", "ordinary");
            body.put("decompose_currency", 0).put("decompose_currency_type", "ordinary");
            body.put("anti_cheat_enable", 0).put("is_ea", 0).put("achievement_enabled", 0);
            body.put("achievement_background_url", "").put("is_spigot", false).put("banner_pic", "");
            body.put("season_begin", 0).put("is_lottery_reward", false).put("lottery_id", 0);
            body.put("is_persona", false).put("is_recommend", false);
            body.put("persona_mtypeid", 0).put("persona_stypeid", 0).put("dyeing", "");
            body.put("need_method_uuid", false).put("need_behaviour_uuid", false);
            body.put("dyeing_origin", false).put("is_vip_benefit", false).put("is_test_server", false);
            body.put("is_access_by_uid", false).put("is_can_comment", false);
            String url = BASE + "items/categories/" + platform + "/upload";
            String resp = http("POST", url, cookie, body.toString(), false);
            JSONObject obj = new JSONObject(resp);
            if (!"ok".equals(obj.optString("status"))) return null;
            JSONObject data = obj.optJSONObject("data");
            if (data != null && data.has("item_id")) return data.optString("item_id");
            return "ok";
        } catch (Exception e) {
            return null;
        }
    }
}