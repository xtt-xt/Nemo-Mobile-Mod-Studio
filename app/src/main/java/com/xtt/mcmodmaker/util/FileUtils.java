package com.xtt.mcmodmaker.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 文件读写、复制、移动、删除等通用工具。
 * 统一使用 try-with-resources 确保资源释放。
 */
public final class FileUtils {

    private static final int BUFFER_SIZE = 8192;

    private FileUtils() {
    }

    /** 读取文件全部文本，失败返回空串（不抛出）。 */
    public static String readText(File file) {
        if (file == null || !file.exists() || file.length() <= 0) return "";
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int read = fis.read(data);
            return read <= 0 ? "" : new String(data, 0, read, "UTF-8");
        } catch (IOException e) {
            return "";
        }
    }

    /** 写入文本到文件。 */
    public static void writeText(File file, String content) throws IOException {
        try (FileWriter fw = new FileWriter(file)) {
            fw.write(content == null ? "" : content);
        }
    }

    /** 写入文本到文件，失败静默（用于非关键路径）。 */
    public static void writeTextQuietly(File file, String content) {
        try {
            writeText(file, content);
        } catch (IOException ignored) {
        }
    }

    /** 递归复制目录或文件。 */
    public static void copyDirectory(File source, File dest) throws IOException {
        if (source == null) return;
        if (source.isDirectory()) {
            if (!dest.exists() && !dest.mkdirs()) {
                throw new IOException("无法创建目录: " + dest);
            }
            File[] children = source.listFiles();
            if (children != null) {
                for (File child : children) {
                    copyDirectory(child, new File(dest, child.getName()));
                }
            }
        } else {
            try (InputStream in = new FileInputStream(source);
                 OutputStream out = new FileOutputStream(dest)) {
                copyStream(in, out);
            }
        }
    }

    /** 递归移动目录或文件（移动后删除源）。 */
    public static void moveFile(File source, File dest) throws IOException {
        if (source == null) return;
        if (source.isDirectory()) {
            if (!dest.exists() && !dest.mkdirs()) {
                throw new IOException("无法创建目录: " + dest);
            }
            File[] children = source.listFiles();
            if (children != null) {
                for (File child : children) {
                    moveFile(child, new File(dest, child.getName()));
                }
            }
        } else {
            try (InputStream in = new FileInputStream(source);
                 OutputStream out = new FileOutputStream(dest)) {
                copyStream(in, out);
            }
        }
        if (!source.delete()) {
            throw new IOException("无法删除源路径: " + source);
        }
    }

    /** 递归删除文件或目录，返回是否成功。 */
    public static boolean deleteRecursive(File file) {
        if (file == null) return true;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return file.delete();
    }

    /** 复制输入流到输出流。 */
    public static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int len;
        while ((len = in.read(buffer)) != -1) {
            out.write(buffer, 0, len);
        }
        out.flush();
    }

    /** 生成指定长度的随机小写字母数字串。 */
    public static String randomString(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return sb.toString();
    }

    /** 递归统计文件数量（目录计 0，仅统计文件）。 */
    public static int countFiles(File root) {
        if (root == null) return 0;
        if (!root.isDirectory()) return 1;
        int count = 0;
        File[] children = root.listFiles();
        if (children != null) {
            for (File child : children) {
                count += countFiles(child);
            }
        }
        return count;
    }
}
