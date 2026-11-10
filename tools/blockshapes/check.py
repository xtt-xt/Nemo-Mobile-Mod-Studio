#!/usr/bin/env python3
"""全量自查：每张面的朝向、坐标平面、绕序、uv 是否越界。"""
import json, sys
import jbake as j

FACES = ['up', 'down', 'north', 'south', 'east', 'west']
NORM = {'up': (0,1,0), 'down': (0,-1,0), 'north': (0,0,-1),
        'south': (0,0,1), 'east': (1,0,0), 'west': (-1,0,0)}

def check_block(bid, quads):
    bad = []
    for idx, verts, uv, tex in quads:
        name = FACES[idx]
        n = NORM[name]
        # 1. 面必须是平的；轴对齐的面还要落在正确的平面上
        a0, b0, c0 = verts[0], verts[1], verts[2]
        uu = [b0[i]-a0[i] for i in range(3)]
        vv = [c0[i]-a0[i] for i in range(3)]
        cr0 = [uu[1]*vv[2]-uu[2]*vv[1], uu[2]*vv[0]-uu[0]*vv[2], uu[0]*vv[1]-uu[1]*vv[0]]
        ln0 = sum(x*x for x in cr0) ** 0.5
        axis_aligned = False
        if ln0 > 1e-6:
            cr0 = [x/ln0 for x in cr0]
            axis_aligned = max(abs(x) for x in cr0) > 0.995
        if axis_aligned and name in ('up', 'down'):
            vals = {round(p[1], 3) for p in verts}
            if len(vals) != 1:
                bad.append((bid, name, '不在同一 y 平面 %s' % vals)); continue
        # 2. 绕序：叉乘出来的法线要和方向一致
        a, b, c = verts[0], verts[1], verts[2]
        u = [b[i] - a[i] for i in range(3)]
        v = [c[i] - a[i] for i in range(3)]
        cr = [u[1]*v[2] - u[2]*v[1], u[2]*v[0] - u[0]*v[2], u[0]*v[1] - u[1]*v[0]]
        ln = sum(x*x for x in cr) ** 0.5
        if ln < 1e-6:
            bad.append((bid, name, '退化面（三点共线）')); continue
        cr = [x/ln for x in cr]
        dot = sum(cr[i]*n[i] for i in range(3))
        if dot < 0.3:
            bad.append((bid, name, '绕序反了 dot=%.2f' % dot))
        # 3. uv 越界（允许 0..16 之外一点点，方块模型有时故意超出）
        for x in uv:
            if x < -0.51 or x > 16.51:
                bad.append((bid, name, 'uv 越界 %s' % uv)); break
    return bad

def main():
    faces = json.load(open('/workspace/Nemo-Mobile-Mod-Studio/app/src/main/assets/block_faces.json'))
    paths = faces['paths']
    shapes = json.load(open('/workspace/Nemo-Mobile-Mod-Studio/app/src/main/assets/block_shapes.json'))
    tpl = shapes['t']
    bmap = shapes['b']
    junk = []
    total = 0
    for bid, ti in bmap.items():
        quads = []
        for row in tpl[ti]:
            idx = row[0]
            slot = row[1]
            uv = row[2:10]
            verts = [tuple(row[10 + k*3: 13 + k*3]) for k in range(4)]
            tex = paths[faces['blocks'][bid][slot]] if bid in faces['blocks'] else 'x%d' % slot
            quads.append((idx, verts, uv, tex))
            total += 1
        junk += check_block(bid, quads)
    print('检查 %d 个方块 / %d 个面，问题 %d 处' % (len(bmap), total, len(junk)))
    for j_ in junk[:25]:
        print('  ', j_[0], j_[1], j_[2])

if __name__ == '__main__':
    main()
