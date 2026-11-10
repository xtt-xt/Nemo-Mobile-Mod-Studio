# 生成原版方块形状

`app/src/main/assets/block_shapes.json` 是这份工具产出的，别手改。

## 为什么需要它

基岩版资源包里没有原版方块的模型：官方 `blocks.json` 里带 `geometry` 的方块是 0 个，
台阶、楼梯、栅栏、铁砧这些形状是引擎 C++ 里算出来的，资源包和 `libminecraftpe.so`
里都拿不到。所以形状取的是 **Java 版官方模型** —— 同一批方块两边尺寸一致，
而且官方模型自带每个面的 UV，贴图不会拉歪。

## 怎么跑

1. 下载官方客户端的 jar（版本随意，用当前 release）：

   ```
   curl -s https://piston-meta.mojang.com/mc/game/version_manifest_v2.json
   # 从里面取 versions[0].url，再取 downloads.client.url
   curl -o client.jar <上一步拿到的地址>
   ```

2. 把 `client.jar` 放到 `/tmp/client263.jar`（或者改 `jbake.py` 顶部的路径）。

3. 跑：

   ```
   cd tools/blockshapes
   python3 gen.py
   ```

   会重写 `app/src/main/assets/block_shapes.json`，并把 `block_faces.json` 里
   床、旗帜那几条错的贴图映射改掉。

## 里面三个文件

- `jbake.py`：读 Java 的 blockstates / models，顺着 parent 链把模型摊平成
  「显式四边形」，处理 element 旋转、面旋转、自动 UV、绕序。
- `gen.py`：把基岩版方块 id 映射到 Java 方块名（`lit_furnace` → `furnace`、
  `*_double_slab` → `*_slab` + `type=double` 之类），逐个烘出来，按几何去重后写文件。
- `manual.py`：Java 里靠方块实体渲染、没有静态模型的方块（箱子、潜龙箱、床、旗帜、
  装饰陶罐、钟），按游戏里的样子手工拼。

## 输出格式

```json
{"t":[[[方向, 贴图槽, u0,v0,u1,v1,u2,v2,u3,v3, x,y,z ×4], ...], ...],
 "b":{"minecraft:oak_slab": 0, ...}}
```

- 方向：0=up 1=down 2=north 3=south 4=east 5=west（和 `BlockModel.FACES` 一致）
- 贴图槽：用这个方块六面贴图数组里的第几张（0~5），对上 Java 模型里的贴图变量
- uv 单位是 16×16 的贴图空间，和 Java 模型一致
- `t` 是去重后的几何模板，`b` 是方块 id → 模板下标
