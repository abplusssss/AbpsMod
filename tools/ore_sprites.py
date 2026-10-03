"""
Draws the ruby and endite item sprites by recolouring Minecraft's own diamond and netherite shapes (VANILLA points at an
extracted copy of the vanilla assets). Used by tools/gen_ores.py.
"""
import colorsys
from PIL import Image
import os, sys
B=os.environ.get('VANILLA', 'mca') + '/assets/minecraft/textures/'
OUT=os.environ.get('SPRITES_OUT', 'sprites/')
RAMPS={
 'ruby':  [(0x33,0x03,0x0e),(0x62,0x08,0x1a),(0x96,0x0e,0x26),(0xc4,0x16,0x30),(0xe8,0x2c,0x44),(0xff,0x66,0x74),(0xff,0xb8,0xbe)],
 'endite':[(0x1c,0x06,0x2c),(0x3e,0x12,0x5e),(0x66,0x22,0x96),(0x93,0x3c,0xd0),(0xbe,0x6e,0xf2),(0xe2,0xab,0xff),(0xf8,0xe4,0xff)],
}
def is_gem(c):
    r,g,b,a=c
    if a<10: return False
    h,s,v=colorsys.rgb_to_hsv(r/255,g/255,b/255)
    return (0.40<h<0.58 and s>0.18) or (b>r+12 and g>r+12 and v>0.55)
def lum(c): return 0.299*c[0]+0.587*c[1]+0.114*c[2]
def recolor(src, ramp, dst, gem_test=is_gem):
    im=Image.open(B+src+'.png').convert('RGBA')
    px=im.load(); w,h=im.size
    cols=sorted({px[x,y] for x in range(w) for y in range(h) if gem_test(px[x,y])}, key=lum)
    ls=[lum(c) for c in cols]; lo,hi=min(ls),max(ls)
    R=RAMPS[ramp]
    for y in range(h):
        for x in range(w):
            c=px[x,y]
            if gem_test(c):
                t=((lum(c)-lo)/max(1,hi-lo))**1.35
                i=min(len(R)-1, int(round(t*(len(R)-1))))
                px[x,y]=R[i]+(c[3],)
    im.save(OUT+dst+'.png'); return im
def gray_metal(c):  # netherite pixels: dark low-saturation
    r,g,b,a=c
    return a>10
for t in ['pickaxe','axe','shovel','hoe','sword','helmet','chestplate','leggings','boots']:
    recolor('item/diamond_'+t,'ruby','ruby_'+t)
    dia=Image.open(B+'item/diamond_'+t+'.png').convert('RGBA').load()
    def endite_test(c, _d=dia):
        return c[3]>10
    # Keep the wooden handle: wherever the diamond tool has a non-gem (wood) pixel, leave netherite's own colour
    im=Image.open(B+'item/netherite_'+t+'.png').convert('RGBA'); px=im.load()
    keep={(x,y) for y in range(16) for x in range(16) if dia[x,y][3]>10 and not is_gem(dia[x,y]) and t in ('pickaxe','axe','shovel','hoe','sword')}
    cols=sorted({px[x,y] for y in range(16) for x in range(16) if px[x,y][3]>10 and (x,y) not in keep}, key=lum)
    ls=[lum(c) for c in cols]; lo,hi=min(ls),max(ls); R=RAMPS['endite']
    for y in range(16):
        for x in range(16):
            c=px[x,y]
            if c[3]>10 and (x,y) not in keep:
                tt=((lum(c)-lo)/max(1,hi-lo))**0.85
                px[x,y]=R[min(6,int(round(tt*6)))]+(c[3],)
    im.save(OUT+'endite_'+t+'.png')
