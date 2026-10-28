"""Usage: python generate_button_drawables.py <res/drawable output dir> <svg preview output dir>

Generates the Spirit Tracks styled on-screen button drawables (Android vectors) plus SVG previews."""
import os, sys
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen

OUT_RES, OUT_PREVIEW = sys.argv[1], sys.argv[2]
FONT = TTFont('/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf')
GLYPHS, CMAP = FONT.getGlyphSet(), FONT.getBestCmap()
CAP = getattr(FONT['OS/2'], 'sCapHeight', 0) or 1493

WOOD, GOLD, FACE, FACE_SHADE, INK = '#3B2412', '#E2B54A', '#F6EDD3', '#E6D3A3', '#3A2614'

def text_path(text, cx, cy, cap_px, max_w=None):
    names = [CMAP[ord(c)] for c in text]
    advance = sum(FONT['hmtx'][n][0] for n in names)
    s = cap_px / CAP
    if max_w and advance * s > max_w:
        s = max_w / advance
    x = cx - advance * s / 2
    base = cy + CAP * s / 2
    pen = SVGPathPen(GLYPHS)
    for n in names:
        GLYPHS[n].draw(TransformPen(pen, (s, 0, 0, -s, x, base)))
        x += FONT['hmtx'][n][0] * s
    return pen.getCommands()

def circle(cx, cy, r):
    return f'M{cx - r:.2f},{cy:.2f}a{r:.2f},{r:.2f} 0 1,0 {2 * r:.2f},0a{r:.2f},{r:.2f} 0 1,0 {-2 * r:.2f},0z'

def rrect(x, y, w, h, r):
    return (f'M{x + r:.2f},{y:.2f}h{w - 2 * r:.2f}a{r:.2f},{r:.2f} 0 0 1 {r:.2f},{r:.2f}v{h - 2 * r:.2f}'
            f'a{r:.2f},{r:.2f} 0 0 1 {-r:.2f},{r:.2f}h{-(w - 2 * r):.2f}a{r:.2f},{r:.2f} 0 0 1 {-r:.2f},{-r:.2f}'
            f'v{-(h - 2 * r):.2f}a{r:.2f},{r:.2f} 0 0 1 {r:.2f},{-r:.2f}z')

def cross(w, e, c=50):
    pts = [(c - w, c - e), (c + w, c - e), (c + w, c - w), (c + e, c - w), (c + e, c + w), (c + w, c + w), (c + w, c + e),
           (c - w, c + e), (c - w, c + w), (c - e, c + w), (c - e, c - w), (c - w, c - w)]
    return 'M' + 'L'.join(f'{x:.2f},{y:.2f}' for x, y in pts) + 'Z'

def disk_button(cx, cy, r, label, cap):
    return [
        (circle(cx, cy, r), WOOD),
        (circle(cx, cy, r - 1.8), GOLD),
        (circle(cx, cy, r - 3.4), FACE_SHADE),
        (circle(cx, cy - 1.0, r - 4.4), FACE),
        (text_path(label, cx, cy, cap), INK),
    ]

def plank(x, y, w, h, r, label, cap, max_w):
    return [
        (rrect(x, y, w, h, r), WOOD),
        (rrect(x + 2, y + 2, w - 4, h - 4, r - 2), GOLD),
        (rrect(x + 3.6, y + 3.6, w - 7.2, h - 7.2, r - 3.6), FACE_SHADE),
        (rrect(x + 4.6, y + 4.2, w - 9.2, h - 9.8, r - 4.6), FACE),
        (text_path(label, x + w / 2, y + h / 2, cap, max_w), INK),
    ]

def dpad():
    shapes = [(cross(17, 48), WOOD), (cross(15, 46), GOLD), (cross(13.4, 44.4), FACE_SHADE), (cross(12.6, 43.4), FACE)]
    # Arrows
    shapes += [
        ('M50,10L58,22L42,22Z', INK), ('M50,90L58,78L42,78Z', INK),
        ('M10,50L22,42L22,58Z', INK), ('M90,50L78,42L78,58Z', INK),
        (circle(50, 50, 7), WOOD), (circle(50, 50, 5.2), GOLD),
    ]
    return shapes

def action_disk():
    return [
        (circle(50, 50, 48), WOOD),
        (circle(50, 50, 44), GOLD),
        (circle(50, 50, 40), FACE_SHADE),
        (circle(50, 48, 38), FACE),
    ]

