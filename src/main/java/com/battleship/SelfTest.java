package com.battleship;

import com.battleship.ai.*;
import com.battleship.app.*;
import com.battleship.domain.*;
import com.battleship.engine.*;
import com.battleship.lan.*;
import com.battleship.persistence.*;
import java.net.ServerSocket;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Self-contained regression suite that runs offline without Maven/Gradle. */
public final class SelfTest {
  private static int assertions;

  private static void check(boolean condition, String message) {
    assertions++;
    if (!condition) throw new AssertionError(message);
  }

  private static void geometry() {
    Ship carrier = new Ship(ShipType.CARRIER, new Coordinate(0, 0), Orientation.HORIZONTAL);
    check(
        carrier
            .cells()
            .equals(
                List.of(
                    new Coordinate(0, 0),
                    new Coordinate(0, 1),
                    new Coordinate(0, 2),
                    new Coordinate(0, 3),
                    new Coordinate(0, 4))),
        "carrier geometry");
    Board board = new Board(Ruleset.classic());
    check(
        !board.place(new Ship(ShipType.CARRIER, new Coordinate(6, 9), Orientation.VERTICAL)),
        "reject out of bounds");
    check(board.place(carrier), "accept carrier");
    check(
        !board.place(new Ship(ShipType.BATTLESHIP, new Coordinate(0, 4), Orientation.VERTICAL)),
        "reject overlap last cell");
    check(
        board.place(new Ship(ShipType.BATTLESHIP, new Coordinate(1, 0), Orientation.HORIZONTAL)),
        "touching allowed");
    Board noTouch = new Board(new Ruleset(true));
    check(noTouch.place(carrier), "no-touch initial placement");
    check(
        !noTouch.place(new Ship(ShipType.BATTLESHIP, new Coordinate(1, 0), Orientation.HORIZONTAL)),
        "reject edge touching");
    check(
        !noTouch.place(new Ship(ShipType.BATTLESHIP, new Coordinate(1, 5), Orientation.VERTICAL)),
        "reject corner touching");
    check(
        noTouch.place(new Ship(ShipType.BATTLESHIP, new Coordinate(2, 0), Orientation.HORIZONTAL)),
        "accept separated fleets");
    check(board.shoot(new Coordinate(4, 4)).outcome() == ShotOutcome.MISS, "miss");
    check(board.shoot(new Coordinate(0, 0)).outcome() == ShotOutcome.HIT, "first hit not sunk");
    check(
        board.shoot(new Coordinate(0, 0)).outcome() == ShotOutcome.INVALID,
        "duplicate shot rejected");
    for (int c = 1; c < 4; c++)
      check(
          board.shoot(new Coordinate(0, c)).outcome() == ShotOutcome.HIT,
          "ship not prematurely sunk");
    check(board.shoot(new Coordinate(0, 4)).outcome() == ShotOutcome.SUNK, "final hit sinks");
    check(board.sunk(carrier), "carrier sunk status");
  }

  private static Match readyMatch(long seed, boolean noTouch) {
    Match m = new Match(new Ruleset(noTouch));
    check(m.randomize(0, new Random(seed)), "random player fleet");
    check(m.randomize(1, new Random(seed + 1)), "random opponent fleet");
    m.start((int) (seed & 1));
    return m;
  }

