package com.bobincraft.armament.client.presentation;

import com.bobincraft.armament.api.WeaponId;
import com.bobincraft.armament.serialization.*;
import com.bobincraft.armament.validation.Checks;
import com.google.gson.*;
import java.util.*;

/** Client-only presentation metadata. Metres, XYZW quaternions, no model scaling. */
public record FirstPersonProfile(int schemaVersion,WeaponId id,WeaponId model,WeaponId rig,WeaponId sockets,
        List<Double> cameraOffset,List<Double> cameraRotation,double cameraClearance,double transitionSeconds,
        double movementThreshold,double contactMetres,double contactDegrees,Map<String,String> clips,List<Grip> grips,Inertia inertia) {
    public static final Set<String> ACTIONS=Set.of("idle","inspect","raise","lower","move");
    public record Grip(String name,String target,String hand){public Grip{Checks.name(name,"$.grips.name");Checks.name(target,"$.grips.target");Checks.name(hand,"$.grips.hand");}}
    public record Inertia(boolean enabled,double frequencyHz,double lagSeconds,double maxYawDegrees,double maxPitchDegrees,double metresPerDegree){
        public Inertia{range(frequencyHz,1,15,"$.inertia.frequency_hz");range(lagSeconds,0,0.05,"$.inertia.lag_seconds");range(maxYawDegrees,0,3,"$.inertia.max_yaw_degrees");range(maxPitchDegrees,0,3,"$.inertia.max_pitch_degrees");range(metresPerDegree,0,0.005,"$.inertia.metres_per_degree");}
    }
    public FirstPersonProfile{
        Checks.version(schemaVersion);Checks.present(id,"$.id");resource(model,".glb","$.model");resource(rig,".json","$.rig");resource(sockets,".json","$.sockets");
        vector(cameraOffset,3,-4,4,"$.camera_offset");vector(cameraRotation,4,-1,1,"$.camera_rotation");
        double norm=0;for(double v:cameraRotation)norm+=v*v;Checks.require(Math.abs(norm-1)<1e-5,"$.camera_rotation","expected unit quaternion");
        range(cameraClearance,0.06,0.25,"$.camera_clearance");range(transitionSeconds,0.05,1,"$.transition_seconds");
        range(movementThreshold,0.01,1,"$.movement_threshold");range(contactMetres,1e-5,0.005,"$.contact_metres");range(contactDegrees,0.001,1,"$.contact_degrees");
        Checks.require(clips!=null&&clips.keySet().equals(ACTIONS),"$.clips","expected exactly idle/inspect/raise/lower/move");
        clips.forEach((action,clip)->Checks.name(clip,"$.clips."+action));
        Checks.size(grips,2,2,"$.grips");Set<String> names=new HashSet<>(),targets=new HashSet<>(),hands=new HashSet<>();
        for(Grip grip:grips)Checks.require(names.add(grip.name())&&targets.add(grip.target())&&hands.add(grip.hand())&&!grip.target().equals(grip.hand()),"$.grips","duplicate/self attachment");
        Checks.require(names.equals(Set.of("primary","secondary")),"$.grips","expected primary and secondary grips");Checks.present(inertia,"$.inertia");
        cameraOffset=List.copyOf(cameraOffset);cameraRotation=List.copyOf(cameraRotation);clips=Map.copyOf(clips);grips=List.copyOf(grips);
    }
    private static void resource(WeaponId id,String extension,String path){Checks.present(id,path);Checks.require(id.path().endsWith(extension),path,"expected "+extension+" resource");}
    private static void range(double value,double min,double max,String path){Checks.require(Double.isFinite(value)&&value>=min&&value<=max,path,"expected finite number in "+min+".."+max);}
    private static void vector(List<Double> values,int length,double min,double max,String path){Checks.size(values,length,length,path);for(int i=0;i<values.size();i++)range(values.get(i),min,max,path+"["+i+"]");}
    private static List<Double> numbers(JsonFields fields,String name,int length){JsonArray array=fields.array(name,length,length);List<Double> out=new ArrayList<>();for(int i=0;i<length;i++)out.add(JsonFields.numberValue(array.get(i),fields.at(name)+"["+i+"]"));return out;}
    public static FirstPersonProfile read(String json){
        JsonFields f=new JsonFields(StrictJson.parse(json),"$","schema_version","id","model","rig","sockets","camera_offset","camera_rotation","camera_clearance","transition_seconds","movement_threshold","contact_metres","contact_degrees","clips","grips","inertia");
        JsonFields clip=new JsonFields(f.get("clips"),"$.clips","idle","inspect","raise","lower","move");Map<String,String> clips=new HashMap<>();for(String action:ACTIONS)clips.put(action,clip.string(action));
        List<Grip> grips=new ArrayList<>();for(JsonElement entry:f.array("grips",2,2)){var g=new JsonFields(entry,"$.grips[]","name","target","hand");grips.add(new Grip(g.string("name"),g.string("target"),g.string("hand")));}
        JsonFields i=new JsonFields(f.get("inertia"),"$.inertia","enabled","frequency_hz","lag_seconds","max_yaw_degrees","max_pitch_degrees","metres_per_degree");
        JsonElement enabled=i.get("enabled");Checks.require(enabled.isJsonPrimitive()&&enabled.getAsJsonPrimitive().isBoolean(),"$.inertia.enabled","expected boolean");
        return new FirstPersonProfile(f.intValue("schema_version"),f.id("id"),f.id("model"),f.id("rig"),f.id("sockets"),numbers(f,"camera_offset",3),numbers(f,"camera_rotation",4),f.number("camera_clearance"),f.number("transition_seconds"),f.number("movement_threshold"),f.number("contact_metres"),f.number("contact_degrees"),clips,grips,
            new Inertia(enabled.getAsBoolean(),i.number("frequency_hz"),i.number("lag_seconds"),i.number("max_yaw_degrees"),i.number("max_pitch_degrees"),i.number("metres_per_degree")));
    }
}
