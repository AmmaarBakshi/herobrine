package io.github.ammaarbakshi.herobrine.haunt.events;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/** Registry of haunt events. Add new ones with {@link #register}; ids must be unique. */
public final class HauntEvents {
	private static final Map<String, HauntEvent> EVENTS = new LinkedHashMap<>();

	private HauntEvents() {
	}

	public static <T extends HauntEvent> T register(T event) {
		if (EVENTS.putIfAbsent(event.id(), event) != null) {
			throw new IllegalStateException("Duplicate haunt event id " + event.id());
		}
		return event;
	}

	public static void init() {
		register(new TorchThiefEvent());
		register(new TunnelEvent());
		register(new SandPyramidEvent());
		register(new LeaflessTreeEvent());
		register(new SignMessageEvent());
		register(new AmbientSoundEvent());
		register(new GhostReplayEvent());
		register(new FakeChatEvent());
		register(new SightingEvents.Distant());
		register(new SightingEvents.Stalker());
		register(new SightingEvents.Jumpscare());
	}

	public static Collection<HauntEvent> all() {
		return Collections.unmodifiableCollection(EVENTS.values());
	}

	public static @Nullable HauntEvent byId(String id) {
		return EVENTS.get(id);
	}

	public static List<String> signIds() {
		return EVENTS.values().stream().filter(e -> e.category() == HauntEvent.Category.SIGN).map(HauntEvent::id).toList();
	}
}
