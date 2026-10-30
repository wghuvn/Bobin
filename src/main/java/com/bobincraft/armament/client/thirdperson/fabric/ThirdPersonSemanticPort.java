package com.bobincraft.armament.client.thirdperson.fabric;

import com.bobincraft.armament.client.thirdperson.PresentationAction;

/** Additive opt-in bridge: action/time only, deliberately no FPS pose/asset access. */
public interface ThirdPersonSemanticPort {
    void bobin$enablePairedPresentation();
    boolean bobin$requestPresentation(String action);
    PresentationAction bobin$presentationAction();
    long bobin$presentationTick();
}
