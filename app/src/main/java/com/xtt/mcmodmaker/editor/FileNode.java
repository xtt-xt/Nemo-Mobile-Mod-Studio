package com.xtt.mcmodmaker.editor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** 文件树节点模型，扫描时跳过 .mcs 与 *_back 目录。 */
public class FileNode {

    public final String name;
    public final File file;
    public final boolean isDirectory;
    public final List<FileNode> children = new ArrayList<>();

    public FileNode(File file) {
        this.file = file;
        this.name = file.getName();
        this.isDirectory = file.isDirectory();
    }

    /** 递归扫描目录构建文件树。 */
    public static FileNode scan(File dir) {
        FileNode node = new FileNode(dir);
        if (!dir.isDirectory()) return node;
        File[] files = dir.listFiles();
        if (files == null) return node;
        for (File f : files) {
            if (f.isDirectory() && (f.getName().equals(".mcs") || f.getName().endsWith("_back"))) {
                continue;
            }
            node.children.add(scan(f));
        }
        return node;
    }
}