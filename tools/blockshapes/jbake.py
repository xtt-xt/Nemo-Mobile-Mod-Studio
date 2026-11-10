#!/usr/bin/env python3
"""把官方 Java 版的方块模型摊平成「显式四边形」,供 app 端直接画。"""
import zipfile, json, re

Z = zipfile.ZipFile('/tmp/client263.jar')
_cache = {}

def raw(path):
    if path in _cache: return _cache[path]
    try:
        v = json.loads(Z.read('assets/minecraft/' + path))
    except KeyError:
        v = None
    _cache[path] = v
    return v

def model(name):
    name = name.replace('minecraft:', '')
    if not name.startswith('block/'):
        name = 'block/' + name
    return raw('models/' + name + '.json')

def flatten(name, depth=0):
    if depth > 12: return [], {}
    m = model(name)
    if m is None: return None, None
    tex = dict(m.get('textures') or {})
    if 'elements' in m:
        return m['elements'], tex
    p = m.get('parent')
    if not p: return [], tex
    els, ptex = flatten(p, depth + 1)
    if ptex is None: return [], tex
    merged = dict(ptex); merged.update(tex)
    return els, merged

def blockstate(name):
    return raw('blockstates/' + name + '.json')

DIRS = ['down', 'up', 'north', 'south', 'west', 'east']
# 输出到 app 的方向下标: BlockModel.FACES = up,down,north,south,east,west
OUT_IDX = {'up': 0, 'down': 1, 'north': 2, 'south': 3, 'east': 4, 'west': 5}
NORMAL = {'down': (0,-1,0), 'up': (0,1,0), 'north': (0,0,-1),
          'south': (0,0,1), 'west': (-1,0,0), 'east': (1,0,0)}

def corner_pos(e, d):
    x1, y1, z1 = e['from']; x2, y2, z2 = e['to']
    if d == 'down':  return [(x1,y1,z1),(x1,y1,z2),(x2,y1,z2),(x2,y1,z1)]
    if d == 'up':    return [(x1,y2,z1),(x2,y2,z1),(x2,y2,z2),(x1,y2,z2)]
    if d == 'north': return [(x1,y1,z1),(x2,y1,z1),(x2,y2,z1),(x1,y2,z1)]
    if d == 'south': return [(x2,y1,z2),(x1,y1,z2),(x1,y2,z2),(x2,y2,z2)]
    if d == 'west':  return [(x1,y1,z1),(x1,y1,z2),(x1,y2,z2),(x1,y2,z1)]
    return [(x2,y1,z2),(x2,y1,z1),(x2,y2,z1),(x2,y2,z2)]  # east

def uv_slots(d):
    if d == 'down':  return [(0,0),(0,1),(1,1),(1,0)]
    if d == 'up':    return [(0,0),(1,0),(1,1),(0,1)]
    if d == 'north': return [(0,1),(1,1),(1,0),(0,0)]
    if d == 'south': return [(0,1),(1,1),(1,0),(0,0)]
    if d == 'west':  return [(0,1),(1,1),(1,0),(0,0)]
    return [(0,1),(1,1),(1,0),(0,0)]

def default_uv(e, d):
    x1, y1, z1 = e['from']; x2, y2, z2 = e['to']
    if d == 'down':  return [x1, 16-z2, x2, 16-z1]
    if d == 'up':    return [x1, z1, x2, z2]
    if d == 'north': return [16-x2, 16-y2, 16-x1, 16-y1]
    if d == 'south': return [x1, 16-y2, x2, 16-y1]
    if d == 'west':  return [z1, 16-y2, z2, 16-y1]
    return [16-z2, 16-y2, 16-z1, 16-y1]

