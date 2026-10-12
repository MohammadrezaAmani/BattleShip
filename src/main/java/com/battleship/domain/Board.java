package com.battleship.domain;

import java.util.*;

/** Stores fleet geometry and shot history. No GUI state is ever kept in the board. */
public final class Board {
  private final Ruleset rules;
  private final EnumMap<ShipType, Ship> ships = new EnumMap<>(ShipType.class);
  private final Set<Coordinate> shots = new HashSet<>();
  private boolean placementLocked;

  public Board(Ruleset rules) {
    this.rules = Objects.requireNonNull(rules);
  }

  public Ruleset rules() {
    return rules;
  }

  public Map<ShipType, Ship> ships() {
    return Collections.unmodifiableMap(ships);
  }

  public Set<Coordinate> shots() {
    return Set.copyOf(shots);
  }

  public boolean ready() {
    return ships.size() == ShipType.values().length;
  }

  public Optional<Ship> shipAt(Coordinate p) {
    return ships.values().stream().filter(s -> s.cells().contains(p)).findFirst();
  }

  public boolean canPlace(Ship ship) {
    if (ship.cells().stream().anyMatch(p -> !p.inside())) return false;
    for (Ship other : ships.values()) {
      if (other.type() == ship.type()) continue;
      for (Coordinate a : ship.cells())
        for (Coordinate b : other.cells()) {
          int dy = Math.abs(a.row() - b.row()), dx = Math.abs(a.col() - b.col());
          if (rules.noTouching() ? dy <= 1 && dx <= 1 : dy == 0 && dx == 0) return false;
        }
    }
    return true;
  }

  public boolean place(Ship ship) {
    if (placementLocked || !shots.isEmpty()) return false;
    if (!canPlace(ship)) return false;
    ships.put(ship.type(), ship);
    return true;
  }

  public boolean remove(ShipType type) {
    return !placementLocked && shots.isEmpty() && ships.remove(type) != null;
  }

  public void clearPlacement() {
    if (placementLocked || !shots.isEmpty())
      throw new IllegalStateException("Match already started");
    ships.clear();
  }

  public ShotResult shoot(Coordinate p) {
    if (p == null || !p.inside() || !shots.add(p)) return ShotResult.invalid(p);
    Ship hit = shipAt(p).orElse(null);
    if (hit == null) return new ShotResult(ShotOutcome.MISS, p, null);
    boolean sunk = hit.cells().stream().allMatch(shots::contains);
    return new ShotResult(sunk ? ShotOutcome.SUNK : ShotOutcome.HIT, p, hit.type());
  }

  public boolean sunk(Ship ship) {
    return ship.cells().stream().allMatch(shots::contains);
  }

  public int afloat() {
    return (int) ships.values().stream().filter(s -> !sunk(s)).count();
  }

  public boolean allSunk() {
    return ready() && afloat() == 0;
  }

  public void lockPlacement() {
    placementLocked = true;
  }

  /** A defensive copy. Editing this board cannot mutate an active Match. */
  public Board copy() {
    Board result = new Board(rules);
    for (Ship ship : ships.values()) result.place(ship);
    for (Coordinate target : shots) result.shoot(target);
    if (placementLocked) result.lockPlacement();
    return result;
  }

  public int hits() {
    return (int) shots.stream().filter(p -> shipAt(p).isPresent()).count();
  }

  public int misses() {
    return shots.size() - hits();
  }

  /** Bounded randomized backtracking: guaranteed to either find a fleet or report failure. */
  public boolean randomize(Random rng) {
    if (placementLocked || !shots.isEmpty()) return false;
    ships.clear();
    if (placeBacktracking(ShipType.values(), 0, rng)) return true;
    ships.clear();
    return false;
  }

  private boolean placeBacktracking(ShipType[] types, int index, Random rng) {
    if (index == types.length) return true;
    ShipType type = types[index];
    List<Ship> possible = new ArrayList<>();
    for (Orientation dir : Orientation.values())
      for (int r = 0; r < Coordinate.SIZE; r++)
        for (int c = 0; c < Coordinate.SIZE; c++) {
          Ship ship = new Ship(type, new Coordinate(r, c), dir);
          if (canPlace(ship)) possible.add(ship);
        }
    Collections.shuffle(possible, rng);
    for (Ship s : possible) {
      ships.put(type, s);
      if (placeBacktracking(types, index + 1, rng)) return true;
      ships.remove(type);
    }
    return false;
  }
}
