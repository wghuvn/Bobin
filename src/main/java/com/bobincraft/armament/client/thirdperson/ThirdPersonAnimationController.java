package com.bobincraft.armament.client.thirdperson;

import com.bobincraft.armament.client.animation.gltf.*;
import java.util.List;

/** Per-entity 3P controller. Separate torso/arm action, locomotion and stance layers.
 * Only semantic action IDs and simulation times enter from another presentation.
 */
public final class ThirdPersonAnimationController {
    private static final double FADE = .20;
    private final GltfAnimationController controller;
    private final NodeMask actionMask, motionMask, stanceMask;
    private PresentationAction action;
    private int gait = -1;
    private boolean crouched;
    public ThirdPersonAnimationController(ThirdPersonAsset asset) {
        controller = new GltfAnimationController(asset.model().rig());
        actionMask = NodeMask.subtree(controller.model(), "tp_body");
        motionMask = single("tp_motion"); stanceMask = single("tp_stance");
    }
    private NodeMask single(String name) {
        double[] weights = new double[controller.model().nodes().size()];
        weights[controller.model().node(name)] = 1;
        return new NodeMask(weights);
    }
    public void update(PresentationAction next, double now, boolean moving, boolean running, boolean crouching) {
        if (!next.equals(action)) {
            boolean initial = action == null;
            action = next;
            String clip = next.action() == PresentationAction.Action.MOVE ? "idle" : next.action().key();
            var mode = next.action().loops() ? GltfAnimationController.Mode.LOOP : GltfAnimationController.Mode.HOLD;
            // Late observers/reloads join the elapsed action, never restart it.
            controller.layer(0, List.of(new GltfAnimationController.ClipRequest(clip, mode, 1)), actionMask,
                    false, 1, now, initial ? 0 : FADE, GltfAnimationController.BlendCurve.SMOOTHSTEP);
            controller.seek(0, next.calibration() ? 0 : Math.max(0, now - next.started()), now);
        }
        int desired = next.calibration() ? 0 : (moving || next.action() == PresentationAction.Action.MOVE) ? (running ? 2 : 1) : 0;
        if (desired != gait) {
            controller.layer(1, desired == 0 ? List.of() : List.of(new GltfAnimationController.ClipRequest(desired == 2 ? "run" : "move", GltfAnimationController.Mode.LOOP, 1)),
                    motionMask, true, 1, now, gait == -1 ? 0 : FADE, GltfAnimationController.BlendCurve.SMOOTHSTEP);
            if (desired != 0) controller.seek(1, next.action() == PresentationAction.Action.MOVE ? Math.max(0, now-next.started()) : now, now);
            gait = desired;
        }
        if (crouching != crouched) {
            controller.layer(2, crouching ? List.of(new GltfAnimationController.ClipRequest("crouch", GltfAnimationController.Mode.HOLD, 1)) : List.of(),
                    stanceMask, true, 1, now, FADE, GltfAnimationController.BlendCurve.SMOOTHSTEP);
            crouched = crouching;
        }
    }
    public RigFrame sample(double now) {
        if (action == null) throw new IllegalStateException("supply semantic state before sampling");
        // Static calibration must not start breathing as frames are rendered.
        if (action.calibration()) controller.seek(0, 0, now);
        return controller.sampleSeconds(now);
    }
    public PresentationAction action() { return action; }
    public int gait() { return gait; }
}
