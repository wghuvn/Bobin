package com.bobincraft.armament.client.animation.gltf;

import com.bobincraft.armament.client.resource.WeaponSocketsDefinition;
import com.bobincraft.armament.client.resource.WeaponRigDefinition;
import java.util.*;

/** Validated socket bindings from the existing stage-2 metadata contract. */
public final class RigSockets {
    private record Binding(int node,double[] offset) {}
    private final RigModel model;
    private final Map<String,Binding> bindings;
    public RigSockets(RigModel model,WeaponSocketsDefinition definition){
        this(model,definition,null);
    }
    /** Canonical metadata aliases resolve by node_index, independent of Blender display names. */
    public RigSockets(RigModel model,WeaponRigDefinition rig,WeaponSocketsDefinition definition){
        this(model,definition,indices(model,rig,definition));
    }
    private static Map<String,Integer> indices(RigModel model,WeaponRigDefinition rig,WeaponSocketsDefinition sockets){
        if(!rig.id().equals(sockets.rigId()))throw new IllegalArgumentException("socket: rig_id does not match rig definition");
        Map<String,Integer> nodes=new HashMap<>();
        for(var node:rig.nodes()){
            if(node.nodeIndex()>=model.nodes().size())throw new IllegalArgumentException("rig: node_index outside GLB: "+node.name());
            nodes.put(node.name(),node.nodeIndex());
        }
        for(var node:rig.nodes()){
            int parent=node.parent().map(nodes::get).orElse(-1);
            if(parent!=model.nodes().get(node.nodeIndex()).parent())throw new IllegalArgumentException("rig: parent differs from GLB: "+node.name());
        }
        return nodes;
    }
    private RigSockets(RigModel model,WeaponSocketsDefinition definition,Map<String,Integer> aliases){
        this.model=Objects.requireNonNull(model);Map<String,Binding> result=new LinkedHashMap<>();
        for(var socket:definition.sockets()){
            var t=socket.transform();double[] trs={t.translation().get(0),t.translation().get(1),t.translation().get(2),
                t.rotation().get(0),t.rotation().get(1),t.rotation().get(2),t.rotation().get(3),t.scale().get(0),t.scale().get(1),t.scale().get(2)};
            double[] offset=new double[16];RigMath.compose(trs,0,offset,0);
            int node;
            if(aliases==null)node=model.node(socket.node());
            else{Integer index=aliases.get(socket.node());if(index==null)throw new IllegalArgumentException("socket: unknown rig alias "+socket.node());node=index;}
            result.put(socket.name(),new Binding(node,offset));
        }
        bindings=Map.copyOf(result);
    }
    public Set<String> names(){return bindings.keySet();}
    public void sample(String name,RigFrame frame,double[] destination){
        if(frame.model()!=model)throw new IllegalArgumentException("socket: pose belongs to a different rig");
        Binding binding=bindings.get(name);if(binding==null)throw new IllegalArgumentException("unknown socket: "+name);
        frame.socket(binding.node(),binding.offset(),destination);
    }
}
