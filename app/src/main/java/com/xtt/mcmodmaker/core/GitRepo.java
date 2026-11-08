package com.xtt.mcmodmaker.core;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.transport.CredentialsProvider;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 一个模组项目对应的 git 仓库。
 *
 * 仓库就放在项目目录里（.git 跟着模组文件夹走），每次操作现开现关，
 * 不长期持有 Repository —— 手机上随时可能被系统杀掉进程，留着手柄没意义。
 *
 * 所有方法都是阻塞的，调用方要放到后台线程。
 */
public final class GitRepo {

    /** 仓库目录名，导出/文件树里都要跳过它。 */
    public static final String GIT_DIR = ".git";

    public interface Progress {
        void onStep(String text);
    }

    /** 一条提交。 */
    public static final class CommitInfo {
        public final String id;
        public final String message;
        public final long timeMillis;

        CommitInfo(String id, String message, long timeMillis) {
            this.id = id;
            this.message = message;
            this.timeMillis = timeMillis;
        }

        public String shortId() {
            return id.length() > 8 ? id.substring(0, 8) : id;
        }
    }

    /** 一个版本标签（备份点）。 */
    public static final class TagInfo {
        public final String name;
        public final String message;
        public final String commitId;
        public final long timeMillis;

        TagInfo(String name, String message, String commitId, long timeMillis) {
            this.name = name;
            this.message = message;
            this.commitId = commitId;
            this.timeMillis = timeMillis;
        }

        public String shortCommit() {
            return commitId.length() > 8 ? commitId.substring(0, 8) : commitId;
        }
    }

    private final File workDir;
    private final String authorName;
    private final String authorEmail;

    public GitRepo(File workDir, String authorName, String authorEmail) {
        this.workDir = workDir;
        this.authorName = authorName == null || authorName.trim().isEmpty()
                ? "Nemo 模组制作器" : authorName.trim();
        this.authorEmail = authorEmail == null || authorEmail.trim().isEmpty()
                ? "nemo@local" : authorEmail.trim();
    }

    public File workDir() {
        return workDir;
    }

    /** 这个项目已经是仓库了吗。 */
    public boolean exists() {
        return new File(workDir, GIT_DIR).isDirectory();
    }

    /**
     * 确保项目已经是仓库。
     *
     * @param initialMessage 第一次建仓时给初始提交用的说明；为空则只建仓不提交
     */
    public void ensureInit(String initialMessage) throws IOException, GitAPIException {
        if (!workDir.isDirectory() && !workDir.mkdirs()) {
            throw new IOException("无法创建项目目录：" + workDir.getAbsolutePath());
        }
        if (!exists()) {
            try (Git git = Git.init().setDirectory(workDir).call()) {
                // 身份写进仓库自己的配置，别去读系统里的 .gitconfig（Android 上没有）
                git.getRepository().getConfig().setString("user", null, "name", authorName);
                git.getRepository().getConfig().setString("user", null, "email", authorEmail);
                git.getRepository().getConfig().save();
            }
        }
        if (initialMessage != null && headId() == null) {
            commitIfChanged(initialMessage);
        }
    }

    private Repository open() throws IOException {
        return new FileRepositoryBuilder()
                .setGitDir(new File(workDir, GIT_DIR))
                .setWorkTree(workDir)
                .build();
    }

    private PersonIdent person(long whenMillis) {
        // 时区必须显式给：传 null 会直接空指针
        return new PersonIdent(authorName, authorEmail, new Date(whenMillis), TimeZone.getDefault());
    }

