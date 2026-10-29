"""Source-bound static geometry/terrain audit, NOT a Minecraft runtime test.
Evaluates the authored coordinate formulae and current tag lists. Source hashes
pin every formula to the reviewed implementation. GameTest separately exercises
actual Blueprint generators, BlockState tags and StructureSpatialIndex methods.
"""
from pathlib import Path
import collections, hashlib, json, math, re
root=Path(__file__).resolve().parents[2]
PINNED = {'src/main/java/com/bobincraft/destruction/StructureSpatialIndex.java': 'f3237c441c50cdf0584a542a3080cb226e6632582c4e0d5374ea6921b13b7c68', 'src/main/java/com/bobincraft/destruction/DestructibleStructure.java': 'df50e96ae02e6e14278bfff2748f2b2cf979005e257c428f31a6380abb222154', 'src/main/java/com/bobincraft/destruction/DestructibleStructureRegistry.java': 'd3bcd42954e9c3d1057a114707452626f3b3ea96ef84e0d5ab2bbe0a98292eb2', 'src/main/java/com/bobincraft/destruction/StructureGeometrySnapshot.java': 'e4110e9f1a6168d7ba0e2bb9be99ba60628e2af91d634d427806af343d57803b', 'src/main/java/com/bobincraft/destruction/RefineryGeometryRules.java': '98317f8d6da8fc31cff84637c301fffe879b519cad352f2a10db7858db0f0377', 'src/main/java/com/bobincraft/destruction/StructureCollapseManager.java': '476a19c299a281e3acb32ceeee61fb04cd0b898764cdab2033d8a7b97bce5854', 'src/main/java/com/bobincraft/destruction/StructureDamagePolicy.java': '2795040f1fb98d3b7bab38022fcd5d6fb227201e7d5ae233d4ff5758f5c8e4ea', 'src/main/java/com/bobincraft/destruction/StructureDamageSource.java': 'b9cae8c0c3ea34c37c6cc380a3926cf9ebe3632c805ef34dbe6ea95c3ee50abc', 'src/main/java/com/bobincraft/drone/BlastTerrain.java': '4ee2fd69df93443a0607573aea181b1acc82c166ccb9f0ef1329b00549c3fd62', 'src/main/java/com/bobincraft/drone/DroneExplosion.java': '011d87912f7ace4ba703bbfc94b81f5bad67a6876cb38adb1adba7f2111e4fec', 'src/main/java/com/bobincraft/drone/DroneBlastType.java': '0114001975d97379e6c46894d2295f06f416f1638cd3a5a9f92fa87bcfde3b7b', 'src/main/java/com/bobincraft/warzone/ZoneInfrastructure.java': '6ed0693d0992d1a14640ed7ed2ef6c8b355ba756acce97bd0e5a8ce387d15005', 'src/main/java/com/bobincraft/warzone/IndustrialFacilities.java': 'a1b4f7ee4633df0f62fb12d468466aeb44cecd957f7b46de9f28b5c6ff0ef7d4', 'src/main/java/com/bobincraft/warzone/ZonePlan.java': 'ee045bf88548a0cc4355282267c21690812b21de37279f205c94df5696c64685', 'src/main/resources/data/bobin_craft/tags/blocks/drone_breachable.json': '0e517e056430313114465c39d61fbc926219ea490e1568bc3dc2778419069aa0', 'src/main/resources/data/bobin_craft/tags/blocks/drone_scorchable_plants.json': '99fab53952f6ee6ef84c19a5cbd512856c04ba7b65aa7d5b8177399f159180e4', 'src/main/resources/data/bobin_craft/tags/blocks/drone_shatterable_glass.json': 'e06d166b7e8c48b32a00981adf34d8d595f2129ef80d721d1b64baa03a2c6054', 'src/main/resources/data/bobin_craft/tags/blocks/drone_scorchable_soil.json': '5ac346637b558fb8059511112b263ea16a979a33e395513e916538f7c59c9d9c'}
for path, expected in PINNED.items():
    assert hashlib.sha256((root/path).read_bytes()).hexdigest()==expected, 'Re-audit changed source: '+path
assert PINNED, 'Missing source binding'
def source(name): return (root/'src/main/java/com/bobincraft'/name).read_text()
def method(text, signature):
    start=text.index(signature); start=text.index('{',start); depth=1; end=start+1
    while depth:
        depth += (text[end]=='{')-(text[end]=='}'); end+=1
    return text[start:end]
