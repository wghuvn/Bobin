package com.bobincraft.armament.client.thirdperson;

import com.bobincraft.armament.api.WeaponId;
import com.bobincraft.armament.client.model.WeaponModelLoader.ResourceSource;
import com.bobincraft.armament.client.model.glb.*;
import java.io.IOException;

/** Immutable external-view asset. No player or hand meshes are allowed. */
public final class ThirdPersonAsset {
    public static final WeaponId DEFAULT=new WeaponId("bobin_craft","armament/debug/third_person_fixture.glb");
    public static final WeaponId SLIM=new WeaponId("bobin_craft","armament/debug/third_person_fixture_slim.glb");
    private final GlbModelAsset model;
    private final boolean slim;
    private final double[] rightContact,leftContact;
    public static ThirdPersonAsset load(ResourceSource source,boolean slim)throws IOException {
        return new ThirdPersonAsset(new JgltfModelLoader().load(slim?SLIM:DEFAULT,source),slim);
    }
    public ThirdPersonAsset(GlbModelAsset model,boolean slim){
        this.model=model;this.slim=slim;var rig=model.rig();
        if(rig==null||!rig.skins().isEmpty()||rig.meshes().size()!=7)throw new IllegalArgumentException("3P requires gauge-only geometry");
        for(String node:new String[]{"tp_motion","tp_stance","tp_body","tp_weapon","tp_arm_right","tp_arm_left","tp_grip_right","tp_grip_left","tp_head_assist"})rig.node(node);
        for(var action:PresentationAction.Action.values())rig.clip(action.key());
        rig.clip("crouch");rig.clip("run");rig.clip("inspect_recover");
        for(var mesh:rig.meshes()){
            int n=mesh.node();while(n>=0&&n!=rig.node("tp_weapon"))n=rig.nodes().get(n).parent();
            if(n<0)throw new IllegalArgumentException("player geometry must not be in the GLB");
        }
        double[] bind=new double[rig.nodes().size()*10];rig.copyBind(bind);
        rightContact=contact(bind,true);leftContact=contact(bind,false);
    }
    private double[] contact(double[] bind,boolean right){
        var rig=model.rig();String side=right?"right":"left";int n=rig.node("tp_contact_"+side);
        if(rig.nodes().get(n).parent()!=rig.node("tp_arm_"+side))throw new IllegalArgumentException("contact must be arm-local");
        double[] v={bind[n*10],bind[n*10+1],bind[n*10+2]};
        // The palm contact stays inside the distal four pixels of a full-length vanilla arm.
        double x=(right?1:-1)*(slim?.5:1)/16;
        if(Math.abs(v[0]-x)>1e-6||v[1]<-.625001||v[1]>-.374999||Math.abs(v[2])>1e-6)
            throw new IllegalArgumentException("contact outside the vanilla hand cuboid");
        for(var clip:rig.clips())for(var channel:clip.channels())if(channel.node()==n)throw new IllegalArgumentException("arm-local contacts cannot be animated");
        return v;
    }
    public GlbModelAsset model(){return model;}
    public boolean slim(){return slim;}
    public double[] shoulder(boolean right){return new double[]{(right?5:-5)/16.0,-(slim?2.5:2)/16,0};}
    public double[] armContact(boolean right){return (right?rightContact:leftContact).clone();}
}
