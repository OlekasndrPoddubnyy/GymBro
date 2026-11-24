# Figure adulte (28 anni) vestite per la mappa muscoli di GymBro.
# Uso: -- <uomo|donna> <fronte|retro> <uscita.png> [json]
import bpy, bmesh, importlib, json, math, os, sys
import numpy as np
from mathutils import Matrix, Vector

QUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, QUI)
import forme

args = sys.argv[sys.argv.index("--") + 1:]
CHI, VISTA, OUT = args[:3]
P = dict(altezza=1600, campioni=96)
if len(args) > 3:
    P.update(json.loads(args[3]))

# gonna/top/pantaloncini sono in frazioni dell'altezza: (orlo, vita, elastico)
CONF = dict(
    uomo=dict(
        macro=dict(gender=1.0, muscle=1.0, weight=.43, height=.6),
        pelle="young_caucasian_male", capelli="short01", capelli_tinta=(.75, .46, .27), abbassa=.006,
        sopracciglia="eyebrow001", tono=(.70, .49, .35), short=(.425, .592, .574), top=None, braccio=21,
        arti=dict(upperarm=.5, shoulder=.5, lowerarm=.4, upperleg=.4, lowerleg=.4),
        target={
            "stomach/stomach-tone-incr": 1.0, "torso/torso-muscle-pectoral-incr": .7,
            "torso/torso-muscle-dorsi-incr": .6, "torso/torso-vshape-incr": .3,
            "chin/chin-width-incr": .35, "chin/chin-bones-incr": .3, "chin/chin-prominent-incr": .15,
            "expression/units/caucasian/mouth-corner-puller": .38, "mouth/mouth-lowerlip-volume-incr": .2,
            "nose/nose-scale-horiz-decr": .15, "eyebrows/eyebrows-angle-up": .1,
        }),
    donna=dict(
        macro=dict(gender=0.0, muscle=.85, weight=.42, height=.56, cupsize=.42, firmness=1.0),
        pelle="young_caucasian_female", capelli="ponytail01", capelli_tinta=(.80, .58, .40), abbassa=0,
        sopracciglia="eyebrow010", tono=(.74, .54, .40), short=(.428, .588, .571), top=(.672, .777), braccio=23,
        arti=dict(upperarm=.2, shoulder=.3, lowerarm=.1, upperleg=.3, lowerleg=.25),
        target={
            "stomach/stomach-tone-incr": 1.0, "torso/torso-muscle-dorsi-incr": .2,
            "breast/breast-point-decr": 1.0, "head/head-oval": .35,
            "expression/units/caucasian/mouth-corner-puller": .42, "mouth/mouth-lowerlip-volume-incr": .3,
            "mouth/mouth-upperlip-volume-incr": .15, "nose/nose-scale-horiz-decr": .25,
        }),
)
C = CONF[CHI]
C.update(P.get("conf", {}))

s = importlib.import_module("bl_ext.user_default.mpfb.services")
Human, Target = s.HumanService, s.TargetService
DATI, BASE = s.LocationService.get_user_data(), s.LocationService.get_mpfb_data("targets")

for o in list(bpy.data.objects):
    bpy.data.objects.remove(o)

# ---- corpo
m = Target.get_default_macro_info_dict()
m.update(age=0.523, proportions=1.0, **C["macro"])  # eta' fissa: 28 anni
m["race"] = dict(asian=.05, caucasian=.85, african=.10)
corpo = Human.create_human(macro_detail_dict=m)
tg = dict(C["target"])
a = C["arti"]
for l in "lr":
    tg.update({f"arms/{l}-upperarm-muscle-incr": a["upperarm"], f"arms/{l}-lowerarm-muscle-incr": a["lowerarm"],
               f"arms/{l}-upperarm-shoulder-muscle-incr": a["shoulder"],
               f"legs/{l}-upperleg-muscle-incr": a["upperleg"], f"legs/{l}-lowerleg-muscle-incr": a["lowerleg"]})
for k, v in tg.items():
    Target.load_target(corpo, os.path.join(BASE, *k.split("/")) + ".target.gz", weight=v)

rig = Human.add_builtin_rig(corpo, "game_engine")
Human.set_character_skin(os.path.join(DATI, "skins", C["pelle"], C["pelle"] + ".mhmat"), corpo,
                         skin_type="ENHANCED_SSS", slot_overrides={
                             k: {"Roughness": .56, "Clearcoat": .03} for k in ("body", "face", "ears", "nipple")})


