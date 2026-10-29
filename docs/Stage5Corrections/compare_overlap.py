"""Compare the actual old/new production solver. This is NOT a Minecraft test.
Usage: python docs/Stage5Corrections/compare_overlap.py BASELINE_ROOT CORRECTED_ROOT
"""
import pathlib,subprocess,tempfile,sys,json
PROBE='''import java.util.*;import com.bobincraft.destruction.debris.*;import static com.bobincraft.destruction.debris.DebrisFragment.*;
class OverlapProbe {public static void main(String[] args){
 var f=DebrisPlan.create("test:zone","bridge",UUID.fromString("11111111-2222-3333-4444-555555555555"),List.of(new DebrisPlan.Source(0,0,0,"minecraft:stone"))).get(0);
 var c=f.cells.get(0);c.commit=Commit.REMOVED;c.shapes=List.of(new B(c.local.x(),c.local.y(),c.local.z(),c.local.x()+1,c.local.y()+1,c.local.z()+1));f.phase=Phase.FALLING;f.reconciled=true;f.position=f.position.add(new V(0,-.03,0));
 for(int i=0;i<40;i++)DebrisCollision.tick(f,b->List.of(new DebrisCollision.Obstacle(new B(-10,-1,-10,10,0,10),88)));
 System.out.println("bottom="+f.bounds().y0()+" phase="+f.phase+" age="+f.age+" velocity="+f.velocity);
}}'''
for label,root in zip(['supplied_baseline','correction'],sys.argv[1:]):
 root=pathlib.Path(root).resolve()
 with tempfile.TemporaryDirectory() as tmp:
  d=pathlib.Path(tmp);(d/'OverlapProbe.java').write_text(PROBE)
  names=['DebrisFragment','DebrisPlan','DebrisCollision']
  if (root/'src/main/java/com/bobincraft/destruction/debris/DebrisShape.java').exists():names.append('DebrisShape')
  subprocess.run(['java','-XX:-UsePerfData','com.sun.tools.javac.Main','--release','17','-d',str(d),*[str(root/f'src/main/java/com/bobincraft/destruction/debris/{name}.java') for name in names],str(d/'OverlapProbe.java')],check=True)
  result=subprocess.check_output(['java','-XX:-UsePerfData','-cp',str(d),'OverlapProbe'],text=True).strip()
  print(label+': '+result)
  if label=='supplied_baseline':assert 'phase=FALLING age=0' in result
  else:assert 'phase=SLEEPING' in result
