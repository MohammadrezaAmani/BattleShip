package com.battleship.domain;

public record ShotResult(ShotOutcome outcome, Coordinate coordinate, ShipType ship) {
  public boolean valid() {
    return outcome != ShotOutcome.INVALID;
  }

  public static ShotResult invalid(Coordinate c) {
    return new ShotResult(ShotOutcome.INVALID, c, null);
  }
}
