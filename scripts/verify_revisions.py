#!/usr/bin/env python3
"""Java 17 contracts; optionally run full Gradle build and JUnit using --gradle."""
from pathlib import Path
import subprocess,sys,tempfile,json
root=Path(__file__).resolve().parents[1]
base=root/'src/main/java/com/bobincraft'
sources=[base/name for name in ['drone/DroneBlastType.java','combat/ExplosionVisualProfile.java','combat/ExplosionVisualEvent.java','combat/ExplosionVisualEventWindow.java','combat/VoxelExplosionShape.java','combat/WeaponExplosionLayers.java','destruction/IndustrialSmokeMotion.java','destruction/TransitionLedger.java','destruction/RefineryGeometryRules.java'] if (base/name).exists()]
sources+=list((root/'src/test/java').rglob('*Contract.java'))
for p in root.rglob('*.json'):
 if 'build' not in p.parts:json.loads(p.read_text())
with tempfile.TemporaryDirectory() as output:
 subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','--release','17','-d',output]+list(map(str,sources)),check=True)
 for p in sources:
  if p.name.endswith('Contract.java'):
   cls=p.read_text().split('package ')[1].split(';')[0]+'.'+p.stem
   subprocess.run(['java','-cp',output,cls],check=True)
if '--gradle' in sys.argv:
 wrapper=['cmd','/c','gradlew.bat'] if sys.platform=='win32' else ['bash','gradlew']
 for args in [['clean','build','-x','test'],['test']]:subprocess.run(wrapper+args,cwd=root,check=True)
