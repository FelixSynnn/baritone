"""Tiny helpers for authoring 4-shade pixel art as text.
'.' = transparent, '0'..'3' = Game Boy shades (0 lightest, 3 darkest)."""
from PIL import Image

PAL = [(155,188,15),(139,172,15),(48,98,48),(15,56,15)]

def mirror(half_rows, width):
    rows=[]
    for r in half_rows:
        assert len(r)==width//2, (len(r), r)
        rows.append(r + r[::-1])
    return rows

def check(name, rows, w, h):
    assert len(rows)==h, (name, len(rows), h)
    for i,r in enumerate(rows):
        assert len(r)==w, (name, i, len(r), r)

def put(rows, x, y, patch):
    rows=[list(r) for r in rows]
    for j,pr in enumerate(patch):
        for i,c in enumerate(pr):
            if c!=' ':
                rows[y+j][x+i]=c
    return [''.join(r) for r in rows]

def to_img(rows, scale=4, bg=(120,120,140)):
    h=len(rows); w=len(rows[0])
    im=Image.new('RGB',(w*scale,h*scale),bg)
    px=im.load()
    for y,r in enumerate(rows):
        for x,c in enumerate(r):
            if c in '0123':
                col=PAL[int(c)]
                for dy in range(scale):
                    for dx in range(scale):
                        px[x*scale+dx,y*scale+dy]=col
    return im
