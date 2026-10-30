package com.bobincraft.armament.client.presentation;

import com.bobincraft.armament.api.WeaponId;
import com.bobincraft.armament.client.model.WeaponModelLoader.ResourceSource;
import com.bobincraft.armament.client.model.glb.*;
import com.bobincraft.armament.client.animation.gltf.*;
import com.bobincraft.armament.client.resource.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;

/** Atomic CPU bundle: the model and all attachment metadata are validated before GPU upload. */
public record FirstPersonAsset(FirstPersonProfile profile,GlbModelAsset model,RigSockets sockets){
    public FirstPersonAsset{
        if(model.rig()==null)throw new IllegalArgumentException("first-person asset requires an animated rig");
        for(String clip:profile.clips().values())model.rig().clip(clip);
        if(!new GripAttachments(profile,sockets).matches(new RigFrame(model.rig())))throw new IllegalArgumentException("two-hand bind pose does not match object grip sockets");
        var bind=new RigFrame(model.rig());double[] frame=new double[16];
        for(var grip:profile.grips()){
            sockets.sample(grip.target(),bind,frame);GripFrameConvention.validate(frame,"object grip");
            sockets.sample(grip.hand(),bind,frame);GripFrameConvention.validate(frame,"hand target");
        }
    }
    public static FirstPersonAsset load(WeaponId profileId,ResourceSource source)throws IOException{
        try{
            FirstPersonProfile profile=FirstPersonProfile.read(text(profileId,source));
            GlbModelAsset model=new JgltfModelLoader().load(profile.model(),source);
            var rig=WeaponVisualJson.readRig(text(profile.rig(),source));var sockets=WeaponVisualJson.readSockets(text(profile.sockets(),source));
            return new FirstPersonAsset(profile,model,new RigSockets(model.rig(),rig,sockets));
        }catch(RuntimeException e){throw new IOException(profileId+": invalid first-person bundle: "+e.getMessage(),e);}
    }
    private static String text(WeaponId id,ResourceSource source)throws IOException{
        try(InputStream input=source.open(id)){
            byte[] bytes=input.readNBytes(1_048_577);if(bytes.length>1_048_576)throw new IOException(id+": metadata exceeds 1 MiB");
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        }
    }
}
