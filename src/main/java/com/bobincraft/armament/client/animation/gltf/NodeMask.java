package com.bobincraft.armament.client.animation.gltf;

import java.util.*;

/** Immutable per-node coverage; joints use the same node indices as rigid parts. */
public final class NodeMask {
    private final double[] weights;
    public NodeMask(double[] weights){this.weights=weights.clone();for(double w:weights)if(!Double.isFinite(w)||w<0||w>1)throw new IllegalArgumentException("mask weights must be in 0..1");}
    public static NodeMask all(RigModel model){double[] weights=new double[model.nodes().size()];Arrays.fill(weights,1);return new NodeMask(weights);}
    public static NodeMask subtree(RigModel model,String root){
        int ancestor=model.node(root);double[] weights=new double[model.nodes().size()];
        for(int i=0;i<weights.length;i++){int node=i;while(node>=0){if(node==ancestor){weights[i]=1;break;}node=model.nodes().get(node).parent();}}
        return new NodeMask(weights);
    }
    public int size(){return weights.length;} public double weight(int node){return weights[node];}
}
