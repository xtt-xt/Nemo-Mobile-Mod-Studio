<div align="center">

  <h1>
    <span style="color: #e67e22; font-size: 3em;">Nemo Mobile Mod Studio</span>
  </h1>

  <p style="font-size: 1.2em; color: #7f8c8d;">
    网易模组制作器 · 移动端的Minecraft模组创作工具

目前属于非常不完善阶段，谨慎使用
  </p>

  <p>
    <img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg" alt="License">
    <img src="https://img.shields.io/badge/Platform-Android-green.svg" alt="Platform">
    <img src="https://img.shields.io/badge/Release-v1.1.0-orange.svg" alt="Release">
    <img src="https://img.shields.io/badge/Java-8-green.svg" alt="Java 8">
    <img src="https://img.shields.io/badge/AGP-7.0.2-blue.svg" alt="AGP 7.0.2">
    <img src="https://img.shields.io/badge/Gradle-7.5-purple.svg" alt="Gradle 7.5">
  </p>

<div align="center">
  <a href="https://github.com/xtt-xt/Nemo-Mobile-Mod-Studio">
    <img src="https://socialify.git.ci/xtt-xt/Nemo-Mobile-Mod-Studio/image?custom_description=%E4%B8%80%E4%B8%AA%E7%BD%91%E6%98%93%E5%9F%BA%E5%B2%A9%E7%89%88%E7%9A%84%E5%8F%AF%E8%A7%86%E5%8C%96%E6%A8%A1%E7%BB%84%E5%88%B6%E4%BD%9C%E8%BD%AF%E4%BB%B6%0AA+visual+modding+software+for+NetEase+Bedrock+Edition.&description=1&font=Inter&forks=1&issues=1&language=1&logo=https%3A%2F%2Fraw.githubusercontent.com%2Fxtt-xt%2FNemo-Mobile-Mod-Studio%2Frefs%2Fheads%2Fmain%2Fapp%2Fsrc%2Fmain%2Fres%2Fdrawable%2Fic_launcher.png&name=1&owner=1&pattern=Charlie+Brown&pulls=1&stargazers=1&theme=Auto" alt="Nemo Mobile Mod Studio" style="max-width: 100%;">
  </a>
</div>

</div>
<hr>

<h2>📦 获取编译好的文件</h2>

<div align="center">
  <!-- 设备兼容性徽章 -->
  <img src="https://img.shields.io/badge/Android-6.0%2B-green?logo=android&style=flat-square" alt="Android 6.0+">

  <br/>

  <!-- GitHub 下载按钮 -->
  <a href="https://github.com/xtt-xt/Nemo-Mobile-Mod-Studio/releases">
    <img src="https://img.shields.io/badge/GitHub-下载最新APK-brightgreen?style=for-the-badge&logo=github" alt="GitHub下载">
  </a>

  <br/>

  <!-- 网盘下载按钮 -->
  <a href="https://1825385503.share.123865.com/123pan/0EQWjv-MafMd?pwd=1379#">
    <img src="https://img.shields.io/badge/📥%20网盘下载-123云盘-blue?style=for-the-badge" alt="网盘下载">
  </a>
</div>

<p style="text-align: center; margin-top: 10px;">
  前往 <a href="https://github.com/xtt-xt/Nemo-Mobile-Mod-Studio/releases"><strong>Releases 页面</strong></a> 或通过 <strong>123云盘</strong> 下载已编译好的 APK 文件（提取码：1379）。
  <br/>
  <strong>兼容设备：</strong>Android 6.0 (Marshmallow, API 23) 及以上系统，覆盖绝大多数 Android 手机和平板。
</p>

<h2>⚠️ 使用声明</h2>

本工具（Nemo Mobile Mod Studio）**仅供创作网易版《我的世界》相关模组**使用。

- 禁止将本工具用于违反网易游戏相关服务条款或开发协议的用途。
- 禁止利用本工具生成、分发破坏游戏平衡、侵犯他人权益或违反法律法规的内容。
- 开发者不承担因使用者违反上述声明而产生的任何法律责任。

