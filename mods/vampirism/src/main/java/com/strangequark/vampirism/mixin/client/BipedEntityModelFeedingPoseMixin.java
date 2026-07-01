package com.strangequark.vampirism.mixin.client;

import com.strangequark.vampirism.client.FeedingPlayerRenderState;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelFeedingPoseMixin {
    @Shadow
    @Final
    public ModelPart head;

    @Shadow
    @Final
    public ModelPart body;

    @Shadow
    @Final
    public ModelPart rightArm;

    @Shadow
    @Final
    public ModelPart leftArm;

    @Shadow
    @Final
    public ModelPart rightLeg;

    @Shadow
    @Final
    public ModelPart leftLeg;

    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V", at = @At("TAIL"))
    private void quarkmod_vampirism$applyFeedingPose(BipedEntityRenderState state, CallbackInfo ci) {
        if (!(state instanceof FeedingPlayerRenderState feedingState)) {
            return;
        }

        boolean directFeeding = feedingState.quarkmod_vampirism$isDirectFeeding();
        boolean siphoningBlood = feedingState.quarkmod_vampirism$isSiphoningBlood();
        if (!directFeeding && !siphoningBlood) {
            return;
        }

        float breath = MathHelper.sin(state.age * 0.42F) * 0.035F;
        float pull = MathHelper.sin(state.age * 1.2F) * 0.025F;
        float lean = directFeeding ? 0.7F : 0.48F;

        this.body.pitch = lean + breath;
        this.body.yaw *= 0.35F;
        this.body.originY += 1.2F;
        this.body.originZ -= 0.7F;

        float headPitchOffset = directFeeding ? 0.72F : 0.38F;
        this.head.pitch = MathHelper.clamp(this.head.pitch + headPitchOffset + pull, 0.38F, 1.08F);
        this.head.yaw *= 0.35F;
        this.head.originY += 0.9F;
        this.head.originZ -= 1.0F;

        if (directFeeding) {
            this.rightArm.pitch = -1.15F + breath;
            this.rightArm.yaw = -0.42F;
            this.rightArm.roll = 0.16F;

            this.leftArm.pitch = -1.1F - breath;
            this.leftArm.yaw = 0.42F;
            this.leftArm.roll = -0.16F;
        } else {
            this.rightArm.pitch = -1.28F + breath;
            this.rightArm.yaw = -0.22F;
            this.rightArm.roll = 0.04F;

            this.leftArm.pitch = -1.16F - breath;
            this.leftArm.yaw = 0.22F;
            this.leftArm.roll = -0.04F;
        }

        this.rightArm.originY += 0.7F;
        this.rightArm.originZ -= 0.9F;
        this.leftArm.originY += 0.7F;
        this.leftArm.originZ -= 0.9F;

        this.rightLeg.pitch *= 0.2F;
        this.rightLeg.yaw = 0.08F;
        this.rightLeg.roll = 0.04F;
        this.leftLeg.pitch *= 0.2F;
        this.leftLeg.yaw = -0.08F;
        this.leftLeg.roll = -0.04F;
    }
}
