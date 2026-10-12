package com.battleship.persistence;

import com.battleship.app.*;
import com.battleship.domain.*;
import com.battleship.engine.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Local-only, versioned snapshots. Rebuilds every match by replaying moves to validate invariants.
 */
public final class SaveManager {
  public static Path directory() {
    String home = System.getProperty("user.home");
    if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
      String appdata = System.getenv("APPDATA");
      return Path.of(appdata != null ? appdata : home, "BattleShip");
    }
    String xdg = System.getenv("XDG_DATA_HOME");
    return (xdg == null || xdg.isBlank() ? Path.of(home, ".local", "share") : Path.of(xdg))
        .resolve("battleship");
  }

  public static Path savePath() {
    return directory().resolve("savegame.json");
  }

  private static void atomicWrite(Path path, String content) throws IOException {
    Files.createDirectories(path.getParent());
    Path temp = Files.createTempFile(path.getParent(), "bs-", ".tmp");
    try {
      Files.writeString(temp, content, StandardCharsets.UTF_8);
      if (Files.isRegularFile(path))
        Files.copy(
            path,
            path.resolveSibling(path.getFileName() + ".bak"),
            StandardCopyOption.REPLACE_EXISTING);
      try {
        Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  public static String serialize(GameSession s) {
    Map<String, Object> obj = new LinkedHashMap<>();
    Match m = s.match;
    obj.put("schemaVersion", 1);
    obj.put("mode", s.mode.name());
    obj.put("difficulty", s.difficulty.name());
    obj.put("seed", s.seed);
    obj.put("noTouching", m.rules().noTouching());
    obj.put("setupPlayer", s.setupPlayer);
    obj.put("phase", m.phase().name());
    obj.put("starter", m.starter());
    obj.put("turn", m.turn());
    obj.put("winner", m.winner());
    obj.put("reducedMotion", s.reducedMotion);
    List<Object> boards = new ArrayList<>();
    for (int p = 0; p < 2; p++) {
      List<Object> ships = new ArrayList<>();
      for (Ship ship : m.board(p).ships().values()) {
        ships.add(
            Map.of(
                "type",
                ship.type().name(),
                "row",
                ship.origin().row(),
                "col",
                ship.origin().col(),
                "orientation",
                ship.orientation().name()));
      }
      boards.add(Map.of("ships", ships));
    }
    obj.put("boards", boards);
    List<Object> shots = new ArrayList<>();
    for (Match.Turn t : m.history())
      shots.add(
          Map.of(
              "player",
              t.player(),
              "row",
              t.target().row(),
              "col",
              t.target().col(),
              "outcome",
              t.outcome().name()));
    obj.put("shots", shots);
    return Json.stringify(obj);
  }

  public static void writeJson(String text) throws IOException {
    atomicWrite(savePath(), text);
  }

  public static void save(GameSession s) throws IOException {
    writeJson(serialize(s));
  }

  public static boolean hasSave() {
    return Files.isRegularFile(savePath());
  }

  public static GameSession load() throws IOException {
    if (Files.size(savePath()) > 1_000_000) throw new IOException("Save exceeds size limit");
    try {
      return parseAndValidate(Files.readString(savePath(), StandardCharsets.UTF_8));
    } catch (RuntimeException e) {
      throw new IOException("Save is invalid or corrupted: " + e.getMessage(), e);
    }
  }

  public static GameSession parseAndValidate(String raw) {
    if (raw.length() > 1_000_000) throw new IllegalArgumentException("Save exceeds size limit");
    Map<String, Object> m = Json.object(Json.parse(raw));
    if (Json.integer(m.get("schemaVersion")) != 1)
      throw new IllegalArgumentException("Unsupported save schema");
    GameMode mode = GameMode.valueOf(Json.string(m.get("mode")));
    Difficulty level = Difficulty.valueOf(Json.string(m.get("difficulty")));
    GameSession s =
        new GameSession(
            mode, level, Json.bool(m.get("noTouching")), Json.longNumber(m.get("seed")));
    s.reducedMotion = Json.bool(m.get("reducedMotion"));
    s.setupPlayer = Json.integer(m.get("setupPlayer"));
    if (s.setupPlayer < 0 || s.setupPlayer > 1)
      throw new IllegalArgumentException("Invalid setup player");
    List<Object> boards = Json.array(m.get("boards"));
    if (boards.size() != 2) throw new IllegalArgumentException("Invalid number of boards");
    for (int p = 0; p < 2; p++) {
      s.match.clearPlacement(p);
      List<Object> ships = Json.array(Json.object(boards.get(p)).get("ships"));
      if (ships.size() > ShipType.values().length)
        throw new IllegalArgumentException("Too many ships");
      for (Object object : ships) {
        Map<String, Object> data = Json.object(object);
        Ship ship =
            new Ship(
                ShipType.valueOf(Json.string(data.get("type"))),
                new Coordinate(Json.integer(data.get("row")), Json.integer(data.get("col"))),
                Orientation.valueOf(Json.string(data.get("orientation"))));
        if (s.match.board(p).ships().containsKey(ship.type()) || !s.match.place(p, ship))
          throw new IllegalArgumentException("Invalid fleet geometry");
      }
    }
    int starter = Json.integer(m.get("starter"));
    MatchPhase phase = MatchPhase.valueOf(Json.string(m.get("phase")));
    if (phase != MatchPhase.SETUP) {
      s.match.start(starter);
    } else if (starter != -1) throw new IllegalArgumentException("Starter invalid during setup");
    List<Object> turns = Json.array(m.get("shots"));
    if (turns.size() > 200) throw new IllegalArgumentException("Too many shots");
    for (Object object : turns) {
      Map<String, Object> t = Json.object(object);
      ShotResult result =
          s.match.fire(
              Json.integer(t.get("player")),
              new Coordinate(Json.integer(t.get("row")), Json.integer(t.get("col"))));
      if (!result.valid() || result.outcome() != ShotOutcome.valueOf(Json.string(t.get("outcome"))))
        throw new IllegalArgumentException("Shot history is inconsistent");
    }
    if (s.match.phase() != phase
        || s.match.turn() != Json.integer(m.get("turn"))
        || s.match.winner() != Json.integer(m.get("winner")))
      throw new IllegalArgumentException("Saved phase is inconsistent");
    if (mode == GameMode.SOLO && s.setupPlayer != 0)
      throw new IllegalArgumentException("Invalid solo setup");
    if (mode == GameMode.LOCAL
        && phase == MatchPhase.SETUP
        && ((s.setupPlayer == 0 && !s.match.board(1).ships().isEmpty())
            || (s.setupPlayer == 1 && !s.match.board(0).ready())))
      throw new IllegalArgumentException("Local setup stage inconsistent");
    if (phase == MatchPhase.SETUP && !turns.isEmpty())
      throw new IllegalArgumentException("Shots in setup");
    return s;
  }

  public static boolean loadSettingReducedMotion() {
    try {
      Path settings = directory().resolve("settings.json");
      if (!Files.isRegularFile(settings)) return false;
      return Json.bool(Json.object(Json.parse(Files.readString(settings))).get("reducedMotion"));
    } catch (Exception e) {
      return false;
    }
  }

  public static void saveSettingReducedMotion(boolean value) throws IOException {
    atomicWrite(
        directory().resolve("settings.json"),
        Json.stringify(Map.of("schemaVersion", 1, "reducedMotion", value)));
  }
}