terrain=source('drone/BlastTerrain.java'); index=source('destruction/StructureSpatialIndex.java')
structure=source('destruction/DestructibleStructure.java')
assert 'findPrimaryAt(Vec3d.ofCenter(pos))' in method(terrain,'private static boolean terrainAllowed')
assert '.allows(sourceKind, target.structureType().category().name())' in terrain
assert 'candidate.hitZoneAt(impactPos, 1.25)' in index and 'overallBounds().expand(2.0)' in index
assert 'for (int dx = -1; dx <= 1; dx++)' in index and 'for (int dz = -1; dz <= 1; dz++)' in index
assert 'if (!overallBounds.expand(tolerance).contains(pos)) return HitMatch.NONE;' in structure
assert 'return new HitMatch(HitZone.HULL, defaultComponentId(), null);' in method(structure,'public HitMatch matchAt')
assert 'double maxR = radius + 2.0 + tolerance;' in structure
assert 'type == DroneBlastType.KAMIKAZE_BREACH' in terrain and 'boolean megaStrike = type == DroneBlastType.FP1_DEEP_STRIKE;' in terrain
assert 'if (megaStrike && fully' in terrain # only source of fires.add
assert terrain.count('fires.add(')==1
assert 'if (!insideManagedStructure && state.isIn(SHATTERABLE_GLASS)' in terrain
assert '!terrainAllowed(sourceKind, mark.pos()) || !terrainAllowed(sourceKind, mark.pos().up())' in terrain
# Binding of both passes to the same production predicate: a regression in the client count must fail this audit.
client=source('destruction/client/StructureDestructionClient.java')
assert 'if (isSmokeEmitter(v, world, observer)) activeSmokeEmitters++;' in client
assert 'if (!isSmokeEmitter(v, world, observer)) continue;' in client
assert 'effectiveIntensity, activeSmokeEmitters)' in client and 'activeEffectStructures' not in client

def contains(bounds,p,tolerance=1.25):
    return all(bounds[i]-tolerance<=p[i]<bounds[i+3]+tolerance for i in range(3))
def box_chunk_includes(bounds,p):
    cx,cz=math.floor(p[0])>>4,math.floor(p[2])>>4
    return any((math.floor(bounds[0]-2)>>4)<=cx+dx<=(math.floor(bounds[3]+2)>>4)
        and (math.floor(bounds[2]-2)>>4)<=cz+dz<=(math.floor(bounds[5]+2)>>4)
        for dx in (-1,0,1) for dz in (-1,0,1))

tank_results=[]; probes=0
for r,h in zip((8,6,7,5),(10,8,11,9)):
    # Origin at a negative chunk seam exercises packing/search, independent of layout translation.
    ox,oy,oz=-17,79,-33; c=(ox+.5,oy,oz+.5)
    bounds=(c[0]-r-2.25,oy-1,c[2]-r-2.25,c[0]+r+2.25,oy+h+2.5,c[2]+r+2.25)
    cells=[]
    for a in range(-r-2,r+3):
        for b in range(-r-2,r+3):
            for dy in range(h+2):
                includes=(dy==0 and a*a+b*b<=(r+2)**2) or (1<=dy<=h and a*a+b*b<=r*r) or (a==r and b==0 and 1<=dy<=h) or (a==0 and b==r and dy==2) or (a==0 and b==r+1 and dy==1) or (a==0 and b==0 and dy==h+1)
                if not includes: continue
                p=(ox+a+.5,oy+dy+.5,oz+b+.5)
                assert contains(bounds,p) and a*a+b*b<=(r+3.25)**2 and box_chunk_includes(bounds,p)
                cells.append(p)
    for p in cells:
        for shift in ((0,0,0),(.499,0,0),(-.499,0,0),(0,.499,0),(0,-.499,0),(0,0,.499),(0,0,-.499)):
            point=tuple(p[i]+shift[i] for i in range(3))
            assert contains(bounds,point) and (point[0]-c[0])**2+(point[2]-c[2])**2<=(r+3.25)**2
            probes+=1
    tank_results.append({'radius':r,'height':h,'reference_mask_cells_including_air':len(cells),'missed_by_primary_at_cell_centers':0})

