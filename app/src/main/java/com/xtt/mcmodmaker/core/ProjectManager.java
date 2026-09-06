package com.xtt.mcmodmaker.core;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import com.xtt.mcmodmaker.util.FileUtils;
import com.xtt.mcmodmaker.util.JsonUtils;
import com.xtt.mcmodmaker.util.UiUtils;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 项目核心业务：创建、信息读写、重命名、备份、导入导出。
 * 与 UI 解耦，通过 {@link ImportUi} 处理需要用户参与的交互。
 */
public class ProjectManager {

    /** 导入过程中需要 UI 参与的交互（须在主线程中执行并返回结果）。 */
    public interface ImportUi {
        /** 项目冲突时询问用户：返回 1=覆盖 2=添加副本 3=取消。 */
        int askConflict(String projectId);

        /** 缺少 studio.json 时询问名称与命名空间；返回 {name, namespace} 或 null（取消）。 */
        String[] askNameAndNamespace();
    }

    /** 导入/导出进度回调（任意线程）。 */
    public interface ProgressCallback {
        void onProgress(int percent);
    }

    private final Context context;
    private ImportUi importUi;

    public ProjectManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public void setImportUi(ImportUi importUi) {
        this.importUi = importUi;
    }

    // ==================== 创建 ====================

    /** 新建模组项目，返回项目目录。 */
    public File createProject(String name, String namespace, boolean withScript) {
        String projectUUID = UUID.randomUUID().toString().replace("-", "");
        File projectDir = new File(Constants.PROJECTS_DIR, projectUUID);
        projectDir.mkdirs();

        String behSuffix = FileUtils.randomString(8);
        String resSuffix = FileUtils.randomString(8);

        File behDir = new File(projectDir, "behavior_pack_" + behSuffix);
        behDir.mkdirs();
        new File(behDir, "entities").mkdirs();

        if (withScript) {
            String scriptSuffix = FileUtils.randomString(8);
            String folderName = "Script_NeteaseMod" + scriptSuffix;
            File scriptDir = new File(behDir, folderName);
            scriptDir.mkdirs();
            FileUtils.writeTextQuietly(new File(scriptDir, "__init__.py"), "");
            FileUtils.writeTextQuietly(new File(scriptDir, "modMain.py"), ModTemplates.scriptMain(folderName));
        }

        File resDir = new File(projectDir, "resource_pack_" + resSuffix);
        resDir.mkdirs();
        new File(resDir, "textures").mkdirs();

        FileUtils.writeTextQuietly(new File(behDir, "manifest.json"), ModTemplates.packManifest("data"));
        FileUtils.writeTextQuietly(new File(resDir, "manifest.json"), ModTemplates.packManifest("resources"));
        FileUtils.writeTextQuietly(new File(projectDir, "studio.json"), ModTemplates.studioJson(projectUUID, name, namespace));
        return projectDir;
    }

    // ==================== 信息读写 ====================

    public String getProjectName(File projectDir) {
        return JsonUtils.getProjectName(projectDir);
    }

    public String getProjectId(File projectDir) {
        return JsonUtils.getProjectId(projectDir);
    }

    public String getProjectNamespace(File projectDir) {
        return JsonUtils.getProjectNamespace(projectDir);
    }

    /** 更新命名空间，返回是否成功。 */
    public boolean updateNamespace(File projectDir, String newNamespace) {
        boolean ok = JsonUtils.updateStudioField(projectDir, "namespace", newNamespace);
        if (ok) UiUtils.toast(context, "命名空间已更新");
        return ok;
    }

    /** 重命名项目（仅修改 studio.json 的 name 字段）。 */
    public boolean renameProject(File projectDir, String newName) {
        boolean ok = JsonUtils.updateStudioField(projectDir, "name", newName);
        if (ok) UiUtils.toast(context, "重命名成功");
        else UiUtils.toast(context, "重命名失败");
        return ok;
    }

    // ==================== 备份 ====================

