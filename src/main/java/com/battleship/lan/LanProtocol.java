package com.battleship.lan;

import com.battleship.domain.*;
import com.battleship.persistence.Json;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Versioned, length-prefixed JSON messages. No native Java object serialization. */
public final class LanProtocol {
  public static final int VERSION = 1, MAX_BYTES = 65536;

  private LanProtocol() {}

  public static Map<String, Object> message(String type) {
    return new LinkedHashMap<>(Map.of("v", VERSION, "type", type));
  }

  public static void write(DataOutputStream out, Map<String, Object> message) throws IOException {
    byte[] bytes = Json.stringify(message).getBytes(StandardCharsets.UTF_8);
    if (bytes.length > MAX_BYTES) throw new IOException("Packet exceeds allowed size");
    synchronized (out) {
      out.writeInt(bytes.length);
      out.write(bytes);
      out.flush();
    }
  }

  public static Map<String, Object> read(DataInputStream in) throws IOException {
    int len = in.readInt();
    if (len <= 0 || len > MAX_BYTES) throw new IOException("Invalid frame size: " + len);
    byte[] bytes = new byte[len];
    in.readFully(bytes);
    Map<String, Object> packet;
    try {
      packet = Json.object(Json.parse(new String(bytes, StandardCharsets.UTF_8)));
      if (Json.integer(packet.get("v")) != VERSION)
        throw new IllegalArgumentException("Unsupported protocol version");
      if (Json.string(packet.get("type")).length() > 24)
        throw new IllegalArgumentException("Invalid packet type");
    } catch (RuntimeException e) {
      throw new IOException("Malformed packet: " + e.getMessage(), e);
    }
    return packet;
  }

  public static List<Object> encodeShips(Collection<Ship> ships) {
    List<Object> out = new ArrayList<>();
    for (Ship s : ships)
      out.add(
          Map.of(
              "type",
              s.type().name(),
              "row",
              s.origin().row(),
              "col",
              s.origin().col(),
              "orientation",
              s.orientation().name()));
    return out;
  }

  public static List<Ship> decodeShips(Object input) {
    List<Object> entries = Json.array(input);
    if (entries.size() > 5) throw new IllegalArgumentException("Fleet too large");
    List<Ship> ships = new ArrayList<>();
    Set<ShipType> seen = new HashSet<>();
    for (Object entry : entries) {
      Map<String, Object> e = Json.object(entry);
      ShipType type = ShipType.valueOf(Json.string(e.get("type")));
      if (!seen.add(type)) throw new IllegalArgumentException("Duplicate ship type");
      ships.add(
          new Ship(
              type,
              new Coordinate(Json.integer(e.get("row")), Json.integer(e.get("col"))),
              Orientation.valueOf(Json.string(e.get("orientation")))));
    }
    return ships;
  }

  public static List<Object> encodeCells(Collection<Coordinate> cells) {
    List<Object> result = new ArrayList<>();
    for (Coordinate p : cells) result.add(List.of(p.row(), p.col()));
    return result;
  }

  public static Set<Coordinate> decodeCells(Object cells) {
    Set<Coordinate> out = new HashSet<>();
    for (Object object : Json.array(cells)) {
      List<Object> p = Json.array(object);
      if (p.size() != 2) throw new IllegalArgumentException("Coordinate requires row and col");
      Coordinate c = new Coordinate(Json.integer(p.get(0)), Json.integer(p.get(1)));
      if (!c.inside() || !out.add(c))
        throw new IllegalArgumentException("Duplicate/out of range coordinate");
    }
    return out;
  }

  public static Map<String, Object> visibleState(MatchView view) {
    Map<String, Object> m = message("STATE");
    m.put("phase", view.phase().name());
    m.put("turn", view.turn());
    m.put("winner", view.winner());
    m.put("ownShips", encodeShips(view.ownShips()));
    m.put("ownShots", encodeCells(view.ownShots()));
    OpponentKnowledge k = view.enemy();
    m.put("misses", encodeCells(k.misses()));
    m.put("hits", encodeCells(k.hits()));
    Map<String, Object> sunk = new LinkedHashMap<>();
    k.sunk().forEach((type, cells) -> sunk.put(type.name(), encodeCells(cells)));
    m.put("sunk", sunk);
    m.put("remaining", k.remaining().stream().map(Enum::name).toList());
    m.put("last", view.last());
    return m;
  }

  public static MatchView parseView(Map<String, Object> m, Ruleset rules) {
    MatchPhase phase = MatchPhase.valueOf(Json.string(m.get("phase")));
    int turn = Json.integer(m.get("turn")), winner = Json.integer(m.get("winner"));
    if (turn < -1 || turn > 1 || winner < -1 || winner > 1)
      throw new IllegalArgumentException("Invalid turn or winner");
    Board own = new Board(rules);
    for (Ship ship : decodeShips(m.get("ownShips")))
      if (!own.place(ship)) throw new IllegalArgumentException("Invalid own ship placement");
    for (Coordinate hit : decodeCells(m.get("ownShots")))
      if (!own.shoot(hit).valid()) throw new IllegalArgumentException("Bad own shot list");
    Set<Coordinate> misses = decodeCells(m.get("misses")), hits = decodeCells(m.get("hits"));
    Map<ShipType, List<Coordinate>> sunk = new EnumMap<>(ShipType.class);
    Json.object(m.get("sunk"))
        .forEach(
            (t, coords) -> sunk.put(ShipType.valueOf(t), new ArrayList<>(decodeCells(coords))));
    List<ShipType> remaining = new ArrayList<>();
    for (Object o : Json.array(m.get("remaining"))) remaining.add(ShipType.valueOf(Json.string(o)));
    if (!Collections.disjoint(misses, hits))
      throw new IllegalArgumentException("Overlapping knowledge");
    OpponentKnowledge knowledge = new OpponentKnowledge(misses, hits, sunk, remaining, rules);
    if (m.get("last") != null && Json.string(m.get("last")).length() > 160)
      throw new IllegalArgumentException("Invalid description");
    return new MatchView(
        own.ships().values(),
        own.shots(),
        own,
        knowledge,
        phase,
        turn,
        winner,
        m.get("last") == null ? "" : Json.string(m.get("last")));
  }

  public record MatchView(
      Collection<Ship> ownShips,
      Set<Coordinate> ownShots,
      Board own,
      OpponentKnowledge enemy,
      MatchPhase phase,
      int turn,
      int winner,
      String last) {}
}
