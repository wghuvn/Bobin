#!/usr/bin/env python3
"""Apply this exact checkpoint to a separate, unchanged baseline project.
Extract the patch ZIP separately, then: python apply_stabilization_patch.py /path/to/Bobin_craft
No network, Gradle, world save editing or cache removal is performed.
"""
import hashlib,json,shutil,sys,tempfile
from pathlib import Path
here=Path(__file__).resolve().parent
manifest=json.loads((here/'STABILIZATION_MANIFEST.json').read_text())
if len(sys.argv)!=2:raise SystemExit(__doc__)
target=Path(sys.argv[1]).expanduser().resolve()
if not (target/'build.gradle').is_file():raise SystemExit('Target must be the Bobin_craft project root.')
def path(root,relative):
 p=Path(relative)
 if p.is_absolute() or '..' in p.parts:raise SystemExit('Unsafe manifest path: '+relative)
 q=root/p
 if root.resolve() not in q.resolve().parents:raise SystemExit('Path leaves project root: '+relative)
 return q
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest() if p.is_file() else None
final=manifest['final_files'];baseline=manifest['baseline_files']
if all(digest(path(target,p))==h for p,h in final.items()) and all(not path(target,e['path']).exists() for e in manifest['deleted']):
 print('This exact stabilization checkpoint is already applied.');raise SystemExit(0)
# Validate everything before the first mutation, including unrelated original source files.
errors=[p for p,h in baseline.items() if digest(path(target,p))!=h]
errors += [e['path'] for e in manifest['added'] if path(target,e['path']).exists()]
if errors:raise SystemExit('Baseline differs. Nothing changed. Use the full source ZIP or reconcile first:\n'+'\n'.join(errors[:30]))
payload=here/'Bobin_craft'
changed=manifest['added']+manifest['modified']
for e in changed:
 if digest(path(payload,e['path']))!=e['after_sha256']:raise SystemExit('Patch payload is missing/invalid: '+e['path'])
backup=Path(tempfile.mkdtemp(prefix='Bobin_before_stabilization_',dir=target.parent))
for e in manifest['modified']+manifest['deleted']:
 dest=path(backup,e['path']);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(path(target,e['path']),dest)
(backup/'STABILIZATION_MANIFEST.json').write_text(json.dumps(manifest,indent=2)+'\n')
try:
 for e in changed:
  dest=path(target,e['path']);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(path(payload,e['path']),dest)
 for e in manifest['deleted']:path(target,e['path']).unlink()
 errors=[p for p,h in final.items() if digest(path(target,p))!=h]
 if errors:raise RuntimeError('Final byte verification failed: '+str(errors[:10]))
except BaseException:
 for e in manifest['modified']+manifest['deleted']:shutil.copy2(path(backup,e['path']),path(target,e['path']))
 for e in manifest['added']:
  dest=path(target,e['path'])
  if dest.is_file():dest.unlink()
 print('Patch failed; source files restored. Backup:',backup)
 raise
print('Applied and byte-verified:',len(manifest['added']),'added,',len(manifest['modified']),'modified,',len(manifest['deleted']),'deleted.')
print('Original modified/deleted files backed up at:',backup)
print('Build/runtime verification is still required. Stage 5 not started.')