    /** 创建备份，返回是否成功。 */
    public boolean createBackup(File projectDir) {
        File parent = projectDir.getParentFile();
        if (parent != null && parent.getName().endsWith("_back")) {
            UiUtils.toast(context, "备份中的项目不能再备份");
            return false;
        }
        String originalId = projectDir.getName();
        if (originalId.contains("_back")) {
            originalId = originalId.substring(0, originalId.lastIndexOf("_back"));
        }
        try {
            File backupContainer = new File(Constants.PROJECTS_DIR, originalId + "_back");
            backupContainer.mkdirs();
            File backupSubDir = new File(backupContainer, String.valueOf(System.currentTimeMillis() / 1000));
            FileUtils.copyDirectory(projectDir, backupSubDir);
            UiUtils.toast(context, "备份成功");
            return true;
        } catch (IOException e) {
            UiUtils.toast(context, "备份失败: " + e.getMessage());
            return false;
        }
    }

    /** 用备份覆盖原项目，返回是否成功。 */
    public boolean overwriteOriginalFromBackup(File backupSubDir) {
        File parentDir = backupSubDir.getParentFile();
        if (parentDir == null || !parentDir.getName().endsWith("_back")) {
            UiUtils.toast(context, "无法识别原项目ID");
            return false;
        }
        String originalId = parentDir.getName().substring(0, parentDir.getName().length() - 5);
        File originalDir = new File(Constants.PROJECTS_DIR, originalId);
        try {
            if (originalDir.exists()) FileUtils.deleteRecursive(originalDir);
            FileUtils.copyDirectory(backupSubDir, originalDir);
            UiUtils.toast(context, "覆盖成功");
            return true;
        } catch (IOException e) {
            UiUtils.toast(context, "覆盖失败: " + e.getMessage());
            return false;
        }
    }

    // ==================== 导出 ====================

