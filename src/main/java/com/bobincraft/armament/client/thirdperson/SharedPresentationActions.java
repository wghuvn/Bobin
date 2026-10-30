package com.bobincraft.armament.client.thirdperson;

/** Shared semantic extension. AIM has no new FPS clip: the approved FPS uses
 * its existing idle fallback while 3P presents a dedicated aiming animation.
 * A new legacy action explicitly supersedes the aim intention, even across F5.
 */
public final class SharedPresentationActions {
    private PresentationAction aim, legacyAtAim;
    public void aim(PresentationAction legacy,double now) {
        legacyAtAim=java.util.Objects.requireNonNull(legacy);
        aim=new PresentationAction(PresentationAction.Action.AIM,now,false);
    }
    public PresentationAction observe(PresentationAction legacy) {
        if(legacy==null||!legacy.equals(legacyAtAim))clear();
        return aim==null?legacy:aim;
    }
    public void clear(){aim=null;legacyAtAim=null;}
}