def asset(tipo, nome, classe):
    return Human.add_mhclo_asset(os.path.join(DATI, tipo, nome, nome + ".mhclo"), corpo, asset_type=classe)


occhi = asset("eyes", "high-poly", "Eyes")
asset("eyebrows", C["sopracciglia"], "Eyebrows")
asset("eyelashes", "eyelashes01", "Eyelashes")
chioma = asset("hair", C["capelli"], "Hair")
for mat in occhi.data.materials:
    for n in mat.node_tree.nodes:
        if n.type == "TEX_IMAGE" and n.image:
            n.image = bpy.data.images.load(os.path.join(DATI, "eyes", "materials", "brown_eye.png"))
forme.tingi(chioma.data.materials, colore=C["capelli_tinta"])

# ---- posa
bpy.context.view_layer.update()


def ruota(nome, gradi):
    osso = rig.pose.bones[nome]
    p = osso.head.copy()
    osso.matrix = Matrix.Translation(p) @ Matrix.Rotation(math.radians(gradi), 4, "Y") @ Matrix.Translation(-p) @ osso.matrix
    bpy.context.view_layer.update()


for l, segno in (("l", 1), ("r", -1)):
    p, q = rig.pose.bones["upperarm_" + l].head.copy(), rig.pose.bones["hand_" + l].head.copy()
    ruota("upperarm_" + l, (math.degrees(math.atan2(abs(q.x - p.x), abs(q.z - p.z))) - C["braccio"]) * segno)
    ruota("thigh_" + l, 4 * segno)
    ruota("foot_" + l, -4 * segno)

sub = corpo.modifiers.new("Liscio", "SUBSURF")
sub.levels = sub.render_levels = 3
for p in corpo.data.polygons:
    p.use_smooth = True
bpy.context.view_layer.update()
dg = bpy.context.evaluated_depsgraph_get()
me = bpy.data.meshes.new_from_object(corpo.evaluated_get(dg), depsgraph=dg)
me.transform(corpo.matrix_world)
n = len(me.vertices)
co = np.empty(n * 3, np.float32)
me.vertices.foreach_get("co", co)
co = co.reshape(n, 3)
Z0, H = float(co[:, 2].min()), float(np.ptp(co[:, 2]))
zh, xh = (co[:, 2] - Z0) / H, np.abs(co[:, 0]) / H

nomi = [g.name for g in corpo.vertex_groups]
OSSA = set(b.name for b in rig.data.bones)
osso_dom = []
for v in me.vertices:
    g = [(x.weight, nomi[x.group]) for x in v.groups if x.group < len(nomi) and nomi[x.group] in OSSA]
    osso_dom.append(max(g)[1] if g else "")
osso_dom = np.array(osso_dom)


def ossa(*prefissi):
    r = np.zeros(n, bool)
    for p in prefissi:
        r |= np.char.startswith(osso_dom, p)
    return r


tronco_basso = ossa("pelvis", "thigh_", "spine_", "calf_", "Root")
orlo, vita, banda = C["short"]
short_ok = tronco_basso.copy()
coperti = tronco_basso & (zh > orlo + .006) & (zh < vita - .006)
if C["top"]:
    sotto, sopra = C["top"]
    busto = ossa("spine_", "clavicle_", "neck_", "pelvis", "Root") | (xh < .088)
    # davanti/dietro rispetto alla meta' del busto a quella quota
    fascia = np.clip((zh * 100).astype(int), 0, 100)
    mezzo = np.zeros(101)
    for k in range(60, 90):
        qui = busto & (fascia == k) & (xh < .07)
        if qui.any():
            mezzo[k] = (co[qui, 1].min() + co[qui, 1].max()) / 2
    davanti = co[:, 1] < mezzo[fascia]
    top_ok = busto & (zh > sotto - .02) & forme.taglio_top(xh, zh, davanti, sopra)
    coperti |= top_ok & (zh > sotto + .008) & (zh < sopra - .03)

# sotto i vestiti il corpo e' levigato: la stoffa non segue i dettagli anatomici
liscio = short_ok & (zh > orlo + .012) & (zh < vita + .004)
if C["top"]:
    liscio |= top_ok & (zh > sotto + .003) & (zh < sopra + .008)
