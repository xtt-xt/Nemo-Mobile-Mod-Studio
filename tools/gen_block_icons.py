#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成方块图标渲染需要的两样东西。

1. app/src/main/assets/block_tex/textures/blocks/**.png
   原版方块贴图。工程里的 terrain_texture.json 只写路径（textures/blocks/glass.png），
   文件本身由游戏自带、工程不会复制，所以必须内置一份，
   否则连原版方块都渲染不出图标。
   注意 1.21.130 起有一部分贴图改成了 .tga（RenderDragon 时代迁移），
   Android 的 BitmapFactory 不认 tga，这里统一转成 png 再打包。

2. app/src/main/assets/block_faces.json
   预解析好的「方块 id → 六面贴图路径」。
   链路：minecraft:oak_planks
           → blocks.json["oak_planks"].textures = "planks_oak"
           → terrain_texture.json["planks_oak"].textures = "textures/blocks/planks_oak"
   运行时不用再解析那两个几百 KB 的 json。

用法
  python3 tools/gen_block_icons.py
  python3 tools/gen_block_icons.py 1.21.130 v1.21.130.3
  python3 tools/gen_block_icons.py 1.21.130 v1.21.130.3 /path/to/bedrock-samples
      （本地已有 bedrock-samples 检出时用这个，省掉上千次网络请求）
"""

import io
import json
import os
import re
import shutil
import sys
import urllib.request

BEDROCK_VERSION = "1.21.130"      # 网易 3.10 = BE 1.21.130
SAMPLES_TAG = "v1.21.130.3"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
TEX_OUT = os.path.join(ASSETS, "block_tex")
FACES_OUT = os.path.join(ASSETS, "block_faces.json")

FACES = ("up", "down", "north", "south", "east", "west")
TEX_PREFIX = "textures/blocks/"

# blocks.json 用的是老短名，方块 id 用的是新名，这里补常见别名
ALIASES = {
    "grass_block": "grass",
    "hard_glass": "glass",
    "iron_chain": "chain",
    "sea_lantern": "seaLantern",
    "trip_wire": "tripwire",
    "underwater_tnt": "tnt",
    "bricks": "brick_block",
    "brick": "brick_block",
    "colored_torch_blue": "torch",
    "colored_torch_green": "torch",
    "colored_torch_purple": "torch",
    "colored_torch_red": "torch",
    "underwater_torch": "torch",
}


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "nemo-gen/1.0"})
    with urllib.request.urlopen(req, timeout=180) as r:
        return r.read()


def load_json_text(text):
    """官方 json 带 // 注释和尾逗号，去掉再解析。"""
    text = text.lstrip("\ufeff")
    text = re.sub(r"^\s*//.*$", "", text, flags=re.M)
    text = re.sub(r",(\s*[\]}])", r"\1", text)
    return json.loads(text)


