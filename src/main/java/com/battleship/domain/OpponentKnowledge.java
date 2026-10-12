package com.battleship.domain;

import java.util.*;

/** Projection: no access to unhit ship coordinates or the opponent Board. */
public record OpponentKnowledge(
    Set<Coordinate> misses,
    Set<Coordinate> hits,
    Map<ShipType, List<Coordinate>> sunk,
    List<ShipType> remaining,
    Ruleset rules) {
  public OpponentKnowledge {
    misses = Set.copyOf(misses);
    hits = Set.copyOf(hits);
    EnumMap<ShipType, List<Coordinate>> m = new EnumMap<>(ShipType.class);
    sunk.forEach((k, v) -> m.put(k, List.copyOf(v)));
    sunk = Collections.unmodifiableMap(m);
    remaining = List.copyOf(remaining);
  }

  public Set<Coordinate> sunkCells() {
    Set<Coordinate> cells = new HashSet<>();
    sunk.values().forEach(cells::addAll);
    return Set.copyOf(cells);
  }

  public boolean available(Coordinate p) {
    return p.inside() && !misses.contains(p) && !hits.contains(p) && !sunkCells().contains(p);
  }

  public List<Coordinate> unknown() {
    List<Coordinate> result = new ArrayList<>();
    for (int r = 0; r < 10; r++)
      for (int c = 0; c < 10; c++) {
        Coordinate p = new Coordinate(r, c);
        if (available(p)) result.add(p);
      }
    return result;
  }
}
