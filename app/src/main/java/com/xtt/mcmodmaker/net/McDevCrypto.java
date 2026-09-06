package com.xtt.mcmodmaker.net;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.Cipher;

/**
 * MCDev 登录加密工具：SM4（ECB+PKCS7）、RSA（PKCS1）、murmurHash3。
 * 参考 BitterLemonn/MCDevManager 的 SM4.kt / EncryptUtils.kt / KeyUtils.kt。
 */
public final class McDevCrypto {

    private McDevCrypto() {}

    // ==================== SM4 (GB/T 32907-2016, ECB + PKCS7) ====================

    private static final int[] SBOX = {
        0xD6,0x90,0xE9,0xFE,0xCC,0xE1,0x3D,0xB7,0x16,0xB6,0x14,0xC2,0x28,0xFB,0x2C,0x05,
        0x2B,0x67,0x9A,0x76,0x2A,0xBE,0x04,0xC3,0xAA,0x44,0x13,0x26,0x49,0x86,0x06,0x99,
        0x9C,0x42,0x50,0xF4,0x91,0xEF,0x98,0x7A,0x33,0x54,0x0B,0x43,0xED,0xCF,0xAC,0x62,
        0xE4,0xB3,0x1C,0xA9,0xC9,0x08,0xE8,0x95,0x80,0xDF,0x94,0xFA,0x75,0x8F,0x3F,0xA6,
        0x47,0x07,0xA7,0xFC,0xF3,0x73,0x17,0xBA,0x83,0x59,0x3C,0x19,0xE6,0x85,0x4F,0xA8,
        0x68,0x6B,0x81,0xB2,0x71,0x64,0xDA,0x8B,0xF8,0xEB,0x0F,0x4B,0x70,0x56,0x9D,0x35,
        0x1E,0x24,0x0E,0x5E,0x63,0x58,0xD1,0xA2,0x25,0x22,0x7C,0x3B,0x01,0x21,0x78,0x87,
        0xD4,0x00,0x46,0x57,0x9F,0xD3,0x27,0x52,0x4C,0x36,0x02,0xE7,0xA0,0xC4,0xC8,0x9E,
        0xEA,0xBF,0x8A,0xD2,0x40,0xC7,0x38,0xB5,0xA3,0xF7,0xF2,0xCE,0xF9,0x61,0x15,0xA1,
        0xE0,0xAE,0x5D,0xA4,0x9B,0x34,0x1A,0x55,0xAD,0x93,0x32,0x30,0xF5,0x8C,0xB1,0xE3,
        0x1D,0xF6,0xE2,0x2E,0x82,0x66,0xCA,0x60,0xC0,0x29,0x23,0xAB,0x0D,0x53,0x4E,0x6F,
        0xD5,0xDB,0x37,0x45,0xDE,0xFD,0x8E,0x2F,0x03,0xFF,0x6A,0x72,0x6D,0x6C,0x5B,0x51,
        0x8D,0x1B,0xAF,0x92,0xBB,0xDD,0xBC,0x7F,0x11,0xD9,0x5C,0x41,0x1F,0x10,0x5A,0xD8,
        0x0A,0xC1,0x31,0x88,0xA5,0xCD,0x7B,0xBD,0x2D,0x74,0xD0,0x12,0xB8,0xE5,0xB4,0xB0,
        0x89,0x69,0x97,0x4A,0x0C,0x96,0x77,0x7E,0x65,0xB9,0xF1,0x09,0xC5,0x6E,0xC6,0x84,
        0x18,0xF0,0x7D,0xEC,0x3A,0xDC,0x4D,0x20,0x79,0xEE,0x5F,0x3E,0xD7,0xCB,0x39,0x48
    };
    private static final int[] FK = {
        (int)0xA3B1BAC6L, 0x56AA3350, 0x677D9197, (int)0xB27022DCL
    };
    private static final int[] CK = {
        0x00070E15,0x1C232A31,0x383F464D,0x545B6269,
        0x70777E85,0x8C939AA1,(int)0xA8AFB6BDL,(int)0xC4CBD2D9L,
        (int)0xE0E7EEF5L,(int)0xFC030A11L,0x181F262D,0x343B4249,
        0x50575E65,0x6C737A81,0x888F969D,(int)0xA4ABB2B9L,
        (int)0xC0C7CED5L,(int)0xDCE3EAF1L,(int)0xF8FF060DL,0x141B2229,
        0x30373E45,0x4C535A61,0x686F767D,0x848B9299,
        (int)0xA0A7AEB5L,(int)0xBCC3CAD1L,(int)0xD8DFE6EDL,(int)0xF4FB0209L,
        0x10171E25,0x2C333A41,0x484F565D,0x646B7279
    };

