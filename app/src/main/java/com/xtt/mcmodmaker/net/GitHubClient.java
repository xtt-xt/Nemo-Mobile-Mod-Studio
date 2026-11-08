package com.xtt.mcmodmaker.net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** GitHub 账号接口：列出自己的仓库，用于「从 GitHub 导入」。 */
public final class GitHubClient {

    /** 一个仓库的基本信息。 */
    public static final class Repo {
        public final String name;
        public final String fullName;
        public final String cloneUrl;
        public final String description;
        public final String defaultBranch;
        public final boolean isPrivate;
        public final String updatedAt;

        Repo(String name, String fullName, String cloneUrl, String description,
             String defaultBranch, boolean isPrivate, String updatedAt) {
            this.name = name;
            this.fullName = fullName;
            this.cloneUrl = cloneUrl;
            this.description = description;
            this.defaultBranch = defaultBranch;
            this.isPrivate = isPrivate;
            this.updatedAt = updatedAt;
        }
    }

    private static final String API = "https://api.github.com";

    /** 列出当前令牌可见的仓库，最近更新的在前。 */
    public static List<Repo> listRepos(String token) throws Exception {
        List<Repo> out = new ArrayList<>();
        int page = 1;
        while (page <= 5) {
            JSONArray arr = get(token, "/user/repos?per_page=100&sort=updated&page=" + page);
            if (arr == null) break;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                out.add(new Repo(
                        o.optString("name"),
                        o.optString("full_name"),
                        o.optString("clone_url"),
                        o.optString("description"),
                        o.optString("default_branch", "main"),
                        o.optBoolean("private"),
                        o.optString("updated_at")));
            }
            if (arr.length() < 100) break;
            page++;
        }
        return out;
    }

    /** 令牌对应的是谁（用来确认令牌有效）。 */
    public static String login(String token) throws Exception {
        JSONObject o = getObject(token, "/user");
        return o == null ? "" : o.optString("login");
    }

    private static JSONArray get(String token, String path) {
        try {
            Object parsed = request(token, path);
            return parsed instanceof JSONArray ? (JSONArray) parsed : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static JSONObject getObject(String token, String path) {
        try {
            Object parsed = request(token, path);
            return parsed instanceof JSONObject ? (JSONObject) parsed : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Object request(String token, String path) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(API + path).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(25000);
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("Authorization", "token " + token.trim());
        conn.setRequestProperty("User-Agent", "Nemo-Mobile-Mod-Studio");
        try {
            int code = conn.getResponseCode();
            if (code == 401) throw new Exception("令牌无效或已过期");
            if (code == 403) throw new Exception("令牌权限不足（需要 repo 权限）");
            if (code < 200 || code >= 300) throw new Exception("GitHub 返回 " + code);
            InputStream in = conn.getInputStream();
            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            String text = sb.toString().trim();
            if (text.startsWith("[")) return new JSONArray(text);
            return new JSONObject(text);
        } finally {
            conn.disconnect();
        }
    }

    private GitHubClient() {
    }
}