recolor('block/diamond_ore','ruby','ruby_ore')
recolor('block/deepslate_diamond_ore','ruby','deepslate_ruby_ore')
recolor('item/netherite_ingot','endite','endite_ingot', gem_test=lambda c: c[3]>10)
# Endite ore: the diamond ore's gem pattern set into end stone, in endite colours
ore=Image.open(B+'block/diamond_ore.png').convert('RGBA').load()
es=Image.open(B+'block/end_stone.png').convert('RGBA')
ep=es.load()
gems=[(x,y) for y in range(16) for x in range(16) if is_gem(ore[x,y])]
ls=[lum(ore[x,y]) for x,y in gems]; lo,hi=min(ls),max(ls)
R=RAMPS['endite']
for x,y in gems:
    t=(lum(ore[x,y])-lo)/max(1,hi-lo)
    ep[x,y]=R[min(6,int(round(t*6)))]+(255,)
# a dark rim around each crystal so it reads on pale end stone
for x,y in gems:
    for dx,dy in ((1,0),(-1,0),(0,1),(0,-1)):
        nx,ny=x+dx,y+dy
        if 0<=nx<16 and 0<=ny<16 and (nx,ny) not in gems:
            c=ep[nx,ny]; ep[nx,ny]=(int(c[0]*0.62),int(c[1]*0.6),int(c[2]*0.66),255)
es.save(OUT+'endite_ore.png')
# Ruby gem: hand-drawn faceted cut
G=["................",
   "................",
   "................",
   "....AAAAAAAA....",
   "...ADWWBBDDEA...",
   "..ADBWBBDDEEFA..",
   ".ADDBBBDDEEEFFA.",
   ".AEEEEEEEFFFFFA.",
   "..AEDDDEEFFFCA..",
   "...AEDDEEFFCA...",
   "....AEDEFFCA....",
   ".....AEEFCA.....",
   "......AEFA......",
   ".......AA.......",
   "................",
   "................"]
pal={'A':RAMPS['ruby'][0],'C':RAMPS['ruby'][1],'F':RAMPS['ruby'][2],'E':RAMPS['ruby'][3],'D':RAMPS['ruby'][4],'B':RAMPS['ruby'][5],'W':RAMPS['ruby'][6]}
im=Image.new('RGBA',(16,16),(0,0,0,0))
for y,row in enumerate(G):
    for x,ch in enumerate(row):
        if ch in pal: im.putpixel((x,y),pal[ch]+(255,))
im.save(OUT+'ruby.png')
# Endite shard: a jagged crystal
S=["................",
   "..........A.....",
   ".........AWA....",
   "........ABDA....",
   ".......ABDEA....",
   "......ABDDEA....",
   "...A..ABDEFA....",
   "..AWA.ABDEFA..A.",
   "..ABDAABDEFA.AWA",
   "..ABDEABDEFAABDA",
   "...ADEFADEFAADEA",
   "...AEEFAEEFAEEFA",
   "....AFFAAFFAAFA.",
   ".....AAA.AA.AA..",
   "................",
   "................"]
pal={'A':RAMPS['endite'][0],'F':RAMPS['endite'][2],'E':RAMPS['endite'][3],'D':RAMPS['endite'][4],'B':RAMPS['endite'][5],'W':RAMPS['endite'][6]}
im=Image.new('RGBA',(16,16),(0,0,0,0))
for y,row in enumerate(S):
    for x,ch in enumerate(row):
        if ch in pal: im.putpixel((x,y),pal[ch]+(255,))
im.save(OUT+'endite_shard.png')
# Copies of vanilla for the tier ladder
for n in ['item/diamond_pickaxe','item/netherite_pickaxe','item/diamond_sword','item/netherite_sword']:
    Image.open(B+n+'.png').convert('RGBA').save(OUT+n.split('/')[1]+'.png')
# Preview sheet
import os
names=sorted(f[:-4] for f in os.listdir(OUT) if f.endswith('.png'))
sc=8;cols=8
sheet=Image.new('RGBA',(cols*18*sc,((len(names)+cols-1)//cols)*18*sc),(60,62,70,255))
for i,n in enumerate(names):
    im=Image.open(OUT+n+'.png').resize((16*sc,16*sc),Image.NEAREST)
    sheet.paste(im,((i%cols)*18*sc+sc,(i//cols)*18*sc+sc),im)
print(len(names), 'sprites')
