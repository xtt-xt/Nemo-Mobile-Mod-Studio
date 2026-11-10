#!/usr/bin/env python3
"""把官方 Java 方块模型烘成 app 用的 block_shapes.json。"""
import json, re
import jbake as j
from manual import MANUAL

COLORS = ['white','orange','magenta','light_blue','yellow','lime','pink','gray','light_gray',
          'cyan','purple','blue','brown','green','red','black']

ALIAS = {
    'fence_gate': 'oak_fence_gate', 'wooden_door': 'oak_door', 'wooden_button': 'oak_button',
    'wooden_pressure_plate': 'oak_pressure_plate', 'trapdoor': 'oak_trapdoor',
    'standing_sign': 'oak_sign', 'wall_sign': 'oak_wall_sign',
    'darkoak_standing_sign': 'dark_oak_sign', 'darkoak_wall_sign': 'dark_oak_wall_sign',
    'noteblock': 'note_block', 'grass_path': 'dirt_path', 'deadbush': 'dead_bush',
    'waterlily': 'lily_pad', 'reeds': 'sugar_cane', 'web': 'cobweb', 'melon_block': 'melon',
    'nether_brick': 'nether_bricks', 'red_nether_brick': 'red_nether_bricks',
    'mob_spawner': 'spawner', 'end_bricks': 'end_stone_bricks',
    'end_brick_stairs': 'end_stone_brick_stairs', 'normal_stone_slab': 'stone_slab',
    'normal_stone_stairs': 'stone_stairs', 'prismarine_bricks_stairs': 'prismarine_brick_stairs',
    'brick_block': 'bricks', 'hardened_clay': 'terracotta', 'trip_wire': 'tripwire',
    'slime': 'slime_block', 'snow_layer': 'snow', 'quartz_ore': 'nether_quartz_ore',
    'silver_glazed_terracotta': 'light_gray_glazed_terracotta',
    'stonecutter_block': 'stonecutter', 'underwater_tnt': 'tnt',
    'dirt_with_roots': 'rooted_dirt', 'azalea_leaves_flowered': 'flowering_azalea_leaves',
    'small_dripleaf_block': 'small_dripleaf', 'glowingobsidian': 'obsidian',
    'deprecated_anvil': 'anvil', 'lit_pumpkin': 'jack_o_lantern',
    'lit_redstone_lamp': 'redstone_lamp', 'underwater_torch': 'torch',
    'unlit_redstone_torch': 'redstone_torch', 'powered_repeater': 'repeater',
    'unpowered_repeater': 'repeater', 'powered_comparator': 'comparator',
    'unpowered_comparator': 'comparator', 'piston_arm_collision': 'piston_head',
    'sticky_piston_arm_collision': 'piston_head',
    'cave_vines_body_with_berries': 'cave_vines_plant',
    'cave_vines_head_with_berries': 'cave_vines',
    'hard_glass': 'glass', 'hard_glass_pane': 'glass_pane',
    'undyed_shulker_box': 'shulker_box',
    'frame': 'item_frame', 'glow_frame': 'glow_item_frame',
    'golden_rail': 'powered_rail', 'magma': 'magma_block',
    'normal_stone_double_slab': 'stone_slab',
    'trapped_chest': 'trapped_chest',
}
for c in COLORS:
    ALIAS['hard_%s_stained_glass' % c] = '%s_stained_glass' % c
    ALIAS['hard_%s_stained_glass_pane' % c] = '%s_stained_glass_pane' % c


def alias(name):
    if name in ALIAS:
        base = ALIAS[name]
        if base == 'stone_slab' and 'double' in name:
            return base, {'type': 'double'}
        return base, {}
    m = re.match(r'^(.*)_double_slab$', name)
    if m:
        return m.group(1) + '_slab', {'type': 'double'}
    m = re.match(r'^(.*)double_(cut_copper_slab)$', name)
    if m:
        return m.group(1) + 'cut_copper_slab', {'type': 'double'}
    m = re.match(r'^(.*)_standing_sign$', name)
    if m:
        return m.group(1) + '_sign', {'rotation': '0'}
    if name.startswith('lit_'):
        return name[4:], {'lit': 'true'}
    if name.startswith('colored_torch_'):
        return 'torch', {}
    if name.endswith('_banner') or name == 'standing_banner':
        return None, {}
    return name, {}


FORCE = {}
FORCE['end_rod'] = {'facing': 'up'}
FORCE['lightning_rod'] = {'facing': 'up'}
FORCE['stone_button'] = {'face': 'floor'}

for n in ['oak_fence', 'spruce_fence', 'birch_fence', 'jungle_fence', 'acacia_fence',
          'dark_oak_fence', 'mangrove_fence', 'cherry_fence', 'pale_oak_fence',
          'bamboo_fence', 'crimson_fence', 'warped_fence', 'nether_brick_fence']:
    FORCE[n] = {'north': 'true', 'south': 'true'}


def build_one(name, force):
    if name in FORCE:
        return j.build_block(name, FORCE[name])
    inv = name + '_inventory'
    if '_' in inv and j.model(inv) is not None:
        q = j.build_model(inv)
        if q: return q
    bs = j.blockstate(name)
    if bs is None:
        if j.model(name) is not None:
            q = j.build_model(name)
            if q: return q
        return None
    if force and 'variants' in bs:
        for k in bs['variants']:
            st = j.parse_key(k)
            if all(st.get(p) == v for p, v in force.items()):
                ap = bs['variants'][k]
                if isinstance(ap, list): ap = ap[0]
                els, tex = j.flatten(ap['model'])
                return j.from_elements(els or [], ap.get('x', 0), ap.get('y', 0)) or None
    return j.build_block(name, FORCE.get(name))


