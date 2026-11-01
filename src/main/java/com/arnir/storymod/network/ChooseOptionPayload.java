package com.arnir.storymod.network;

import com.arnir.storymod.StoryMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: the player picked an option (or -1 to close). */
public record ChooseOptionPayload(int npcId, int option) implements CustomPacketPayload {
    public static final Type<ChooseOptionPayload> TYPE = new Type<>(StoryMod.id("choose_option"));
    public static final StreamCodec<ByteBuf, ChooseOptionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChooseOptionPayload::npcId,
            ByteBufCodecs.VAR_INT, ChooseOptionPayload::option,
            ChooseOptionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
