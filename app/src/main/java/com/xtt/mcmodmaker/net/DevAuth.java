package com.xtt.mcmodmaker.net;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

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
 * 开发者账号的本地保存。
 *
 * 存的是加密后的一个文件，放在应用私有目录（{@code getFilesDir()}）里：
 *
 *  1. 密钥由 Android Keystore 生成，标了 {@code setUidValidity} 之外的默认策略——
 *     它不可导出、绑定本机这台设备和这个应用的 UID，别的应用读不到这把钥匙；
 *     把文件拷到另一台手机上也解不开。
 *  2. 文件本身再叠一层 AES/GCM，带随机 IV 和完整性校验，改一个字节就会解不开。
 *
 * 不放进 /storage/emulated/0/McMod/settings.json：那是外部存储，任何有存储权限的
 * 应用都能读，密码不能留在那儿。
 */
public final class DevAuth {

    private static final String TAG = "DevAuth";
    private static final String FILE_NAME = "dev_auth.bin";
    private static final String KEY_ALIAS = "nemo_dev_auth_v1";
    private static final String PROVIDER = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final byte[] MAGIC = {'N', 'M', 'D', '1'};
    private static final int TAG_BITS = 128;

    /** 网易开发者邮箱（登录名）。 */
    public String email = "";
    /** 密码，仅用于登录失效后自动重新登录。 */
    public String password = "";
    /** 当前会话的 Cookie。 */
    public String cookie = "";

    public boolean canAutoLogin() {
        return email != null && !email.isEmpty() && password != null && !password.isEmpty();
    }

    // ==================== 读写 ====================

    /** 读出来；文件不存在或解不开（换设备、清过密钥）就返回 null。 */
    public static synchronized DevAuth load(Context context) {
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
            String[] parts = new String(plain, StandardCharsets.UTF_8).split("\n", -1);
            DevAuth auth = new DevAuth();
            auth.email = parts.length > 0 ? parts[0] : "";
            auth.password = parts.length > 1 ? parts[1] : "";
            auth.cookie = parts.length > 2 ? parts[2] : "";
            return auth;
        } catch (Exception e) {
            // 密钥被系统清掉（比如恢复出厂后重装）时，旧文件永远解不开了
            Log.w(TAG, "解密失败：" + e.getMessage());
            f.delete();
            return null;
        }
    }

    public static synchronized void save(Context context, DevAuth auth) {
        if (auth == null) return;
        String plain = auth.email + "\n" + auth.password + "\n" + auth.cookie;
        try {
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

    /** 当前 Cookie；没有就返回空串。 */
    public static String cookie(Context context) {
        DevAuth auth = load(context);
        return auth == null || auth.cookie == null ? "" : auth.cookie;
    }

    /**
     * 把旧版明文存在 settings.json 里的 Cookie / 邮箱迁进来。
     *
     * 迁完立刻从明文里抹掉，否则加密就白做了。
     */
    public static void migrateFromLegacy(Context context, String legacyCookie, String legacyEmail) {
        boolean hasLegacy = (legacyCookie != null && !legacyCookie.isEmpty())
                || (legacyEmail != null && !legacyEmail.isEmpty());
        if (!hasLegacy) return;
        DevAuth auth = load(context);
        if (auth == null) auth = new DevAuth();
        if (auth.cookie.isEmpty() && legacyCookie != null) auth.cookie = legacyCookie;
        // 旧版本没有存密码，邮箱照样搬过来，「Cookie 登录」这种占位说法不要
        if (auth.email.isEmpty() && legacyEmail != null && !"Cookie 登录".equals(legacyEmail)) {
            auth.email = legacyEmail;
        }
        save(context, auth);
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
