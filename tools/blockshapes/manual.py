#!/usr/bin/env python3
"""Java 里由「方块实体渲染」的方块，没有静态模型，这里按游戏里的样子手工拼。"""
import jbake as j


def boxb(x0, y0, z0, x1, y1, z1, slots):
    """一个六面盒子，slots = [up,down,north,south,east,west] 的贴图槽号"""
    e = {'from': [x0, y0, z0], 'to': [x1, y1, z1]}
    out = []
    for d in j.DIRS:
        verts = j.corner_pos(e, d)
        uv = [0.0, 0.0, 16.0, 16.0]
        pairs = []
        for (su, sv) in j.uv_slots(d):
            pairs += [uv[0] + (uv[2] - uv[0]) * su, uv[1] + (uv[3] - uv[1]) * sv]
        q = [(pairs[i * 2], pairs[i * 2 + 1]) for i in range(4)]
        verts, q = j.fix_winding(verts, q, d)
        flat = []
        for p in q:
            flat += list(p)
        out.append([j.OUT_IDX[d], [list(p) for p in verts], flat, slots[j.OUT_IDX[d]]])
    return out


def chest():
    return boxb(1, 0, 1, 15, 14, 15, [0, 1, 2, 3, 4, 5])


def cube():
    return boxb(0, 0, 0, 16, 16, 16, [0, 1, 2, 3, 4, 5])


def bed():
    # 床腿用木板（槽 1 = down = 木板），床面顶上是床头顶图，四周是床头侧图
    out = boxb(0, 3, 0, 16, 7, 16, [0, 1, 2, 3, 4, 5])
    out += boxb(0, 7, 0, 16, 9, 16, [0, 1, 2, 3, 4, 5])
    for (x0, z0) in [(0, 0), (13, 0), (0, 13), (13, 13)]:
        out += boxb(x0, 0, z0, x0 + 3, 3, z0 + 3, [1, 1, 1, 1, 1, 1])
    return out


def banner():
    # 挂墙的旗面 + 上面那根横杆
    out = boxb(0, 0, 7, 16, 15, 9, [0, 0, 0, 0, 0, 0])
    out += boxb(0, 15, 6.8, 16, 16, 9.2, [0, 0, 0, 0, 0, 0])
    return out


def bell():
    # 钟体 + 上面的吊杆。bell_side 是整块钟的侧图，所以钟体就是一个盒子
    out = boxb(5, 4, 5, 11, 11, 11, [0, 1, 2, 3, 2, 2])
    out += boxb(7, 11, 7, 9, 16, 9, [0, 0, 0, 0, 0, 0])
    return out


def pot():
    # 陶罐：身子略收口，上面一圈窄口
    out = boxb(1, 0, 1, 15, 13, 15, [0, 1, 2, 3, 4, 5])
    out += boxb(2, 13, 2, 14, 16, 14, [0, 0, 0, 0, 0, 0])
    return out


MANUAL = {
    'chest': chest(),
    'trapped_chest': chest(),
    'ender_chest': chest(),
    'copper_chest': chest(),
    'exposed_copper_chest': chest(),
    'weathered_copper_chest': chest(),
    'oxidized_copper_chest': chest(),
    'waxed_copper_chest': chest(),
    'waxed_exposed_copper_chest': chest(),
    'waxed_weathered_copper_chest': chest(),
    'waxed_oxidized_copper_chest': chest(),
    'bed': bed(),
    'standing_banner': banner(),
    'wall_banner': banner(),
    'decorated_pot': pot(),
    'bell': bell(),
}
for c in ['white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray',
          'light_gray', 'cyan', 'purple', 'blue', 'brown', 'green', 'red', 'black']:
    MANUAL[c + '_shulker_box'] = cube()
MANUAL['undyed_shulker_box'] = cube()
