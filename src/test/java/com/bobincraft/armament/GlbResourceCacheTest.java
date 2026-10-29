package com.bobincraft.armament;

import com.bobincraft.armament.client.model.glb.ReloadableResourceCache;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

final class GlbResourceCacheTest {
    private static final class Owned implements AutoCloseable{
        private final int[] alive;private boolean closed;
        Owned(int[] alive){this.alive=alive;alive[0]++;}
        public void close(){assertFalse(closed,"resource must close exactly once");closed=true;alive[0]--;}
    }
    @Test void repeatedReloadsDoNotAccumulateOwnedResources()throws Exception{
        int[] alive={0};var cache=new ReloadableResourceCache<String,Owned>();
        for(int i=0;i<200;i++){
            var value=cache.reload("fixture",()->new Owned(alive));assertEquals(1,alive[0]);assertEquals(1,cache.size());
            assertSame(value,cache.getOrLoad("fixture",()->{throw new AssertionError("cache miss");}));
        }
        assertEquals(200,cache.generation());cache.close();cache.close();assertEquals(0,alive[0]);
    }
    @Test void failedReloadPreservesLastGoodResourceAndGeneration()throws Exception{
        int[] alive={0};var cache=new ReloadableResourceCache<String,Owned>();var good=cache.reload("fixture",()->new Owned(alive));
        assertThrows(IOException.class,()->cache.reload("fixture",()->{throw new IOException("corrupt GLB");}));
        assertSame(good,cache.get("fixture").orElseThrow());assertEquals(1,alive[0]);assertEquals(1,cache.generation());
        cache.close();assertEquals(0,alive[0]);
    }
}
