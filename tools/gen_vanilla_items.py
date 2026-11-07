#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成 app/src/main/assets/vanilla_items.json（原版物品/方块清单）。

为什么要这个脚本：内置清单是「某个基岩版版本」的快照，游戏一更新就得重新生成。
把流程写成脚本，下次网易更新（比如 3.11 = BE 1.21.14x）只要改下面两个版本号重跑。

数据来源
  - 物品与方块清单：pmmp/BedrockData 的 bedrock-<版本> 分支
      required_item_list.json   → 所有物品与方块的 id
      block_id_to_item_id_map.json → 哪些是方块
  - 创造模式分类：同分支的 creative/{construction,equipment,items,nature}.json
  - 中文名：Mojang/bedrock-samples 对应 tag 的 resource_pack/texts/zh_CN.lang
      老版本用 tile.planks.oak.name 这种「属性名」写法，
      新版本用 tile.oak_planks.name，脚本两种都收。
      官方文件里缺的（教育版方块、部分分变体）再用 Java 版中文补一遍。

用法
  python3 tools/gen_vanilla_items.py
  # 换版本：
  python3 tools/gen_vanilla_items.py 1.21.130 v1.21.130.3
"""

import json
import os
import sys
import urllib.request

BEDROCK_VERSION = "1.21.130"        # 网易中国版 3.10 = BE 1.21.130
SAMPLES_TAG = "v1.21.130.3"         # bedrock-samples 里对应的 tag
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "app", "src", "main", "assets", "vanilla_items.json")

SKIP = {
    "minecraft:air", "minecraft:allow", "minecraft:deny",
    "minecraft:client_request_placeholder_block", "minecraft:unknown",
}


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "nemo-gen/1.0"})
    with urllib.request.urlopen(req, timeout=120) as r:
        return r.read()


def fetch_json(url):
    return json.loads(fetch(url).decode("utf-8-sig"))


def load_lang(text):
    """把 lang 读成 {键去掉 item./tile. 前缀与 .name 后缀: 中文名}。"""
    out = {}
    for line in text.splitlines():
        line = line.strip().lstrip("\ufeff")
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        k, v = k.strip(), v.strip()
        if not v:
            continue
        parts = k.split(".")
        if len(parts) < 3 or parts[0] not in ("item", "tile") or parts[-1] != "name":
            continue
        key = ".".join(parts[1:-1])
        if key and key not in out:      # 先出现的优先，避免被别名覆盖
            out[key] = v
    return out


def legacy_candidates(short_id):
    """老命名兜底：oak_planks → planks.oak / planks，acacia_door → door.acacia。"""
    parts = short_id.split("_")
    out = []
    if len(parts) >= 2:
        out.append(parts[-1] + "." + "_".join(parts[:-1]))
        out.append("_".join(parts[:-1]) + "." + parts[-1])
    return out


def main():
    global BEDROCK_VERSION, SAMPLES_TAG
    if len(sys.argv) >= 2:
        BEDROCK_VERSION = sys.argv[1]
    if len(sys.argv) >= 3:
        SAMPLES_TAG = sys.argv[2]

    base = ("https://raw.githubusercontent.com/pmmp/BedrockData/"
            "bedrock-%s/" % BEDROCK_VERSION)
    print("拉取 BedrockData %s ..." % BEDROCK_VERSION)
    items = fetch_json(base + "required_item_list.json")
    block_map = fetch_json(base + "block_id_to_item_id_map.json")
    blocks = set(block_map.keys())

    categories = {}
    for name, label in (("construction", "Construction"), ("equipment", "Equipment"),
                        ("items", "Items"), ("nature", "Nature")):
        try:
            groups = fetch_json(base + "creative/%s.json" % name)
        except Exception as e:
            print("  跳过 creative/%s: %s" % (name, e))
            continue
        for g in groups:
            for it in g.get("items") or []:
                iid = it if isinstance(it, str) else it.get("name")
                if iid:
                    categories.setdefault(iid, label)

    lang_url = ("https://raw.githubusercontent.com/Mojang/bedrock-samples/%s/"
                "resource_pack/texts/zh_CN.lang" % SAMPLES_TAG)
    print("拉取官方语言文件 %s ..." % SAMPLES_TAG)
    lang = load_lang(fetch(lang_url).decode("utf-8-sig"))

    # 对应版本的官方文件里也漏了一批老方块（新版本把 tile.<名字>.name 改了写法），
    # 再拉一份最新版基岩语言文件补上
    lang_new = {}
    try:
        print("拉取新版基岩语言文件补漏 ...")
        lang_new = load_lang(fetch(
            "https://raw.githubusercontent.com/Mojang/bedrock-samples/main/"
            "resource_pack/texts/zh_CN.lang").decode("utf-8-sig"))
    except Exception as e:
        print("  跳过新版补漏: %s" % e)

    # 基岩版语言文件不全（教育版方块、部分分变体没有条目），用 Java 版中文兜底
    java_lang = {}
    try:
        print("拉取 Java 版中文兜底 ...")
        jz = json.loads(fetch(
            "https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/"
            "1.21.1/assets/minecraft/lang/zh_cn.json").decode("utf-8-sig"))
        java_lang = jz
    except Exception as e:
        print("  跳过 Java 版兜底: %s" % e)

    def pretty(short):
        return " ".join(w.capitalize() for w in short.split("_"))

    out, unnamed = [], []
    for iid in sorted(items):
        if iid in SKIP:
            continue
        short = iid.split(":", 1)[1]
        name = lang.get(iid) or lang.get(short)
        if not name:
            for c in legacy_candidates(short):
                if c in lang:
                    name = lang[c]
                    break
        if not name:
            name = lang_new.get(iid) or lang_new.get(short)
        if not name:
            for c in legacy_candidates(short):
                if c in lang_new:
                    name = lang_new[c]
                    break
        if not name:
            name = (java_lang.get("item.minecraft." + short)
                    or java_lang.get("block.minecraft." + short) or "")
        if not name:
            name = pretty(short)
            unnamed.append(short)
        out.append([iid, name, categories.get(iid, "Items"), 1 if iid in blocks else 0])

    payload = {
        "version": BEDROCK_VERSION,
        "source": "Mojang/bedrock-samples %s + pmmp/BedrockData bedrock-%s"
                  % (SAMPLES_TAG, BEDROCK_VERSION),
        "items": out,
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, separators=(",", ":"))
    print("写入 %s" % OUT)
    print("  条目 %d，其中方块 %d，无中文名 %d"
          % (len(out), sum(1 for x in out if x[3]), len(unnamed)))
    if unnamed:
        print("  无中文名样例：%s" % ", ".join(unnamed[:10]))


if __name__ == "__main__":
    main()
