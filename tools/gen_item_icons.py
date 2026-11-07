#!/usr/bin/env python3
"""生成物品图标资源：把原版物品贴图塞进 assets，并写一张 id → 贴图的映射表。

用法：
    python3 tools/gen_item_icons.py <bedrock-samples 的 resource_pack 路径> [版本]

产物：
    app/src/main/assets/item_tex/**.png      贴图本体（只收物品库里真正用到的）
    app/src/main/assets/item_icons.json      {"textures": [...], "items": {id: 下标}}
    app/src/main/assets/placeholder_item.png 灰苹果占位图
    app/src/main/assets/placeholder_block.png 灰方块贴图（渲染占位立方体用）

贴图来源：
    textures/item_texture.json 是官方 id → 贴图对照表（文件开头有 // 注释，得先剔掉）；
    剩下对不上的按文件名做词元匹配，再有不认识的走下面的 ALIAS 手写对照。
"""
import json
import os
import re
import sys

from PIL import Image

AS_ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
ASSETS = os.path.abspath(os.path.join(AS_ROOT, 'app/src/main/assets'))

# 文件名与物品 id 对不上、但能确定是同一个东西的，写在这里。
ALIAS = {
    # 工具：官方贴图用 gold_ 前缀，物品 id 是 golden_
    'golden_axe': 'gold_axe', 'golden_pickaxe': 'gold_pickaxe',
    'golden_shovel': 'gold_shovel', 'golden_hoe': 'gold_hoe',
    'golden_sword': 'gold_sword', 'golden_spear': 'gold_spear',
    'golden_helmet': 'gold_helmet', 'golden_chestplate': 'gold_chestplate',
    'golden_leggings': 'gold_leggings', 'golden_boots': 'gold_boots',
    'golden_horse_armor': 'gold_horse_armor',
    'wooden_axe': 'wood_axe', 'wooden_pickaxe': 'wood_pickaxe',
    'wooden_shovel': 'wood_shovel', 'wooden_hoe': 'wood_hoe',
    'wooden_sword': 'wood_sword', 'wooden_spear': 'wood_spear',
    'slime_ball': 'slimeball',
    # 铁/钻石/锁链套装在贴图里叫 helmet/chestplate/...，没有材质前缀的部分靠后缀区分
    'iron_horse_armor': 'horse_armor_iron', 'diamond_horse_armor': 'horse_armor_diamond',
    'leather_horse_armor': 'horse_armor_leather',
    'turtle_helmet': 'turtle_shell', 'turtle_shell_piece': 'scute',
    # 杂项
    'glass_bottle': 'glass_bottle', 'dragon_breath': 'dragons_breath',
    'fire_charge': 'fireball', 'firework_rocket': 'fireworks',
    'firework_star': 'firework_star', 'glow_ink_sac': 'dye_powder_glow',
    'cocoa_beans': 'dye_powder_brown', 'bone_meal': 'dye_powder_white',
    'lapis_lazuli': 'dye_powder_blue', 'ink_sac': 'dye_powder_black',
    'cooked_cod': 'fish_cooked', 'cod_bucket': 'bucket_fish',
    'pufferfish_bucket': 'bucket_pufferfish', 'salmon_bucket': 'bucket_salmon',
    'tadpole_bucket': 'bucket_tadpole', 'axolotl_bucket': 'bucket_axolotl',
    'tropical_fish_bucket': 'bucket_tropical',
    'map': 'map_empty', 'filled_map': 'map_filled',
    'minecart': 'minecart_normal', 'chest_minecart': 'minecart_chest',
    'hopper_minecart': 'minecart_hopper', 'tnt_minecart': 'minecart_tnt',
    'command_block_minecart': 'minecart_command_block',
    'paper': 'paper', 'book': 'book_writable', 'writable_book': 'book_writable',
    'written_book': 'book_written', 'knowledge_book': 'book_written',
    'compass': 'compass_item', 'recovery_compass': 'recovery_compass_item',
    'clock': 'clock_item', 'bow': 'bow_standby', 'crossbow': 'crossbow_standby',
    'fishing_rod': 'fishing_rod_uncast', 'elytra': 'elytra',
    'beef': 'beef_raw', 'porkchop': 'porkchop_raw', 'mutton': 'mutton_raw',
    'chicken': 'chicken_raw', 'rabbit': 'rabbit_raw', 'cod': 'fish_raw',
    'salmon': 'fish_salmon_raw', 'tropical_fish': 'fish_clownfish_raw',
    'pufferfish': 'fish_pufferfish_raw',
    'carrow': 'carrot', 'carrot': 'carrot',
    # 船 / 木牌之类：贴图是 boat_<木种>、sign_<木种>
    'boat': 'boat_oak', 'chest_boat': 'chest_boat_oak',
    'oak_sign': 'sign_oak', 'oak_hanging_sign': 'sign_oak_hanging',
    'spruce_sign': 'sign_spruce', 'birch_sign': 'sign_birch',
    'jungle_sign': 'sign_jungle', 'acacia_sign': 'sign_acacia',
    'dark_oak_sign': 'sign_darkoak', 'mangrove_sign': 'sign_mangrove',
    'cherry_sign': 'sign_cherry', 'bamboo_sign': 'bamboo_sign',
    'crimson_sign': 'crimson_sign_item', 'warped_sign': 'warped_sign_item',
    'pale_oak_sign': 'pale_oak_sign',
    'npc_spawn_egg': 'egg_npc', 'agent_spawn_egg': 'spawn_egg_agent',
    # 唱片：贴图叫 record_<曲名>
    'music_disc_11': 'record_11', 'music_disc_13': 'record_13',
    'music_disc_5': 'record_5', 'music_disc_blocks': 'record_blocks',
    'music_disc_cat': 'record_cat', 'music_disc_chirp': 'record_chirp',
    'music_disc_far': 'record_far', 'music_disc_mall': 'record_mall',
    'music_disc_mellohi': 'record_mellohi', 'music_disc_otherside': 'record_otherside',
    'music_disc_pigstep': 'record_pigstep', 'music_disc_stal': 'record_stal',
    'music_disc_strad': 'record_strad', 'music_disc_wait': 'record_wait',
    'music_disc_ward': 'record_ward',
    # 只有单张贴图、但 id 带变体词的
    'dye': 'dye_powder_black', 'firework_star': 'fireworks_charge',
    'lodestone_compass': 'lodestonecompass_item', 'light_block': 'light_block_0',
    # 门：贴图是 door_<木种>，legacy id 写成 <木种>_door
    'wooden_door': 'door_wood', 'oak_door': 'door_wood',
    'acacia_door': 'door_acacia', 'birch_door': 'door_birch',
    'jungle_door': 'door_jungle', 'spruce_door': 'door_spruce',
    'dark_oak_door': 'door_dark_oak', 'mangrove_door': 'door_mangrove',
    'crimson_door': 'door_crimson', 'warped_door': 'door_warped',
    'bed': 'bed_red', 'nether_wart': 'nether_wart',
    'wood': 'planks_oak', 'leaves': 'leaves_oak', 'log': 'log_oak',
    'log2': 'log_big_oak',
    'carpet': 'wool_white', 'concrete': 'concrete_white',
    'concrete_powder': 'concrete_powder_white',
    'coral': 'coral_blue', 'coral_block': 'coral_block_blue',
    'coral_fan': 'coral_fan_blue', 'coral_fan_dead': 'coral_fan_dead_blue',
    'double_plant': 'double_plant_sunflower_bottom',
    'fence': 'planks_oak', 'hard_stained_glass': 'glass_hardened',
    'chemistry_table': 'chemistry_table_compound_creator',
    'colored_torch_bp': 'colored_torch_bp_blue',
    'colored_torch_rg': 'colored_torch_rg_red',
    'compound': 'compound_creator',
    'bleach': 'bleach',
    'board': 'board_item', 'balloon': 'balloon_white',
    'double_stone_block_slab': 'stone_slab',
    'double_stone_block_slab2': 'red_sandstone_slab',
    'double_stone_block_slab3': 'granite_slab',
    'double_stone_block_slab4': 'stone_slab',
}


