package com.battleship.domain;

import java.util.*;

public record Ship(ShipType type, Coordinate origin, Orientation orientation) {
  public Ship {
    Objects.requireNonNull(type);
    Objects.requireNonNull(origin);
    Objects.requireNonNull(orientation);
  }

  public List<Coordinate> cells() {
    List<Coordinate> result = new ArrayList<>();
    for (int i = 0; i < type.length(); i++)
      result.add(
          new Coordinate(
              origin.row() + (orientation == Orientation.VERTICAL ? i : 0),
              origin.col() + (orientation == Orientation.HORIZONTAL ? i : 0)));
    return List.copyOf(result);
  }
}
