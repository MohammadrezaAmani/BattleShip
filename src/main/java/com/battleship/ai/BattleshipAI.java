package com.battleship.ai;

import com.battleship.domain.*;
import java.util.Random;

public interface BattleshipAI {
  Coordinate choose(OpponentKnowledge knowledge, Random random);
}
