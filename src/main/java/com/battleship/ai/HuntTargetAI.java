package com.battleship.ai;

import com.battleship.domain.*;
import java.util.*;

public final class HuntTargetAI implements BattleshipAI {
  static List<Coordinate> targetCandidates(OpponentKnowledge k) {
    Set<Coordinate> result = new LinkedHashSet<>();
    for (Coordinate hit : k.hits()) {
      for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
        Coordinate n = new Coordinate(hit.row() + d[0], hit.col() + d[1]);
        if (!k.available(n)) continue;
        boolean aligned = false;
        for (Coordinate h : k.hits())
          if (!h.equals(hit)) {
            if ((d[0] != 0 && h.col() == hit.col()) || (d[1] != 0 && h.row() == hit.row()))
              aligned = true;
          }
        if (aligned) result.add(n);
      }
    }
    if (!result.isEmpty()) return new ArrayList<>(result);
    for (Coordinate hit : k.hits())
      for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
        Coordinate n = new Coordinate(hit.row() + d[0], hit.col() + d[1]);
        if (k.available(n)) result.add(n);
      }
    return new ArrayList<>(result);
  }

  @Override
  public Coordinate choose(OpponentKnowledge k, Random random) {
    List<Coordinate> candidates = targetCandidates(k);
    if (!candidates.isEmpty()) return candidates.get(random.nextInt(candidates.size()));
    candidates = k.unknown().stream().filter(p -> (p.row() + p.col()) % 2 == 0).toList();
    if (candidates.isEmpty()) candidates = k.unknown();
    if (candidates.isEmpty()) throw new IllegalStateException("No available shots");
    return candidates.get(random.nextInt(candidates.size()));
  }
}