  private static void transitions() {
    Match m = readyMatch(4, false);
    ShotResult wrong = m.fire(1, new Coordinate(0, 0));
    check(
        wrong.outcome() == ShotOutcome.INVALID && m.turn() == 0 && m.history().isEmpty(),
        "wrong player cannot fire");
    ShotResult first = m.fire(0, new Coordinate(0, 0));
    check(first.valid(), "first shot valid");
    int previousShots = m.history().size();
    check(m.fire(0, new Coordinate(1, 0)).outcome() == ShotOutcome.INVALID, "out of turn invalid");
    check(
        m.history().size() == previousShots && m.turn() == 1,
        "invalid shot leaves turn/history intact");
    check(m.fire(1, new Coordinate(9, 9)).valid(), "other player turn");
    check(
        m.fire(0, new Coordinate(0, 0)).outcome() == ShotOutcome.INVALID, "duplicate shot invalid");
    check(m.turn() == 0, "duplicate shot does not consume turn");
    check(
        m.fire(0, new Coordinate(-1, 0)).outcome() == ShotOutcome.INVALID,
        "negative coordinates invalid");
    check(m.fire(0, null).outcome() == ShotOutcome.INVALID, "null shot invalid");
    check(m.turn() == 0, "bad coordinates do not consume turn");
    // Deliberate winning sequence, opponent fires only at non-ship cells.
    Match winning = readyMatch(8, false);
    Set<Coordinate> playerFleet = new HashSet<>();
    for (Ship ship : winning.board(0).ships().values()) playerFleet.addAll(ship.cells());
    List<Coordinate> misses = new ArrayList<>();
    for (int r = 0; r < 10; r++)
      for (int c = 0; c < 10; c++) {
        Coordinate p = new Coordinate(r, c);
        if (!playerFleet.contains(p)) misses.add(p);
      }
    int count = 0;
    List<Coordinate> enemyCells = new ArrayList<>();
    for (Ship ship : winning.board(1).ships().values()) enemyCells.addAll(ship.cells());
    for (Coordinate p : enemyCells) {
      ShotResult shot = winning.fire(0, p);
      check(shot.valid(), "winning shot valid");
      count++;
      if (count == enemyCells.size())
        check(shot.outcome() == ShotOutcome.WIN, "last ship produces win");
      else {
        check(winning.phase() == MatchPhase.ACTIVE, "not won before final hit");
        check(
            winning.fire(1, misses.get(count - 1)).outcome() == ShotOutcome.MISS,
            "opponent misses");
      }
    }
    check(winning.winner() == 0 && winning.phase() == MatchPhase.FINISHED, "winner registered");
    check(
        winning.fire(0, new Coordinate(5, 6)).outcome() == ShotOutcome.INVALID,
        "no post-win shots");
  }

  private static void placementAndVisibility() {
    for (int i = 0; i < 150; i++) {
      boolean noTouch = i % 2 == 0;
      Board board = new Board(new Ruleset(noTouch));
      check(board.randomize(new Random(i)), "fully random fleet");
      check(board.ready(), "fleet ready");
      Set<Coordinate> cells = new HashSet<>();
      for (Ship ship : board.ships().values())
        for (Coordinate p : ship.cells())
          check(p.inside() && cells.add(p), "no overlap and in bounds");
      check(cells.size() == 17, "exactly 17 fleet cells");
    }
    Match m = readyMatch(10, false);
    OpponentKnowledge k = m.knowledge(0);
    check(
        k.hits().isEmpty() && k.misses().isEmpty() && k.sunk().isEmpty(),
        "no hidden info revealed");
    check(
        k.remaining().size() == 5 && k.unknown().size() == 100,
        "knowledge projection is complete but private");
    Coordinate water = null;
    for (int r = 0; r < 10 && water == null; r++)
      for (int c = 0; c < 10; c++) {
        Coordinate p = new Coordinate(r, c);
        if (m.board(1).shipAt(p).isEmpty()) {
          water = p;
          break;
        }
      }
    m.fire(0, water);
    check(
        m.knowledge(0).misses().contains(water) && m.knowledge(0).unknown().size() == 99,
        "miss projection");
  }

