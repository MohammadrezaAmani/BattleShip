package com.battleship;

import com.battleship.ai.*;
import com.battleship.domain.*;
import com.battleship.engine.Match;
import java.util.*;

/** Reproducible, headless comparative AI benchmark. */
public final class AiBenchmark {
  private static final BattleshipAI EASY = new RandomAI(),
      MEDIUM = new HuntTargetAI(),
      HARD = new ProbabilityAI();

  private static void duel(String label, BattleshipAI opponent, int matches) {
    long start = System.nanoTime();
    int wins = 0, shots = 0;
    for (int i = 0; i < matches; i++) {
      Match match = new Match(Ruleset.classic());
      long seed = 1907L + 17L * i;
      match.randomize(0, new Random(seed));
      match.randomize(1, new Random(seed + 1));
      match.start(i % 2);
      int moves = 0;
      while (match.phase() == MatchPhase.ACTIVE) {
        int shooter = match.turn();
        BattleshipAI ai = shooter == 0 ? HARD : opponent;
        Coordinate shot = ai.choose(match.knowledge(shooter), new Random(seed + 31L * moves));
        ShotResult result = match.fire(shooter, shot);
        if (!result.valid())
          throw new IllegalStateException("Invalid AI shot: " + shooter + " " + shot);
        moves++;
      }
      if (match.winner() == 0) wins++;
      shots += moves;
    }
    System.out.printf(
        "Hard vs %-7s: %d/%d (%.1f%%), %.1f turns/match, %.2fs%n",
        label,
        wins,
        matches,
        100.0 * wins / matches,
        (double) shots / matches,
        (System.nanoTime() - start) / 1e9);
  }

  public static void main(String[] args) {
    int matches = args.length > 0 ? Integer.parseInt(args[0]) : 500;
    duel("Easy", EASY, matches);
    duel("Normal", MEDIUM, matches);
  }
}
