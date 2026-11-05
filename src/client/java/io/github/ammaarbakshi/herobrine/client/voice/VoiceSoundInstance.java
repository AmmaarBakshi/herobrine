package io.github.ammaarbakshi.herobrine.client.voice;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;

/**
 * One of his lines, played straight from its .ogg file. Lines are not sound events in
 * sounds.json: the instance makes its own one-file event when played, so a recording works as
 * soon as it is dropped into {@code assets/herobrine/sounds/voice/...} (in the mod or a resource
 * pack) with nothing else to register.
 *
 * <p>Heard "in your head": not positional, no distance falloff, on the Voice/Speech volume slider.
 */
final class VoiceSoundInstance extends AbstractSoundInstance {
	private final Identifier file;

	/** {@code file} is the path inside {@code sounds/} without the extension, e.g. {@code herobrine:voice/random/hello}. */
	VoiceSoundInstance(Identifier file) {
		super(file, SoundSource.VOICE, SoundInstance.createUnseededRandom());
		this.file = file;
		this.attenuation = Attenuation.NONE;
		this.relative = true;
		this.x = 0.0;
		this.y = 0.0;
		this.z = 0.0;
	}

	@Override
	public WeighedSoundEvents getOrResolve(SoundManager soundManager) {
		// Streamed: a spoken line can be long, and is played once.
		this.sound = new Sound(file, ConstantFloat.of(1.0F), ConstantFloat.of(1.0F), 1, Sound.Type.FILE, true, false, 16);
		this.soundEvent = new WeighedSoundEvents(file, null);
		this.soundEvent.addSound(this.sound);
		return this.soundEvent;
	}
}
