package com.battleship.ai;

import com.battleship.domain.*;
import java.util.*;

public final class RandomAI implements BattleshipAI {
  @Override
  public Coordinate choose(OpponentKnowledge k, Random random) {
    List<Coordinate> unknown = k.unknown();
    if (unknown.isEmpty()) throw new IllegalStateException("No available shots");
    return unknown.get(random.nextInt(unknown.size()));
  }
}
