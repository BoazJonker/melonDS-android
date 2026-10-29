"""Usage: python generate_button_drawables.py <res/drawable output dir> <svg preview output dir>

Generates the Spirit Tracks styled on-screen button drawables (Android vectors) plus SVG previews.

Buttons are 3D: a wooden socket with the gold side of the cap showing (the "base" drawables), and a raised cap on top (the button drawables themselves).
When a button is pressed, the app moves the cap down into the socket. Everything is drawn from shapes, without game assets."""
import math, os, sys
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen

OUT_RES, OUT_PREVIEW = sys.argv[1], sys.argv[2]
# Almendra (SIL Open Font License, see fonts/OFL.txt): a storybook style font, also used by the app's start screen
FONT = TTFont(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'fonts', 'Almendra-Bold.ttf'))
GLYPHS, CMAP = FONT.getGlyphSet(), FONT.getBestCmap()
CAP = getattr(FONT['OS/2'], 'sCapHeight', 0) or 666

INK = '#FF3A2614'
SHADOW = '#4D000000'
SOCKET_HOLE = '#FF1E1008'
EMBOSS = '#99FFFFFF'
GLOSS_TOP, GLOSS_BOTTOM = '#BFFFFFFF', '#00FFFFFF'


def lin(x1, y1, x2, y2, *stops):
    return ('lin', x1, y1, x2, y2, stops)


def rad(cx, cy, r, *stops):
    return ('rad', cx, cy, r, stops)


def wood_fill(cx, cy, r):
    return rad(cx - r * 0.3, cy - r * 0.4, r * 1.7, (0, '#FF7A4E28'), (0.55, '#FF3B2412'), (1, '#FF1E1008'))


def side_fill(top, bottom):
    return lin(0, top, 0, bottom, (0, '#FFB8862A'), (1, '#FF5A3A10'))


def gold_fill(top, bottom):
    return lin(0, top, 0, bottom, (0, '#FFFBE7A1'), (0.45, '#FFE2B54A'), (1, '#FF9C6E1E'))


def face_fill(cx, cy, r):
    return rad(cx - r * 0.3, cy - r * 0.38, r * 1.35, (0, '#FFFFFBEE'), (0.55, '#FFF3E6C4'), (1, '#FFCDB27A'))


def gloss_fill(top, bottom):
    return lin(0, top, 0, bottom, (0, GLOSS_TOP), (1, GLOSS_BOTTOM))


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


def ellipse(cx, cy, rx, ry):
    return f'M{cx - rx:.2f},{cy:.2f}a{rx:.2f},{ry:.2f} 0 1,0 {2 * rx:.2f},0a{rx:.2f},{ry:.2f} 0 1,0 {-2 * rx:.2f},0z'


def rrect(x, y, w, h, r):
    return (f'M{x + r:.2f},{y:.2f}h{w - 2 * r:.2f}a{r:.2f},{r:.2f} 0 0 1 {r:.2f},{r:.2f}v{h - 2 * r:.2f}'
            f'a{r:.2f},{r:.2f} 0 0 1 {-r:.2f},{r:.2f}h{-(w - 2 * r):.2f}a{r:.2f},{r:.2f} 0 0 1 {-r:.2f},{-r:.2f}'
            f'v{-(h - 2 * r):.2f}a{r:.2f},{r:.2f} 0 0 1 {r:.2f},{-r:.2f}z')


def cross(w, e, cx=50, cy=50):
    pts = [(cx - w, cy - e), (cx + w, cy - e), (cx + w, cy - w), (cx + e, cy - w), (cx + e, cy + w), (cx + w, cy + w), (cx + w, cy + e),
           (cx - w, cy + e), (cx - w, cy + w), (cx - e, cy + w), (cx - e, cy - w), (cx - w, cy - w)]
    return 'M' + 'L'.join(f'{x:.2f},{y:.2f}' for x, y in pts) + 'Z'


# --- Round buttons: socket at (50, 54) r46, cap at (50, 48) r38.5. Pressing moves the cap down by DISK_PRESS_DEPTH ---
DISK_CAP_Y = 48


def disk_base():
    return [
        (ellipse(50, 60, 48, 40), SHADOW),
        (circle(50, 54, 46), wood_fill(50, 54, 46)),
        (circle(50, 54, 39), SOCKET_HOLE),
        (circle(50, 53, 38.5), side_fill(15, 92)),
    ]


