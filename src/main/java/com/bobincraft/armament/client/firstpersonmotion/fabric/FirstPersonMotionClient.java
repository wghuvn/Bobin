package com.bobincraft.armament.client.firstpersonmotion.fabric;

import com.bobincraft.armament.client.firstpersonmotion.FirstPersonMotionController.Mode;
import com.bobincraft.armament.client.firstpersonmotion.FirstPersonMotionController;
import com.bobincraft.armament.client.mixin.FirstPersonMotionHostAccess;
import com.bobincraft.armament.client.presentation.FirstPersonWeaponPresenter;
import com.bobincraft.armament.client.presentation.fabric.FirstPersonDiagnostic;
import com.bobincraft.armament.client.thirdperson.PresentationAction.Action;
import com.bobincraft.armament.client.thirdperson.fabric.ThirdPersonSemanticPort;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** Observes aim/sprint inputs for cosmetics only. Does not consume keys or change gameplay/camera. */
public final class FirstPersonMotionClient {
    public record Input(boolean enabled,boolean aim,boolean sprint){}
    private static FirstPersonDiagnostic host;
    private static boolean enabled=true;
    private FirstPersonMotionClient(){}
    public static void register(FirstPersonDiagnostic diagnostic){
        host=diagnostic;var resources=new FirstPersonMotionResources();
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(resources);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client->{host=null;resources.close();});
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,registry)->{
            var anim=literal("anim");
            for(Mode mode:new Mode[]{Mode.REST,Mode.AIM,Mode.SPRINT})anim.then(literal(mode.name().toLowerCase(java.util.Locale.ROOT)).executes(ctx->{
                var semantic=(ThirdPersonSemanticPort)(Object)host;
                // Common actions/time, independent FPS clips. SPRINT uses the existing MOVE action.
                if(!semantic.bobin$requestPresentation(mode==Mode.AIM?"aim":mode==Mode.SPRINT?"move":"idle"))return 0;
                var access=(FirstPersonMotionHostAccess)(Object)host;var p=access.bobin$motionPresenter();
                if(!FirstPersonMotionController.supported(p.asset().model().rig())){
                    ctx.getSource().sendError(Text.literal("New FPS motion assets unavailable; approved FPS remains active."));return 0;
                }
                enabled=true;((FirstPersonMotionPort)(Object)p).bobin$motion(mode,access.bobin$motionTick()/20.0);
                ctx.getSource().sendFeedback(Text.literal(status()));return 1;
            }));
            dispatcher.register(literal("bobin_glb").then(literal("fp").then(anim).then(literal("motion")
                .then(literal("auto").executes(ctx->{
                    enabled=true;((ThirdPersonSemanticPort)(Object)host).bobin$requestPresentation("idle");
                    ctx.getSource().sendFeedback(Text.literal(status()));return 1;
                }))
                .then(literal("off").executes(ctx->{
                    enabled=false;var access=(FirstPersonMotionHostAccess)(Object)host;var p=access.bobin$motionPresenter();
                    if(p!=null)((FirstPersonMotionPort)(Object)p).bobin$motion(null,access.bobin$motionTick()/20.0);
                    ctx.getSource().sendFeedback(Text.literal(status()));return 1;
                }))
                .then(literal("status").executes(ctx->{ctx.getSource().sendFeedback(Text.literal(status()));return 1;})))));
        });
    }
    public static Input input(FirstPersonWeaponPresenter p,boolean moving){
        if(host==null||((FirstPersonMotionHostAccess)(Object)host).bobin$motionPresenter()!=p)return new Input(false,false,false);
        var client=MinecraftClient.getInstance();var action=((ThirdPersonSemanticPort)(Object)host).bobin$presentationAction();
        boolean controls=client.currentScreen==null&&client.player!=null;
        return new Input(enabled,(action!=null&&action.action()==Action.AIM)||(controls&&client.options.useKey.isPressed()),
            controls&&moving&&client.player.isSprinting());
    }
    public static boolean enabled(){return enabled;}
    private static String status(){
        var p=host==null?null:((FirstPersonMotionHostAccess)(Object)host).bobin$motionPresenter();
        return "fpMotionEnabled="+enabled+", "+(p==null?"show the FPS diagnostic first":((FirstPersonMotionPort)(Object)p).bobin$motionStatus());
    }
}
