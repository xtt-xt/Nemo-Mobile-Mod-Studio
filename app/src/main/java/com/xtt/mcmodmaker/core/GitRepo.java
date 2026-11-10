package com.xtt.mcmodmaker.core;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RefSpec;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
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

    /** 远端有哪些分支，以及它的默认分支（远端 HEAD 指的那条）。 */
    public static final class RemoteBranches {
        public final String defaultBranch;
        public final List<String> branches;

        RemoteBranches(String defaultBranch, List<String> branches) {
            this.defaultBranch = defaultBranch;
            this.branches = branches;
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

    /**
     * 本地分支对着远端哪条分支。
     *
     * 存的是 git 标准的上游配置（branch.<本地>.merge），没配过就当成同名分支。
     * 本地叫 master、远端默认叫 main 这种对不上的情况由界面让用户自己选，
     * 这里只负责记住选的结果。
     */
    public String upstreamBranch() {
        try (Repository repo = open()) {
            String merge = repo.getConfig().getString("branch", branch(), "merge");
            if (merge != null && merge.startsWith("refs/heads/")) {
                return merge.substring("refs/heads/".length());
            }
        } catch (Exception ignored) {
        }
        return branch();
    }

    /** 有没有手动选过远端分支。 */
    public boolean hasUpstream() {
        try (Repository repo = open()) {
            return repo.getConfig().getString("branch", branch(), "merge") != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** 记住「本地分支对着远端的哪条分支」。 */
    public void setUpstream(String remoteBranch) throws IOException {
        String local = branch();
        try (Repository repo = open()) {
            repo.getConfig().setString("branch", local, "remote", "origin");
            repo.getConfig().setString("branch", local, "merge", "refs/heads/" + remoteBranch);
            repo.getConfig().save();
        }
    }

    /** 解除关联时把上游配置也清掉，免得下次关联别的仓库还用着旧分支名。 */
    public void clearUpstream() throws IOException {
        String local = branch();
        try (Repository repo = open()) {
            repo.getConfig().unsetSection("branch", local);
            repo.getConfig().save();
        }
    }

    /**
     * 问一下远端有哪些分支、默认分支是哪条。
     *
     * 必须问，不能按本地分支名去猜：本地初始化的分支叫 master，
     * 而远端的默认分支可能叫 main，猜错就会去读一条早就停更的老分支。
     * 判断办法是拿 HEAD 的提交 id 去对 refs/heads/*，对上哪条哪条就是默认分支。
     */
    public RemoteBranches remoteBranches(String token) throws IOException, GitAPIException {
        if (!hasRemote()) throw new IOException("还没有关联远端仓库");
        final String url = remoteUrl();
        Collection<Ref> refs;
        try {
            refs = withRetry(new Attempt<Collection<Ref>>() {
                @Override public Collection<Ref> run() throws Exception {
                    return Git.lsRemoteRepository()
                            .setRemote(url)
                            .setTimeout(NET_TIMEOUT_SECONDS)
                            .setCredentialsProvider(credentials(token))
                            .call();
                }
            });
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            if (e instanceof GitAPIException) throw (GitAPIException) e;
            throw new IOException(String.valueOf(e.getMessage()));
        }

        String headId = null;
        List<String> branches = new ArrayList<>();
        for (Ref r : refs) {
            if ("HEAD".equals(r.getName())) {
                headId = r.getObjectId() == null ? null : r.getObjectId().name();
            } else if (r.getName().startsWith("refs/heads/")) {
                branches.add(r.getName().substring("refs/heads/".length()));
            }
        }
        Collections.sort(branches);

        String def = "";
        if (headId != null) {
            for (Ref r : refs) {
                if (r.getName().startsWith("refs/heads/") && r.getObjectId() != null
                        && headId.equals(r.getObjectId().name())) {
                    def = r.getName().substring("refs/heads/".length());
                    break;
                }
            }
        }
        if (def.isEmpty() && !branches.isEmpty()) def = branches.get(0);
        return new RemoteBranches(def, branches);
    }

    /**
     * 推送：本地分支推到远端选中的那条分支，外加所有版本标签。
     *
     * 标签也要推，否则「备份」只留在手机上，换台设备就没了。
     * 不强制推送：远端有本地没有的提交时让服务端拒绝，由界面提示先拉取。
     *
     * @return 推到的远端分支名
     */
    public String push(String token, Progress progress) throws IOException, GitAPIException {
        if (!hasRemote()) throw new IOException("还没有关联远端仓库");
        if (headId() == null) throw new IOException("仓库里还没有任何版本");
        final String branch = branch();
        final String upstream = upstreamBranch();
        try {
            withRetry(new Attempt<Void>() {
                @Override public Void run() throws Exception {
                    try (Git git = new Git(open())) {
                        git.push()
                                .setRemote("origin")
                                .setTimeout(NET_TIMEOUT_SECONDS)
                                .setCredentialsProvider(credentials(token))
                                .setProgressMonitor(monitor(progress))
                                // 显式写 refspec：本地分支 → 远端选中的分支 + 标签。
                                // setPushTags() 和 setRefSpecs() 会互相覆盖，干脆两个都写进列表
                                .setRefSpecs(new RefSpec(
                                                "refs/heads/" + branch + ":refs/heads/" + upstream),
                                        new RefSpec("refs/tags/*:refs/tags/*"))
                                .call();
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            if (e instanceof GitAPIException) throw (GitAPIException) e;
            throw new IOException(String.valueOf(e.getMessage()));
        }
        // 推成功了就把上游记下来，以后拉取知道对哪个分支
        setUpstream(upstream);
        return upstream;
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
    public PullInfo fetchAndInspect(String token, Progress progress) throws IOException, GitAPIException {
        if (!hasRemote()) throw new IOException("还没有关联远端仓库");
        final String upstream = upstreamBranch();
        try {
            withRetry(new Attempt<Void>() {
                @Override public Void run() throws Exception {
                    try (Git git = new Git(open())) {
                        git.fetch()
                                .setRemote("origin")
                                .setTimeout(NET_TIMEOUT_SECONDS)
                                .setCredentialsProvider(credentials(token))
                                .setProgressMonitor(monitor(progress))
                                .setTagOpt(org.eclipse.jgit.transport.TagOpt.FETCH_TAGS)
                                .call();
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            // 保持和原来的签名一致：网络类问题原样抛，界面自己认
            if (e instanceof IOException) throw (IOException) e;
            if (e instanceof GitAPIException) throw (GitAPIException) e;
            throw new IOException(String.valueOf(e.getMessage()));
        }
        String local = headId();
        String remote;
        try (Repository repo = open()) {
            ObjectId id = repo.resolve("refs/remotes/origin/" + upstream);
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
        String upstream = upstreamBranch();
        try (Git git = new Git(open())) {
            git.reset().setMode(ResetCommand.ResetType.HARD).setRef(commitId).call();
        }
        setUpstream(upstream);
    }

    /** 直接把项目克隆成一个新仓库。 */
    public static void cloneInto(final File dir, final String url, final String token,
                                 final Progress progress)
            throws IOException, GitAPIException {
        try {
            withRetry(new Attempt<Void>() {
                @Override public Void run() throws Exception {
                    // 上一次失败的残留要清掉，否则第二次会因为目录非空直接失败
                    if (dir.exists()) deleteRecursive(dir);
                    if (!dir.mkdirs() && !dir.isDirectory()) {
                        throw new IOException("无法创建文件夹：" + dir.getAbsolutePath());
                    }
                    try (Git git = Git.cloneRepository()
                            .setURI(url)
                            .setDirectory(dir)
                            .setTimeout(NET_TIMEOUT_SECONDS)
                            .setCredentialsProvider(credentials(token))
                            .setProgressMonitor(monitor(progress))
                            .setCloneAllBranches(true)
                            .call()) {
                        // 关掉即完成；cloned repo 的 origin 已经配好
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            try {
                deleteRecursive(dir);
            } catch (Exception ignored) {
            }
            if (e instanceof IOException) throw (IOException) e;
            if (e instanceof GitAPIException) throw (GitAPIException) e;
            throw new IOException(String.valueOf(e.getMessage()));
        }
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) deleteRecursive(c);
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    /** 只把远端的东西取下来，不动工作区。 */
    public void fetchRemote(String token, Progress progress) throws IOException, GitAPIException {
        if (!hasRemote()) throw new IOException("还没有关联远端仓库");
        try {
            withRetry(new Attempt<Void>() {
                @Override public Void run() throws Exception {
                    try (Git git = new Git(open())) {
                        git.fetch()
                                .setRemote("origin")
                                .setTimeout(NET_TIMEOUT_SECONDS)
                                .setCredentialsProvider(credentials(token))
                                .setProgressMonitor(monitor(progress))
                                .setTagOpt(org.eclipse.jgit.transport.TagOpt.FETCH_TAGS)
                                .call();
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            if (e instanceof GitAPIException) throw (GitAPIException) e;
            throw new IOException(String.valueOf(e.getMessage()));
        }
    }

    /** 远端分支上的提交（fetch 之后才有内容），新的在前。 */
    public List<CommitInfo> remoteCommits(int max) {
        List<CommitInfo> out = new ArrayList<>();
        if (!exists()) return out;
        try (Repository repo = open(); RevWalk rw = new RevWalk(repo)) {
            ObjectId id = repo.resolve("refs/remotes/origin/" + upstreamBranch());
            if (id == null) return out;
            rw.markStart(rw.parseCommit(id));
            int n = 0;
            for (RevCommit c : rw) {
                if (n++ >= max) break;
                out.add(new CommitInfo(c.getName(), c.getFullMessage(), c.getCommitTime() * 1000L));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** 远端分支当前指向的提交；没 fetch 过或没有该分支时返回 null。 */
    public String remoteHeadId() {
        if (!exists()) return null;
        try (Repository repo = open()) {
            ObjectId id = repo.resolve("refs/remotes/origin/" + upstreamBranch());
            return id == null ? null : id.getName();
        } catch (Exception e) {
            return null;
        }
    }

    /** 从远端 url 推一个还没关联仓库的本地仓库：建好 origin 再推。 */
    public void pushTo(String url, String token) throws IOException, GitAPIException {
        setRemote(url);
        push(token, null);
    }

    /** 网络操作重试次数：GitHub 的 HTTPS 会偶发被重置，第一次失败就放弃太亏。 */
    private static final int NET_RETRY = 3;
    /** 单次网络操作的超时（秒）。不设的话连不通会一直挂着。 */
    private static final int NET_TIMEOUT_SECONDS = 30;

    private interface Attempt<T> {
        T run() throws Exception;
    }

    /**
     * 跑一次网络操作，失败就退避重试。
     *
     * 认证类错误重试也没用，直接抛出来；
     * 其它（连接重置、超时）重试几次往往就过了。
     */
    private static <T> T withRetry(Attempt<T> attempt) throws Exception {
        Exception last = null;
        for (int i = 1; i <= NET_RETRY; i++) {
            try {
                return attempt.run();
            } catch (Exception e) {
                last = e;
                if (!isTransient(e)) throw e;
                // 认证错误、仓库不存在这类重试也没意义，直接抛
                if (i < NET_RETRY && isTransient(e)) {
                    try {
                        Thread.sleep(1200L * i);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
        }
        throw last;
    }

    /** 是不是「重试一下可能就好了」的错误（连接被重置、超时这类）。 */
    private static boolean isTransient(Throwable e) {
        Throwable cur = e;
        int guard = 0;
        while (cur != null && guard++ < 8) {
            String msg = String.valueOf(cur.getMessage() == null ? "" : cur.getMessage())
                    .toLowerCase();
            if (msg.contains("connection") || msg.contains("timeout") || msg.contains("timed out")
                    || msg.contains("socket") || msg.contains("unable to resolve")
                    || msg.contains("unknownhost") || msg.contains("reset")
                    || msg.contains("broken pipe") || msg.contains("eofexception")) {
                return true;
            }
            // 底层是 SocketException / ConnectException / UnknownHostException 都算
            String cls = cur.getClass().getSimpleName().toLowerCase();
            if (cls.contains("socket") || cls.contains("connect") || cls.contains("unknownhost")
                    || cls.contains("sockettimeout")) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    /**
     * 把 JGit 的英文阶段名换成看得懂的说法，数字照留。
     *
     * 进度回调形如「Writing objects 12/57」，翻译时把尾巴上的 12/57 剥出来接在后面。
     */
    public static String zhStep(String raw) {
        if (raw == null || raw.isEmpty()) return "正在传输…";
        String text = raw.trim();
        String suffix = "";
        int space = text.lastIndexOf(' ');
        if (space > 0) {
            String tail = text.substring(space + 1);
            if (tail.matches("\\d+/\\d+")) {
                suffix = " " + tail;
                text = text.substring(0, space).trim();
            }
        }
        String key = text.toLowerCase(Locale.US);
        String zh;
        if (key.startsWith("finding sources")) zh = "正在整理要推送的内容";
        else if (key.startsWith("finding local")) zh = "正在查找改动";
        else if (key.startsWith("writing objects")) zh = "正在上传";
        else if (key.startsWith("compressing objects")) zh = "正在压缩";
        else if (key.startsWith("building")) zh = "正在打包";
        else if (key.startsWith("receiving objects")) zh = "正在下载";
        else if (key.startsWith("resolving deltas")) zh = "正在整理下载的内容";
        else if (key.startsWith("updating references")) zh = "正在更新分支";
        else if (key.startsWith("checking out")) zh = "正在取出文件";
        else zh = "正在传输";
        return zh + suffix + "…";
    }

    /**
     * 把 JGit 的进度事件转成一句人话交给界面显示。
     *
     * JGit 会按阶段回调（找对象、压包、上传、下载），带上已完成和总数；
     * 回调很密，这里限一下频率，免得界面被刷得一直重绘。
     */
    private static final class Reporter implements ProgressMonitor {
        private final Progress cb;
        private String title = "";
        private int total = 0;
        private int done = 0;
        private long last = 0;

        Reporter(Progress cb) {
            this.cb = cb;
        }

        @Override public void start(int totalTasks) {
        }

        @Override public void beginTask(String name, int totalWork) {
            title = name == null ? "" : name;
            total = totalWork;
            done = 0;
            publish(true);
        }

        @Override public void update(int completed) {
            done += completed;
            publish(false);
        }

        @Override public void endTask() {
        }

        @Override public boolean isCancelled() {
            return false;
        }

        private void publish(boolean force) {
            long now = System.currentTimeMillis();
            if (!force && now - last < 120) return;
            last = now;
            String text = title;
            if (total > 0) {
                text = text + " " + Math.min(done, total) + "/" + total;
            }
            cb.onStep(text);
        }
    }

    private static ProgressMonitor monitor(final Progress cb) {
        return cb == null ? null : new Reporter(cb);
    }

    public static CredentialsProvider credentials(String token) {
        if (token == null || token.trim().isEmpty()) return null;
        return new UsernamePasswordCredentialsProvider("token", token.trim());
    }
}
