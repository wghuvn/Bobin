import java.nio.file.*;
import java.util.*;
import javax.tools.*;
/** Java 17 actual production kernels; the Fabric adapters still require a real Gradle/GameTest run. */
class Verify {
    public static void main(String[] args)throws Exception {
        var compiler=ToolProvider.getSystemJavaCompiler();if(compiler==null)throw new IllegalStateException("JDK compiler required");
        Path root=Path.of("."),out=Files.createTempDirectory("bobin-stage5-kernels-");
        try {
            var options=new ArrayList<String>(List.of("--release","17","-d",out.toString()));
            for(String name:List.of("DebrisFragment","DebrisPlan","DebrisCollision","DebrisShape","DebrisSupportIndex","DebrisLifecycle","DebrisJournal","DebrisRevisionWindow"))options.add("src/main/java/com/bobincraft/destruction/debris/"+name+".java");
            options.add("src/test/java/com/bobincraft/destruction/DebrisContract.java");
            if(compiler.run(null,System.out,System.err,options.toArray(String[]::new))!=0)throw new AssertionError("Production kernel compilation");
            try(var loader=new java.net.URLClassLoader(new java.net.URL[]{out.toUri().toURL()},ClassLoader.getPlatformClassLoader())) {
                Class.forName("com.bobincraft.destruction.DebrisContract",true,loader).getMethod("main",String[].class).invoke(null,(Object)new String[0]);
            }
        }finally{try(var files=Files.walk(out)){for(var p:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
    }
}
