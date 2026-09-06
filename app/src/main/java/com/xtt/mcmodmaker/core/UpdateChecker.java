package com.xtt.mcmodmaker.core;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/** GitHub Release 更新检查。 */
public final class UpdateChecker {

    /** 发布信息。 */
    public static class ReleaseInfo {
        public final String version;
        public final String body;
        public final String htmlUrl;

        ReleaseInfo(String version, String body, String htmlUrl) {
            this.version = version;
            this.body = body;
            this.htmlUrl = htmlUrl;
        }
    }

    public interface Callback {
        /** release 为 null 表示已是最新版本。 */
        void onResult(ReleaseInfo release);

        void onError(String message);
    }

    /**
     * 异步检查最新版本。
     *
     * @param currentVersion 当前版本号（versionName）
     */
    public static void checkLatest(final String currentVersion, final Callback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String response = httpGet(Constants.GITHUB_API_LATEST);
                    String trimmed = response == null ? "" : response.trim();
                    if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
                        callback.onError("服务器返回异常，请稍后再试");
                        return;
                    }
                    JSONObject json = new JSONObject(trimmed);
                    String tag = json.optString("tag_name", "");
                    String version = tag.startsWith("v") ? tag.substring(1) : tag;
                    if (version.isEmpty() || version.equals(currentVersion)) {
                        callback.onResult(null);
                    } else {
                        callback.onResult(new ReleaseInfo(version,
                                json.optString("body", ""),
                                json.optString("html_url", "")));
                    }
                } catch (final Exception e) {
                    callback.onError(e.getMessage());
                }
            }
        }).start();
    }

    /** 发起 GET 请求并返回响应文本。 */
    public static String httpGet(String urlStr) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "NemoModStudio");
            conn.setRequestProperty("Accept", "application/json");
            try (InputStream is = conn.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                return sb.toString();
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private UpdateChecker() {
    }
}