def toks(s):
    return set(t for t in s.split('_') if t)


def strip_comment(text):
    """官方 json 开头有 // 说明行，json 解析器不吃。"""
    return '\n'.join(l for l in text.split('\n') if not l.strip().startswith('//'))


def texture_paths(value):
    """item_texture 一项可以是字符串、数组、{path:...}，统一成路径列表。"""
    if isinstance(value, str):
        return [value]
    if isinstance(value, dict):
        p = value.get('path')
        return [p] if p else []
    out = []
    if isinstance(value, list):
        for v in value:
            if isinstance(v, str):
                out.append(v)
            elif isinstance(v, dict) and v.get('path'):
                out.append(v['path'])
    return out


def collect_files(rp):
    """贴图目录下所有 png，键是文件名（去掉扩展名），值是相对 resource_pack 的路径。"""
    root = os.path.join(rp, 'textures', 'items')
    files = {}
    dup = set()
    for dirpath, _, names in os.walk(root):
        for n in names:
            if not n.endswith('.png') or n.endswith('_mers.png'):
                continue
            name = n[:-4]
            # 统一不带扩展名，和 item_texture.json 里的写法一致，取图时再拼
            rel = os.path.relpath(os.path.join(dirpath, n), rp).replace(os.sep, '/')[:-4]
            if name in files:
                dup.add(name)
            files[name] = rel
    return files, dup


