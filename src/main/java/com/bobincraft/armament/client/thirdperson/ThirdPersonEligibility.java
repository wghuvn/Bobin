package com.bobincraft.armament.client.thirdperson;

/** Opt-in, local player only. Perspective controls drawing, never the action clock. */
public final class ThirdPersonEligibility {
    private ThirdPersonEligibility() {}
    public record View(boolean debug,boolean requested,boolean localPlayer,boolean aliveVisible,boolean ownCamera,
                       boolean externalView,boolean emptyHands,boolean healthyPose,boolean otherOwner,boolean busy,boolean hudVisible) {}
    public static String reason(View v) {
        if(!v.debug())return "debug_disabled";if(!v.requested())return "hidden";
        if(!v.localPlayer())return "not_local_player";if(!v.aliveVisible())return "player_state";
        if(!v.ownCamera())return "camera_owner";if(!v.emptyHands())return "item_in_hand";
        if(!v.healthyPose())return "injury_or_pose";if(v.otherOwner()||v.busy())return "existing_hand_action";
        if(!v.hudVisible())return "hud_hidden";return "ready";
    }
    public static boolean draws(View v){return v.externalView()&&reason(v).equals("ready");}
}
