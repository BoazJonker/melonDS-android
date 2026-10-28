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
}

def to_vector(name, shapes):
    paths = '\n'.join(f'    <path\n        android:fillColor="{c}"\n        android:pathData="{d}" />' for d, c in shapes)
    return (f'<?xml version="1.0" encoding="utf-8"?>\n<!-- Spirit Tracks styled on-screen button. Generated from shapes, no game assets -->\n'
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="100dp"\n    android:height="100dp"\n'
            f'    android:viewportWidth="100"\n    android:viewportHeight="100">\n{paths}\n</vector>\n')

def to_svg(shapes):
    return ('<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100" viewBox="0 0 100 100">'
            + ''.join(f'<path fill="{c}" d="{d}"/>' for d, c in shapes) + '</svg>')

for name, shapes in DRAWABLES.items():
    open(os.path.join(OUT_RES, f'{name}.xml'), 'w').write(to_vector(name, shapes))
    open(os.path.join(OUT_PREVIEW, f'{name}.svg'), 'w').write(to_svg(shapes))
print('generated', ', '.join(DRAWABLES))
