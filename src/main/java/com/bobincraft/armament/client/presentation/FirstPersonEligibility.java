package com.bobincraft.armament.client.presentation;

/** Pure policy shared by command/status and render hook; all inputs are read-only observations. */
public final class FirstPersonEligibility {
    private FirstPersonEligibility(){}
    public record View(boolean debug,boolean requested,boolean playerPresent,boolean alive,boolean spectator,
            boolean firstPerson,boolean localCamera,boolean mainEmpty,boolean offEmpty,boolean compatibleBody,
            boolean otherHandsOwner,boolean usingOrSwinging,boolean mounted,boolean hudVisible,boolean viewportValid){}
    public static String reason(View v){
        if(!v.debug())return "debug_disabled";if(!v.requested())return "hidden";if(!v.playerPresent())return "no_player";
        if(!v.alive()||v.spectator())return "player_state";if(!v.firstPerson()||!v.localCamera())return "camera";
        if(!v.mainEmpty()||!v.offEmpty())return "item_in_hand";if(!v.compatibleBody())return "injury_or_pose";
        if(v.otherHandsOwner()||v.usingOrSwinging())return "existing_hand_action";if(v.mounted())return "mounted";
        if(!v.hudVisible())return "hud_hidden";if(!v.viewportValid())return "viewport";return "ready";
    }
}