请在使用前仔细阅读 [网易开发者协议](https://mc.163.com/dev/) 及相关政策。

<h2>🛠 从源码构建</h2>

<h3>环境要求</h3>
<table>
  <tr><th>组件</th><th>版本</th><th>说明</th></tr>
  <tr><td>JDK</td><td>11+（推荐 17）</td><td>编译与运行 Gradle 所需</td></tr>
  <tr><td>Gradle</td><td>7.5（已包含 Gradle Wrapper）</td><td>可直接使用 <code>./gradlew</code> 构建</td></tr>
  <tr><td>Android SDK</td><td>compileSdk 30 / build-tools 33.0.0</td><td>需安装 <code>platforms;android-30</code> 与 <code>build-tools;33.0.0</code></td></tr>
  <tr><td>Android Gradle Plugin</td><td>7.0.2</td><td>由 <code>build.gradle</code> 自动拉取</td></tr>
</table>

<h3>构建步骤</h3>
<ol>
  <li>克隆仓库：<code>git clone https://github.com/xtt-xt/Nemo-Mobile-Mod-Studio.git</code></li>
  <li>配置 <code>local.properties</code>：写入 <code>sdk.dir=/path/to/android-sdk</code></li>
  <li>执行 <code>gradle :app:assembleDebug</code></li>
  <li>产物位于 <code>app/build/outputs/apk/debug/app-debug.apk</code></li>
</ol>

<h3>注意事项（ARM64 / 容器环境）</h3>
<blockquote>
  <p>本项目默认拉取的 AAPT2（<code>aapt2-7.0.2-linux</code>）为 <strong>x86_64 二进制</strong>，在 <strong>arm64 Linux</strong>（如树莓派、ARM 云服务器、proot 容器）上会报 <code>AAPT2 Daemon startup failed</code>。若遇到该问题：</p>
  <ul>
    <li>安装 <code>qemu-user-static</code> 与 <code>libc6-amd64-cross</code>、<code>libgcc-s1-amd64-cross</code>；</li>
    <li>用 arm64 原生 C 程序包装 <code>qemu-x86_64-static</code> 调用 SDK 的 <code>aapt2</code>，并命名为 <code>aapt2</code>（AGP 要求路径以 <code>aapt2</code> 结尾）；</li>
    <li>在 <code>gradle.properties</code> 中设置 <code>android.aapt2FromMavenOverride=/path/to/aapt2</code>。</li>
  </ul>
</blockquote>

<h2>🤖 GitHub Actions 自动构建正式 APK</h2>

<p>推送到 <code>main</code> 分支后，GitHub Actions 会自动构建一个通用的正式签名 APK；也可以在 Actions 页面通过 <code>workflow_dispatch</code> 手动触发。构建完成后在对应工作流的 Artifacts 中下载：</p>

<pre>Nemo-universal-release.apk</pre>

<p>工作流不会自动创建 GitHub Release。签名信息通过 GitHub 仓库的 <strong>Settings → Secrets and variables → Actions</strong> 保存，绝不能提交到 Git：</p>

<table>
  <tr><th>Secret 名称</th><th>用途</th></tr>
  <tr><td><code>NEMO_KEYSTORE_BASE64</code></td><td>发布 keystore 文件的单行 Base64 内容</td></tr>
  <tr><td><code>NEMO_KEYSTORE_PASSWORD</code></td><td>keystore 密码</td></tr>
  <tr><td><code>NEMO_KEY_ALIAS</code></td><td>密钥别名</td></tr>
  <tr><td><code>NEMO_KEY_PASSWORD</code></td><td>密钥密码</td></tr>
</table>

<blockquote>
  <p><strong>重要：</strong>发布 keystore 是后续 APK 覆盖安装和版本更新的唯一身份凭据。请离线备份 keystore 与密码；一旦丢失，无法再为使用该签名安装的用户发布可覆盖更新的 APK。</p>
</blockquote>

<h2>🏗 项目结构</h2>

<p>项目在 <code>v1.0.2</code> 完成了一次大规模重构，从「三个巨型 Activity + 大量重复代码」整理为「分层架构」：</p>

<pre>
app/src/main/java/com/xtt/mcmodmaker/
├── MainActivity.java           三选项卡主界面（开发 / 关于 / 管理）
├── EditorActivity.java         三栏编辑器（左导航 / 中文件 / 右编辑）
├── BackupsActivity.java        备份管理
├── core/                       核心业务层（与 UI 解耦）
│   ├── Constants.java          全局常量（路径、URL、FileProvider）
│   ├── ModTemplates.java       脚本 / 清单 / studio.json / 配方模板
│   ├── ProjectManager.java     项目创建 / 导入导出 / 备份 / 覆盖
│   └── UpdateChecker.java      版本更新检查
├── editor/
│   ├── FileNode.java           文件树节点模型
│   └── RecipeEditor.java       配方可视化编辑器（含 Host 回调接口）
└── util/                       通用工具层
    ├── FileUtils.java          统一文件读写 / 复制 / 递归删除
    ├── JsonUtils.java          studio.json 多字段兼容解析
    ├── DateUtils.java          时间格式化
    └── UiUtils.java            Toast / 主线程 / 间距工具
</pre>

<p>核心设计约定：</p>
<ul>
  <li><strong>UI 与业务分离</strong>：文件操作、项目逻辑集中在 <code>core/ProjectManager</code>，Activity 只负责界面与交互；</li>
  <li><strong>接口回调解耦</strong>：<code>ProjectManager.ImportUi</code>（冲突处理）、<code>RecipeEditor.Host</code>（刷新文件列表 / 切源码模式）等接口让模块间协作清晰；</li>
  <li><strong>工具复用</strong>：通用逻辑统一收敛到 <code>util/</code> 包，消除了原先散落各处的重复代码。</li>
</ul>

<h2>📖 使用指南</h2>

<h3>🆕 创建模组</h3>
<ol>
  <li>打开应用，点击主界面的 <strong>「新建模组」</strong> 按钮。</li>
  <li>填写模组名称和命名空间。</li>
  <li>点击 <strong>「创建」</strong>，应用会自动生成模组的基础文件结构，如果勾选使用脚本会同时添加py脚本文件。</li>
  <li>创建完成后点击进入进入 <strong>编辑界面</strong>。</li>
</ol>

<h3>✏️ 编辑模组内容</h3>
<ol>
  <li>编辑器支持两种模式：</li>
  <ul>
    <li><strong>可视化编辑</strong>：当前可添加和修改 <strong>合成配方（Recipes）</strong>，点击 <strong>「添加」</strong> 按钮即可创建。</li>
    <li><strong>源代码编辑</strong>：其他文本类文件请使用此模式直接编辑原始内容。</li>
  </ul>
  <li>在元素的背景界面可以对其 <strong>修改或删除</strong>。</li>
</ol>


<h3>🧪 测试模组</h3>
<ol>
  <li>确保你已拥有网易《我的世界》开发者账号并且已安装 <strong>“手机测试版启动器”</strong>。</li>
  <li>点击模组详情的打开文件夹按钮，将文件夹里的3个文件全选压缩</li>
  <li>打开网易<a href="https://mcdev.webapp.163.com/#/square" target="_blank">开发者平台</a>或<strong>网易模组制作器-主页-管理</strong>，打开<strong>「上架与资源管理-发布新资源」</strong>，填写相关信息后点击「保存」，点击三个点后点击「免机审测试」。等待收到自测提交成功消息即可打开我的世界测试版测试</li>
  <li>进入游戏，在 <strong>“组件中心”</strong> 中下载该模组，即可开始体验。</li>
</ol>

<h3>💾 备份与恢复</h3>
<ol>
  <li>在首页模组设置或编辑器功能区中点击 <strong>「备份模组」</strong>即可生成备份文件。</li>
  <li>选择模组的备份，点击 <strong>「覆盖」</strong> 即可覆盖原模组。</li>
</ol>

> **提示**：文件里的studio.json通常不会影响上传，如果上传失败可以尝试删除。

<h2> 功能列表 </h2>
<h3> 启动器 </h3>

- [x] 模组导入
- [x] 模组导出
- [x] 模组创建

  - [x] 清单文件
  - [x] 配置文件
  - [x] 脚本文件(可选)

- [x] 模组查看
- [x] 备份

  - [x] 备份创建
  - [x] 备份覆盖

- [x] 模组上传(内置浏览器)
     
<h3> 可视化创建 </h3>

- [x] 配方
- [ ] 物品
- [ ] 生物
- [ ] 方块
- [ ] 物品分页
- [ ] 物品分组

<h3> 可视化编辑 </h3>

- [x] 配方
- [ ] 物品
- [ ] 生物
- [ ] 方块
- [ ] 物品分页
- [ ] 物品分组

<h3> 文件编辑查看 </h3>

- [x] 贴图图片(查看)
- [x] json文件
- [x] py脚本

<h2>📝 更新日志</h2>

<h3>v1.1.0</h3>
<ul>
  <li><strong>开发者平台管理：</strong>新增作品管理、收件箱、作品详情编辑、资源文件与宣传图上传、自测申请等功能；</li>
  <li><strong>分类修复：</strong>支持按资源类别加载 add_ons 次级分类，避免提交时出现“二级分类错误”；</li>
  <li><strong>关于页优化：</strong>鸣谢内容支持上下滚动，压缩卡片间距，并补充 MCDevManager API 实现参考鸣谢；</li>
  <li><strong>导入说明：</strong>暂时隐藏开发者平台发布包导入入口，避免将加密发布包误作为可编辑源项目导入。</li>
</ul>

<h3>v1.0.2（重构版）</h3>
<ul>
  <li><strong>代码重构</strong>：将三个巨型 Activity（共 6400+ 行、大量重复代码）重构为分层架构，拆分为 <code>core / editor / util</code> 三个包，共 14 个 Java 文件；</li>
  <li><strong>业务抽离</strong>：项目创建 / 导入导出 / 备份 / 覆盖等逻辑统一收敛到 <code>ProjectManager</code>，配方可视化编辑独立为 <code>RecipeEditor</code>；</li>
  <li><strong>构建现代化</strong>：移除已停服的 bintray 镜像，升级依赖配置（AndroidX、documentfile、core），支持 Gradle 7.5 + AGP 7.0.2 构建；</li>
  <li><strong>Bug 修复</strong>：修复 JSONException 未捕获、时间戳解析错误、StyleSheet 类型错误等若干问题。</li>
</ul>

<h3>v1.0.0 / v1.0.1</h3>
<ul>
  <li>初版：模组创建 / 导入导出 / 备份 / 内置浏览器上传 / 配方可视化编辑等核心功能。</li>
</ul>

<h2>📜 开源许可与致谢</h2>

<p>本项目（Nemo Mobile Mod Studio）采用 <a href="LICENSE"><strong>GNU General Public License v3.0</strong></a> 进行许可。</p>

<h3>🎨 第三方组件与致谢</h3>
<p>本项目的界面实现基于 <strong><a href="https://github.com/1503Dev/ore-ui-for-android">ore-ui-for-android</a></strong>，的分支<strong><a href="https://github.com/xtt-xt/ore-ui-for-android">ore-ui-for-android</a></strong>，感谢该组件库的原作者 <strong><a href="https://github.com/TheChuan1503">TheChuan1503</a></strong> 及所有贡献者的出色工作。</p>

<p>开发者平台的登录、加密、上传和作品管理 API 实现参考了 <strong><a href="https://github.com/BitterLemonn/MCDevManager">MCDevManager</a></strong> 的开源实现，感谢作者 <strong>BitterLemonn</strong> 及所有贡献者。</p>
<ul>
  <li><strong>项目地址(分支)</strong>：<a href="https://github.com/xtt-xt/ore-ui-for-android">https://github.com/xtt-xt/ore-ui-for-android</a></li>
  <li><strong>项目地址(原项目)</strong>：<a href="https://github.com/1503Dev/ore-ui-for-android">https://github.com/1503Dev/ore-ui-for-android</a></li>
  <li><strong>开源许可证</strong>：<a href="https://github.com/1503Dev/ore-ui-for-android/blob/main/LICENSE">Apache License 2.0</a></li>
</ul>

<blockquote>
  <p><strong>特别说明</strong>：本项目和 <i>ore-ui-for-android</i> 均非 Minecraft 官方作品，与 Mojang Studios、Microsoft 以及网易公司无从属关系，亦未获得它们的官方认可或授权。Minecraft 及相关资产的知识产权归 Mojang Studios 和 Microsoft 所有，网易版《我的世界》的相关权益归网易公司所有。本工具仅供学习交流，使用者需自行遵守对应平台的开发者协议与条款。</p>
</blockquote>

<h2>💬 开发者的话</h2>

<blockquote>
  <p>目前世面上的大部分手机模组制作器都是国际版的，被砍了假日创作者模式后几乎全部用不了。</p>
  <p>于是我制作了这个软件，大部分代码都是由AI编写，力求还原mc store</p>
  <p>由于我也不是很懂开发模组，所以bug什么的都是难免的，如果有想法可以提Issues</p>
  <p><del>咕咕咕</del></p>
</blockquote>
