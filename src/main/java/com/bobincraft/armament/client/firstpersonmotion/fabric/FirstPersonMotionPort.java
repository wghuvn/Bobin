package com.bobincraft.armament.client.firstpersonmotion.fabric;

import com.bobincraft.armament.client.firstpersonmotion.FirstPersonMotionController.Mode;

public interface FirstPersonMotionPort {
    void bobin$motion(Mode manual,double now);
    String bobin$motionStatus();
}