def disk_cap(content):
    cy = DISK_CAP_Y
    return [
        (circle(50, cy, 38.5), gold_fill(cy - 38.5, cy + 38.5)),
        (circle(50, cy, 33), face_fill(50, cy, 33)),
        (ellipse(50, cy - 16, 22, 11), gloss_fill(cy - 27, cy - 5)),
    ] + content


def letter(label, size=30):
    return [(text_path(label, 50, DISK_CAP_Y + 1.6, size), EMBOSS), (text_path(label, 50, DISK_CAP_Y, size), INK)]


def sword_shapes(cx, cy, scale, stroke=3.2):
    return [('transform', cx, cy, 45, scale, [
        ('M0,-30l6.5,8.5v27h-13v-27z', '#FFF7F9FB', INK, stroke),
        ('M0,-26v30', None, '#FFB8C4D0', 2),
        (rrect(-14, 5, 28, 7, 3), '#FF5D6F86', INK, stroke),
        (rrect(-4, 12, 8, 11, 1), '#FF7A4A25', INK, stroke),
        (circle(0, 26.5, 4.5), '#FFE2B54A', INK, stroke),
    ])]


SWORD_ICON = sword_shapes(50, DISK_CAP_Y, 0.95)
SPIN_ICON = [
    ('M50,22a26,26 0 1,1 -25,19', None, INK, 5),
    ('M19,34l9,-11l6,13z', INK),
] + sword_shapes(50, DISK_CAP_Y + 2, 0.6, 4)
ROLL_ICON = [
    (circle(56, 50, 15), '#FF6FAE52', INK, 3.5),
    ('M47,44q9,-8 18,2', None, '#FF2F5D3A', 3),
    ('M45,56q10,6 20,-3', None, '#FF2F5D3A', 3),
    ('M22,40h16M18,50h18M22,60h16', None, INK, 4),
    ('M74,40a18,18 0 0,1 0,20', None, INK, 3.5),
    ('M70,62l8,1l-2,-8z', INK),
]
ITEM_ICON = [
    ('M36,44q-6,24 14,26q20,-2 14,-26z', '#FFA86B3C', INK, 3.5),
    ('M38,44h24', None, INK, 3.5),
    ('M42,44q-3,-10 8,-12q11,2 8,12', '#FFC98A52', INK, 3.5),
    ('M50,52l2.5,5.5 6,0.6 -4.5,4 1.3,6 -5.3,-3.1 -5.3,3.1 1.3,-6 -4.5,-4 6,-0.6z', '#FFF3D27A', INK, 1.5),
]

SWAP_ICON = [
    # A big screen and a small screen, with arrows showing them trading places
    (rrect(27, 36, 30, 22, 3), '#FFF6EDD3', INK, 3),
    (rrect(46, 26, 22, 16, 2.5), '#FF6FAE52', INK, 3),
    ('M30,32q2,-9 12,-10', None, INK, 3.5),
    ('M40,18l5,4l-5,4z', INK),
    ('M70,48q-1,10 -11,13', None, INK, 3.5),
    ('M61,57l-6,4l5,4z', INK),
]

BLOW_ICON = [
    # Gusts of wind
    ('M24,38h26q10,0 10,-8q0,-7 -7,-7q-6,0 -6,6', None, INK, 4),
    ('M20,50h44q11,0 11,9q0,8 -8,8q-7,0 -7,-6', None, INK, 4),
    ('M28,62h14', None, INK, 4),
    ('M64,40h10', None, INK, 4),
]

def gear_path(cx, cy, outer, inner, teeth=8):
    points = []
    for i in range(teeth * 4):
        angle = math.pi * 2 * i / (teeth * 4) - math.pi / 2
        r = outer if i % 4 in (1, 2) else inner
        points.append(f'{cx + math.cos(angle) * r:.2f},{cy + math.sin(angle) * r:.2f}')
    return 'M' + 'L'.join(points) + 'Z'


SETTINGS_ICON = [
    (gear_path(50, DISK_CAP_Y, 21, 15.5), INK),
    (circle(50, DISK_CAP_Y, 7), '#FFF3E6C4', INK, 3),
]
FOLDER_ICON = [
    ('M28,37h14l4,5h23a3,3 0 0 1 3,3v17a3,3 0 0 1 -3,3h-38a3,3 0 0 1 -3,-3z', '#FFE2B54A', INK, 3.2),
    ('M28,47h44', None, INK, 2.6),
]

# --- Plank buttons (L/R/Start/Select): socket at y 28..70, cap at y 28..61. Pressing moves the cap down by PLANK_PRESS_DEPTH ---
PLANK_TOP = 28