SWORD_ICON = [('rotate', 45, [
    ('M50,18l7,9v30h-14v-30z', '#F7F9FB', INK, 3),
    (rrect(36, 57, 28, 7, 3), '#5D6F86', INK, 3),
    (rrect(46, 64, 8, 12, 1), '#7A4A25', INK, 3),
    (circle(50, 80, 5), GOLD, INK, 3),
])]
SPIN_ICON = [('M50,24a26,26 0 1,1 -24,16', None, INK, 6), ('M18,36l10,-12l6,14z', INK)]
ROLL_ICON = [(circle(46, 52, 15), None, INK, 6), ('M22,70h8M36,70h8M50,70h8M64,70h4', None, INK, 5), ('M66,30l12,8l-12,8z', INK)]
ITEM_ICON = [('M30,64q20,-40 40,0', None, INK, 7), ('M34,62q16,-28 32,0', None, GOLD, 3), (circle(50, 72, 4), INK)]

DRAWABLES = {
    'buttons': [*disk_button(50, 17.5, 16, 'X', 13), *disk_button(82.5, 50, 16, 'A', 13),
                *disk_button(50, 82.5, 16, 'B', 13), *disk_button(17.5, 50, 16, 'Y', 13)],
    'keypad': dpad(),
    'button_l': plank(3, 18, 94, 64, 14, 'L', 30, 60),
    'button_r': plank(3, 18, 94, 64, 14, 'R', 30, 60),
    'button_start': plank(3, 30, 94, 40, 18, 'START', 13, 70),
    'button_select': plank(3, 30, 94, 40, 18, 'SELECT', 13, 70),
    # Individual face buttons, used by the animated A/B/X/Y view so each button can be pressed separately
    'st_face_a': disk_button(50, 50, 46, 'A', 38),
    'st_face_b': disk_button(50, 50, 46, 'B', 38),
    'st_face_x': disk_button(50, 50, 46, 'X', 38),
    'st_face_y': disk_button(50, 50, 46, 'Y', 38),
    # Action buttons for the stick control style
    'st_action_sword': action_disk() + SWORD_ICON,
    'st_action_spin': action_disk() + SPIN_ICON,
    'st_action_roll': action_disk() + ROLL_ICON,
    'st_action_item': action_disk() + ITEM_ICON,
}

def vector_shapes(shapes, indent='    '):
    out = []
    for shape in shapes:
        if shape[0] == 'rotate':
            inner = vector_shapes(shape[2], indent + '    ')
            out.append(f'{indent}<group\n{indent}    android:pivotX="50"\n{indent}    android:pivotY="50"\n{indent}    android:rotation="{shape[1]}">\n{inner}\n{indent}</group>')
            continue
        d, fill = shape[0], shape[1]
        attrs = [f'android:pathData="{d}"']
        if fill:
            attrs.insert(0, f'android:fillColor="{fill}"')
        if len(shape) > 2:
            attrs += [f'android:strokeColor="{shape[2]}"', f'android:strokeWidth="{shape[3]}"', 'android:strokeLineCap="round"', 'android:strokeLineJoin="round"']
        out.append(f'{indent}<path\n' + '\n'.join(f'{indent}    {a}' for a in attrs) + ' />')
    return '\n'.join(out)

def to_vector(name, shapes):
    return (f'<?xml version="1.0" encoding="utf-8"?>\n<!-- Spirit Tracks styled on-screen button. Generated from shapes, no game assets -->\n'
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="100dp"\n    android:height="100dp"\n'
            f'    android:viewportWidth="100"\n    android:viewportHeight="100">\n{vector_shapes(shapes)}\n</vector>\n')

def svg_shapes(shapes):
    out = ''
    for shape in shapes:
        if shape[0] == 'rotate':
            out += f'<g transform="rotate({shape[1]} 50 50)">{svg_shapes(shape[2])}</g>'
            continue
        fill = shape[1] or 'none'
        stroke = f' stroke="{shape[2]}" stroke-width="{shape[3]}" stroke-linecap="round" stroke-linejoin="round"' if len(shape) > 2 else ''
        out += f'<path fill="{fill}"{stroke} d="{shape[0]}"/>'
    return out

def to_svg(shapes):
    return '<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100" viewBox="0 0 100 100">' + svg_shapes(shapes) + '</svg>'

for name, shapes in DRAWABLES.items():
    open(os.path.join(OUT_RES, f'{name}.xml'), 'w').write(to_vector(name, shapes))
    open(os.path.join(OUT_PREVIEW, f'{name}.svg'), 'w').write(to_svg(shapes))
print('generated', ', '.join(DRAWABLES))
