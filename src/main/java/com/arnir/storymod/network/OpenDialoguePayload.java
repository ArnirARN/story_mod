package com.arnir.storymod.network;

import com.arnir.storymod.StoryMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** Server → client: show a dialogue node. {@code optionIds} are the server-side indices of {@code options}. */
public record OpenDialoguePayload(int npcId, String speaker, String text, List<String> options, List<Integer> optionIds)
        implements CustomPacketPayload {
    public static final Type<OpenDialoguePayload> TYPE = new Type<>(StoryMod.id("open_dialogue"));

    public static final StreamCodec<ByteBuf, OpenDialoguePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpenDialoguePayload::npcId,
            ByteBufCodecs.STRING_UTF8, OpenDialoguePayload::speaker,
            ByteBufCodecs.STRING_UTF8, OpenDialoguePayload::text,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), OpenDialoguePayload::options,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), OpenDialoguePayload::optionIds,
            OpenDialoguePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
