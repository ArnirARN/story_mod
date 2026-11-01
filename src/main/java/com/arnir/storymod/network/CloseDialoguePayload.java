package com.arnir.storymod.network;

import com.arnir.storymod.StoryMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → client: close the dialogue window. */
public record CloseDialoguePayload() implements CustomPacketPayload {
    public static final Type<CloseDialoguePayload> TYPE = new Type<>(StoryMod.id("close_dialogue"));
    public static final StreamCodec<ByteBuf, CloseDialoguePayload> STREAM_CODEC = StreamCodec.unit(new CloseDialoguePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
