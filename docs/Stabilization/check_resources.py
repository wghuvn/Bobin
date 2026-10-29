"""Static resources and protected-source comparison; not a Fabric build.
Usage: python docs/Stabilization/check_resources.py [path/to/original/Bobin_craft(1).zip]
"""
from pathlib import Path
import json, gzip, io, struct, collections, hashlib, zipfile, sys
root=Path(__file__).resolve().parents[2]
for p in root.joinpath('src').rglob('*.json'): json.loads(p.read_text())
print('PASS JSON:',len(list(root.joinpath('src').rglob('*.json'))))
a=root/'src/main/resources/assets/bobin_craft'
missing=[]; sprites=0
for p in (a/'particles').glob('*.json'):
 for tex in json.loads(p.read_text()).get('textures',[]):
  if tex.startswith('bobin_craft:'):
   sprites+=1
   if not (a/'textures/particle'/((tex.split(':',1)[1])+'.png')).is_file():missing.append(tex)
print('PARTICLE_SPRITE_REFERENCES',sprites,'MISSING',missing);assert not missing
for event,body in json.loads((a/'sounds.json').read_text()).items():
 for sound in body.get('sounds',[]):
  name=sound if isinstance(sound,str) else sound['name']
  if isinstance(sound,dict) and sound.get('type')=='event':continue
  if name.startswith('bobin_craft:') and not (a/'sounds'/(name.split(':',1)[1]+'.ogg')).is_file():missing.append(name)
print('SOUND_REFERENCES_MISSING',missing);assert not missing
meta=json.loads((root/'src/main/resources/fabric.mod.json').read_text())
assert meta['entrypoints']['fabric-gametest']==['com.bobincraft.destruction.DestructionGameTestSuite']
mixins=json.loads((root/'src/main/resources/bobin_craft.mixins.json').read_text())
for c in mixins['mixins']+mixins['client']:
 assert (root/'src/main/java'/((mixins['package']+'.'+c).replace('.','/')+'.java')).is_file(),c
assert 'RefineryBlueprintScopeMixin' not in mixins['mixins'] and 'RefineryGeneratedCellMixin' not in mixins['mixins']
print('PASS metadata: all mixin classes present; GameTest entrypoint explicit (application not executed)')
def nbt(p):
 raw=p.read_bytes();data=gzip.decompress(raw) if raw[:2]==b'\x1f\x8b' else raw
 f=io.BytesIO(data)
 def number(fmt):return struct.unpack('>'+fmt,f.read(struct.calcsize('>'+fmt)))[0]
 def string():return f.read(number('H')).decode()
 def tag(kind):
  if kind in (1,2,3,4,5,6):return number({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[kind])
  if kind==7:return f.read(number('i'))
  if kind==8:return string()
  if kind==9:
   sub=number('B');return [tag(sub) for _ in range(number('i'))]
  if kind==10:
   d={}
   while True:
    sub=number('B')
    if not sub:return d
    key=string();d[key]=tag(sub)
  if kind in (11,12):return [number('i' if kind==11 else 'q') for _ in range(number('i'))]
  raise ValueError(kind)
 kind=number('B');string();return tag(kind)
count=0
for p in (root/'src/main/resources/data/bobin_craft/structures').rglob('*.nbt'):
 d=nbt(p);count+=1
 for b in d.get('blocks',[]):
  assert 0<=b['state']<len(d['palette'])
  assert all(0<=v<d['size'][i] for i,v in enumerate(b['pos']))
 if p.parent.name=='bridge':
  materials=collections.Counter(d['palette'][b['state']]['Name'] for b in d['blocks'])
  print('BRIDGE_NBT',p.name,'SIZE',d['size'],'COUNTS',dict(materials))
print('PASS NBT structure syntax/indices/coordinates:',count,'(does not prove useful template geometry)')
if len(sys.argv)>1:
 baseline=Path(sys.argv[1]);print('BASELINE_SHA256',hashlib.sha256(baseline.read_bytes()).hexdigest())
 permitted={'warzone/BlueprintBlitter.java','warzone/ChunkWriter.java','drone/BlastTerrain.java','drone/DroneExplosionEffects.java',
            'drone/DroneContent.java','drone/client/DroneRotorSound.java','weapon/grom7/Grom7ChargeEntity.java'}
 checked=0
 with zipfile.ZipFile(baseline) as z:
  for entry in z.infolist():
   prefix='Bobin_craft/src/main/java/com/bobincraft/'
   if not entry.filename.startswith(prefix) or entry.is_dir():continue
   relative=entry.filename[len(prefix):]
   if relative.startswith(('warzone/','drone/','vehicle/','npc/','raid/','injury/','weapon/')) and relative not in permitted:
    assert z.read(entry)==(root/'src/main/java/com/bobincraft'/relative).read_bytes(),relative
    checked+=1
  path='src/main/java/com/bobincraft/weapon/grom7/Grom7ChargeEntity.java'
  old=z.read('Bobin_craft/'+path).decode();new=(root/path).read_text()
  marker='// 3. Scorch terrain'
  assert old.split(marker)[0]==new.split(marker)[0]
  start='// 4. Sympathetic detonation';end='// Layered realistic demolition sound choreography:'
  assert old.split(start)[1].split(end)[0]==new.split(start)[1].split(end)[0]
  for entry in z.infolist():
   if '/structures/' in entry.filename and entry.filename.endswith('.nbt') or '/destruction/' in entry.filename and entry.filename.endswith(('.animation.json','.geo.json')):
    assert z.read(entry)==(root/entry.filename.removeprefix('Bobin_craft/')).read_bytes(),entry.filename
  for path in ['destruction/StructurePersistentState.java','destruction/DestructibleStructure.java','destruction/RefineryTransitionState.java','destruction/StructureGeometrySnapshot.java']:
   assert z.read(prefix+path)==(root/'src/main/java/com/bobincraft'/path).read_bytes(),path
 print('PASS protected source byte comparison:',checked,'files; GROM gameplay prefix, all NBT/animation models, existing save serializers and snapshot preserved')
print('STAGE 5 NOT STARTED')
