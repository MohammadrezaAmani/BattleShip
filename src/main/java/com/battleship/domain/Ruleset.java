package com.battleship.domain;

public record Ruleset(boolean noTouching) {
  public static Ruleset classic() {
    return new Ruleset(false);
  }
}