e = np.empty(len(me.edges) * 2, np.int64)
me.edges.foreach_get("vertices", e)
e0, e1 = e[0::2], e[1::2]
grado = np.maximum(np.bincount(e, minlength=n), 1)
for _ in range(300):
    somma = np.stack([np.bincount(e0, co[e1, k], n) + np.bincount(e1, co[e0, k], n) for k in range(3)], 1)
    co[liscio] = (somma / grado[:, None])[liscio] * .7 + co[liscio] * .3
if C["top"]:
    # petto: media gaussiana su campioni uniformi (un punto per cm), indipendente dalla densita' della mesh
    sel = np.flatnonzero(top_ok & davanti & (zh > sotto + .004) & (zh < sopra + .008))
    vicino = busto & (co[:, 1] < mezzo[fascia] + .03) & (zh > sotto - .02) & (zh < sopra + .03)
    peso = np.clip(np.minimum(zh[sel] - sotto - .004, sopra + .008 - zh[sel]) / .016, 0, 1)
    peso *= np.clip((mezzo[fascia[sel]] - co[sel, 1]) / .04, 0, 1)
    for _ in range(3):
        pt = co[vicino]
        _, uno = np.unique(np.floor(pt / .010).astype(np.int32), axis=0, return_index=True)
        pt = pt[uno]
        for i in range(0, len(sel), 1500):
            q = co[sel[i:i + 1500]]
            w = np.exp(-((q[:, None] - pt[None]) ** 2).sum(2) / (2 * .022 ** 2))
            media = (w @ pt) / w.sum(1)[:, None]
            f = peso[i:i + 1500, None]
            co[sel[i:i + 1500]] = q * (1 - f) + media * f
me.vertices.foreach_set("co", co.ravel())
me.update()

# ---- pelle scolpita: sotto i vestiti e' dipinta del colore della stoffa
corpo.hide_render = True
chioma.location.z -= C["abbassa"]
scolpito = me.copy()
forme.applica(scolpito, H, Z0, CHI, VISTA)
forme.colora(scolpito, H, Z0, CHI, C["tono"], coperti)
forme.tingi(scolpito.materials, attributo="tono")
so = bpy.data.objects.new("Scolpito", scolpito)
bpy.context.collection.objects.link(so)
for p in scolpito.polygons:
    p.use_smooth = True


# ---- vestiti
def stoffa(colore):
    mt = bpy.data.materials.new("Stoffa")
    mt.use_nodes = True
    b = next(x for x in mt.node_tree.nodes if x.type == "BSDF_PRINCIPLED")
    b.inputs["Base Color"].default_value = (*colore, 1)
    b.inputs["Roughness"].default_value = .72
    b.inputs["Sheen Weight"].default_value = .25
    return mt


def capo(nome, tieni, tagli, colore, gonfia=.007, giri=90):
    bm = bmesh.new()
    bm.from_mesh(me)
    bm.verts.ensure_lookup_table()
    bmesh.ops.delete(bm, geom=[v for v, ok in zip(bm.verts, tieni) if not ok], context="VERTS")
    for quota, verso in tagli:
        bmesh.ops.bisect_plane(bm, geom=bm.verts[:] + bm.edges[:] + bm.faces[:],
                               plane_co=(0, 0, Z0 + H * quota), plane_no=(0, 0, verso), clear_inner=True)
    # bordo liscio: media solo lungo il bordo
    vicini = {}
    for e in bm.edges:
        if e.is_boundary:
            x, y = e.verts
            vicini.setdefault(x, []).append(y)
            vicini.setdefault(y, []).append(x)
    def liscia_bordo(volte):
      for _ in range(volte):
        nuovi = [(v, v.co * .5 + sum((w.co for w in w_), Vector()) / len(w_) * .5)
                 for v, w_ in vicini.items() if len(w_) == 2]
        for v, p in nuovi:
            v.co = p
    liscia_bordo(40)
    bm.normal_update()
    for v in bm.verts:
        v.co += v.normal * .004
    interni = [v for v in bm.verts if not v.is_boundary]
    for _ in range(giri):
        bmesh.ops.smooth_vert(bm, verts=interni, factor=.5, use_axis_x=True, use_axis_y=True, use_axis_z=True)
    bm.normal_update()
    for v in bm.verts:
        v.co += v.normal * gonfia
    liscia_bordo(25)
    for _ in range(4):
        bmesh.ops.smooth_vert(bm, verts=interni, factor=.5, use_axis_x=True, use_axis_y=True, use_axis_z=True)
    pm = bpy.data.meshes.new(nome)
    bm.to_mesh(pm)
    if len(pm.polygons) < 100:
        raise SystemExit(nome + " mancante: niente render")
    pm.materials.clear()
    pm.materials.append(stoffa(colore))
    for p in pm.polygons:
        p.material_index = 0
        p.use_smooth = True
    ob = bpy.data.objects.new(nome, pm)
    bpy.context.collection.objects.link(ob)
    ob.modifiers.new("Spessore", "SOLIDIFY").thickness = .003
    return ob