def apply_face_rotation(uv8, deg):
    if deg % 90: return uv8
    k = (deg // 90) % 4
    pts = [(uv8[i*2], uv8[i*2+1]) for i in range(4)]
    for _ in range(k):
        pts = [pts[1], pts[2], pts[3], pts[0]]
    out = []
    for p in pts: out += [p[0], p[1]]
    return out

def rotate_point(p, origin, axis, deg):
    import math
    a = math.radians(deg); c, s = math.cos(a), math.sin(a)
    x, y, z = p[0]-origin[0], p[1]-origin[1], p[2]-origin[2]
    if axis == 'x': y, z = y*c - z*s, y*s + z*c
    elif axis == 'y': x, z = x*c + z*s, -x*s + z*c
    else: x, y = x*c - y*s, x*s + y*c
    return (x+origin[0], y+origin[1], z+origin[2])

def fix_winding(verts, uvs, d):
    """保证 4 个顶点从外侧看是逆时针(法线朝外)"""
    ax, ay, az = NORMAL[d]
    e1 = tuple(verts[1][i]-verts[0][i] for i in range(3))
    e2 = tuple(verts[2][i]-verts[0][i] for i in range(3))
    nx = e1[1]*e2[2]-e1[2]*e2[1]
    ny = e1[2]*e2[0]-e1[0]*e2[2]
    nz = e1[0]*e2[1]-e1[1]*e2[0]
    if nx*ax + ny*ay + nz*az >= 0:
        return verts, uvs
    v = [verts[0], verts[3], verts[2], verts[1]]
    u = [uvs[0], uvs[3], uvs[2], uvs[1]]
    return v, u

def bake_element(e):
    rot = e.get('rotation')
    faces = e.get('faces') or {}
    out = []
    for d in DIRS:
        if d not in faces: continue
        f = faces[d]
        verts = corner_pos(e, d)
        uv = f.get('uv') or default_uv(e, d)
        pairs = []
        for (su, sv) in uv_slots(d):
            pairs += [round(uv[0] + (uv[2]-uv[0])*su, 4),
                      round(uv[1] + (uv[3]-uv[1])*sv, 4)]
        pairs = apply_face_rotation(pairs, f.get('rotation', 0))
        if rot and rot.get('angle'):
            verts = [rotate_point(p, rot['origin'], rot['axis'], rot['angle']) for p in verts]
            if rot.get('rescale'):
                verts = [tuple(max(0.0, min(16.0, c)) for c in p) for p in verts]
        verts = [tuple(round(c, 4) for c in p) for p in verts]
        uvq = [(pairs[i*2], pairs[i*2+1]) for i in range(4)]
        verts, uvq = fix_winding(verts, uvq, d)
        flat = []
        for p in uvq: flat += [p[0], p[1]]
        out.append((OUT_IDX[d], verts, flat, f.get('texture')))
    return out

BOOL_PREF = {'up': 'true', 'north': 'false', 'south': 'false', 'east': 'false', 'west': 'false',
             'open': 'false', 'powered': 'false', 'lit': 'false', 'unpowered': 'false',
             'in_wall': 'false', 'conditional': 'false', 'connected': 'false', 'occupied': 'false',
             'attached': 'false', 'extended': 'false', 'triggered': 'false', 'snowy': 'false',
             'waterlogged': 'false', 'hanging': 'false', 'signal_fire': 'false',
             'has_record': 'false', 'has_book': 'false', 'bloom': 'false', 'eye': 'false',
             'disarmed': 'false', 'inverted': 'false', 'drag': 'false', 'short': 'false',
             'locked': 'false', 'ominous': 'false', 'can_summon': 'false', 'shrieking': 'false',
             'crafting': 'false', 'cracked': 'false', 'upside_down_bit': 'false',
             'side_chain': 'false', 'berries': 'false', 'bottom': 'false', 'slot_0_occupied': 'false',
             'slot_1_occupied': 'false', 'slot_2_occupied': 'false', 'slot_3_occupied': 'false',
             'slot_4_occupied': 'false', 'slot_5_occupied': 'false', 'tips': 'false'}
STR_PREF = {'facing': 'north', 'half': 'bottom', 'shape': 'straight', 'axis': 'y',
            'age': '0', 'stage': '0', 'level': '0', 'moisture': '0', 'distance': '7',
            'layers': '1', 'note': '0', 'instrument': 'harp', 'rotation': '0',
            'hinge': 'left', 'part': 'foot', 'type': 'bottom', 'mode': 'compare',
            'attachment': 'floor', 'orientation': 'north_up', 'vertical_direction': 'up',
            'tilt': 'none', 'thickness': 'tip', 'cracks': 'none', 'bites': '0',
            'pickles': '1', 'eggs': '1', 'candles': '1', 'delay': '1', 'power': '0',
            'charge': '1', 'segment_amount': '1', 'signal': '0', 'hatch': '0',
            'facing_direction': '0', 'bone_block_type': '0', 'coral_direction': '0',
            'growth': '0', 'vine_direction_bits': '0', 'torch_facing_direction': 'north',
            'ground_sign_direction': '0', 'pillar_axis': 'y', 'trial_spawner_state': 'inactive',
            'sculk_sensor_phase': 'inactive', 'north': 'false', 'south': 'false',
            'east': 'false', 'west': 'false', 'up': 'true', 'down': 'false'}

def pick_value(prop, vals):
    vals = list(vals)
    if prop in BOOL_PREF:
        if BOOL_PREF[prop] in vals: return BOOL_PREF[prop]
        if 'false' in vals: return 'false'
        if 'true' in vals: return 'true'
    if prop in STR_PREF and STR_PREF[prop] in vals: return STR_PREF[prop]
    if 'true' in vals and 'false' in vals: return 'false'
    def key(s):
        try: return (0, int(s), '')
        except ValueError: return (1, 0, s)
    return sorted(vals, key=key)[0]

def when_match(when, state):
    if not when: return True
    for k, v in when.items():
        if k == 'OR':
            if not any(when_match(x, state) for x in v): return False
            continue
        if k == 'AND':
            if not all(when_match(x, state) for x in v): return False
            continue
        sv = state.get(k)
        if sv is None: return False
        opts = v.split('|') if isinstance(v, str) else list(v)
        if sv not in opts: return False
    return True

def parse_key(k):
    st = {}
    if not k: return st
    for part in k.split(','):
        if '=' in part:
            a, b = part.split('=', 1); st[a] = b
    return st

def resolve_state(bs, prefer=None):
    if 'variants' not in bs: return None
    keys = list(bs['variants'].keys())
    propvals = {}
    for k in keys:
        for p, v in parse_key(k).items():
            propvals.setdefault(p, set()).add(v)
    for p, vs in propvals.items():
        if p in BOOL_PREF: vs.add(BOOL_PREF[p])
        if p in STR_PREF: vs.add(STR_PREF[p])
    pref = {p: pick_value(p, vs) for p, vs in propvals.items()}
    if prefer:
        pref.update(prefer)
    best = None
    for k in keys:
        st = parse_key(k)
        s = sum(1 for p, v in st.items() if pref.get(p) != v)
        # 状态完全没写(单变体)也算最优
        if best is None or s < best[0]:
            best = (s, k)
    return best[1]

def resolve(name, prefer=None):
    bs = blockstate(name)
    if bs is None: return None
    out = []
    if 'variants' in bs:
        k = resolve_state(bs, prefer)
        apps = bs['variants'][k]
        if isinstance(apps, dict): apps = [apps]
        for ap in apps[:1]:
            els, tex = flatten(ap['model'])
            out.append((els or [], tex or {}, ap.get('x', 0), ap.get('y', 0)))
    else:
        props = {}
        for part in bs['multipart']:
            for p, v in (part.get('when') or {}).items():
                if p in ('OR', 'AND'): continue
                for o in (v.split('|') if isinstance(v, str) else v):
                    props.setdefault(p, set()).add(o)
        for p, vs in props.items():
            if p in BOOL_PREF: vs.add(BOOL_PREF[p])
            if p in STR_PREF: vs.add(STR_PREF[p])
        state = {p: pick_value(p, vs) for p, vs in props.items()}
        if prefer:
            state.update(prefer)
        for part in bs['multipart']:
            if not when_match(part.get('when'), state): continue
            ap = part['apply']
            if isinstance(ap, list): ap = ap[0]
            els, tex = flatten(ap['model'])
            out.append((els or [], tex or {}, ap.get('x', 0), ap.get('y', 0)))
    return out

def rot_block(verts, xr, yr):
    yr = yr % 360
    if yr == 90:
        verts = [(p[2], p[1], 16-p[0]) for p in verts]
    elif yr == 180:
        verts = [(16-p[0], p[1], 16-p[2]) for p in verts]
    elif yr == 270:
        verts = [(16-p[2], p[1], p[0]) for p in verts]
    xr = xr % 360
    if xr == 90:
        verts = [(p[0], p[2], 16-p[1]) for p in verts]
    elif xr == 180:
        verts = [(p[0], 16-p[1], 16-p[2]) for p in verts]
    elif xr == 270:
        verts = [(p[0], 16-p[2], p[1]) for p in verts]
    return verts

def resolve_tex(texdict, var):
    """#stand -> brewing_stand(去掉 block/ 前缀的短名)"""
    if not var: return None
    v = var
    seen = 0
    while isinstance(v, str) and v.startswith('#') and texdict and seen < 6:
        v = texdict.get(v[1:], v)
        seen += 1
    if not isinstance(v, str): return None
    v = v.replace('minecraft:', '')
    if v.startswith('block/'): v = v[6:]
    return v


def from_elements(els, xr=0, yr=0, texdict=None):
    quads = []
    for e in els:
        for idx, verts, uv, tex in bake_element(e):
            verts = rot_block(verts, xr, yr)
            quads.append([idx, [list(p) for p in verts], uv, resolve_tex(texdict, tex)])
    return quads

def build_block(name, prefer=None):
    parts = resolve(name, prefer)
    if not parts: return None
    quads = []
    for els, tex, xr, yr in parts:
        quads += from_elements(els, xr, yr, tex)
    return quads or None

def build_model(name):
    els, tex = flatten(name)
    if not els: return None
    return from_elements(els, 0, 0, tex) or None
