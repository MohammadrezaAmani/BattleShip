package com.battleship.ai;

import com.battleship.domain.*;
import java.util.*;

/** Enumerates every locally consistent placement, with priority on unresolved hits. */
public final class ProbabilityAI implements BattleshipAI {
  @Override
  public Coordinate choose(OpponentKnowledge knowledge, Random random) {
    int[][] heat = new int[10][10];
    Set<Coordinate> forbidden = new HashSet<>(knowledge.misses());
    forbidden.addAll(knowledge.sunkCells());
    // No-touch fleets exclude all neighbors around already sunk ships.
    if (knowledge.rules().noTouching())
      for (Coordinate sunk : knowledge.sunkCells())
        for (int dr = -1; dr <= 1; dr++)
          for (int dc = -1; dc <= 1; dc++) {
            Coordinate p = new Coordinate(sunk.row() + dr, sunk.col() + dc);
            if (p.inside()) forbidden.add(p);
          }
    Set<Coordinate> hits = knowledge.hits();
    for (ShipType type : knowledge.remaining()) {
      for (Orientation orientation : Orientation.values())
        for (int r = 0; r < 10; r++)
          for (int c = 0; c < 10; c++) {
            Ship candidate = new Ship(type, new Coordinate(r, c), orientation);
            List<Coordinate> cells = candidate.cells();
            if (cells.stream().anyMatch(p -> !p.inside() || forbidden.contains(p))) continue;
            int matching = (int) cells.stream().filter(hits::contains).count();
            // Focus on unresolved hits, but preserve candidates for unrelated ships.
            int weight = hits.isEmpty() ? 1 : (matching > 0 ? 30 * matching : 1);
            for (Coordinate p : cells) if (knowledge.available(p)) heat[p.row()][p.col()] += weight;
          }
    }
    // In target mode prefer reachable neighbors of a known damaged ship.
    if (!hits.isEmpty()) {
      List<Coordinate> near = HuntTargetAI.targetCandidates(knowledge);
      if (!near.isEmpty()) {
        int peak = 0;
        for (Coordinate p : near) peak = Math.max(peak, heat[p.row()][p.col()]);
        if (peak > 0) {
          List<Coordinate> tied = new ArrayList<>();
          for (Coordinate p : near) if (heat[p.row()][p.col()] == peak) tied.add(p);
          return tied.get(random.nextInt(tied.size()));
        }
      }
    }
    int high = -1;
    List<Coordinate> best = new ArrayList<>();
    for (Coordinate p : knowledge.unknown()) {
      int score = heat[p.row()][p.col()];
      if (score > high) {
        high = score;
        best.clear();
        best.add(p);
      } else if (score == high) best.add(p);
    }
    if (best.isEmpty()) throw new IllegalStateException("No available shots");
    return best.get(random.nextInt(best.size()));
  }
}
