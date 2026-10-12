package com.battleship.engine;

import com.battleship.domain.*;
import java.util.*;

/** Single source of truth for match rules, safe to run headlessly. */
public final class Match {
  public record Turn(int player, Coordinate target, ShotOutcome outcome, ShipType ship) {}

  private final Ruleset rules;
  private final Board[] boards;
  private final List<Turn> history = new ArrayList<>();
  private MatchPhase phase = MatchPhase.SETUP;
  private int starter = -1, turn = -1, winner = -1;

  public Match(Ruleset rules) {
    this.rules = Objects.requireNonNull(rules);
    boards = new Board[] {new Board(rules), new Board(rules)};
  }

  public Ruleset rules() {
    return rules;
  }

  public Board board(int player) {
    validatePlayer(player);
    return boards[player].copy();
  }

  public MatchPhase phase() {
    return phase;
  }

  public int turn() {
    return turn;
  }

  public int starter() {
    return starter;
  }

  public int winner() {
    return winner;
  }

  public List<Turn> history() {
    return List.copyOf(history);
  }

  private void validatePlayer(int p) {
    if (p < 0 || p > 1) throw new IllegalArgumentException("Player must be 0 or 1");
  }

  public boolean place(int p, Ship ship) {
    validatePlayer(p);
    return phase == MatchPhase.SETUP && boards[p].place(ship);
  }

  public boolean remove(int p, ShipType type) {
    validatePlayer(p);
    return phase == MatchPhase.SETUP && boards[p].remove(type);
  }

  public boolean randomize(int p, Random rng) {
    validatePlayer(p);
    return phase == MatchPhase.SETUP && boards[p].randomize(rng);
  }

  public void clearPlacement(int p) {
    validatePlayer(p);
    if (phase != MatchPhase.SETUP) throw new IllegalStateException("Cannot edit fleet after start");
    boards[p].clearPlacement();
  }

  public void start(int first) {
    validatePlayer(first);
    if (phase != MatchPhase.SETUP || !boards[0].ready() || !boards[1].ready())
      throw new IllegalStateException("Fleets not ready");
    boards[0].lockPlacement();
    boards[1].lockPlacement();
    phase = MatchPhase.ACTIVE;
    starter = first;
    turn = first;
  }

  public ShotResult fire(int player, Coordinate target) {
    if (phase != MatchPhase.ACTIVE
        || player != turn
        || target == null
        || !target.inside()
        || boards[1 - player].shots().contains(target)) return ShotResult.invalid(target);
    ShotResult shot = boards[1 - player].shoot(target);
    if (!shot.valid()) return shot;
    if (boards[1 - player].allSunk()) {
      shot = new ShotResult(ShotOutcome.WIN, target, shot.ship());
      winner = player;
      phase = MatchPhase.FINISHED;
    } else turn = 1 - player;
    history.add(new Turn(player, target, shot.outcome(), shot.ship()));
    return shot;
  }

  public OpponentKnowledge knowledge(int player) {
    validatePlayer(player);
    Board enemy = boards[1 - player];
    Set<Coordinate> misses = new HashSet<>(), hits = new HashSet<>();
    Map<ShipType, List<Coordinate>> sunk = new EnumMap<>(ShipType.class);
    for (Ship ship : enemy.ships().values())
      if (enemy.sunk(ship)) sunk.put(ship.type(), ship.cells());
    Set<Coordinate> sunkCells = new HashSet<>();
    sunk.values().forEach(sunkCells::addAll);
    for (Coordinate p : enemy.shots()) {
      if (enemy.shipAt(p).isEmpty()) misses.add(p);
      else if (!sunkCells.contains(p)) hits.add(p);
    }
    List<ShipType> remaining =
        Arrays.stream(ShipType.values()).filter(t -> !sunk.containsKey(t)).toList();
    return new OpponentKnowledge(misses, hits, sunk, remaining, rules);
  }
}