    /** 将项目目录压缩导出到目标 Uri。 */
    public void exportProject(File projectDir, Uri destUri) throws IOException {
        OutputStream os = context.getContentResolver().openOutputStream(destUri);
        if (os == null) throw new IOException("无法打开输出流");
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(os))) {
            zipDirectory(projectDir, projectDir, zos);
        }
    }

    private void zipDirectory(File rootDir, File sourceDir, ZipOutputStream zos) throws IOException {
        File[] files = sourceDir.listFiles();
        if (files == null) return;
        byte[] buffer = new byte[8192];
        for (File file : files) {
            String entryName = rootDir.toURI().relativize(file.toURI()).getPath();
            if (file.isDirectory()) {
                if (!entryName.endsWith("/")) entryName += "/";
                ZipEntry entry = new ZipEntry(entryName);
                zos.putNextEntry(entry);
                zos.closeEntry();
                zipDirectory(rootDir, file, zos);
            } else {
                try (FileInputStream fis = new FileInputStream(file)) {
                    ZipEntry entry = new ZipEntry(entryName);
                    zos.putNextEntry(entry);
                    int len;
                    while ((len = fis.read(buffer)) != -1) {
                        zos.write(buffer, 0, len);
                    }
                    zos.closeEntry();
                }
            }
        }
    }

    // ==================== 导入 ====================

    /** 从文件夹导入（在后台线程调用）。 */
    public void importFromFolder(Uri treeUri) throws IOException {
        DocumentFile rootDoc = DocumentFile.fromTreeUri(context, treeUri);
        if (rootDoc == null) throw new IOException("无法读取文件夹");

        boolean hasBehavior = false;
        boolean hasResource = false;
        DocumentFile[] children = rootDoc.listFiles();
        if (children != null) {
            for (DocumentFile child : children) {
                String name = child.getName();
                if (name == null) continue;
                String lower = name.toLowerCase();
                if (lower.startsWith("behavior_pack_") || lower.startsWith("behaviour_pack_")) {
                    hasBehavior = true;
                } else if (lower.startsWith("resource_pack_")) {
                    hasResource = true;
                }
            }
        }
        if (!hasBehavior || !hasResource) {
            throw new IOException("必须包含行为包和资源包文件夹");
        }

        String projectUUID = readStudioId(rootDoc);
        if (projectUUID == null || projectUUID.isEmpty()) {
            projectUUID = UUID.randomUUID().toString().replace("-", "");
        }

        File projectDir = resolveTargetDir(projectUUID, false);
        if (projectDir == null) return; // 用户取消

        copyDocumentTree(rootDoc, projectDir);
        normalizeImportedProject(projectDir);
        handleImportStudioJson(projectDir, projectDir.getName());
    }

    /** 从 ZIP 导入（在后台线程调用）。 */
    public void importFromZip(Uri zipUri, ProgressCallback progress) throws IOException {
        // 1. 统计文件数
        int totalFiles = 0;
        InputStream countIs = context.getContentResolver().openInputStream(zipUri);
        if (countIs == null) throw new IOException("无法读取文件");
        try (ZipInputStream countZis = new ZipInputStream(new BufferedInputStream(countIs))) {
            ZipEntry countEntry;
            while ((countEntry = countZis.getNextEntry()) != null) {
                if (!countEntry.isDirectory()) totalFiles++;
                countZis.closeEntry();
            }
        }

        // 2. 解压到临时目录
        final File tempDir = new File(Constants.PROJECTS_DIR, "temp_" + System.currentTimeMillis());
        tempDir.mkdirs();
        int processed = 0;
        InputStream is = context.getContentResolver().openInputStream(zipUri);
        if (is == null) {
            FileUtils.deleteRecursive(tempDir);
            throw new IOException("无法读取文件");
        }
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                File outFile = new File(tempDir, name);
                if (entry.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    File parent = outFile.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    processed++;
                    if (progress != null && totalFiles > 0) {
                        progress.onProgress(processed * 100 / totalFiles);
                    }
                }
                zis.closeEntry();
            }
        }

        // 3. 读取 id
        String projectUUID = readStudioIdFromFile(new File(tempDir, "studio.json"));
        if (projectUUID == null || projectUUID.isEmpty()) {
            projectUUID = UUID.randomUUID().toString().replace("-", "");
        }

        // 4. 冲突处理
        File targetDir = resolveTargetDir(projectUUID, true);
        if (targetDir == null) {
            FileUtils.deleteRecursive(tempDir);
            return; // 用户取消
        }

        // 5. 整理
        organizeZipImportedFiles(tempDir, targetDir);
        FileUtils.deleteRecursive(tempDir);
        handleImportStudioJson(targetDir, targetDir.getName());
    }

    /**
     * 解析目标项目目录（处理冲突）。
     *
     * @param deleteTempOnCancel ZIP 导入取消时是否需要清理临时目录（由调用方处理，此处仅返回 null）
     * @return 目标目录；用户取消返回 null
     */
    private File resolveTargetDir(String projectUUID, boolean forZip) {
        File projectDir = new File(Constants.PROJECTS_DIR, projectUUID);
        if (!projectDir.exists()) {
            projectDir.mkdirs();
            return projectDir;
        }
        if (importUi == null) {
            // 无 UI 时默认添加副本
            return createCopyTarget(projectUUID);
        }
        int choice = importUi.askConflict(projectUUID);
        if (choice == 3) {
            UiUtils.toast(context, "已取消导入");
            return null;
        } else if (choice == 2) {
            return createCopyTarget(projectUUID);
        } else {
            FileUtils.deleteRecursive(projectDir);
            projectDir.mkdirs();
            return projectDir;
        }
    }

    private File createCopyTarget(String projectUUID) {
        File backupContainer = new File(Constants.PROJECTS_DIR, projectUUID + "_back");
        backupContainer.mkdirs();
        File target = new File(backupContainer, String.valueOf(System.currentTimeMillis() / 1000));
        target.mkdirs();
        return target;
    }

    /** 复制 DocumentFile 树到本地目录。 */
    private void copyDocumentTree(DocumentFile source, File dest) throws IOException {
        if (source.isDirectory()) {
            if (!dest.exists()) dest.mkdirs();
            DocumentFile[] children = source.listFiles();
            if (children != null) {
                for (DocumentFile child : children) {
                    copyDocumentTree(child, new File(dest, child.getName()));
                }
            }
        } else {
            InputStream is = context.getContentResolver().openInputStream(source.getUri());
            if (is != null) {
                try (InputStream in = is; OutputStream out = new FileOutputStream(dest)) {
                    FileUtils.copyStream(in, out);
                }
            }
        }
    }

    /** 从 DocumentFile 的 studio.json 读取项目 id。 */
    private String readStudioId(DocumentFile rootDoc) {
        DocumentFile studioFile = rootDoc.findFile("studio.json");
        if (studioFile == null || !studioFile.exists() || studioFile.length() <= 0) return null;
        try {
            InputStream is = context.getContentResolver().openInputStream(studioFile.getUri());
            if (is == null) return null;
            try (InputStream in = is) {
                byte[] data = new byte[(int) studioFile.length()];
                int read = in.read(data);
                if (read <= 0) return null;
                JSONObject obj = new JSONObject(new String(data, 0, read, "UTF-8"));
                String id = obj.optString("id", "");
                if (id.isEmpty()) id = obj.optString("Id", "");
                return id.isEmpty() ? null : id;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** 从本地 studio.json 文件读取项目 id。 */
    private String readStudioIdFromFile(File studioFile) {
        if (studioFile == null || !studioFile.exists()) return null;
        JSONObject obj = JsonUtils.readJson(studioFile);
        if (obj == null) return null;
        String id = obj.optString("id", "");
        if (id.isEmpty()) id = obj.optString("Id", "");
        return id.isEmpty() ? null : id;
    }

    /** 整理 ZIP 解压结果：移动行为包/资源包等至项目目录。 */
    private void organizeZipImportedFiles(File tempDir, File targetProjectDir) throws IOException {
        File[] entries = tempDir.listFiles();
        if (entries == null) throw new IOException("ZIP 内容为空");

        File behPack = null;
        File resPack = null;
        File studioJson = null;
        File workMcscfg = null;
        for (File entry : entries) {
            String name = entry.getName().toLowerCase();
            if (name.startsWith("behavior_pack_") || name.startsWith("behaviour_pack_")) {
                behPack = entry;
            } else if (name.startsWith("resource_pack_")) {
                resPack = entry;
            } else if (name.equals("studio.json")) {
                studioJson = entry;
            } else if (name.equals("work.mcscfg")) {
                workMcscfg = entry;
            }
        }
        if (behPack == null || resPack == null) {
            throw new IOException("ZIP包必须包含behavior_pack和resource_pack文件夹");
        }

        targetProjectDir.mkdirs();
        FileUtils.moveFile(behPack, new File(targetProjectDir, behPack.getName()));
        FileUtils.moveFile(resPack, new File(targetProjectDir, resPack.getName()));
        if (studioJson != null) FileUtils.moveFile(studioJson, new File(targetProjectDir, "studio.json"));
        if (workMcscfg != null) FileUtils.moveFile(workMcscfg, new File(targetProjectDir, "work.mcscfg"));
        normalizeImportedProject(targetProjectDir);
    }

    /** 规范化导入的项目：补齐 entities/textures 目录与 manifest.json。 */
    private void normalizeImportedProject(File projectDir) {
        File[] entries = projectDir.listFiles();
        if (entries == null) return;
        for (File entry : entries) {
            String name = entry.getName();
            if (entry.isDirectory() && name.startsWith("behavior_pack_")) {
                File entitiesDir = new File(entry, "entities");
                if (!entitiesDir.exists()) entitiesDir.mkdirs();
                File manifestFile = new File(entry, "manifest.json");
                if (!manifestFile.exists()) {
                    FileUtils.writeTextQuietly(manifestFile, ModTemplates.packManifest("data"));
                }
            } else if (entry.isDirectory() && name.startsWith("resource_pack_")) {
                File texturesDir = new File(entry, "textures");
                if (!texturesDir.exists()) texturesDir.mkdirs();
                File manifestFile = new File(entry, "manifest.json");
                if (!manifestFile.exists()) {
                    FileUtils.writeTextQuietly(manifestFile, ModTemplates.packManifest("resources"));
                }
            }
        }
    }

    /** 处理导入后的 studio.json：存在则统一字段，缺失则询问用户。 */
    private void handleImportStudioJson(File targetDir, String projectId) {
        File existingStudio = new File(targetDir, "studio.json");
        if (existingStudio.exists()) {
            JSONObject obj = JsonUtils.readJson(existingStudio);
            String importName = "未知模组";
            String importNamespace = "test";
            if (obj != null) {
                importName = obj.optString("EditName", obj.optString("name", "未知模组"));
                importNamespace = obj.optString("NameSpace", obj.optString("namespace", "test"));
                if (importNamespace.isEmpty()) importNamespace = "test";
            }
            FileUtils.writeTextQuietly(existingStudio,
                    ModTemplates.studioJson(projectId, importName, importNamespace));
        } else if (importUi != null) {
            String[] result = importUi.askNameAndNamespace();
            if (result != null) {
                FileUtils.writeTextQuietly(new File(targetDir, "studio.json"),
                        ModTemplates.studioJson(projectId, result[0], result[1]));
            }
        }
    }
}