package com.arnir.storymod.client;

import com.arnir.storymod.StoryMod;
import com.arnir.storymod.entity.StoryNpc;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Renders story characters with the player model and a skin per character: textures/entity/npc/&lt;id&gt;.png */
public class StoryNpcRenderer extends HumanoidMobRenderer<StoryNpc, PlayerModel<StoryNpc>> {
    public StoryNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(StoryNpc entity) {
        return StoryMod.id("textures/entity/npc/" + entity.getCharacter() + ".png");
    }
}
