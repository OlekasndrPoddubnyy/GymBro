# Griglia di coordinate (896x1600, riga ogni 50 px, piu' chiara ogni 100): -- uscita ingressi...
import bpy, sys, numpy as np
out,*src = sys.argv[sys.argv.index('--')+1:]
pz=[]
for p in src:
    i=bpy.data.images.load(p); i.scale(896,1600); a=np.array(i.pixels[:],np.float32).reshape(1600,896,4)[::-1].copy()
    c=a[...,:3]*a[...,3:]+.08*(1-a[...,3:])
    for v in range(0,1600,50):
        o=.5 if v%100==0 else .2; col=np.array([0,.85,1])
        c[v]=c[v]*(1-o)+col*o
    for v in range(448%50,896,50):
        o=.5 if (v-448)%100==0 else .2
        c[:,v]=c[:,v]*(1-o)+np.array([0,.85,1])*o
    pz.append(c); pz.append(np.ones((1600,16,3),np.float32))
t=np.concatenate(pz,1); r=np.concatenate([t,np.ones(t.shape[:2]+(1,))],2)[::-1].astype(np.float32)
o=bpy.data.images.new('o',t.shape[1],1600,alpha=True); o.pixels=r.ravel().tolist(); o.filepath_raw=out; o.file_format='PNG'; o.save()