    /** 当前 HEAD 指向的提交 id；还没提交过时返回 null。 */
    public String headId() {
        if (!exists()) return null;
        try (Repository repo = open(); RevWalk rw = new RevWalk(repo)) {
            ObjectId id = repo.resolve("HEAD");
            if (id == null) return null;
            RevCommit commit = rw.parseCommit(id);
            return commit.getName();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 把所有改动提交一次。
     *
     * 工作区和索引一样干净时不产生空提交，直接返回 null ——
     * 用户只是翻了一眼配方就退出来，不该多一条记录。
     */
    public CommitInfo commitIfChanged(String message) throws IOException, GitAPIException {
        if (!exists()) return null;
        try (Git git = new Git(open())) {
            // 先加新增/修改，再用 --update 记下删除，两遍才对得上工作区
            git.add().addFilepattern(".").call();
            git.add().setUpdate(true).addFilepattern(".").call();
            Status status = git.status().call();
            if (status.isClean()) return null;
            long now = System.currentTimeMillis();
            RevCommit commit = git.commit()
                    .setMessage(message)
                    .setAuthor(person(now))
                    .setCommitter(person(now))
                    .setAllowEmpty(false)
                    .call();
            return new CommitInfo(commit.getName(), commit.getFullMessage(), now);
        }
    }

    /** 最近的提交，新的在前；每个提交的时间用它自己的提交时间。 */
    public List<CommitInfo> commits(int max) throws IOException, GitAPIException {
        List<CommitInfo> out = new ArrayList<>();
        if (!exists()) return out;
        try (Git git = new Git(open())) {
            Iterable<RevCommit> it = git.log().setMaxCount(max).call();
            for (RevCommit c : it) {
                out.add(new CommitInfo(c.getName(), c.getFullMessage(),
                        c.getCommitTime() * 1000L));
            }
        }
        return out;
    }

    /** 某条提交的完整信息。 */
    public CommitInfo commitOf(String id) {
        if (!exists()) return null;
        try (Repository repo = open(); RevWalk rw = new RevWalk(repo)) {
            RevCommit c = rw.parseCommit(repo.resolve(id));
            return new CommitInfo(c.getName(), c.getFullMessage(), c.getCommitTime() * 1000L);
        } catch (Exception e) {
            return null;
        }
    }

    /** 所有版本标签，时间新的在前。 */
    public List<TagInfo> tags() {
        List<TagInfo> out = new ArrayList<>();
        if (!exists()) return out;
        try (Repository repo = open(); RevWalk rw = new RevWalk(repo)) {
            for (Ref ref : repo.getRefDatabase().getRefsByPrefix("refs/tags/")) {
                Ref peeled = repo.getRefDatabase().peel(ref);
                String commitId = peeled.getPeeledObjectId() != null
                        ? peeled.getPeeledObjectId().getName()
                        : (ref.getObjectId() == null ? "" : ref.getObjectId().getName());
                String message = "";
                long time = 0;
                try {
                    RevObject obj = rw.parseAny(ref.getObjectId());
                    if (obj instanceof RevTag) {
                        RevTag tag = (RevTag) obj;
                        message = tag.getFullMessage() == null ? "" : tag.getFullMessage().trim();
                        if (tag.getTaggerIdent() != null) {
                            time = tag.getTaggerIdent().getWhen().getTime();
                        }
                    }
                } catch (Exception ignored) {
                }
                if (time == 0) {
                    CommitInfo c = commitOf(commitId);
                    if (c != null) time = c.timeMillis;
                }
                out.add(new TagInfo(ref.getName().substring("refs/tags/".length()),
                        message, commitId, time));
            }
        } catch (Exception ignored) {
        }
        Collections.sort(out, new Comparator<TagInfo>() {
            @Override public int compare(TagInfo a, TagInfo b) {
                return Long.compare(b.timeMillis, a.timeMillis);
            }
        });
        return out;
    }

    /** 打一个带说明的标签（备份点）。 */
    public void createTag(String name, String message) throws IOException, GitAPIException {
        long now = System.currentTimeMillis();
        try (Git git = new Git(open())) {
            git.tag().setName(name).setAnnotated(true)
                    .setMessage(message == null ? "" : message)
                    .setTagger(person(now))
                    .setForceUpdate(false)
                    .call();
        }
    }

    public void deleteTag(String name) throws IOException, GitAPIException {
        try (Git git = new Git(open())) {
            git.tagDelete().setTags(name).call();
        }
    }

    public boolean tagExists(String name) {
        if (!exists()) return false;
        try (Repository repo = open()) {
            return repo.exactRef("refs/tags/" + name) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** 把工作区硬切到某条提交（分支仍停在原处，恢复本身不产生提交）。 */
    public void restoreHard(String commitId) throws IOException, GitAPIException {
        try (Git git = new Git(open())) {
            // 分支指针也一起挪过去，否则后面的自动保存会出现「分支落后于工作区」的假分叉
            git.reset().setMode(ResetCommand.ResetType.HARD).setRef(commitId).call();
        }
    }

    /**
     * 恢复到某个版本，同时把「恢复前」的状态存成一个版本标签。
     *
     * 不用 reset --hard 直接改分支指针：那样会把历史甩掉，最新那版就只剩标签能找回。
     * 这里的做法是先把工作区切成目标版本的内容，再把 HEAD 放回原处补一个提交，
     * 于是历史是一条线，恢复本身也是一次可以再回退的改动。
     *
     * @param targetCommitId 目标版本
     * @param label          给用户看的说明（写进提交信息）
     * @return 新产生的提交；目标就是当前版本时返回 null
     */
    public CommitInfo restore(String targetCommitId, String label,
                              String preTagPrefix, String preTagMessage)
            throws IOException, GitAPIException {
        if (!exists()) throw new IOException("这个模组还不是仓库");
        String target = resolveCommitId(targetCommitId);
        String base = headId();
        if (base == null) throw new IOException("仓库里还没有任何版本");
        if (base.equals(target)) return null;

        // 恢复前留一个副本标签，位置就是现在这一版
        createTag(uniqueTagName(preTagPrefix), preTagMessage);

        try (Git git = new Git(open())) {
            // 1. 工作区与索引换成目标版本的内容（HEAD 会跟着走，下一步再放回来）
            git.reset().setMode(ResetCommand.ResetType.HARD).setRef(target).call();
            // 2. HEAD 放回原处，索引和工作区保持目标版本的样子
            git.reset().setMode(ResetCommand.ResetType.SOFT).setRef(base).call();
            // 3. 补一个提交：内容 = 目标版本，父提交 = 恢复前的状态
            long now = System.currentTimeMillis();
            RevCommit commit = git.commit()
                    .setMessage(label)
                    .setAuthor(person(now))
                    .setCommitter(person(now))
                    .setAllowEmpty(false)
                    .call();
            return new CommitInfo(commit.getName(), commit.getFullMessage(), now);
        }
    }

    /** 把标签名或短 id 解析成完整的提交 id。 */
    public String resolveCommitId(String ref) throws IOException {
        try (Repository repo = open()) {
            ObjectId id = repo.resolve(ref);
            if (id == null) throw new IOException("找不到这个版本：" + ref);
            try (RevWalk rw = new RevWalk(repo)) {
                return rw.parseCommit(id).getName();
            }
        }
    }

    /** 生成一个不会重名的标签名，形如 备份-20261108-072133。 */
    public String uniqueTagName(String prefix) {
        SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);
        String base = prefix + "-" + f.format(new Date());
        String name = base;
        int n = 2;
        while (tagExists(name)) {
            name = base + "-" + n;
            n++;
        }
        return name;
    }

    // ==================== 远端 ====================

    /** 当前分支名；还没提交过时返回默认分支名。 */
    public String branch() {
        try (Repository repo = open()) {
            String b = repo.getBranch();
            if (b != null) return b;
        } catch (Exception ignored) {
        }
        return "master";
    }

    public String remoteUrl() {
        if (!exists()) return "";
        try (Repository repo = open()) {
            String url = repo.getConfig().getString("remote", "origin", "url");
            return url == null ? "" : url;
        } catch (Exception e) {
            return "";
        }
    }

    public boolean hasRemote() {
        String url = remoteUrl();
        return url != null && !url.trim().isEmpty();
    }

    public void setRemote(String url) throws IOException {
        try (Repository repo = open()) {
            repo.getConfig().setString("remote", "origin", "url", url.trim());
            repo.getConfig().setString("remote", "origin", "fetch",
                    "+refs/heads/*:refs/remotes/origin/*");
            repo.getConfig().save();
        }
    }

    public void removeRemote() throws IOException {
        try (Repository repo = open()) {
            // 只清 origin 这一节，别的远端别一起抹掉
            repo.getConfig().unsetSection("remote", "origin");
            repo.getConfig().save();
        }
    }

    /** 把 origin 上另一个分支设成本分支的上游（推送第一次要先有它）。 */
    private void setTracking(String branch) throws IOException {
        try (Repository repo = open()) {
            repo.getConfig().setString("branch", branch, "remote", "origin");
            repo.getConfig().setString("branch", branch, "merge", "refs/heads/" + branch);
            repo.getConfig().save();
        }
    }

    /**
     * 推送：本分支 + 所有版本标签。
     *
     * 标签也要推，否则「备份」只留在手机上，换台设备就没了。
     * 不强制推送：远端有本地没有的提交时让服务端拒绝，由界面提示先拉取。
     */
    public String push(String token) throws IOException, GitAPIException {
        if (!hasRemote()) throw new IOException("还没有关联远端仓库");
        if (headId() == null) throw new IOException("仓库里还没有任何版本");
        String branch = branch();
        try (Git git = new Git(open())) {
            git.push()
                    .setRemote("origin")
                    .setCredentialsProvider(credentials(token))
                    // 显式写 refspec：分支 + 标签。
                    // setPushTags() 和 setRefSpecs() 会互相覆盖，干脆两个都写进列表
                    .setRefSpecs(new org.eclipse.jgit.transport.RefSpec(
                                    "refs/heads/" + branch + ":refs/heads/" + branch),
                            new org.eclipse.jgit.transport.RefSpec(
                                    "refs/tags/*:refs/tags/*"))
                    .call();
            // 推成功了就把上游记下来，以后拉取知道对哪个分支
            setTracking(branch);
            return branch;
        }
    }

    /** 拉取的三种结局。 */
    public static final int PULL_SAME = 0;
    /** 远端比本地新，可以直接快进。 */
    public static final int PULL_REMOTE_AHEAD = 1;
    /** 本地比远端新，没什么可拉的（该推送了）。 */
    public static final int PULL_LOCAL_AHEAD = 2;
    /** 两边都有新提交，分叉了，得由用户选保留哪边。 */
    public static final int PULL_DIVERGED = 3;
    /** 远端还没有这个分支。 */
    public static final int PULL_NO_REMOTE_BRANCH = 4;

    /** 一次拉取前的检查结果。 */
    public static final class PullInfo {
        public final int state;
        public final String localId;
        public final String remoteId;

        PullInfo(int state, String localId, String remoteId) {
            this.state = state;
            this.localId = localId;
            this.remoteId = remoteId;
        }
    }

    /** 拉取第一步：fetch 完先看两边什么关系，不直接改工作区。 */
    public PullInfo fetchAndInspect(String token) throws IOException, GitAPIException {
        if (!hasRemote()) throw new IOException("还没有关联远端仓库");
        String branch = branch();
        try (Git git = new Git(open())) {
            git.fetch()
                    .setRemote("origin")
                    .setCredentialsProvider(credentials(token))
                    .setTagOpt(org.eclipse.jgit.transport.TagOpt.FETCH_TAGS)
                    .call();
        }
        String local = headId();
        String remote;
        try (Repository repo = open()) {
            ObjectId id = repo.resolve("refs/remotes/origin/" + branch);
            remote = id == null ? null : id.getName();
        }
        if (remote == null) return new PullInfo(PULL_NO_REMOTE_BRANCH, local, null);
        if (local == null) return new PullInfo(PULL_REMOTE_AHEAD, null, remote);
        if (local.equals(remote)) return new PullInfo(PULL_SAME, local, remote);
        if (isAncestor(local, remote)) return new PullInfo(PULL_REMOTE_AHEAD, local, remote);
        if (isAncestor(remote, local)) return new PullInfo(PULL_LOCAL_AHEAD, local, remote);
        return new PullInfo(PULL_DIVERGED, local, remote);
    }

    /** a 是不是 b 的祖先（即 a 已经在 b 的历史里）。 */
    public boolean isAncestor(String a, String b) {
        try (Repository repo = open(); RevWalk rw = new RevWalk(repo)) {
            RevCommit ca = rw.parseCommit(repo.resolve(a));
            RevCommit cb = rw.parseCommit(repo.resolve(b));
            return rw.isMergedInto(ca, cb);
        } catch (Exception e) {
            return false;
        }
    }

    /** 把本地切到某个提交（分支跟着走），用于快进到远端 / 以远端为准。 */
    public void hardResetTo(String commitId) throws IOException, GitAPIException {
        try (Git git = new Git(open())) {
            git.reset().setMode(ResetCommand.ResetType.HARD).setRef(commitId).call();
        }
        setTracking(branch());
    }

    /** 直接把项目克隆成一个新仓库。 */
    public static void cloneInto(File dir, String url, String token) throws IOException, GitAPIException {
        if (dir.exists() && dir.listFiles() != null && dir.listFiles().length > 0) {
            throw new IOException("目标文件夹不是空的");
        }
        if (!dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("无法创建文件夹：" + dir.getAbsolutePath());
        }
        try (Git git = Git.cloneRepository()
                .setURI(url)
                .setDirectory(dir)
                .setCredentialsProvider(credentials(token))
                .setCloneAllBranches(true)
                .call()) {
            // 关掉即完成；cloned repo 的 origin 已经配好
        }
    }

    /** 从远端 url 推一个还没关联仓库的本地仓库：建好 origin 再推。 */
    public void pushTo(String url, String token) throws IOException, GitAPIException {
        setRemote(url);
        push(token);
    }

    public static CredentialsProvider credentials(String token) {
        if (token == null || token.trim().isEmpty()) return null;
        return new UsernamePasswordCredentialsProvider("token", token.trim());
    }
}