NERO, GRIGIO = (.012, .013, .016), (.045, .048, .053)
capo("Pantaloncini", short_ok, ((orlo, 1), (vita, -1)), NERO)
capo("Elastico", short_ok, ((banda, 1), (vita, -1)), GRIGIO, .011)
if C["top"]:
    capo("Top", top_ok, ((sotto, 1),), NERO, .007, 140)
    capo("Fascia", top_ok & (zh < sotto + .03), ((sotto, 1), (sotto + .015, -1)), GRIGIO, .011, 10)

# ---- camera e luci
sc = bpy.context.scene
d = -1 if VISTA == "fronte" else 1
centro = Vector((0, 0, Z0 + H * .505))
cd = bpy.data.cameras.new("Camera")
cd.type, cd.ortho_scale = "ORTHO", H * 1.06
if P.get("ritratto"):
    centro.z, cd.ortho_scale = Z0 + H * .88, H * .30
cam = bpy.data.objects.new("Camera", cd)
bpy.context.collection.objects.link(cam)
cam.location = centro + Vector((0, 6 * d, 0))
cam.rotation_euler = (centro - cam.location).to_track_quat("-Z", "Y").to_euler()
sc.camera = cam


def luce(pos, watt, misura, colore=(1, 1, 1)):
    ld = bpy.data.lights.new("Luce", "AREA")
    ld.energy, ld.size, ld.color = watt, misura, colore
    o = bpy.data.objects.new("Luce", ld)
    bpy.context.collection.objects.link(o)
    # stessa luce rispetto a chi guarda, davanti e dietro
    o.location = Vector((pos[0] * -d, pos[1] * -d, Z0 + pos[2]))
    o.rotation_euler = (Vector((0, 0, Z0 + H * .58)) - o.location).to_track_quat("-Z", "Y").to_euler()


luce((-2.0, -1.5, 3.3), 170, .55, (1, .90, .80))
luce((1.8, -2.4, 1.5), 16, 2.0, (.85, .92, 1))
luce((2.1, 1.3, 2.3), 110, .6, (.80, .90, 1))
luce((-2.1, 1.3, 1.6), 70, .6, (1, .88, .75))
luce((-.6, -2.6, .6), 26, 1.4, (.95, .97, 1))
mondo = bpy.data.worlds.new("Studio")
mondo.use_nodes = True
mondo.node_tree.nodes["Background"].inputs[0].default_value = (.025, .027, .03, 1)
sc.world = mondo

sc.render.engine = "CYCLES"
pref = bpy.context.preferences.addons["cycles"].preferences
pref.compute_device_type = "OPTIX"
pref.get_devices()
for x in pref.devices:
    x.use = x.type == "OPTIX"
sc.cycles.device, sc.cycles.samples, sc.cycles.use_denoising = "GPU", P["campioni"], True
sc.cycles.transparent_max_bounces = 32
sc.render.film_transparent = True
sc.view_settings.view_transform = "AgX"
sc.view_settings.look = "AgX - Medium High Contrast"
sc.render.resolution_y = P["altezza"]
sc.render.resolution_x = P["altezza"] if P.get("ritratto") else round(P["altezza"] * .56)
sc.render.resolution_percentage = 100
sc.render.image_settings.color_mode = "RGBA"
sc.render.filepath = OUT
bpy.ops.render.render(write_still=True)
print("FATTO", OUT, round(H, 3))
