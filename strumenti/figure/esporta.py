# Ritaglia e salva WebP: -- cartella_out cartella_res
import bpy, sys, os
import numpy as np
src, dst = sys.argv[sys.argv.index('--')+1:]
os.makedirs(dst, exist_ok=True)
k=2000/1600
for n in ('uomo_fronte','uomo_retro','donna_fronte','donna_retro'):
    i=bpy.data.images.load(os.path.join(src,n+'.png')); w,h=i.size
    a=np.array(i.pixels[:],np.float32).reshape(h,w,4)[::-1]
    c=a[round(36*k):round(1566*k), round(92*k):round(804*k)][::-1].copy()
    o=bpy.data.images.new(n,c.shape[1],c.shape[0],alpha=True); o.pixels=c.ravel().tolist()
    o.scale(712,1530); o.file_format='WEBP'; o.save(filepath=os.path.join(dst,'figura_'+n+'.webp'),quality=90)
    print('WEBP',n)