  private static void jsonAndSave() throws Exception {
    Object input = Json.parse("{\"x\":123,\"safe\":true,\"a\":[\"\\u0041\",null]}");
    check(Json.stringify(input).contains("\"safe\":true"), "JSON roundtrip");
    for (String bad : List.of("{\"a\":1,\"a\":2}", "{\"a\":1}trail", "[1,2,]", "{\"x\":1.1}")) {
      boolean rejected = false;
      try {
        Json.parse(bad);
      } catch (IllegalArgumentException e) {
        rejected = true;
      }
      check(rejected, "bad JSON rejected: " + bad);
    }
    GameSession s = new GameSession(GameMode.SOLO, Difficulty.HARD, true, 12345L);
    check(s.match.randomize(0, new Random(77)), "test fleet placed");
    s.match.start(0);
    for (int i = 0; i < 12; i++) {
      int current = s.match.turn();
      Coordinate target = new RandomAI().choose(s.match.knowledge(current), new Random(i));
      check(s.match.fire(current, target).valid(), "saved shot valid");
    }
    String contents = SaveManager.serialize(s); // no mutation of user save files
    GameSession load = SaveManager.parseAndValidate(contents);
    check(load.match.history().equals(s.match.history()), "replay restored identical turns");
    check(
        load.match.turn() == s.match.turn() && load.match.phase() == s.match.phase(),
        "turn and phase restored");
    check(load.match.board(0).ships().equals(s.match.board(0).ships()), "fleet restored");
    String forged = contents.replaceFirst("\\\"turn\\\":\\d+", "\"turn\":99");
    boolean rejected = false;
    try {
      SaveManager.parseAndValidate(forged);
    } catch (RuntimeException e) {
      rejected = true;
    }
    check(rejected, "inconsistent turn rejected");
  }

  private static void simulate(int matches) {
    BattleshipAI[] bots = {new RandomAI(), new HuntTargetAI(), new ProbabilityAI()};
    long started = System.nanoTime();
    for (int i = 0; i < matches; i++) {
      Match match = new Match(new Ruleset(i % 7 == 0));
      check(match.randomize(0, new Random(i * 2L + 5)), "simulation fleet 1");
      check(match.randomize(1, new Random(i * 2L + 6)), "simulation fleet 2");
      match.start(i % 2);
      int moves = 0;
      while (match.phase() == MatchPhase.ACTIVE) {
        int player = match.turn();
        OpponentKnowledge knowledge = match.knowledge(player);
        Coordinate target =
            bots[(i + player) % bots.length].choose(knowledge, new Random(i * 1000L + moves));
        check(knowledge.available(target), "AI used available coordinate");
        ShotResult shot = match.fire(player, target);
        check(shot.valid(), "AI shot valid");
        check(++moves <= 199, "match terminates in at most 199 shots");
      }
      check(match.winner() == 0 || match.winner() == 1, "game has winner");
    }
    System.out.printf(
        "%d headless matches complete in %.2fs%n", matches, (System.nanoTime() - started) * 1e-9);
  }

