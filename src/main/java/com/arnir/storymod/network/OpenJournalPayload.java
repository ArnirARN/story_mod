package com.arnir.storymod.network;

import com.arnir.storymod.StoryMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** Server → client: open the quest journal with these pages. */
public record OpenJournalPayload(List<String> pages) implements CustomPacketPayload {
    public static final Type<OpenJournalPayload> TYPE = new Type<>(StoryMod.id("open_journal"));
    public static final StreamCodec<ByteBuf, OpenJournalPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), OpenJournalPayload::pages,
            OpenJournalPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
