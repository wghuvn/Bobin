"""Stage 5 changes against the exact Minecraft-approved R2 source archive; not a Fabric build."""
from pathlib import Path
import hashlib, json, zipfile,sys
root=Path(__file__).resolve().parents[2]
baseline=Path(sys.argv[1]) if len(sys.argv)>1 else root.parents[1]/'deliverables/BobinCraft_Stabilized_Full_Source.zip'
assert hashlib.sha256(baseline.read_bytes()).hexdigest()=='bc6471e819a4bd1bbde0cd0958cbfcf8272748e3f48dd96efe51dc36f695344f','Wrong R2 archive'
allowed={'BobinCraftClient.java','destruction/DestructionGameTestSuite.java','destruction/RefineryTransitions.java','destruction/StructureUpdateQueue.java',
'destruction/StructureNetworkSync.java','destruction/StructureDamageManager.java','destruction/StructureCollapseManager.java',
'destruction/client/DestructionKeyframeAnimator.java','destruction/client/StructureDestructionClient.java','mixin/RefineryExternalEditMixin.java'}
changed=[];unchanged=0
with zipfile.ZipFile(baseline) as z:
    manifest=json.loads(z.read('STABILIZATION_MANIFEST.json'));assert manifest['revision']==2 and len(manifest['final_files'])==2325
    for path,h in manifest['final_files'].items():
        old=z.read('Bobin_craft/'+path);assert hashlib.sha256(old).hexdigest()==h
        current=root/path;assert current.is_file(),'Deleted R2 project file '+path
        if old!=current.read_bytes():
            if path.startswith('src/'):
                assert path.removeprefix('src/main/java/com/bobincraft/') in allowed,'Unrelated R2 source/resource change: '+path
                changed.append(path)
        else:unchanged+=1
    before=z.read('Bobin_craft/src/main/java/com/bobincraft/destruction/StructureDamageManager.java').decode()
    after=(root/'src/main/java/com/bobincraft/destruction/StructureDamageManager.java').read_text()
    assert before.split('    public static synchronized void forceStage(')[0]==after.split('    public static synchronized void forceStage(')[0], 'Damage, activation or structural policy changed'
assert len(changed)==len(allowed),(len(changed),changed)
anim=(root/'src/main/java/com/bobincraft/destruction/client/DestructionKeyframeAnimator.java').read_text()
for obsolete in ('resolveGroundSurfaceY','SETTLED_WRECKAGE','bakeSequenceToAftermath','matrices.scale('):assert obsolete not in anim,obsolete
renderer=(root/'src/main/java/com/bobincraft/destruction/client/StructureDebrisRenderer.java').read_text()
assert 'matrices.scale(' not in renderer and 'Commit.REMOVED' in renderer
assert 'world.setBlockState' not in renderer and 'DebrisCollision.tick' not in renderer
manager=(root/'src/main/java/com/bobincraft/destruction/debris/StructureDebrisManager.java').read_text()
assert 'c.commit=Commit.ARMED;c.durable=false' in manager and 'DebrisLifecycle.mayRemove' in manager
assert 'claimedOrProtected' in manager and 'Retarget uncommitted constituents atomically' in manager
assert 'ioFailed' in (root/'src/main/java/com/bobincraft/destruction/debris/DebrisPersistentState.java').read_text()
assert 'viewOrigin(player)' in (root/'src/main/java/com/bobincraft/destruction/debris/DebrisNetworking.java').read_text()
assert 'bridgeSupportGraph' not in manager
for forbidden in ('BridgeSupportGraph.java','BridgeComponentLayout.java'):assert not list((root/'src').rglob(forbidden))
for file in (root/'src').rglob('*.json'):json.loads(file.read_text())
print('PASS exact R2 baseline: 2,325 hashes; no original files deleted; 10 permitted original Java modifications')
print('PASS all other R2 source/resources byte-identical, including damage/policy prefix, smoke budgets/appearance, motor/audio, transient chips, world generation, saves, all six animation/model assets')
print('PASS static ownership wiring: no legacy aftermath/scaled block rendering; new client is render-only and committed-source gated; source transfer and drone observer present')
print('Stage 6 and Stage 7 NOT STARTED. Static source checks do not establish Minecraft runtime or visual acceptance.')