def load_index(rp):
    raw = open(os.path.join(rp, 'textures', 'item_texture.json'), encoding='utf-8').read()
    data = json.loads(strip_comment(raw)).get('texture_data', {})
    return {k: texture_paths(v.get('textures')) for k, v in data.items()}


def pick_from_array(paths, id_tokens):
    """数组里挑一张：先用 id 里多出来的词挑，挑不出来就第一张。"""
    if len(paths) == 1:
        return paths[0]
    same = []
    for p in paths:
        ft = toks(p.split('/')[-1])
        if id_tokens & ft:
            same.append((len(ft & id_tokens), p))
    if same:
        same.sort(reverse=True)
        if len(same) == 1 or same[0][0] != same[1][0]:
            return same[0][1]
    return paths[0]


def match_one(sid, keys, files, dup):
    """返回 (贴图相对路径, 匹配方式) 或 (None, 原因)。"""
    # required_item_list 里有一批 legacy 写法 item.xxx，去掉前缀再匹配
    if sid.startswith('item.'):
        inner = sid[5:]
        rel, how = match_one(inner, keys, files, dup)
        if rel:
            return rel, how + '(legacy)'
    st = toks(sid)

    # 1. 官方对照表里直接有
    if sid in keys:
        p = pick_from_array(keys[sid], st)
        if p:
            return p, 'table'

    # 2. 手写别名
    alias = ALIAS.get(sid)
    if alias:
        if alias in files:
            return files[alias], 'alias'
        if alias in keys:
            return pick_from_array(keys[alias], st), 'alias'

    # 3. 词元匹配：同一集合 > id 是文件名子集 > 文件名是 id 子集
    cands = []
    for name, rel in files.items():
        ft = toks(name)
        if ft == st:
            cands.append((1000, len(ft), rel))
        elif st <= ft:
            cands.append((500 - len(ft - st), len(ft), rel))
        elif ft <= st:
            cands.append((400 - len(st - ft), len(ft), rel))
    if cands:
        cands.sort(reverse=True)
        if len(cands) == 1 or cands[0][0] != cands[1][0]:
            return cands[0][2], 'fuzzy'
        return None, '有歧义'

    # 4. 对照表里的键名碰碰运气
    for k, ps in keys.items():
        if toks(k) == st and ps:
            return pick_from_array(ps, st), 'table-fuzzy'
    return None, '没找到'