    public static String sm4Encrypt(String plaintext, String keyHex) {
        byte[] keyBytes = hexToBytes(keyHex);
        byte[] inputBytes = plaintext.getBytes(StandardCharsets.UTF_8);
        byte[] padded = pkcs7Pad(inputBytes);
        int[] roundKeys = expandKey(keyBytes);
        byte[] output = new byte[padded.length];
        for (int i = 0; i < padded.length; i += 16) {
            byte[] block = new byte[16];
            System.arraycopy(padded, i, block, 0, Math.min(16, padded.length - i));
            byte[] encrypted = encryptBlock(block, roundKeys);
            System.arraycopy(encrypted, 0, output, i, 16);
        }
        return bytesToHex(output);
    }

    private static byte[] pkcs7Pad(byte[] data) {
        int padLen = 16 - (data.length % 16);
        byte[] out = new byte[data.length + padLen];
        System.arraycopy(data, 0, out, 0, data.length);
        for (int i = data.length; i < out.length; i++) out[i] = (byte) padLen;
        return out;
    }

    private static int[] expandKey(byte[] key) {
        int[] mk = new int[4];
        for (int i = 0; i < 4; i++) mk[i] = bytesToInt(key, i * 4);
        int[] k = new int[36];
        for (int i = 0; i < 4; i++) k[i] = mk[i] ^ FK[i];
        int[] rk = new int[32];
        for (int i = 0; i < 32; i++) {
            k[i + 4] = k[i] ^ tPrime(k[i + 1] ^ k[i + 2] ^ k[i + 3] ^ CK[i]);
            rk[i] = k[i + 4];
        }
        return rk;
    }

    private static byte[] encryptBlock(byte[] block, int[] rk) {
        int[] x = new int[36];
        for (int i = 0; i < 4; i++) x[i] = bytesToInt(block, i * 4);
        for (int i = 0; i < 32; i++) {
            x[i + 4] = x[i] ^ t(x[i + 1] ^ x[i + 2] ^ x[i + 3] ^ rk[i]);
        }
        byte[] out = new byte[16];
        for (int i = 0; i < 16; i++) {
            int wordIdx = 35 - i / 4;
            int byteIdx = i % 4;
            out[i] = (byte) (x[wordIdx] >>> (24 - byteIdx * 8));
        }
        return out;
    }

    private static int t(int input) {
        int substituted = tau(input);
        return substituted ^ rotateLeft(substituted, 2)
                ^ rotateLeft(substituted, 10)
                ^ rotateLeft(substituted, 18)
                ^ rotateLeft(substituted, 24);
    }

    private static int tPrime(int input) {
        int substituted = tau(input);
        return substituted ^ rotateLeft(substituted, 13) ^ rotateLeft(substituted, 23);
    }

    private static int tau(int input) {
        return (SBOX[(input >>> 24) & 0xFF] << 24)
                | (SBOX[(input >>> 16) & 0xFF] << 16)
                | (SBOX[(input >>> 8) & 0xFF] << 8)
                | SBOX[input & 0xFF];
    }

    private static int rotateLeft(int value, int bits) {
        return (value << bits) | (value >>> (32 - bits));
    }

    private static int bytesToInt(byte[] b, int offset) {
        return ((b[offset] & 0xFF) << 24)
                | ((b[offset + 1] & 0xFF) << 16)
                | ((b[offset + 2] & 0xFF) << 8)
                | (b[offset + 3] & 0xFF);
    }

