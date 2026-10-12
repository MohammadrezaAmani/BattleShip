package com.battleship;

import static org.junit.jupiter.api.Assertions.*;

import com.battleship.ai.*;
import com.battleship.domain.*;
import com.battleship.engine.Match;
import java.util.*;
import org.junit.jupiter.api.Test;

class GameEngineTest {
  @Test
  void geometryIsExact() {
    Ship ship = new Ship(ShipType.CARRIER, new Coordinate(0, 0), Orientation.HORIZONTAL);
    assertEquals(
        List.of(
            new Coordinate(0, 0),
            new Coordinate(0, 1),
            new Coordinate(0, 2),
            new Coordinate(0, 3),
            new Coordinate(0, 4)),
        ship.cells());
    assertFalse(
        new Board(Ruleset.classic())
            .place(new Ship(ShipType.CARRIER, new Coordinate(6, 9), Orientation.VERTICAL)));
  }

  @Test
  void touchingRulesAreExplicit() {
    Board normal = new Board(Ruleset.classic()), strict = new Board(new Ruleset(true));
    Ship a = new Ship(ShipType.CARRIER, new Coordinate(0, 0), Orientation.HORIZONTAL);
    Ship b = new Ship(ShipType.BATTLESHIP, new Coordinate(1, 0), Orientation.HORIZONTAL);
    assertTrue(normal.place(a));
    assertTrue(normal.place(b));
    assertTrue(strict.place(a));
    assertFalse(strict.place(b));
  }

  @Test
  void hitDoesNotPrematurelySink() {
    Board board = new Board(Ruleset.classic());
    assertTrue(
        board.place(new Ship(ShipType.CRUISER, new Coordinate(2, 1), Orientation.HORIZONTAL)));
    assertEquals(ShotOutcome.HIT, board.shoot(new Coordinate(2, 1)).outcome());
    assertEquals(ShotOutcome.HIT, board.shoot(new Coordinate(2, 2)).outcome());
    assertEquals(ShotOutcome.SUNK, board.shoot(new Coordinate(2, 3)).outcome());
    assertEquals(ShotOutcome.INVALID, board.shoot(new Coordinate(2, 3)).outcome());
  }

  @Test
  void invalidMovesCannotChangeTurns() {
    Match match = new Match(Ruleset.classic());
    assertTrue(match.randomize(0, new Random(1)));
    assertTrue(match.randomize(1, new Random(2)));
    match.start(0);
    assertEquals(ShotOutcome.INVALID, match.fire(1, new Coordinate(2, 2)).outcome());
    assertEquals(0, match.turn());
    assertTrue(match.fire(0, new Coordinate(2, 2)).valid());
    assertEquals(1, match.turn());
    assertEquals(ShotOutcome.INVALID, match.fire(1, new Coordinate(100, 100)).outcome());
    assertEquals(1, match.turn());
  }

  @Test
  void exposedBoardsAreDefensiveCopies() {
    Match match = new Match(Ruleset.classic());
    assertTrue(match.randomize(0, new Random(3)));
    assertTrue(match.randomize(1, new Random(4)));
    match.start(0);
    Board copy = match.board(1);
    assertTrue(copy.shoot(new Coordinate(0, 0)).valid());
    assertEquals(ShotOutcome.INVALID, copy.shoot(new Coordinate(0, 0)).outcome());
    assertTrue(match.board(1).shots().isEmpty(), "Cannot mutate match through view");
    assertFalse(copy.randomize(new Random(99)), "Fleet cannot be edited after the match starts");
  }

  @Test
  void aiNeverUsesHiddenBoards() {
    Match match = new Match(Ruleset.classic());
    assertTrue(match.randomize(0, new Random(3)));
    assertTrue(match.randomize(1, new Random(4)));
    match.start(1);
    for (BattleshipAI ai : List.of(new RandomAI(), new HuntTargetAI(), new ProbabilityAI())) {
      Coordinate target = ai.choose(match.knowledge(1), new Random(5));
      assertTrue(match.knowledge(1).available(target));
    }
  }
}
