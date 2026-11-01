from artlib import *
from portraits import reese, eli

def mir(p):  # mirror a patch horizontally
    return [r[::-1] for r in p]

# eye patches are drawn for the LEFT eye at (10,y) and mirrored to (18,y)
EYES = {
 'open':   ["3333","3203","3223",".33."],
 'closed': ["    ","    ","3333",".  ."],
 'happy':  ["    ",".33.","3  3","    "],
 'wide':   [".33.","3003","3033",".33."],
 'half':   ["    ","3333","3223",".33."],
 'down':   ["    ","3333","3223",".33."],
}
ELI_EYES = {
 'open':   ["    ","3333","3203","3223"],
 'closed': ["    ","    ","3333","    "],
 'happy':  ["    ",".33.","3  3","    "],
 'wide':   ["    ","3333","3023","3333"],
}
BROWS = { # left brow, 4 wide, 2 rows, at (10,15)
 'flat': ["    ","3333"],
 'up':   ["3333","    "],
 'sad':  ["  33","33  "],
 'mad':  ["33  ","  33"],
}
MOUTHS = { # 4x3 at (14,24)
 'neutral': ["    "," 33 ","    "],
 'smile':   ["3  3"," 33 ","    "],
 'grin':    ["3333","3113"," 33 "],
 'o':       [" 33 ","3003"," 33 "],
 'frown':   ["    "," 33 ","3  3"],
 'flat':    ["    ","3333","    "],
 'wavy':    ["    ","3 3 "," 3 3"],
 'set':     ["    "," 333","    "],
}
BLUSH = [" 1 1","1 1 "]          # at (9,22) and mirrored at (19,22)
SWEAT = ["  0 "," 000"," 000","  0 "] # at (24,13) (right side)

def face(base, eyes, brows, mouth, blush=False, sweat=False, eyeset=EYES, ey=17):
    r = put(base, 10, ey, eyeset[eyes]); r = put(r, 18, ey, mir(eyeset[eyes]))
    r = put(r, 10, 15, BROWS[brows]); r = put(r, 18, 15, mir(BROWS[brows]))
    r = put(r, 14, 24, MOUTHS[mouth])
    if blush:
        r = put(r, 8, 22, BLUSH); r = put(r, 20, 22, mir(BLUSH))
    if sweat:
        r = put(r, 23, 12, SWEAT)
    return r

REESE_EXPR = {
 'neutral':  dict(eyes='open',  brows='flat', mouth='neutral'),
 'happy':    dict(eyes='happy', brows='up',   mouth='grin'),
 'blush':    dict(eyes='down',  brows='sad',  mouth='smile', blush=True),
 'surprised':dict(eyes='wide',  brows='up',   mouth='o'),
 'sad':      dict(eyes='down',  brows='sad',  mouth='frown'),
 'annoyed':  dict(eyes='half',  brows='mad',  mouth='flat'),
}
ELI_EXPR = {
 'neutral':    dict(eyes='open',  brows='flat', mouth='neutral'),
 'happy':      dict(eyes='happy', brows='up',   mouth='grin'),
 'nervous':    dict(eyes='wide',  brows='sad',  mouth='wavy', sweat=True),
 'determined': dict(eyes='open',  brows='mad',  mouth='set'),
}

def build():
    out={}
    for k,v in REESE_EXPR.items():
        out['reese_'+k]=face(reese, **v)
        out['reese_'+k+'_blink']=face(reese, **{**v,'eyes':'closed'})
    for k,v in ELI_EXPR.items():
        out['eli_'+k]=face(eli, eyeset=ELI_EYES, **v)
        out['eli_'+k+'_blink']=face(eli, eyeset=ELI_EYES, **{**v,'eyes':'closed'})
    return out

if __name__=='__main__':
    b=build()
    names=[n for n in b if not n.endswith('_blink')]+['reese_neutral_blink']
    im=Image.new('RGB',(len(names)*(32*3+6),48*3),(120,120,140))
    for i,n in enumerate(names):
        im.paste(to_img(b[n],3),(i*(32*3+6),0))
    im.save(__import__('os').path.join(__import__('os').path.dirname(__file__),'preview_e.png'))
