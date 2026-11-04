package io.github.ammaarbakshi.herobrine.client.screen;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;

import io.github.ammaarbakshi.herobrine.mirror.ProfileView;
import io.github.ammaarbakshi.herobrine.mirror.TraitId;
import io.github.ammaarbakshi.herobrine.net.MirrorProfilePayload;

/**
 * The Mirror Shard's screen: the player's own profile as readable stats and bars, in the
 * words of the one who learned them.
 */
public final class MirrorProfileScreen extends Screen {
	private static final int TITLE_COLOR = 0xFFE0E0E0;
	private static final int TEXT_COLOR = 0xFFC8C8C8;
	private static final int UNKNOWN_COLOR = 0xFF707070;
	private static final int ACCENT_COLOR = 0xFFD03030;
	private static final int BAR_BACK = 0xFF2A2A2A;
	private static final int ROW_HEIGHT = 12;
	private static final int COLUMN_WIDTH = 200;
	private static final int COLUMN_GAP = 12;
	private static final int LABEL_WIDTH = 98;
	private static final int BAR_WIDTH = 40;

	private final ProfileView view;
	private final int wins;
	private final int losses;

	public MirrorProfileScreen(MirrorProfilePayload payload) {
		super(Component.translatable("herobrine.screen.mirror.title"));
		this.view = payload.view();
		this.wins = payload.wins();
		this.losses = payload.losses();
	}

	@Override
	protected void init() {
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.bounds(width / 2 - 50, height - 26, 100, 20)
				.build());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(font, title, width / 2, 10, TITLE_COLOR);

		int y = 26;
		Component weapon = view.weaponKnown()
				? Component.translatable("herobrine.weapon." + view.topWeapon().name().toLowerCase(Locale.ROOT))
				: Component.translatable("herobrine.screen.mirror.learning");
		Component heal = view.healKnown()
				? Component.translatable("herobrine.heal." + view.topHealItem().name().toLowerCase(Locale.ROOT))
				: Component.translatable("herobrine.screen.mirror.learning");
		graphics.centeredText(font, Component.translatable("herobrine.screen.mirror.weapon").append(": ").append(weapon)
				.append("   ").append(Component.translatable("herobrine.screen.mirror.heal_item")).append(": ").append(heal),
				width / 2, y, TEXT_COLOR);
		y += ROW_HEIGHT;
		graphics.centeredText(font, Component.translatable("herobrine.screen.mirror.blocks").append(": ").append(blocks()),
				width / 2, y, TEXT_COLOR);
		y += ROW_HEIGHT;
		graphics.centeredText(font, Component.translatable("herobrine.screen.mirror.confidence", Math.round(view.confidence() * 100))
				.append("   ").append(Component.translatable("herobrine.screen.mirror.encounters", wins + losses, wins, losses)),
				width / 2, y, ACCENT_COLOR);
		y += ROW_HEIGHT + 6;

		int columns = width >= COLUMN_WIDTH * 2 + COLUMN_GAP + 8 ? 2 : 1;
		int left = width / 2 - (columns * COLUMN_WIDTH + (columns - 1) * COLUMN_GAP) / 2;
		List<ProfileView.Entry> traits = view.traits();
		int rows = (traits.size() + columns - 1) / columns;
		for (int i = 0; i < traits.size(); i++) {
			int column = i / rows;
			int row = i % rows;
			drawTrait(graphics, traits.get(i), left + column * (COLUMN_WIDTH + COLUMN_GAP), y + row * ROW_HEIGHT);
		}
	}

	private Component blocks() {
		if (view.favoriteBlocks().isEmpty()) {
			return Component.translatable("herobrine.screen.mirror.none");
		}
		MutableComponent out = Component.empty();
		for (int i = 0; i < view.favoriteBlocks().size(); i++) {
			if (i > 0) {
				out.append(", ");
			}
			String id = view.favoriteBlocks().get(i);
			int colon = id.indexOf(':');
			String namespace = colon >= 0 ? id.substring(0, colon) : "minecraft";
			String path = colon >= 0 ? id.substring(colon + 1) : id;
			out.append(Component.translatable("block." + namespace + "." + path));
		}
		return out;
	}

	private void drawTrait(GuiGraphicsExtractor graphics, ProfileView.Entry entry, int x, int y) {
		TraitId id = entry.id();
		if (id == null) {
			return;
		}
		boolean known = entry.known();
		graphics.text(font, Component.translatable("herobrine.trait." + entry.key()), x, y, known ? TEXT_COLOR : UNKNOWN_COLOR, false);
		int barX = x + LABEL_WIDTH;
		int barY = y + 3;
		graphics.fill(barX, barY, barX + BAR_WIDTH, barY + 3, BAR_BACK);
		if (!known) {
			graphics.text(font, Component.translatable("herobrine.screen.mirror.learning"), barX + BAR_WIDTH + 4, y, UNKNOWN_COLOR, false);
			return;
		}
		double value = entry.value();
		if (id == TraitId.STRAFE_BIAS) {
			// Centred bar: left of the middle for left-strafers, right for right-strafers.
			int mid = barX + BAR_WIDTH / 2;
			int reach = (int) Math.round(Mth.clamp(value, -1.0, 1.0) * BAR_WIDTH / 2.0);
			graphics.fill(Math.min(mid, mid + reach), barY, Math.max(mid, mid + reach) + 1, barY + 3, ACCENT_COLOR);
		} else {
			int fill = (int) Math.round(Mth.clamp(barFraction(id, value), 0.0, 1.0) * BAR_WIDTH);
			graphics.fill(barX, barY, barX + fill, barY + 3, ACCENT_COLOR);
		}
		graphics.text(font, describe(id, value), barX + BAR_WIDTH + 4, y, TEXT_COLOR, false);
	}

	private static double barFraction(TraitId id, double value) {
		return switch (id) {
			case BOW_DRAW_TICKS -> value / 20.0;
			case PREFERRED_RANGE -> value / 16.0;
			case SHIELD_REACTION_TICKS -> 1.0 - value / 20.0;
			default -> value;
		};
	}

	private static Component describe(TraitId id, double value) {
		return switch (id) {
			case BOW_DRAW_TICKS, SHIELD_REACTION_TICKS ->
					Component.translatable("herobrine.unit.seconds", String.format(Locale.ROOT, "%.2f", value / 20.0));
			case PREFERRED_RANGE -> Component.translatable("herobrine.unit.blocks", String.format(Locale.ROOT, "%.1f", value));
			case JUMP_FREQUENCY -> Component.translatable("herobrine.unit.per_second", String.format(Locale.ROOT, "%.1f", value));
			case HEAL_THRESHOLD, RETREAT_THRESHOLD, HEAL_THRESHOLD_SPREAD ->
					Component.translatable("herobrine.unit.hearts", String.format(Locale.ROOT, "%.1f", value * 10.0));
			case STRAFE_BIAS -> {
				if (Math.abs(value) < 0.1) {
					yield Component.translatable("herobrine.unit.even");
				}
				long share = Math.round((1.0 + Math.abs(Mth.clamp(value, -1.0, 1.0))) / 2.0 * 100.0);
				yield Component.translatable(value > 0 ? "herobrine.unit.right" : "herobrine.unit.left", share);
			}
			default -> Component.translatable("herobrine.unit.percent", Math.round(value * 100.0));
		};
	}
}
