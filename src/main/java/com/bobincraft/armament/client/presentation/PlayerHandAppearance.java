package com.bobincraft.armament.client.presentation;

import com.bobincraft.armament.api.WeaponId;
import com.bobincraft.armament.client.model.glb.GlbModelAsset;
import com.bobincraft.armament.serialization.*;
import com.bobincraft.armament.validation.Checks;
import java.util.*;

/** Client metadata only. The Minecraft bridge supplies a borrowed, live player skin. */
public record PlayerHandAppearance(int schemaVersion,Map<String,WeaponId> profiles,Map<String,String> materials) {
    public static final Set<String> VARIANTS=Set.of("default","slim");
    public static final Set<String> ROLES=Set.of("base_right","base_left","sleeve_right","sleeve_left");
    public PlayerHandAppearance {
        Checks.version(schemaVersion);
        Checks.require(profiles!=null&&profiles.keySet().equals(VARIANTS),"$.profiles","expected default and slim profile resources");
        profiles.forEach((key,value)->Checks.require(value!=null&&value.path().endsWith(".profile.json"),"$.profiles."+key,"expected .profile.json resource"));
        Checks.require(new HashSet<>(profiles.values()).size()==2,"$.profiles","Steve and Alex require distinct Blender-authored assets");
        Checks.require(materials!=null&&materials.keySet().equals(ROLES),"$.materials","expected base/sleeve material for each hand");
        Set<String> names=new HashSet<>();
        materials.forEach((role,name)->{Checks.name(name,"$.materials."+role);Checks.require(names.add(name),"$.materials."+role,"material is assigned more than one skin role");});
        profiles=Map.copyOf(profiles);materials=Map.copyOf(materials);
    }
    public static PlayerHandAppearance read(String json){
        var f=new JsonFields(StrictJson.parse(json),"$","schema_version","profiles","materials");
        var p=new JsonFields(f.get("profiles"),"$.profiles","default","slim");
        var m=new JsonFields(f.get("materials"),"$.materials","base_right","base_left","sleeve_right","sleeve_left");
        Map<String,WeaponId> profiles=new HashMap<>();for(String key:VARIANTS)profiles.put(key,p.id(key));
        Map<String,String> materials=new HashMap<>();for(String key:ROLES)materials.put(key,m.string(key));
        return new PlayerHandAppearance(f.intValue("schema_version"),profiles,materials);
    }
    /** Compile once at reload. No string lookup, image copying or geometry scaling per frame. */
    public SkinMaterials bind(GlbModelAsset model){Checks.present(model,"$.model");return new SkinMaterials(this,model);}
    public SkinMaterials bind(GlbModelAsset model,String variant){
        Checks.require(variant!=null&&VARIANTS.contains(variant),"$.profiles","unknown arm variant "+variant);
        SkinMaterials result=bind(model);result.validateUvs(model,variant.equals("slim")?3:4);return result;
    }
    public static final class SkinMaterials {
        public enum Role { EMBEDDED,BASE_RIGHT,BASE_LEFT,SLEEVE_RIGHT,SLEEVE_LEFT }
        private final Role[] roles;
        private SkinMaterials(PlayerHandAppearance appearance,GlbModelAsset model){
            roles=new Role[model.materials().size()];Arrays.fill(roles,Role.EMBEDDED);Set<String> found=new HashSet<>();
            for(int i=0;i<roles.length;i++){
                var material=model.materials().get(i);
                for(var entry:appearance.materials().entrySet())if(entry.getValue().equals(material.name())){
                    Checks.require(found.add(entry.getKey()),"$.materials."+entry.getKey(),"ambiguous GLB material name "+material.name());
                    var role=Role.valueOf(entry.getKey().toUpperCase(Locale.ROOT));var color=material.baseColor();
                    Checks.require(color.r()==1&&color.g()==1&&color.b()==1&&color.a()==1,"$.materials."+entry.getKey(),"player skin must not be tinted by a fixed base color");
                    Checks.require(material.alpha()==(role==Role.SLEEVE_LEFT||role==Role.SLEEVE_RIGHT?GlbModelAsset.Alpha.MASK:GlbModelAsset.Alpha.OPAQUE),"$.materials."+entry.getKey(),"base skin requires OPAQUE; sleeve requires MASK");
                    roles[i]=role;
                }
            }
            Checks.require(found.equals(ROLES),"$.materials","GLB is missing player skin/sleeve material roles: "+difference(found));
        }
        private static Set<String> difference(Set<String> found){Set<String> missing=new HashSet<>(ROLES);missing.removeAll(found);return missing;}
        private void validateUvs(GlbModelAsset model,int width){
            Set<Role> drawn=new HashSet<>();
            for(var primitive:model.primitives()){
                Role role=role(primitive.material());if(role==Role.EMBEDDED)continue;drawn.add(role);
                boolean left=role==Role.BASE_LEFT||role==Role.SLEEVE_LEFT,sleeve=role==Role.SLEEVE_LEFT||role==Role.SLEEVE_RIGHT;
                double u=left?(sleeve?48:32):40,v=left?48:(sleeve?32:16);
                for(var vertex:primitive.vertices()){
                    double x=vertex.u()*64,y=vertex.v()*64;
                    boolean sides=between(x,u,u+8+2*width)&&between(y,v+4,v+16),caps=between(x,u+4,u+4+2*width)&&between(y,v,v+4);
                    Checks.require(sides||caps,"$.materials."+role.name().toLowerCase(Locale.ROOT),"UV "+x+","+y+" is outside the "+width+" pixel arm net on a 64x64 skin");
                }
            }
            Checks.require(drawn.containsAll(Set.of(Role.BASE_LEFT,Role.BASE_RIGHT,Role.SLEEVE_LEFT,Role.SLEEVE_RIGHT)),"$.materials","all four skin layers need actual GLB mesh primitives");
        }
        private static boolean between(double value,double min,double max){return value>=min-1e-4&&value<=max+1e-4;}
        public Role role(int material){return roles[material];}
        public boolean playerSkin(int material){return role(material)!=Role.EMBEDDED;}
        public boolean visible(int material,boolean rightSleeve,boolean leftSleeve){return switch(role(material)){case SLEEVE_RIGHT->rightSleeve;case SLEEVE_LEFT->leftSleeve;default->true;};}
    }
}
