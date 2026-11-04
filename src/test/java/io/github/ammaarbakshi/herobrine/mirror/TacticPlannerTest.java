package io.github.ammaarbakshi.herobrine.mirror;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import io.github.ammaarbakshi.herobrine.mirror.TacticPlanner.Habits;
import io.github.ammaarbakshi.herobrine.mirror.TacticPlanner.Situation;
import io.github.ammaarbakshi.herobrine.mirror.TacticPlanner.Tactic;

class TacticPlannerTest {
	private static final Habits KITER = new Habits(0.85, 14, 0.5, 0.9, 0, 0);
	private static final Habits SPAMMER = new Habits(0.0, 1.8, 0.25, 0.1, 0, 0);

	private static Situation fresh(double health, double distance) {
		return new Situation(health, distance, 3, false, false, true, Tactic.MELEE, TacticPlanner.MIN_COMMIT_TICKS);
	}

	private static int count(Habits habits, Situation situation, Tactic tactic) {
		SplittableRandom random = new SplittableRandom(7);
		int n = 0;
		for (int i = 0; i < 1000; i++) {
			if (TacticPlanner.decide(habits, situation, random) == tactic) {
				n++;
			}
		}
		return n;
	}

	@Test
	void kiterShootsAboutAsOftenAsThePlayerDoes() {
		int ranged = count(KITER, fresh(1.0, 12), Tactic.RANGED);
		assertTrue(ranged > 800 && ranged < 900, "ranged " + ranged);
	}

	@Test
	void spammerNeverShoots() {
		assertEquals(0, count(SPAMMER, fresh(1.0, 12), Tactic.RANGED));
	}

	@Test
	void noBowMeansMelee() {
		Situation noBow = new Situation(1.0, 12, 3, false, false, false, Tactic.MELEE, TacticPlanner.MIN_COMMIT_TICKS);
		assertEquals(0, count(KITER, noBow, Tactic.RANGED));
	}

	@Test
	void healsAtThePlayersOwnThreshold() {
		assertEquals(1000, count(SPAMMER, fresh(0.2, 3), Tactic.HEAL));
		assertEquals(0, count(SPAMMER, fresh(0.3, 3), Tactic.HEAL));
	}

	@Test
	void noHealsLeftOrOnCooldownMeansNoHeal() {
		Situation none = new Situation(0.2, 3, 0, false, true, true, Tactic.MELEE, TacticPlanner.MIN_COMMIT_TICKS);
		Situation cooldown = new Situation(0.2, 3, 2, true, true, true, Tactic.MELEE, TacticPlanner.MIN_COMMIT_TICKS);
		assertEquals(0, count(SPAMMER, none, Tactic.HEAL));
		assertEquals(0, count(SPAMMER, cooldown, Tactic.HEAL));
	}

	@Test
	void retreatsAtThePlayersRetreatPoint() {
		assertEquals(1000, count(KITER, fresh(0.85, 6), Tactic.RETREAT));
		assertEquals(0, count(KITER, fresh(1.0, 6), Tactic.RETREAT), "full health never retreats");
	}

	@Test
	void commitsToAChoiceForAWhile() {
		Situation justSwitched = new Situation(1.0, 12, 3, false, false, true, Tactic.MELEE, 5);
		assertEquals(1000, count(KITER, justSwitched, Tactic.MELEE));
	}

	@Test
	void spamClickersSwingMoreOften() {
		assertEquals(4, TacticPlanner.swingInterval(0.3, 12.5));
		assertEquals(13, TacticPlanner.swingInterval(1.0, 12.5));
		assertEquals(1.0, TacticPlanner.chargeDamageScale(1.0), 1e-9);
		assertEquals(0.2, TacticPlanner.chargeDamageScale(0.0), 1e-9);
	}

	@Test
	void strafeSideFollowsBias() {
		SplittableRandom random = new SplittableRandom(3);
		int right = 0;
		for (int i = 0; i < 1000; i++) {
			right += TacticPlanner.strafeSide(0.6, random) > 0 ? 1 : 0;
		}
		assertTrue(right > 760 && right < 840, "right " + right);
	}

	@Test
	void escapesFollowHabits() {
		Habits coward = new Habits(0.3, 5, 0.4, 0.6, 0.9, 0.5);
		SplittableRandom random = new SplittableRandom(11);
		int pillar = 0;
		for (int i = 0; i < 1000; i++) {
			pillar += TacticPlanner.escape(coward, true, random) == TacticPlanner.Escape.PILLAR ? 1 : 0;
		}
		assertTrue(pillar > 860 && pillar < 940, "pillar " + pillar);
		assertEquals(TacticPlanner.Escape.RUN, TacticPlanner.escape(SPAMMER, true, random));
	}
}
