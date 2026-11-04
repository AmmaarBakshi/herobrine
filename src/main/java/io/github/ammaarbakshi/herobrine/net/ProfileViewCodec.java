package io.github.ammaarbakshi.herobrine.net;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import io.github.ammaarbakshi.herobrine.mirror.HealItem;
import io.github.ammaarbakshi.herobrine.mirror.ProfileView;
import io.github.ammaarbakshi.herobrine.mirror.WeaponType;

/** Wire format for {@link ProfileView}. Enum values go by ordinal; both sides run the same mod. */
public final class ProfileViewCodec {
	/** Generous caps so a bad packet can't make the client allocate much. */
	private static final int MAX_TRAITS = 64;
	private static final int MAX_BLOCKS = 8;

	public static final StreamCodec<FriendlyByteBuf, ProfileView> CODEC = StreamCodec.of(ProfileViewCodec::write, ProfileViewCodec::read);

	private ProfileViewCodec() {
	}

	private static void write(FriendlyByteBuf buf, ProfileView view) {
		buf.writeVarInt(view.traits().size());
		for (ProfileView.Entry e : view.traits()) {
			buf.writeUtf(e.key(), 64);
			buf.writeDouble(e.value());
			buf.writeVarInt(e.samples());
			buf.writeBoolean(e.known());
		}
		buf.writeVarInt(view.topWeapon().ordinal());
		buf.writeBoolean(view.weaponKnown());
		buf.writeVarInt(view.topHealItem().ordinal());
		buf.writeBoolean(view.healKnown());
		buf.writeVarInt(view.favoriteBlocks().size());
		for (String block : view.favoriteBlocks()) {
			buf.writeUtf(block, 256);
		}
		buf.writeDouble(view.confidence());
		buf.writeVarInt(view.encounters());
		buf.writeBoolean(view.inCombat());
	}

	private static ProfileView read(FriendlyByteBuf buf) {
		int traitCount = Math.min(buf.readVarInt(), MAX_TRAITS);
		List<ProfileView.Entry> traits = new ArrayList<>(traitCount);
		for (int i = 0; i < traitCount; i++) {
			traits.add(new ProfileView.Entry(buf.readUtf(64), buf.readDouble(), buf.readVarInt(), buf.readBoolean()));
		}
		WeaponType weapon = byOrdinal(WeaponType.values(), buf.readVarInt());
		boolean weaponKnown = buf.readBoolean();
		HealItem heal = byOrdinal(HealItem.values(), buf.readVarInt());
		boolean healKnown = buf.readBoolean();
		int blockCount = Math.min(buf.readVarInt(), MAX_BLOCKS);
		List<String> blocks = new ArrayList<>(blockCount);
		for (int i = 0; i < blockCount; i++) {
			blocks.add(buf.readUtf(256));
		}
		return new ProfileView(traits, weapon, weaponKnown, heal, healKnown, blocks, buf.readDouble(), buf.readVarInt(), buf.readBoolean());
	}

	private static <E> E byOrdinal(E[] values, int ordinal) {
		return values[Math.floorMod(ordinal, values.length)];
	}
}