# Use every supplied baked layout; v10 is current, older layouts are compatibility evidence.
bridges=[]
for layout in sorted((root/'src/main/resources/data/bobin_craft/warzone').glob('black_alder_layout*.json')):
    for b in json.loads(layout.read_text())['crossings']:
        def floor(u):
            rnd=lambda x:math.floor(x+.5) # Java Math.round, including negative grades
            if u<-b['negative']+24:return b['leftY']+rnd((b['deckY']-b['leftY'])*(u+b['negative'])/24)
            if u>b['positive']-24:return b['rightY']+rnd((b['deckY']-b['rightY'])*(b['positive']-u)/24)
            return b['deckY']
        def point(u,v,y):return (b['x']+(u if b['alongX'] else v)+.5,y+.5,b['z']+(v if b['alongX'] else u)+.5)
        start,end=-b['negative']+3,b['positive']-3; length=(end-start)/3; bounds=[]
        for s in range(3):
            u0=math.floor(start+s*length+.5);u1=end if s==2 else math.floor(start+(s+1)*length+.5)-1
            mid=int((u0+u1)/2); half=max(4,int((u1-u0)/2));cx,_,cz=point(mid,0,0);cy=floor(mid)
            hw,hd=(half+.15,5.15) if b['alongX'] else (5.15,half+.15)
            bounds.append((cx-hw,cy-14,cz-hd,cx+hw,cy+7,cz+hd))
        cells={}
        def add(u,v,y,part):
            if 40<=y<b['deckY']+20:cells[point(u,v,y)]=part
        for u in range(-b['negative'],b['positive']+1):
            y=floor(u)
            for v in range(-4,5):
                add(u,v,y-1,'girder');add(u,v,y,'deck')
                if abs(v)==4:
                    add(u,v,y+1,'parapet')
                    if b['style']==1 and u%8==0:
                        for h in range(2,6):add(u,v,y+h,'truss')
            if u%16==0 and u>-b['negative']+12 and u<b['positive']-12:
                # Conservative possible pier cells down to blueprint minY, not invented raw terrain.
                for v in (-3,3):
                    for h in range(40,y-1):add(u,v,h,'possible_pier')
                for v in range(-4,5):add(u,v,y-2,'cap')
            if b['style']==1 and -b['negative']+16<=u<=b['positive']-16:
                for v in (-4,4):add(u,v,y+5,'truss');add(u,v,y+1+(u%8)//2,'truss')
        for u in (-b['negative'],b['positive']):
            for v in range(-5,6):
                for h in range(40,floor(u)+1):add(u,v,h,'possible_abutment')
                if abs(v)==5:
                    for h in (1,2):add(u,v,floor(u)+h,'abutment_edge')
        missed=collections.Counter()
        for p,part in cells.items():
            hits=[box for box in bounds if contains(box,p)]
            if not hits:missed[part]+=1
            else:assert any(box_chunk_includes(box,p) for box in hits)
        bridges.append({'layout':layout.name,'crossing':b['id'],'style':b['style'],'alongX':b['alongX'],
            'conservative_candidate_cells':len(cells),'outside_primary_envelopes':dict(missed)})

# Every possible authored material in current tank/reference and bridge stage paths is non-glass/non-soil/non-plant.
# Nested vanilla tags are verified with actual BlockState membership by the unexecuted GameTest.
materials={'bobin_craft:cracked_concrete','bobin_craft:mossy_concrete','bobin_craft:cracked_asphalt','bobin_craft:rusted_sheet_metal',
'bobin_craft:weathered_asphalt_slab','bobin_craft:weathered_gravel','minecraft:dark_oak_log','minecraft:iron_bars','minecraft:waxed_oxidized_copper','minecraft:polished_andesite',
'minecraft:gray_terracotta','minecraft:light_gray_terracotta','minecraft:ladder','bobin_craft:utility_pipe','bobin_craft:equipment_panel','minecraft:hopper',
'minecraft:cobblestone_slab','minecraft:stone_brick_stairs'}
explicit={}
for kind in ('shatterable_glass','scorchable_soil','scorchable_plants'):
    values=json.loads((root/f'src/main/resources/data/bobin_craft/tags/blocks/drone_{kind}.json').read_text())['values']
    explicit[kind]=sorted(materials.intersection(v for v in values if not v.startswith('#')))
    assert not explicit[kind]
print(json.dumps({'kind':'STATIC SOURCE-BOUND GEOMETRY AUDIT; NOT FABRIC RUNTIME',
'source_sha256':PINNED,'tank_masks':tank_results,'tank_face_and_attachment_probes':probes,'bridge_envelopes':bridges,
'authored_materials':sorted(materials),'explicit_mutable_tag_intersections':explicit,
'conclusion':'Tank reference/attachment cells covered. Bridge hit zones are not exact geometry: endpoints and deep supports can lie outside. Forbidden profiles have no breach/fire branch; authored structural materials are not their glass/soil/plant targets. No terrain/index production change required for current authored content.',
'limitations':'Geometry formula evaluation and explicit tags only. Actual generated blocks, dynamic/nested tags, Minecraft index, collection/application, saves and multiplayer require Fabric GameTest/runtime; NOT EXECUTED.'},indent=2))
print('PASS static geometry/source gates; terrainAllowed and findPrimaryAt unchanged; no ordinary-terrain protection expansion')
