package com.xtt.mcmodmaker.net;

import android.util.Base64;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 网易 FP 文件上传工具（zip 资源 / 渠道图 / 视频）。
 *
 * 流程（与 MCDevManager 一致）：
 *  1. McDevApi.getFileToken(cookie, fileType) 获取上传凭证（"Policy <sig>:<base64-json>"）
 *  2. 解析 token 中 base64 部分 → 实际上传地址（zip_package → pfp.ps.netease.com，image/video → fp.ps.netease.com）
 *  3. multipart/form-data POST：Authorization=<token>，fpfile=<文件>
 *  4. 读取响应头 x-ntes-signature（文件签名）与响应体（<textarea>JSON</textarea>）
 *  5. 得到 FileInfoDTO{body=jsonText, file_type, sign}
 */
public class UploadHelper {

    public static class UploadResult {
        public String body;   // 响应体中的 JSON 文本
        public String sign;   // x-ntes-signature 响应头
    }

    private static final String DEFAULT_UPLOAD_URL = "https://fp.ps.netease.com/x19/file/new/";
    private static final int TIMEOUT = 120000; // 文件较大放宽到 120s

    /**
     * 上传本地文件。
     * @param auth      上传 token（Policy ...）
     * @param file      本地文件
     * @param fileName  上传文件名（含扩展名）
     * @param mimeType  MIME 类型，如 application/octet-stream、image/jpeg、video/mp4
     */
    public static UploadResult uploadFile(String auth, File file, String fileName, String mimeType)
            throws Exception {
        String uploadUrl = resolveUploadUrl(auth);
        String boundary = "----NemoModBoundary" + System.currentTimeMillis();
        HttpURLConnection conn = (HttpURLConnection) new URL(uploadUrl).openConnection();
        conn.setConnectTimeout(TIMEOUT);
        conn.setReadTimeout(TIMEOUT);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36");

        DataOutputStream out = new DataOutputStream(conn.getOutputStream());
        String CRLF = "\r\n";
        // 字段 Authorization
        out.writeBytes("--" + boundary + CRLF);
        out.writeBytes("Content-Disposition: form-data; name=\"Authorization\"" + CRLF + CRLF);
        out.writeBytes(auth + CRLF);
        // 字段 fpfile（文件）
        out.writeBytes("--" + boundary + CRLF);
        out.writeBytes("Content-Disposition: form-data; name=\"fpfile\"; filename=\""
                + sanitize(fileName) + "\"" + CRLF);
        out.writeBytes("Content-Type: " + mimeType + CRLF + CRLF);
        BufferedInputStream fis = new BufferedInputStream(new FileInputStream(file));
        byte[] buf = new byte[8192];
        int n;
        while ((n = fis.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        fis.close();
        out.writeBytes(CRLF);
        out.writeBytes("--" + boundary + "--" + CRLF);
        out.flush();
        out.close();

        int code = conn.getResponseCode();
        InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String body = readStream(is);
        if (is != null) is.close();

        UploadResult r = new UploadResult();
        r.sign = conn.getHeaderField("x-ntes-signature");
        r.body = body;
        // 响应体为 HTML，JSON 在 <textarea> 内
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("<textarea>(.*?)</textarea>", java.util.regex.Pattern.DOTALL)
                .matcher(body);
        if (m.find()) {
            r.body = m.group(1).trim();
        }
        conn.disconnect();
        return r;
    }

    /** 解析 token 中的实际上传地址；失败回退默认地址。 */
    public static String resolveUploadUrl(String auth) {
        try {
            int idx = auth.indexOf(':');
            if (idx >= 0) {
                String b64 = auth.substring(idx + 1).trim();
                String json = new String(Base64.decode(b64, Base64.DEFAULT), "UTF-8");
                JSONObject o = new JSONObject(json);
                if (o.has("url") && !o.optString("url").isEmpty()) {
                    return o.optString("url");
                }
            }
        } catch (Exception ignored) {
        }
        return DEFAULT_UPLOAD_URL;
    }

    private static String sanitize(String name) {
        if (name == null) return "file";
        return name.replace("\"", "").replace("\r", "").replace("\n", "");
    }

    private static String readStream(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        reader.close();
        return sb.toString();
    }
}