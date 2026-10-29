package com.bobincraft.armament.client.resource.fabric;

import com.bobincraft.armament.client.model.glb.GlbModelAsset;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.texture.*;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** GPU texture ownership for one cached static scene. Meshes remain in immutable CPU memory. */
public final class GlbGpuAsset implements AutoCloseable {
    private static final AtomicInteger LIVE_TEXTURES=new AtomicInteger();
    private static final AtomicLong ALLOCATIONS=new AtomicLong();
    private final TextureManager manager;
    private final Map<Identifier,NativeImageBackedTexture> owned=new LinkedHashMap<>();
    private final List<Identifier> textures=new ArrayList<>();
    private final GlbModelAsset asset;
    private Identifier white;
    private boolean closed;

    private GlbGpuAsset(TextureManager manager,GlbModelAsset asset){this.manager=manager;this.asset=asset;}
    public static GlbGpuAsset upload(TextureManager manager,GlbModelAsset asset,long generation)throws IOException{
        RenderSystem.assertOnRenderThread();
        GlbGpuAsset result=new GlbGpuAsset(manager,asset);
        String allocation=generation+"_"+ALLOCATIONS.incrementAndGet(); // Independent reload listeners must never share texture names.
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);int bound=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try{
            NativeImage white=new NativeImage(1,1,false);white.setColor(0,0,-1);
            result.white=result.add(white,new Identifier("bobin_craft","armament/glb/"+allocation+"/white"),10497,10497,9728,9728);
            for(int i=0;i<asset.textures().size();i++){
                var sampler=asset.textures().get(i);var source=asset.images().get(sampler.image());
                NativeImage image=NativeImage.read(new ByteArrayInputStream(source.encoded()));
                Identifier id=new Identifier("bobin_craft","armament/glb/"+allocation+"/"+i);
                result.textures.add(result.add(image,id,sampler.wrapS(),sampler.wrapT(),sampler.minFilter(),sampler.magFilter()));
            }
            return result;
        }catch(Exception e){result.close();throw new IOException("GLB texture upload failed: "+e.getMessage(),e);}
        finally{RenderSystem.activeTexture(GL13.GL_TEXTURE0);RenderSystem.bindTexture(bound);RenderSystem.activeTexture(active);}
    }
    private Identifier add(NativeImage image,Identifier id,int wrapS,int wrapT,int min,int mag){
        NativeImageBackedTexture texture;
        try{texture=new NativeImageBackedTexture(image);}catch(RuntimeException e){image.close();throw e;}
        try{
            manager.registerTexture(id,texture);texture.upload();texture.bindTexture();
            RenderSystem.texParameter(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,wrapS);
            RenderSystem.texParameter(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,wrapT);
            RenderSystem.texParameter(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,min);
            RenderSystem.texParameter(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,mag);
            if(min>=9984)GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            owned.put(id,texture);LIVE_TEXTURES.incrementAndGet();return id;
        }catch(RuntimeException e){manager.destroyTexture(id);texture.close();throw e;}
    }
    public GlbModelAsset asset(){return asset;}
    public Identifier texture(int index){return index<0?white:textures.get(index);}
    public static int liveTextures(){return LIVE_TEXTURES.get();}
    @Override public void close(){
        RenderSystem.assertOnRenderThread();if(closed)return;closed=true;
        for(var entry:owned.entrySet()){
            manager.destroyTexture(entry.getKey());
            // Also release the NativeImage backing allocation; not just its GL name.
            entry.getValue().close();LIVE_TEXTURES.decrementAndGet();
        }
        owned.clear();textures.clear();
    }
}