def gray_apple(src_png, dst_png):
    """占位图：把真苹果去饱和压成灰色，一眼能看出「这里缺图」。"""
    im = Image.open(src_png).convert('RGBA')
    px = []
    for r, g, b, a in im.getdata():
        v = int(0.299 * r + 0.587 * g + 0.114 * b)
        v = min(255, int(v * 0.85) + 20)
        px.append((v, v, v, a if a > 0 else 200))
    im.putdata(px)
    im.save(dst_png, optimize=True)


def gray_block(dst_png):
    """占位方块贴图：浅灰底加一点噪点，六面受光不同才分得清是立方体。"""
    im = Image.new('RGBA', (16, 16), (128, 128, 128, 255))
    px = []
    for i in range(256):
        v = 128 + ((i * 37) % 11) - 5
        px.append((v, v, v, 255))
    im.putdata(px)
    im.save(dst_png, optimize=True)


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    rp = sys.argv[1]
    version = sys.argv[2] if len(sys.argv) > 2 else '1.21.130'

    van = json.load(open(os.path.join(ASSETS, 'vanilla_items.json'), encoding='utf-8'))
    items = [e[0] for e in van['items'] if e[3] == 0]
    keys = load_index(rp)
    files, dup = collect_files(rp)
    print('物品 %d 条，贴图文件 %d 个，对照表 %d 项' % (len(items), len(files), len(keys)))

    out_dir = os.path.join(ASSETS, 'item_tex')
    os.makedirs(out_dir, exist_ok=True)
    for old in os.listdir(out_dir):
        if old.endswith('.png'):
            os.remove(os.path.join(out_dir, old))

    mapping = {}
    texture_list = []
    pool = {}
    methods = {}
    miss = []
    for iid in items:
        sid = iid.split(':', 1)[1]
        rel, how = match_one(sid, keys, files, dup)
        if rel is None:
            miss.append((sid, how))
            continue
        src = os.path.join(rp, rel + '.png')
        if not os.path.exists(src):
            src = os.path.join(rp, rel + '.tga')
            if not os.path.exists(src):
                miss.append((sid, '文件不存在'))
                continue
        # 扁平化文件名：同名文件加目录前缀区分
        base = os.path.basename(rel)
        name = base if base not in pool else rel.replace('/', '_')
        dst = os.path.join(out_dir, name + '.png')
        im = Image.open(src)
        if im.mode != 'RGBA':
            im = im.convert('RGBA')
        im.save(dst, optimize=True)
        if name not in pool:
            pool[name] = len(texture_list)
            texture_list.append(name)
        mapping[iid] = pool[name]
        methods[how] = methods.get(how, 0) + 1

    json.dump({'version': version, 'textures': texture_list, 'items': mapping},
              open(os.path.join(ASSETS, 'item_icons.json'), 'w', encoding='utf-8'),
              ensure_ascii=False, separators=(',', ':'))
    print('已匹配 %d / %d，方式 %s' % (len(mapping), len(items), methods))
    if miss:
        print('未匹配 %d 条：%s' % (len(miss), ' '.join(m[0] for m in miss[:80])))

    apple = os.path.join(out_dir, 'apple.png')
    if os.path.exists(apple):
        gray_apple(apple, os.path.join(ASSETS, 'placeholder_item.png'))
    gray_block(os.path.join(ASSETS, 'placeholder_block.png'))

    total = sum(os.path.getsize(os.path.join(out_dir, f)) for f in os.listdir(out_dir))
    print('贴图 %d 个，合计 %.0f KB' % (len(os.listdir(out_dir)), total / 1024.0))


if __name__ == '__main__':
    main()