def r(v):
    """取两位小数，写成数字（不写字符串，文件小一半）。"""
    x = round(float(v), 2)
    return 0 if x == 0 else x


WOOD = {'dark_oak': 'big_oak'}


def norm_tex(n):
    """贴图短名归一：去数字、去掉木头叫法差异（基岩版 dark_oak 叫 big_oak）"""
    n = n.lower().replace('minecraft:', '')
    if n.startswith('block/'):
        n = n[6:]
    n = re.sub(r'\d+', '', n)
    for a, b in WOOD.items():
        n = n.replace(a, b)
    return n


def tex_score(java, bedrock):
    """java 模型里的贴图变量 vs 基岩版这张脸的贴图名，越大越像"""
    if java == bedrock:
        return 100
    a, b = norm_tex(java), norm_tex(bedrock)
    if a == b:
        return 90
    ta = sorted(t for t in re.split(r'[_\-]', a) if t)
    tb = sorted(t for t in re.split(r'[_\-]', b) if t)
    if ta == tb:
        return 85
    if a in b or b in a:
        return 70
    # 至少主词对上（stonecutter_side vs stonecutter_side）
    if ta and tb and ta[0] == tb[0]:
        return 40
    return 0


def pick_slot(java, bases, fallback):
    best, bestScore = fallback, 0
    for i, base in enumerate(bases):
        s = tex_score(java, base)
        if s > bestScore:
            best, bestScore = i, s
    return best


def pack(q, six=None):
    """[dir, verts, uv, 贴图短名] -> [dir, 贴图槽, u0..v3, x,y,z *4]"""
    bases = [p.split('/')[-1] for p in six] if six else []
    out = []
    for item in q:
        idx, verts, uv, tex = item
        slot = idx
        if isinstance(tex, int):
            slot = tex
        elif tex and bases:
            slot = pick_slot(tex, bases, idx)
        row = [idx, slot]
        row += [r(x) for x in uv]
        for p in verts:
            row += [r(c) for c in p]
        out.append(row)
    return out


OVERRIDE = {
    'minecraft:bed': ['textures/blocks/bed_head_top', 'textures/blocks/planks_oak',
                      'textures/blocks/bed_head_side', 'textures/blocks/bed_head_side',
                      'textures/blocks/bed_head_side', 'textures/blocks/bed_head_side'],
}
for _b in ['minecraft:standing_banner', 'minecraft:wall_banner']:
    OVERRIDE[_b] = ['textures/entity/banner/banner_base'] * 6


def apply_override(faces):
    """有些方块的六个面在基岩版 blocks.json 里是错的（床、旗帜走实体渲染），这里改掉。"""
    paths = faces['paths']
    changed = False
    for bid, six in OVERRIDE.items():
        if bid not in faces['blocks']:
            continue
        idx = []
        for pth in six:
            if pth in paths:
                idx.append(paths.index(pth))
            else:
                paths.append(pth)
                idx.append(len(paths) - 1)
        faces['blocks'][bid] = idx
        changed = True
    return changed


def main():
    faces = json.load(open('/workspace/Nemo-Mobile-Mod-Studio/app/src/main/assets/block_faces.json'))
    if apply_override(faces):
        json.dump(faces, open('/workspace/Nemo-Mobile-Mod-Studio/app/src/main/assets/block_faces.json',
                              'w'), ensure_ascii=False, separators=(',', ':'))
        print('block_faces.json 已更新')
    ids = list(faces['blocks'].keys())
    out = {}
    ok = manual = none = 0
    miss = []
    for bid in ids:
        name = bid.split(':', 1)[1]
        if name in MANUAL:
            pth = faces['paths']
            six = [pth[i] for i in faces['blocks'][bid]]
            out[bid] = pack(MANUAL[name], six)
            manual += 1
            continue
        jn, force = alias(name)
        if jn is None:
            none += 1; miss.append(name); continue
        try:
            q = build_one(jn, force)
        except Exception as e:
            q = None
            print('ERR', name, e)
        if not q:
            none += 1; miss.append(name); continue
        pth = faces['paths']
        six = [pth[i] for i in faces['blocks'][bid]]
        out[bid] = pack(q, six)
        ok += 1
    print('built ok=%d manual=%d none=%d' % (ok, manual, none))
    print('miss', len(miss), ' '.join(miss))
    # 相同几何只存一份
    tmpl = []
    idx = {}
    bmap = {}
    for bid, q in out.items():
        k = json.dumps(q, separators=(',', ':'))
        if k not in idx:
            idx[k] = len(tmpl)
            tmpl.append(k)
        bmap[bid] = idx[k]
    s = '{"t":[' + ','.join(tmpl) + '],"b":' + json.dumps(bmap, separators=(',', ':'), sort_keys=True) + '}'
    print('templates', len(tmpl), 'json bytes', len(s))
    with open('/workspace/Nemo-Mobile-Mod-Studio/app/src/main/assets/block_shapes.json', 'w') as f:
        f.write(s)


if __name__ == '__main__':
    main()
