#!/usr/bin/env python3
"""Run Java 17 contracts and verify scope against the exact supplied baseline ZIP."""
import hashlib, json, pathlib, subprocess, sys, tempfile, zipfile
root = pathlib.Path(__file__).resolve().parents[1]
modified = {
'src/main/java/com/bobincraft/combat/CombatEffects.java',
'src/main/java/com/bobincraft/combat/VoxelExplosionShape.java',
'src/main/java/com/bobincraft/combat/client/VoxelBlastClient.java',
'src/main/java/com/bobincraft/drone/DroneExplosion.java',
'src/main/java/com/bobincraft/drone/DroneExplosionEffects.java',
'src/main/java/com/bobincraft/weapon/grom7/Grom7ChargeEntity.java'}
if len(sys.argv) > 1:
    baseline = pathlib.Path(sys.argv[1])
    assert hashlib.sha256(baseline.read_bytes()).hexdigest() == '16b502c9cea8a9d0698df52f51c9354aad4e4dba0863c2cc433c18c8932bc57d'
    with zipfile.ZipFile(baseline) as archive:
        changed = set()
        for name in archive.namelist():
            if name.endswith('/'): continue
            rel = name.removeprefix('Bobin_craft/')
            target = root / rel
            assert target.is_file(), 'Missing baseline file: ' + rel
            if target.read_bytes() != archive.read(name): changed.add(rel)
        assert changed == modified, changed
        for rel, old, new in [
            ('src/main/java/com/bobincraft/drone/DroneExplosion.java', 'play(world, owner,', 'play(world, source, owner,'),
            ('src/main/java/com/bobincraft/weapon/grom7/Grom7ChargeEntity.java', 'playDemolitionBlast(world, owner, center,', 'playDemolitionBlast(world, owner, this.chargeId, center,')]:
            before=archive.read('Bobin_craft/'+rel).decode()
            assert (root/rel).read_text() == before.replace(old,new), 'Gameplay callsite changed beyond visual arguments'
        before=archive.read('Bobin_craft/src/main/java/com/bobincraft/combat/CombatEffects.java').decode()
        assert (root/'src/main/java/com/bobincraft/combat/CombatEffects.java').read_text().split('    public static void blood')[1] == before.split('    public static void blood')[1]
    print('PASS: exact baseline; six permitted modifications; zero deleted files; all other original files unchanged')
files = list((root/'src').rglob('*.json'))
for file in files: json.loads(file.read_text())
print('PASS:',len(files),'JSON resources')
server=(root/'src/main/java/com/bobincraft/drone/DroneExplosionEffects.java').read_text()
client=(root/'src/main/java/com/bobincraft/combat/client/VoxelBlastClient.java').read_text()
assert 'DestructionParticles' not in server
assert 'ExplosionVisualProfile.GROM7_DEMOLITION' in server
assert 'source instanceof com.bobincraft.weapon.heavy.RpgProjectileEntity' in server
assert 'payload.handThrown()' in server
assert 'DestructionParticles.' not in client.split('    /**\n     * Spawns a soft')[0]
for token in ['VOXEL_BLAST','VOXEL_MEGA_BLAST','VOXEL_DEMOLITION_BLAST']:
    assert token not in client
assert client.count('registerGlobalReceiver(') == 1
print('PASS: weapon dispatch has no industrial particles; one receiver; old packet emitters removed')
sources=['src/main/java/com/bobincraft/drone/DroneBlastType.java',
'src/main/java/com/bobincraft/combat/ExplosionVisualProfile.java',
'src/main/java/com/bobincraft/combat/ExplosionVisualEvent.java',
'src/main/java/com/bobincraft/combat/ExplosionVisualEventWindow.java',
'src/main/java/com/bobincraft/combat/VoxelExplosionShape.java',
'src/test/java/com/bobincraft/combat/ExplosionVisualContract.java']
with tempfile.TemporaryDirectory() as output:
    subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','--release','17','-d',output]+[str(root/s) for s in sources],check=True)
    subprocess.run(['java','-cp',output,'com.bobincraft.combat.ExplosionVisualContract'],check=True)