    private static byte[] hexToBytes(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static final char[] HEX_CHARS = {
        '0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'
    };

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            int v = b & 0xFF;
            sb.append(HEX_CHARS[v >> 4]).append(HEX_CHARS[v & 0x0F]);
        }
        return sb.toString();
    }

    // ==================== RSA (PKCS1) ====================

    public static String rsaEncrypt(String input, String publicKeyBase64) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(publicKeyBase64);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PublicKey publicKey = kf.generatePublic(spec);
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encrypted = cipher.doFinal(input.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    // ==================== murmurHash3 (32-bit) ====================

    /** Kotlin 版 murmurHash3（与 MCDevManager 一致）。 */
    public static long murmurHash3(String key, long seed) {
        final long c1 = 0xcc9e2d51L;
        final long c2 = 0x1b873593L;
        final long r1 = 15, r2 = 13, m = 5, n = 0xe6546b64L;
        long hash = seed & 0xFFFFFFFFL;
        int i = 0;
        byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
        while (i + 4 <= bytes.length) {
            long k1 = ((long)(bytes[i + 3] & 0xFF) << 24)
                    | ((long)(bytes[i + 2] & 0xFF) << 16)
                    | ((long)(bytes[i + 1] & 0xFF) << 8)
                    | (bytes[i] & 0xFFL);
            i += 4;
            k1 = (k1 * c1) & 0xFFFFFFFFL;
            k1 = ((k1 << r1) | (k1 >>> (32 - r1))) & 0xFFFFFFFFL;
            k1 = (k1 * c2) & 0xFFFFFFFFL;
            hash ^= k1;
            hash = ((hash << r2) | (hash >>> (32 - r2))) & 0xFFFFFFFFL;
            hash = ((hash * m) + n) & 0xFFFFFFFFL;
        }
        if (i < bytes.length) {
            long k2 = 0;
            for (int j = 0; j < bytes.length - i; j++) {
                k2 |= ((long)(bytes[i + j] & 0xFF)) << (j * 8);
            }
            k2 = (k2 * c1) & 0xFFFFFFFFL;
            k2 = ((k2 << r1) | (k2 >>> (32 - r1))) & 0xFFFFFFFFL;
            k2 = (k2 * c2) & 0xFFFFFFFFL;
            hash ^= k2;
        }
        hash ^= bytes.length;
        hash ^= hash >>> 16;
        hash = (hash * 0x85ebca6bL) & 0xFFFFFFFFL;
        hash ^= hash >>> 13;
        hash = (hash * 0xc2b2ae35L) & 0xFFFFFFFFL;
        hash ^= hash >>> 16;
        return hash & 0xFFFFFFFFL;
    }

    // ==================== VDF ====================

    /** VDF 计算结果：迭代次数、耗时、args JSON。 */
    public static class VdfResult {
        public int runTimes;
        public long spendTime;
        public String args;
    }

    /**
     * VDF 计算：x = x^2 mod modulus 迭代 t 次（或 minTime 毫秒）。
     * 返回实际迭代次数/耗时 + args JSON（与 MCDevManager computeVDF 一致）。
     */
    public static VdfResult computeVdf(String puzzle, String modHex, String xHex, int t,
                                       int minTime, int maxTime) {
        BigInteger modulus = new BigInteger(modHex, 16);
        BigInteger x = new BigInteger(xHex, 16);
        long start = System.currentTimeMillis();
        int count = 0;
        while (count < t || (System.currentTimeMillis() - start) < minTime) {
            x = x.multiply(x).mod(modulus);
            count++;
            if (System.currentTimeMillis() - start > maxTime) break;
        }
        long time = System.currentTimeMillis() - start;
        // 排序参数: runTimes, spendTime, t, x（与 Kotlin 版 joinToString 顺序一致）
        String encodedParams = "runTimes=" + count + "&spendTime=" + time + "&t=" + count
                + "&x=" + x.toString(16);
        long sign = murmurHash3(encodedParams, count & 0xFFFFFFFFL);
        VdfResult r = new VdfResult();
        r.runTimes = count;
        r.spendTime = time;
        r.args = "{\"x\":\"" + x.toString(16) + "\",\"t\":" + count + ",\"sign\":\"" + sign + "\"}";
        return r;
    }

    /** 随机 32 位 rtid。 */
    public static String randomTid() {
        final String set = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        StringBuilder sb = new StringBuilder();
        java.util.Random r = new java.util.Random();
        for (int i = 0; i < 32; i++) sb.append(set.charAt(r.nextInt(set.length())));
        return sb.toString();
    }
}