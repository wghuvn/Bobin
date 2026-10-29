package com.bobincraft.armament.client.presentation;

import com.bobincraft.armament.client.animation.gltf.*;

/** Imported two-hand constraints, checked in a common model space before any camera transform. */
public final class GripAttachments {
    private final FirstPersonProfile profile;private final RigSockets sockets;
    private final double[] target=new double[16],hand=new double[16],inverse=new double[16],relative=new double[16];
    private double positionError,angleError;
    public GripAttachments(FirstPersonProfile profile,RigSockets sockets){
        this.profile=profile;this.sockets=sockets;
        for(var grip:profile.grips())if(!sockets.names().contains(grip.target())||!sockets.names().contains(grip.hand()))throw new IllegalArgumentException("missing attachment socket: "+grip.name());
    }
    public boolean matches(RigFrame pose){
        positionError=angleError=0;
        for(int i=0;i<profile.grips().size();i++){
            var grip=profile.grips().get(i);sockets.sample(grip.target(),pose,target);sockets.sample(grip.hand(),pose,hand);
            double x=target[12]-hand[12],y=target[13]-hand[13],z=target[14]-hand[14];positionError=Math.max(positionError,Math.sqrt(x*x+y*y+z*z));
            RigMath.inverseAffine(target,0,inverse,0);RigMath.multiply(inverse,0,hand,0,relative,0);
            double trace=0;
            for(int c=0;c<3;c++){
                double length=Math.sqrt(relative[c*4]*relative[c*4]+relative[c*4+1]*relative[c*4+1]+relative[c*4+2]*relative[c*4+2]);
                if(!Double.isFinite(length)||length<1e-12)throw new IllegalArgumentException("attachment has a singular orientation");
                trace+=relative[c*4+c]/length;
            }
            double cosine=Math.max(-1,Math.min(1,(trace-1)/2));angleError=Math.max(angleError,Math.toDegrees(Math.acos(cosine)));
        }
        return positionError<=profile.contactMetres()&&angleError<=profile.contactDegrees();
    }
    public double positionError(){return positionError;}public double angleError(){return angleError;}
}