def main():
    global BEDROCK_VERSION, SAMPLES_TAG
    if len(sys.argv) >= 2:
        BEDROCK_VERSION = sys.argv[1]
    if len(sys.argv) >= 3:
        SAMPLES_TAG = sys.argv[2]
    local = os.path.abspath(sys.argv[3]) if len(sys.argv) >= 4 else None

    print("拉取 pmmp/BedrockData bedrock-%s 的方块清单 ..." % BEDROCK_VERSION)
    base = "https://raw.githubusercontent.com/pmmp/BedrockData/bedrock-%s/" % BEDROCK_VERSION
    block_map = json.loads(fetch(base + "block_id_to_item_id_map.json").decode("utf-8-sig"))

    raw_base = ("https://raw.githubusercontent.com/Mojang/bedrock-samples/%s/resource_pack/"
                % SAMPLES_TAG)

    def read_asset(rel):
        if local:
            with open(os.path.join(local, "resource_pack", rel), "rb") as fh:
                return fh.read().decode("utf-8-sig")
        return fetch(raw_base + rel).decode("utf-8-sig")

    print("读取 %s 的 blocks.json / terrain_texture.json ..."
          % ("本地 " + local if local else "Mojang/bedrock-samples " + SAMPLES_TAG))
    blocks = load_json_text(read_asset("blocks.json"))
    terrain = load_json_text(read_asset("textures/terrain_texture.json"))["texture_data"]

    # ---------- 解析 id → 六面 ----------
    def resolve_key(key):
        """terrain_texture.json 的一项 → 实际贴图路径。"""
        if isinstance(key, dict):
            key = key.get("path") or key.get("texture")
        if not isinstance(key, str):
            return None
        v = terrain.get(key)
        if v is None:
            return None
        t = v.get("textures") if isinstance(v, dict) else v
        if isinstance(t, str):
            return t
        if isinstance(t, list) and t:
            first = t[0]
            return first if isinstance(first, str) else first.get("path")
        return None

    def faces_of(textures):
        """blocks.json 的 textures 展开成六面；side = 四个侧面共用。"""
        if isinstance(textures, str):
            return {k: textures for k in FACES}
        if not isinstance(textures, dict):
            return None
        out = {k: v for k, v in textures.items() if k in FACES}
        side = textures.get("side")
        if side is not None:
            for k in ("north", "south", "east", "west"):
                out.setdefault(k, side)
        return out or None

    def find_entry(short, iid):
        """在 blocks.json 里找方块条目，兼容旧命名。"""
        for key in (short, iid):
            if key in blocks:
                return blocks[key]
        if short in ALIASES and ALIASES[short] in blocks:
            return blocks[ALIASES[short]]
        # hard_glass 系列：去掉 hard_ 前缀
        if short.startswith("hard_") and short[5:] in blocks:
            return blocks[short[5:]]
        # 下划线转驼峰：invisible_bedrock → invisibleBedrock
        parts = short.split("_")
        if len(parts) > 1:
            camel = parts[0] + "".join(x.capitalize() for x in parts[1:])
            if camel in blocks:
                return blocks[camel]
        # 去掉 _block 后缀：bricks → brick_block 反过来，这里处理 xxx_block → xxx
        if short.endswith("_block") and short[:-6] in blocks:
            return blocks[short[:-6]]
        return None

    faces_out = {}
    needed = set()
    skipped = []
    for iid in sorted(block_map):
        short = iid.split(":", 1)[1]
        entry = find_entry(short, iid)
        if entry is None:
            skipped.append(short)
            continue
        f = faces_of(entry.get("textures"))
        if not f:
            skipped.append(short)
            continue
        paths = []
        for face in FACES:
            raw_key = f.get(face)
            path = resolve_key(raw_key) if raw_key else None
            if not path:
                paths = None
                break
            paths.append(path)
        if not paths:
            skipped.append(short)
            continue
        faces_out[iid] = paths
        needed.update(paths)

    # ---------- 打包贴图（只打包用得到的） ----------
    if os.path.isdir(TEX_OUT):
        shutil.rmtree(TEX_OUT)
    dst_dir = os.path.join(TEX_OUT, "textures", "blocks")
    os.makedirs(dst_dir, exist_ok=True)

    from PIL import Image

    src_root = os.path.join(local, "resource_pack", "textures", "blocks") if local else None
    copied = total = 0
    missing = []
    tga_count = 0

    print("打包贴图（共 %d 条路径）..." % len(needed))
    for path in sorted(needed):
        rel = path[len(TEX_PREFIX):] if path.startswith(TEX_PREFIX) else path
        target = os.path.join(dst_dir, rel + ".png")
        os.makedirs(os.path.dirname(target) or dst_dir, exist_ok=True)

        data = None
        is_tga = False
        if src_root:
            for cand, tga in ((rel + ".png", False), (rel + ".tga", True)):
                fp = os.path.join(src_root, cand)
                if os.path.isfile(fp):
                    with open(fp, "rb") as fh:
                        data = fh.read()
                    is_tga = tga
                    break
        else:
            for cand, tga in ((path + ".png", False), (path + ".tga", True)):
                url = ("https://raw.githubusercontent.com/Mojang/bedrock-samples/%s/"
                       "resource_pack/%s" % (SAMPLES_TAG, cand))
                try:
                    data = fetch(url)
                    is_tga = tga
                    break
                except Exception:
                    continue

        if data is None:
            missing.append(rel)
            continue

        if is_tga:
            try:
                im = Image.open(io.BytesIO(data)).convert("RGBA")
                buf = io.BytesIO()
                im.save(buf, "PNG")
                data = buf.getvalue()
                tga_count += 1
            except Exception as e:
                missing.append("%s (tga 转换失败: %s)" % (rel, e))
                continue

        with open(target, "wb") as f:
            f.write(data)
        copied += 1
        total += len(data)

    payload = {
        "version": BEDROCK_VERSION,
        "faces": list(FACES),
        "paths": [],          # 占位，下面填
        "blocks": {},
    }
    # 路径池索引化：大量方块共用同一批贴图，索引后映射表小一个数量级
    pool, pool_index = [], {}
    for iid, paths in faces_out.items():
        idx = []
        for p in paths:
            if p not in pool_index:
                pool_index[p] = len(pool)
                pool.append(p)
            idx.append(pool_index[p])
        payload["blocks"][iid] = idx
    payload["paths"] = pool

    with open(FACES_OUT, "w", encoding="utf-8") as fh:
        json.dump(payload, fh, ensure_ascii=False, separators=(",", ":"))

    print()
    print("方块 id 总数 %d，能渲染 %d，跳过 %d（教育版/不可见方块）"
          % (len(block_map), len(faces_out), len(skipped)))
    print("打包贴图 %d 张（其中 tga 转 png %d 张），%.1f KB"
          % (copied, tga_count, total / 1024))
    print("唯一贴图路径 %d 条，block_faces.json %.1f KB"
          % (len(pool), os.path.getsize(FACES_OUT) / 1024))
    if missing:
        print("缺失 %d 个：%s" % (len(missing), ", ".join(missing[:8])))


if __name__ == "__main__":
    main()
