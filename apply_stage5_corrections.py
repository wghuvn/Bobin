"""Apply only to the exact supplied Stage 5 project. Preserves unrelated files.
Usage: python apply_stage5_corrections.py /path/to/Bobin_craft [--dry-run]
"""
from pathlib import Path
import hashlib,json,sys,shutil,tempfile,os
here=Path(__file__).resolve().parent
manifest=json.loads((here/'manifest.json').read_text())
root=Path(sys.argv[1]).resolve()
if not root.is_dir():raise SystemExit('Project directory does not exist')
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest() if p.is_file() else None
def checked(path):
 p=root/path
 if any(q.is_symlink() for q in [p,*p.parents] if q!=root.parent):
  raise SystemExit('Symlink refused: '+path)
 if root not in p.resolve().parents:raise SystemExit('Escaping project path: '+path)
 return p
for path in manifest['target_files']:checked(path)
if all(digest(root/path)==value for path,value in manifest['target_files'].items()):
 print('ALREADY_APPLIED: all target file hashes match; no mutation');raise SystemExit(0)
for path,value in manifest['baseline_files'].items():
 if digest(checked(path))!=value:raise SystemExit('BASELINE_MISMATCH: '+path)
for path in manifest['added']:
 if checked(path).exists():raise SystemExit('ADDED_PATH_COLLISION: '+path)
changes=manifest['added']+manifest['modified']
for path in changes:
 payload=here/'files'/path
 if digest(payload)!=manifest['target_files'][path]:raise SystemExit('PAYLOAD_MISMATCH: '+path)
if '--dry-run' in sys.argv:
 print('DRY_RUN_OK: exact baseline and every payload hash verified');raise SystemExit(0)
with tempfile.TemporaryDirectory(prefix='bobin-stage5-backup-',dir=root.parent) as tmp:
 backup=Path(tmp);written=[]
 try:
  for path in changes:
   target=checked(path);target.parent.mkdir(parents=True,exist_ok=True)
   if target.exists():
    old=backup/path;old.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(target,old)
   stage=target.with_name(target.name+'.stage5-correction-tmp')
   if stage.exists():raise RuntimeError('Temporary path collision: '+str(stage))
   try:
    shutil.copyfile(here/'files'/path,stage);os.replace(stage,target);written.append(path)
   finally:
    if stage.exists():stage.unlink()
  if not all(digest(root/path)==value for path,value in manifest['target_files'].items()):raise RuntimeError('Final hash verification failed')
 except BaseException:
  for path in reversed(written):
   target=root/path;old=backup/path
   if old.exists():shutil.copy2(old,target)
   elif target.exists():target.unlink()
  raise
print('APPLIED: full target hash map matches; unrelated files preserved')
