package com.battleship.app;

import com.battleship.ai.*;

public enum Difficulty {
  EASY,
  NORMAL,
  HARD;

  public BattleshipAI create() {
    return switch (this) {
      case EASY -> new RandomAI();
      case NORMAL -> new HuntTargetAI();
      case HARD -> new ProbabilityAI();
    };
  }
}
