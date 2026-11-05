package io.github.ammaarbakshi.herobrine.behaviour;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import io.github.ammaarbakshi.herobrine.dialogue.DialogueLine;
import io.github.ammaarbakshi.herobrine.encounter.EncounterHistory;
import io.github.ammaarbakshi.herobrine.entity.HerobrineEntity;
import io.github.ammaarbakshi.herobrine.haunt.HauntLevel;
import io.github.ammaarbakshi.herobrine.memory.Counters;
import io.github.ammaarbakshi.herobrine.memory.ObserverMemory;
import io.github.ammaarbakshi.herobrine.registry.ModAttachments;
import io.github.ammaarbakshi.herobrine.sighting.SightingController;

/**
 * The situation a line is chosen in, as tags and counters (see docs/DIALOGUE.md for the full
 * vocabulary). Built only when he is about to speak, so it may look around a little.
 */
public final class ObserverContext {
	private ObserverContext() {
	}

	/** The trigger's own tags plus everything true about the player right now. */
	public static Set<String> tags(ServerPlayer player, Set<String> triggerTags) {
		Set<String> tags = new HashSet<>(triggerTags);
		ServerLevel level = player.level();
		tags.add("dimension:" + level.dimension().identifier().getPath());
		tags.add(level.getServer().overworld().isDarkOutside() ? "time:night" : "time:day");
		if (level.dimension() == Level.OVERWORLD) {
			tags.add(level.isThundering() ? "weather:thunder" : level.isRaining() ? "weather:rain" : "weather:clear");
		}
		BlockPos pos = player.blockPosition();
		if (level.canSeeSky(pos.above())) {
			tags.add("outdoors");
		} else if (level.dimension() == Level.OVERWORLD && pos.getY() < level.getSeaLevel()) {
			tags.add("underground");
		} else {
			tags.add("indoors");
		}
		if (HauntLevel.isAlone(player)) {
			tags.add("alone");
		}
		if (player.getHealth() <= player.getMaxHealth() * 0.3F) {
			tags.add("health:low");
		}
		if (player.isCreative()) {
			tags.add("creative");
		}
		ObserverState state = ModAttachments.observer(player);
		if (state.nearVillage) {
			tags.add("near:village");
		}
		if (state.nearBerserk) {
			tags.add("near:berserk");
		}
		if (state.nearHome) {
			tags.add("near:home");
		}
		HerobrineEntity apparition = SightingController.activeEntity(player);
		if (apparition != null) {
			tags.add("visible");
			tags.add("mode:" + apparition.mode().name().toLowerCase(Locale.ROOT));
		}
		if (ModAttachments.memory(player).counter(Counters.IGNORED_STREAK) >= 2) {
			tags.add("ignored");
		}
		tags.add("haunt:" + HauntLevel.phase(player).name().toLowerCase(Locale.ROOT));
		return tags;
	}

	/** Stored counters, plus the derived ones listed in {@link Counters}. */
	public static DialogueLine.MemoryLookup counters(ServerPlayer player, ObserverMemory memory, long now) {
		EncounterHistory history = ModAttachments.encounters(player);
		return name -> switch (name) {
			case Counters.DAYS_KNOWN -> memory.firstMet() < 0 ? 0 : (int) ((now - memory.firstMet()) / Observer.TICKS_PER_DAY);
			case Counters.MINUTES_SINCE_ENCOUNTER -> memory.lastEncounter() < 0
					? Integer.MAX_VALUE
					: (int) Math.min(Integer.MAX_VALUE, (now - memory.lastEncounter()) / Observer.TICKS_PER_MINUTE);
			case Counters.MILESTONES -> memory.milestones().size();
			case Counters.CONFRONTATIONS -> history.total();
			case Counters.MIRROR_WINS -> history.wins();
			case Counters.MIRROR_LOSSES -> history.losses();
			case Counters.HAUNT -> ModAttachments.haunt(player).levelInt();
			default -> memory.counter(name);
		};
	}
}
