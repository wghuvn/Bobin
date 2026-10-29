import java.nio.file.*;
import java.util.*;
import javax.tools.*;
import com.sun.source.util.JavacTask;

/** java docs/Stabilization/Verify.java [project-root]. Uses the installed JDK compiler module. */
class Verify {
    public static void main(String[] args) throws Exception {
        Path root=args.length==0?Path.of("."):Path.of(args[0]);
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        if(compiler==null)throw new IllegalStateException("A Java 17 compiler module is required");
        var diagnostics=new DiagnosticCollector<JavaFileObject>();
        try(var fm=compiler.getStandardFileManager(diagnostics,null,null)) {
            List<Path> all=new ArrayList<>();
            for(String dir:List.of("src/main/java","src/test/java"))try(var paths=Files.walk(root.resolve(dir))) {
                paths.filter(p->p.toString().endsWith(".java")).forEach(all::add);
            }
            var task=(JavacTask)compiler.getTask(null,fm,diagnostics,List.of("--release","17","-proc:none"),null,fm.getJavaFileObjectsFromPaths(all));
            task.parse();
            boolean error=false;for(var d:diagnostics.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR){System.out.println(d);error=true;}
            if(error)throw new AssertionError("Java syntax failure");
            System.out.println("PASS Java 17 syntax parse: "+all.size()+" files (not Minecraft symbol resolution)");
        }
        var kernels=List.of("warzone/BpSpec","warzone/Blueprint",
            "drone/DroneBlastType","combat/ExplosionVisualProfile","combat/ExplosionVisualEvent",
            "combat/ExplosionVisualEventWindow","combat/VoxelExplosionShape","combat/WeaponExplosionLayers","combat/TransientDebrisCollision",
            "destruction/IndustrialSmokeMotion","destruction/IndustrialSmokeBudget","destruction/IndustrialSmokeColor","destruction/RefineryGeometryRules",
            "destruction/TransitionLedger","destruction/StructureDamagePolicy","destruction/ExplosionSoundMixer","destruction/StructuralSupportSolver");
        var contracts=List.of("combat/ExplosionVisualContract","combat/Stage21Contract","destruction/SmokeMotionContract",
            "destruction/RefineryTransitionContract","destruction/StabilizationContract","destruction/RealisticDestructionContract");
        Path out=Files.createTempDirectory("bobin-stabilization-java-");
        try {
            Path stubDir = out.resolve("stubs");
            Files.createDirectories(stubDir.resolve("com/bobincraft/warzone"));
            Files.writeString(stubDir.resolve("com/bobincraft/warzone/CableGeometry.java"),
                    "package com.bobincraft.warzone;\npublic final class CableGeometry {\n"
                    + "  public record Vertex(double x,double y,double z) {}\n"
                    + "  public record Segment(Vertex a,Vertex b) {}\n}\n");
            List<String> options=new ArrayList<>(List.of("--release","17","-d",out.toString(),
                    stubDir.resolve("com/bobincraft/warzone/CableGeometry.java").toString()));
            for(String c:kernels) {
                options.add(root.resolve("src/main/java/com/bobincraft/"+c+".java").toString());
            }
            for(String c:contracts)options.add(root.resolve("src/test/java/com/bobincraft/"+c+".java").toString());
            int code=compiler.run(null,System.out,System.err,options.toArray(String[]::new));
            if(code!=0)throw new AssertionError("Production kernel compilation failed: "+code);
            try(var loader=new java.net.URLClassLoader(new java.net.URL[]{out.toUri().toURL()},ClassLoader.getPlatformClassLoader())) {
                for(String c:contracts)Class.forName("com.bobincraft."+c.replace('/','.'),true,loader)
                    .getMethod("main",String[].class).invoke(null,(Object)new String[0]);
            }
        } finally {
            try(var files=Files.walk(out)){for(var f:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(f);}
        }
    }
}