def plank_base():
    t = PLANK_TOP
    return [
        (rrect(2, t + 7, 96, 38, 13), SHADOW),
        (rrect(1, t, 98, 42, 14), wood_fill(50, t + 21, 50)),
        (rrect(5, t + 4, 90, 34, 11), SOCKET_HOLE),
        (rrect(5, t + 4, 90, 33, 11), side_fill(t + 4, t + 37)),
    ]


def plank_cap(label, size, max_w):
    t = PLANK_TOP
    return [
        (rrect(5, t, 90, 33, 11), gold_fill(t, t + 33)),
        (rrect(8, t + 3, 84, 27, 9), face_fill(50, t + 16, 45)),
        (rrect(14, t + 4, 72, 10, 4), gloss_fill(t + 4, t + 14)),
        (text_path(label, 50, t + 16.5 + 1.2, size, max_w), EMBOSS),
        (text_path(label, 50, t + 16.5, size, max_w), INK),
    ]


# --- Static images (D-pad, and the A/B/X/Y image used by the layout editor) ---
def dpad():
    shapes = [
        (cross(17, 46, 50, 54), SHADOW),
        (cross(17.5, 47.5, 50, 51.5), wood_fill(50, 50, 50)),
        (cross(14.5, 44.5, 50, 52.5), side_fill(8, 97)),
        (cross(14.5, 44.5, 50, 49), gold_fill(4.5, 93.5)),
        (cross(12.5, 42.5, 50, 49), face_fill(50, 49, 50)),
        (rrect(39, 8.5, 22, 16, 3), gloss_fill(8.5, 24.5)),
    ]
    arrows = ['M50,9L58,21L42,21Z', 'M50,89L58,77L42,77Z', 'M10,49L22,41L22,57Z', 'M90,49L78,41L78,57Z']
    shapes += [(a, INK) for a in arrows]
    shapes += [(circle(50, 49, 7), wood_fill(50, 49, 7)), (circle(50, 49, 5.2), gold_fill(43.8, 54.2)), (ellipse(50, 46.6, 3, 1.5), GLOSS_TOP)]
    return shapes


def face_buttons_image():
    shapes = []
    for label, cx, cy in (('X', 50, 17.5), ('A', 82.5, 50), ('B', 50, 82.5), ('Y', 17.5, 50)):
        # Scale the 100x100 round button down to a radius of 16 around (cx, cy)
        shapes.append(('transform', cx - 50 * 0.348, cy - 50 * 0.348, 0, 0.348, disk_base() + disk_cap(letter(label)), 'translate'))
    return shapes


DRAWABLES = {
    # Static images
    'buttons': face_buttons_image(),
    'keypad': dpad(),
    # Bases (sockets)
    'st_disk_base': disk_base(),
    'st_plank_base': plank_base(),
    # Caps
    'button_l': plank_cap('L', 22, 60),
    'button_r': plank_cap('R', 22, 60),
    'button_start': plank_cap('START', 11, 64),
    'button_select': plank_cap('SELECT', 11, 64),
    'st_face_a': disk_cap(letter('A')),
    'st_face_b': disk_cap(letter('B')),
    'st_face_x': disk_cap(letter('X')),
    'st_face_y': disk_cap(letter('Y')),
    'st_action_sword': disk_cap(SWORD_ICON),
    'st_action_spin': disk_cap(SPIN_ICON),
    'st_action_roll': disk_cap(ROLL_ICON),
    'st_action_item': disk_cap(ITEM_ICON),
    'st_swap_screens': disk_cap(SWAP_ICON),
    'st_blow': disk_cap(BLOW_ICON),
    'st_home_settings': disk_cap(SETTINGS_ICON),
    'st_home_folder': disk_cap(FOLDER_ICON),
    # Full round button (socket + cap), used as the backing of the small utility buttons
    'st_button_round': disk_base() + disk_cap([]),
}


# --- Output ---
def argb_to_svg(c):
    if len(c) == 9:
        return f'#{c[3:]}', int(c[1:3], 16) / 255
    return c, 1.0


def vector_gradient(fill, indent):
    items = ''.join(f'{indent}            <item android:offset="{o}" android:color="{c}" />\n' for o, c in fill[-1])
    if fill[0] == 'lin':
        _, x1, y1, x2, y2, _stops = fill
        attrs = f'android:type="linear" android:startX="{x1}" android:startY="{y1}" android:endX="{x2}" android:endY="{y2}"'
    else:
        _, cx, cy, r, _stops = fill
        attrs = f'android:type="radial" android:centerX="{cx:.2f}" android:centerY="{cy:.2f}" android:gradientRadius="{r:.2f}"'
    return (f'{indent}    <aapt:attr name="android:fillColor">\n{indent}        <gradient {attrs}>\n{items}'
            f'{indent}        </gradient>\n{indent}    </aapt:attr>\n')


