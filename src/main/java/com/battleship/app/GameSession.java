package com.battleship.app;

import com.battleship.domain.*;
import com.battleship.engine.Match;
import java.util.*;

public final class GameSession {
  public final Match match;
  public final GameMode mode;
  public final Difficulty difficulty;
  public final long seed;
  public final Random random;
  public int setupPlayer;
  public boolean reducedMotion;

  public GameSession(GameMode mode, Difficulty difficulty, boolean noTouching, long seed) {
    this.mode = Objects.requireNonNull(mode);
    this.difficulty = Objects.requireNonNull(difficulty);
    this.seed = seed;
    this.random = new Random(seed);
    this.match = new Match(new Ruleset(noTouching));
    if (mode == GameMode.SOLO && !match.randomize(1, random))
      throw new IllegalStateException("Cannot place AI fleet");
  }
}
