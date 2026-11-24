# Affianca PNG su fondo grigio scuro: -- uscita altezza ingressi...
import bpy, sys, numpy as np
out, h, *src = sys.argv[sys.argv.index('--')+1:]
h=int(h); pezzi=[]
for p in src:
    i=bpy.data.images.load(p); w=round(i.size[0]*h/i.size[1]); i.scale(w,h)
    a=np.array(i.pixels[:],dtype=np.float32).reshape(h,w,4); f=np.array([.075,.078,.085])
    a[...,:3]=a[...,:3]*a[...,3:]+f*(1-a[...,3:]); a[...,3]=1; pezzi.append(a)
t=np.concatenate(pezzi,1); o=bpy.data.images.new('o',t.shape[1],h,alpha=True)
o.pixels=t.ravel().tolist(); o.filepath_raw=out; o.file_format='PNG'; o.save()