def vector_shapes(shapes, indent='    '):
    out = []
    for shape in shapes:
        if shape[0] == 'transform':
            _, tx, ty, rotation, scale, inner = shape[:6]
            body = vector_shapes(inner, indent + '    ')
            if len(shape) > 6:
                # Plain translate + scale from the origin
                attrs = f'android:translateX="{tx:.2f}"\n{indent}    android:translateY="{ty:.2f}"\n{indent}    android:scaleX="{scale}"\n{indent}    android:scaleY="{scale}"'
            else:
                attrs = (f'android:translateX="{tx:.2f}"\n{indent}    android:translateY="{ty:.2f}"\n{indent}    android:rotation="{rotation}"\n'
                         f'{indent}    android:scaleX="{scale}"\n{indent}    android:scaleY="{scale}"')
            out.append(f'{indent}<group\n{indent}    {attrs}>\n{body}\n{indent}</group>')
            continue
        d, fill = shape[0], shape[1]
        attrs = [f'android:pathData="{d}"']
        gradient = ''
        if isinstance(fill, str):
            attrs.insert(0, f'android:fillColor="{fill}"')
        elif fill is not None:
            gradient = vector_gradient(fill, indent)
        if len(shape) > 2:
            attrs += [f'android:strokeColor="{shape[2]}"', f'android:strokeWidth="{shape[3]}"', 'android:strokeLineCap="round"', 'android:strokeLineJoin="round"']
        attr_text = '\n'.join(f'{indent}    {a}' for a in attrs)
        out.append(f'{indent}<path\n{attr_text}>\n{gradient}{indent}</path>' if gradient else f'{indent}<path\n{attr_text} />')
    return '\n'.join(out)


def to_vector(shapes):
    return ('<?xml version="1.0" encoding="utf-8"?>\n<!-- Spirit Tracks styled on-screen button. Generated by tools/generate_button_drawables.py, no game assets -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    xmlns:aapt="http://schemas.android.com/aapt"\n'
            '    android:width="100dp"\n    android:height="100dp"\n'
            f'    android:viewportWidth="100"\n    android:viewportHeight="100">\n{vector_shapes(shapes)}\n</vector>\n')


def svg_shapes(shapes, defs):
    out = ''
    for shape in shapes:
        if shape[0] == 'transform':
            _, tx, ty, rotation, scale, inner = shape[:6]
            # Android applies scale, then rotation, then translation
            out += f'<g transform="translate({tx} {ty}) rotate({rotation}) scale({scale})">{svg_shapes(inner, defs)}</g>'
            continue
        fill = shape[1]
        if fill is None:
            fill_attr = 'fill="none"'
        elif isinstance(fill, str):
            col, a = argb_to_svg(fill)
            fill_attr = f'fill="{col}" fill-opacity="{a:.3f}"'
        else:
            gid = f'g{len(defs)}'
            stops = ''.join(f'<stop offset="{o}" stop-color="{argb_to_svg(c)[0]}" stop-opacity="{argb_to_svg(c)[1]:.3f}"/>' for o, c in fill[-1])
            if fill[0] == 'lin':
                defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{fill[1]}" y1="{fill[2]}" x2="{fill[3]}" y2="{fill[4]}">{stops}</linearGradient>')
            else:
                defs.append(f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" cx="{fill[1]:.2f}" cy="{fill[2]:.2f}" r="{fill[3]:.2f}">{stops}</radialGradient>')
            fill_attr = f'fill="url(#{gid})"'
        stroke = ''
        if len(shape) > 2:
            col, a = argb_to_svg(shape[2])
            stroke = f' stroke="{col}" stroke-opacity="{a:.3f}" stroke-width="{shape[3]}" stroke-linecap="round" stroke-linejoin="round"'
        out += f'<path {fill_attr}{stroke} d="{shape[0]}"/>'
    return out


def to_svg(shapes):
    defs = []
    body = svg_shapes(shapes, defs)
    return '<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100" viewBox="0 0 100 100"><defs>' + ''.join(defs) + '</defs>' + body + '</svg>'


for name, shapes in DRAWABLES.items():
    open(os.path.join(OUT_RES, f'{name}.xml'), 'w').write(to_vector(shapes))
    open(os.path.join(OUT_PREVIEW, f'{name}.svg'), 'w').write(to_svg(shapes))
print('generated', ', '.join(DRAWABLES))
