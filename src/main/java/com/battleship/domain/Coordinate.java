package com.battleship.domain;

public record Coordinate(int row, int col) {
  public static final int SIZE = 10;

  public boolean inside() {
    return row >= 0 && row < SIZE && col >= 0 && col < SIZE;
  }

  @Override
  public String toString() {
    return (char) ('A' + row) + Integer.toString(col + 1);
  }
}
