package com.xtt.mcmodmaker.net;

import android.content.Context;
import android.util.Log;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * GitHub 个人访问令牌的本地保存。
 *
 * 和开发者账号（{@link DevAuth}）用同一套思路：文件放在应用私有目录，
 * 密钥由 Android Keystore 生成（不可导出、绑定本机 + 本应用 UID），内容再用 AES/GCM 加密。
 *
 * 不能进 /storage/emulated/0/McMod/setting.json：那是外部存储，
 * 有存储权限的应用都能读，令牌就等于账号权限，不能留在那儿。
 */
public final class GitAuth {

    private static final String TAG = "GitAuth";
    private static final String FILE_NAME = "git_auth.bin";
    private static final String KEY_ALIAS = "nemo_git_auth_v1";
    private static final String PROVIDER = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final byte[] MAGIC = {'N', 'M', 'G', '1'};
    private static final int TAG_BITS = 128;

    /** 令牌本身。 */
    public String token = "";

    public boolean isEmpty() {
        return token == null || token.isEmpty();
    }

    // ==================== 读写 ====================

    /** 读出来；没存过或解不开（换设备、清过密钥）时返回 null。 */
    public static synchronized GitAuth load(Context context) {
        File f = new File(context.getFilesDir(), FILE_NAME);
        if (!f.isFile() || f.length() < MAGIC.length + 13) return null;
        byte[] raw;
        try {
            raw = readAll(f);
        } catch (Exception e) {
            Log.w(TAG, "读取失败：" + e.getMessage());
            return null;
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (raw[i] != MAGIC[i]) return null;
        }
        int ivLen = raw[MAGIC.length] & 0xFF;
        int offset = MAGIC.length + 1;
        if (ivLen <= 0 || ivLen > 32 || raw.length <= offset + ivLen) return null;

        byte[] iv = new byte[ivLen];
        System.arraycopy(raw, offset, iv, 0, ivLen);
        byte[] cipherText = new byte[raw.length - offset - ivLen];
        System.arraycopy(raw, offset + ivLen, cipherText, 0, cipherText.length);

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(cipherText);
            GitAuth auth = new GitAuth();
            auth.token = new String(plain, StandardCharsets.UTF_8).trim();
            return auth;
        } catch (Exception e) {
            // 密钥被系统清掉（恢复出厂、换机）时，旧文件永远解不开了
            Log.w(TAG, "解密失败：" + e.getMessage());
            f.delete();
            return null;
        }
    }

    public static synchronized void save(Context context, String token) {
        try {
            String plain = token == null ? "" : token.trim();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey());
            byte[] iv = cipher.getIV();
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bos.write(MAGIC);
            bos.write(iv.length);
            bos.write(iv);
            bos.write(cipherText);

            File f = new File(context.getFilesDir(), FILE_NAME);
            FileOutputStream out = new FileOutputStream(f);
            try {
                out.write(bos.toByteArray());
                out.flush();
            } finally {
                out.close();
            }
            // 只有本应用可读写（getFilesDir 本身已经私有，这一步是额外的保险）
            f.setReadable(false, false);
            f.setReadable(true, true);
            f.setWritable(false, false);
            f.setWritable(true, true);
        } catch (Exception e) {
            Log.w(TAG, "保存失败：" + e.getMessage());
        }
    }

    public static synchronized void clear(Context context) {
        File f = new File(context.getFilesDir(), FILE_NAME);
        if (f.exists()) f.delete();
    }

    // ==================== 便捷读取 ====================

    /** 当前令牌；没有就返回空串。 */
    public static String token(Context context) {
        GitAuth auth = load(context);
        return auth == null || auth.token == null ? "" : auth.token;
    }

    public static boolean hasToken(Context context) {
        return !token(context).isEmpty();
    }

    // ==================== 密钥 ====================

    private static SecretKey secretKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(PROVIDER);
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) {
            return (SecretKey) ks.getKey(KEY_ALIAS, null);
        }
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER);
        kg.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build());
        return kg.generateKey();
    }

    private static byte[] readAll(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        } finally {
            in.close();
        }
    }
}
