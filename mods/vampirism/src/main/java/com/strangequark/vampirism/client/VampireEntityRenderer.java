package com.strangequark.vampirism.client;

import com.strangequark.vampirism.entity.VampireEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.ZombieBaseEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.ZombieEntityModel;
import net.minecraft.client.render.entity.state.ZombieEntityRenderState;

public class VampireEntityRenderer extends ZombieBaseEntityRenderer<VampireEntity, ZombieEntityRenderState, ZombieEntityModel<ZombieEntityRenderState>> {
    public VampireEntityRenderer(EntityRendererFactory.Context context) {
        super(
                context,
                model(context, EntityModelLayers.ZOMBIE),
                model(context, EntityModelLayers.ZOMBIE_BABY),
                model(context, EntityModelLayers.ZOMBIE_INNER_ARMOR),
                model(context, EntityModelLayers.ZOMBIE_OUTER_ARMOR),
                model(context, EntityModelLayers.ZOMBIE_BABY_INNER_ARMOR),
                model(context, EntityModelLayers.ZOMBIE_BABY_OUTER_ARMOR)
        );
    }

    @Override
    public ZombieEntityRenderState createRenderState() {
        return new ZombieEntityRenderState();
    }

    private static ZombieEntityModel<ZombieEntityRenderState> model(EntityRendererFactory.Context context, EntityModelLayer layer) {
        return new ZombieEntityModel<>(context.getPart(layer));
    }
}