  private static void lanRoundTrip() throws Exception {
    int port;
    try (ServerSocket free = new ServerSocket(0)) {
      port = free.getLocalPort();
    }
    if (port < 1024) throw new AssertionError("Bad local test port");
    AtomicReference<LanProtocol.MatchView> hostState = new AtomicReference<>(),
        guestState = new AtomicReference<>();
    AtomicReference<String> failure = new AtomicReference<>();
    LanSession.Listener hostListener =
        new LanSession.Listener() {
          public void status(String s) {}

          public void view(LanProtocol.MatchView v) {
            hostState.set(v);
          }

          public void disconnected(String reason) {
            failure.set("host: " + reason);
          }
        };
    LanSession.Listener guestListener =
        new LanSession.Listener() {
          public void status(String s) {}

          public void view(LanProtocol.MatchView v) {
            guestState.set(v);
          }

          public void disconnected(String reason) {
            failure.set("guest: " + reason);
          }
        };
    try (LanSession host = LanSession.host(port, hostListener);
        LanSession guest = LanSession.join("127.0.0.1", port, guestListener)) {
      waitUntil(() -> host.connected() && guest.connected(), 6000, "Host and guest connect");
      Board player0 = new Board(Ruleset.classic()), player1 = new Board(Ruleset.classic());
      check(player0.randomize(new Random(999)), "LAN host placement");
      check(player1.randomize(new Random(1000)), "LAN guest placement");
      host.submitFleet(player0.ships().values());
      guest.submitFleet(player1.ships().values());
      waitUntil(
          () ->
              hostState.get() != null
                  && guestState.get() != null
                  && hostState.get().phase() == MatchPhase.ACTIVE
                  && guestState.get().phase() == MatchPhase.ACTIVE,
          6000,
          "Both peers start game");
      LanProtocol.MatchView v = guestState.get();
      check(v.own().ships().equals(player1.ships()), "Guest sees only own fleet");
      check(
          v.enemy().hits().isEmpty() && v.enemy().misses().isEmpty() && v.enemy().sunk().isEmpty(),
          "Guest enemy remains hidden");
      Map<String, Object> frame = LanProtocol.visibleState(v);
      check(!frame.containsKey("enemyShips"), "No remote ship placement in protocol");
      check(
          LanProtocol.parseView(frame, Ruleset.classic()).own().ships().equals(player1.ships()),
          "LAN view roundtrip");
      int starter = v.turn();
      Coordinate shot = new Coordinate(2, 7);
      if (starter == 0) host.fire(shot);
      else guest.fire(shot);
      waitUntil(
          () -> {
            LanProtocol.MatchView a = hostState.get(), b = guestState.get();
            return a != null && b != null && a.turn() != starter && b.turn() != starter;
          },
          6000,
          "LAN shot confirmed on both sides");
      if (starter == 0)
        check(guestState.get().ownShots().contains(shot), "Guest receives only own incoming shot");
      else check(hostState.get().ownShots().contains(shot), "Host receives incoming shot");
      int completedShots = 1;
      while (hostState.get().phase() == MatchPhase.ACTIVE) {
        LanProtocol.MatchView hv = hostState.get(), gv = guestState.get();
        int acting = hv.turn();
        OpponentKnowledge visible = acting == 0 ? hv.enemy() : gv.enemy();
        Coordinate next = visible.unknown().get(0);
        if (acting == 0) host.fire(next);
        else guest.fire(next);
        int expected = completedShots + 1;
        waitUntil(
            () ->
                lanShotCount(hostState.get()) == expected
                    && lanShotCount(guestState.get()) == expected,
            5000,
            "LAN turn " + expected + " synchronizes");
        completedShots = expected;
        check(completedShots <= 199, "LAN match terminates");
      }
      check(hostState.get().winner() == guestState.get().winner(), "LAN winner synchronized");
      check(
          hostState.get().phase() == MatchPhase.FINISHED
              && guestState.get().phase() == MatchPhase.FINISHED,
          "LAN full match completed");
      check(failure.get() == null, "No LAN connection failure: " + failure.get());
    }
  }

  private static int lanShotCount(LanProtocol.MatchView v) {
    return v == null
        ? -1
        : v.ownShots().size()
            + v.enemy().misses().size()
            + v.enemy().hits().size()
            + v.enemy().sunkCells().size();
  }

  private static void waitUntil(
      java.util.function.BooleanSupplier done, long millis, String operation) throws Exception {
    long until = System.nanoTime() + millis * 1_000_000;
    while (!done.getAsBoolean()) {
      if (System.nanoTime() > until) throw new AssertionError("LAN timeout: " + operation);
      Thread.sleep(20);
    }
  }

  public static void main(String[] args) throws Exception {
    geometry();
    transitions();
    placementAndVisibility();
    jsonAndSave();
    lanRoundTrip();
    simulate(args.length == 0 ? 100 : Integer.parseInt(args[0]));
    System.out.println("PASS — " + assertions + " assertions, zero invalid AI shots");
  }
}
