package com.battleship.domain;

public enum ShipType {
  CARRIER("Carrier", 5),
  BATTLESHIP("Battleship", 4),
  CRUISER("Cruiser", 3),
  SUBMARINE("Submarine", 3),
  DESTROYER("Destroyer", 2);
  private final String label;
  private final int length;

  ShipType(String label, int length) {
    this.label = label;
    this.length = length;
  }

  public String label() {
    return label;
  }

  public int length() {
    return length;
  }

  @Override
  public String toString() {
    return label + "  ·  " + length + " cells";
  }
}
