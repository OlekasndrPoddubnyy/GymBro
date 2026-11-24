# Rilievo muscolare e colore, nelle coordinate del render 896x1600 (specchiate su x=448).
import numpy as np
from rilievi_extra import R_EXTRA
R = dict(R_EXTRA)
R['uomo','fronte'] = (
[
    ([(448,352),(448,470)],7,3.0), ([(448,478),(448,560),(448,622)],6,3.8), ([(448,640),(448,700)],7,1.3),
    ([(452,452),(478,472),(518,479),(558,468),(590,440),(612,408)],8,4.2),
    ([(450,497),(474,494),(500,505)],6,2.4), ([(450,541),(476,539),(500,543)],6,3.4),
    ([(450,585),(476,583),(499,586)],6,3.2),
    ([(508,492),(507,540),(503,590),(506,635),(496,680)],9,3.0),
    ([(566,640),(540,670),(512,704),(492,730)],9,3.2),
    ([(548,500),(566,512),(578,505)],5,1.8), ([(538,530),(558,540),(570,532)],5,1.6),
    ([(532,558),(550,566),(560,560)],5,1.3),
    ([(462,300),(500,294),(548,292),(590,298)],7,1.6),
    ([(590,308),(606,342),(618,384)],9,2.6),
    ([(640,384),(660,418),(676,400),(690,356)],8,2.2), ([(652,436),(676,480),(690,516)],7,1.6),
    ([(704,560),(724,610),(748,660)],7,1.2),
    ([(540,975),(544,1030),(548,1075)],9,2.4), ([(496,985),(508,1040),(524,1090)],9,1.8),
    ([(594,1150),(600,1230),(612,1330)],8,1.6),
]
,
[
    ((476,519),(22,19),3.4), ((475,563),(22,18),3.4), ((474,605),(21,17),3.0), ((474,655),(20,36),1.6),
    ((522,420),(58,44),4.0), ((650,352),(30,46),2.5), ((666,470),(17,42),2.5), ((700,565),(17,32),1.5),
    ((530,1072),(16,30),3.0), ((570,1010),(17,60),2.0), ((570,1240),(16,60),1.6),
]
)

def sm(a,b,x):
    t = np.clip((x-a)/(b-a),0,1); return t*t*(3-2*t)

def coord(me, H, z0):
    n = len(me.vertices); co = np.empty(n*3,np.float32); me.vertices.foreach_get('co',co); co = co.reshape(n,3)
    k = H*1.06/1600
    return n, co, 448+np.abs(co[:,0])/k, 800-(co[:,2]-(z0+H*.505))/k

def applica(me, H, z0, chi, vista):
    if (chi,vista) not in R: return
    solchi, gonfi = R[chi,vista]
    if (chi,vista)==('uomo','retro'): solchi = [(p,w*1.6,mm*.72) for p,w,mm in solchi]
    n, co, px, py = coord(me,H,z0)
    no = np.empty(n*3,np.float32); me.vertices.foreach_get('normal',no); no = no.reshape(n,3)
    q = np.stack([px,py],1); h = np.zeros(n,np.float32)
    for pts,w,mm in solchi:
        best = np.full(n,1e12)
        for a,b in zip(pts,pts[1:]):
            a,b = np.array(a,float),np.array(b,float); d=b-a
            t = np.clip(((q-a)*d).sum(1)/(d*d).sum(),0,1)
            best = np.minimum(best,((q-(a+t[:,None]*d))**2).sum(1))
        h -= mm*np.exp(-best/(w*w))
    for (x,y),(rx,ry),mm in gonfi:
        h += mm*np.exp(-(((px-x)/rx)**2+((py-y)/ry)**2)**1.5)
    verso = -1 if vista=='fronte' else 1
    f = np.clip((no[:,1]*verso-.05)/.55,0,1); f = f*f*(3-2*f)
    co = co+no*(h*f/1000)[:,None]
    me.vertices.foreach_set('co',co.ravel()); me.update()

def taglio_top(x, z, davanti, sopra):
    """Bordo alto del top: scollo e bretelle davanti, vogatore dietro."""
    spalline = (x>.040)&(x<.058)
    fronte = (z < sopra-.012*np.clip(1-x/.045,0,1)-.02*np.clip((x-.065)/.035,0,1)) | spalline
    c = .012+.046*np.clip((z-sopra)/.055,0,1)
    retro = (z < sopra-.018) | (np.abs(x-c)<.010)
    return np.where(davanti,fronte,retro) & (z<.86)

def colora(me, H, z0, chi, pelle, coperti):
    n, co, px, y = coord(me,H,z0); x = px-448
    c = np.tile(np.array(pelle,np.float32),(n,1))
    if chi=='uomo':
        grana = np.random.default_rng(7).random(n)
        linea = np.interp(x,[0,20,40,52,66],[192,190,186,172,162])
        barba = sm(linea,linea+9,y)*(1-sm(228,246,y))*(x<72)*(co[:,1]<0)
        labbra = np.exp(-(((x/25)**2+((y-206)/7.5)**2)**2))
        barba *= (1-labbra)*(.26+.22*grana)
        attacco = np.interp(x,[0,30,44,52,60],[94,96,106,126,142])
        capo = (1-sm(attacco-6,attacco+4,y))*(x<72)*(.6+.3*grana)
        for peso,col in ((barba,(.20,.14,.11)),(capo,(.07,.045,.03))):
            c = c*(1-peso[:,None])+np.array(col)*peso[:,None]
    c[coperti] = (.015,.015,.018)
    a = me.color_attributes.new('tono','FLOAT_COLOR','POINT')
    a.data.foreach_set('color',np.concatenate([c,np.ones((n,1))],1).astype(np.float32).ravel())

def tingi(materiali, colore=None, attributo=None):
    visti = set()
    def giro(t):
        if t.name in visti: return
        visti.add(t.name)
        for n in list(t.nodes):
            if n.type=='GROUP' and n.node_tree: giro(n.node_tree)
            if n.type=='BSDF_PRINCIPLED' and n.inputs['Base Color'].is_linked:
                i = n.inputs['Base Color']; da = i.links[0].from_socket
                m = t.nodes.new('ShaderNodeMixRGB'); m.blend_type='MULTIPLY'; m.inputs[0].default_value=1
                t.links.new(da,m.inputs[1])
                if attributo:
                    a = t.nodes.new('ShaderNodeAttribute'); a.attribute_name=attributo
                    t.links.new(a.outputs['Color'],m.inputs[2])
                else:
                    m.inputs[2].default_value=(*colore,1)
                t.links.new(m.outputs[0],i)
    for m in materiali:
        if m: giro(m.node_tree)
