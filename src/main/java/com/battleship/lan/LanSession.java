package com.battleship.lan;

import com.battleship.domain.*;
import com.battleship.engine.Match;
import com.battleship.persistence.Json;
import java.io.*;
import java.net.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.SwingUtilities;

/** One authority (host), one guest. Socket I/O never runs on Swing's event thread. */
public final class LanSession implements AutoCloseable {
  public interface Listener {
    void status(String message);

    void view(LanProtocol.MatchView snapshot);

    void disconnected(String reason);
  }

  public enum Role {
    HOST,
    GUEST
  }

  private final Role role;
  private final String address;
  private final int port;
  private final Listener listener;
  private final ExecutorService tasks =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "battleship-lan-logic");
            t.setDaemon(true);
            return t;
          });
  private final Match match;
  private volatile boolean closed, connected, ready, remoteReady;
  private volatile Socket socket;
  private volatile DataOutputStream output;
  private volatile ServerSocket server;
  private volatile LanProtocol.MatchView snapshot;
  private volatile String sessionToken = "";
  private long lastRequest = 0, nextRequest = 0;
  private final SecureRandom random = new SecureRandom();

  private LanSession(Role role, String address, int port, Listener listener) {
    if (port < 1024 || port > 65535)
      throw new IllegalArgumentException("Choose a port between 1024 and 65535");
    this.role = role;
    this.address = address;
    this.port = port;
    this.listener = listener;
    match = role == Role.HOST ? new Match(Ruleset.classic()) : null;
  }

  public static LanSession host(int port, Listener listener) throws IOException {
    LanSession s = new LanSession(Role.HOST, "0.0.0.0", port, listener);
    s.server = new ServerSocket();
    s.server.setReuseAddress(true);
    s.server.bind(new InetSocketAddress(port), 1);
    Thread acceptor = new Thread(s::acceptLoop, "battleship-lan-accept");
    acceptor.setDaemon(true);
    acceptor.start();
    s.status("Hosting on port " + port + " · Waiting for another player");
    return s;
  }

  public static LanSession join(String address, int port, Listener listener) {
    LanSession s = new LanSession(Role.GUEST, address, port, listener);
    s.connect();
    return s;
  }

  public Role role() {
    return role;
  }

  public boolean connected() {
    return connected;
  }

  public boolean ready() {
    return ready;
  }

  public LanProtocol.MatchView view() {
    return snapshot;
  }

  private void status(String message) {
    SwingUtilities.invokeLater(
        () -> {
          if (!closed) listener.status(message);
        });
  }

  private void updated(LanProtocol.MatchView view) {
    snapshot = view;
    SwingUtilities.invokeLater(
        () -> {
          if (!closed) listener.view(view);
        });
  }

  private void disconnected(String reason) {
    if (closed) return;
    connected = false;
    SwingUtilities.invokeLater(
        () -> {
          if (!closed) listener.disconnected(reason);
        });
  }

  private void acceptLoop() {
    while (!closed) {
      try {
        Socket peer = server.accept();
        peer.setTcpNoDelay(true);
        peer.setSoTimeout(10000);
        DataInputStream in = new DataInputStream(new BufferedInputStream(peer.getInputStream()));
        DataOutputStream out =
            new DataOutputStream(new BufferedOutputStream(peer.getOutputStream()));
        Map<String, Object> hello = LanProtocol.read(in);
        if (!"HELLO".equals(hello.get("type"))) {
          peer.close();
          continue;
        }
        String received = Json.string(hello.get("token"));
        boolean resume = !sessionToken.isEmpty();
        if ((resume && !sessionToken.equals(received)) || (connected)) {
          LanProtocol.write(
              out,
              Map.of(
                  "v",
                  LanProtocol.VERSION,
                  "type",
                  "ERROR",
                  "reason",
                  "Session is reserved; invalid reconnect token"));
          peer.close();
          continue;
        }
        if (!resume) sessionToken = UUID.randomUUID().toString();
        LanProtocol.write(
            out, Map.of("v", LanProtocol.VERSION, "type", "WELCOME", "token", sessionToken));
        peer.setSoTimeout(0);
        socket = peer;
        output = out;
        connected = true;
        status(resume ? "Guest reconnected" : "Guest connected · Place your fleet and press READY");
        tasks.execute(
            () -> {
              if (match.phase() == MatchPhase.ACTIVE || match.phase() == MatchPhase.FINISHED)
                push();
            });
        readLoop(peer, in);
      } catch (SocketException e) {
        if (!closed) disconnected("Socket error: " + e.getMessage());
      } catch (Exception e) {
        if (!closed) status("Connection rejected or closed: " + e.getMessage());
      }
    }
  }

  public void connect() {
    if (role != Role.GUEST || closed) return;
    Thread connectThread =
        new Thread(
            () -> {
              try {
                Socket peer = new Socket();
                peer.connect(new InetSocketAddress(address, port), 5000);
                peer.setSoTimeout(10000);
                peer.setTcpNoDelay(true);
                DataInputStream in =
                    new DataInputStream(new BufferedInputStream(peer.getInputStream()));
                DataOutputStream out =
                    new DataOutputStream(new BufferedOutputStream(peer.getOutputStream()));
                LanProtocol.write(
                    out, Map.of("v", LanProtocol.VERSION, "type", "HELLO", "token", sessionToken));
                Map<String, Object> welcome = LanProtocol.read(in);
                if (!"WELCOME".equals(welcome.get("type")))
                  throw new IOException(
                      "Connection rejected: " + welcome.getOrDefault("reason", "Unknown reason"));
                sessionToken = Json.string(welcome.get("token"));
                peer.setSoTimeout(0);
                socket = peer;
                output = out;
                connected = true;
                status("Connected to " + address + ":" + port);
                if (ready) tasks.execute(() -> sendFleet(stagedFleet));
                readLoop(peer, in);
              } catch (Exception e) {
                if (!closed) disconnected("Connection failed: " + e.getMessage());
              }
            },
            "battleship-lan-connect");
    connectThread.setDaemon(true);
    connectThread.start();
  }

  private volatile List<Ship> stagedFleet = List.of();

  public void submitFleet(Collection<Ship> fleet) {
    if (closed || ready) return;
    List<Ship> candidates = List.copyOf(fleet);
    if (candidates.size() != 5) throw new IllegalArgumentException("Fleet must contain five ships");
    Board check = new Board(Ruleset.classic());
    for (Ship ship : candidates)
      if (check.ships().containsKey(ship.type()) || !check.place(ship))
        throw new IllegalArgumentException("Invalid fleet placement");
    if (!check.ready()) throw new IllegalArgumentException("Fleet incomplete");
    stagedFleet = candidates;
    ready = true;
    tasks.execute(
        () -> {
          if (role == Role.HOST) {
            for (Ship ship : candidates)
              if (!match.place(0, ship)) throw new IllegalStateException("Host placement invalid");
            status(
                "Fleet ready · " + (remoteReady ? "Starting game" : "Waiting for guest's fleet"));
            tryStart();
          } else {
            sendFleet(candidates);
            status("Fleet ready · Waiting for host");
          }
        });
  }

  private void sendFleet(List<Ship> fleet) {
    if (!connected) return;
    Map<String, Object> msg = LanProtocol.message("FLEET");
    msg.put("ships", LanProtocol.encodeShips(fleet));
    send(msg);
  }

  public void fire(Coordinate target) {
    if (target == null || !target.inside() || closed) return;
    tasks.execute(
        () -> {
          if (role == Role.HOST) {
            if (match.phase() != MatchPhase.ACTIVE || match.turn() != 0) return;
            ShotResult result = match.fire(0, target);
            if (result.valid()) push();
          } else {
            if (snapshot == null
                || snapshot.phase() != MatchPhase.ACTIVE
                || snapshot.turn() != 1
                || !snapshot.enemy().available(target)) return;
            Map<String, Object> command = LanProtocol.message("FIRE");
            command.put("row", target.row());
            command.put("col", target.col());
            command.put("requestId", ++nextRequest);
            send(command);
          }
        });
  }

  public void resign() {
    tasks.execute(
        () -> {
          if (role == Role.GUEST) send(LanProtocol.message("RESIGN"));
          else close();
        });
  }

  private void tryStart() {
    if (role != Role.HOST
        || !ready
        || !remoteReady
        || !connected
        || match.phase() != MatchPhase.SETUP) return;
    match.start(random.nextInt(2));
    push();
  }

  private LanProtocol.MatchView viewFor(int p) {
    Board src = match.board(p);
    Board own = new Board(Ruleset.classic());
    src.ships().values().forEach(own::place);
    src.shots().forEach(own::shoot);
    String last = "";
    List<Match.Turn> history = match.history();
    if (!history.isEmpty()) {
      Match.Turn t = history.get(history.size() - 1);
      last = "PLAYER " + (t.player() + 1) + " · " + t.target() + " · " + t.outcome();
    }
    return new LanProtocol.MatchView(
        own.ships().values(),
        own.shots(),
        own,
        match.knowledge(p),
        match.phase(),
        match.turn(),
        match.winner(),
        last);
  }

  private void push() {
    if (role != Role.HOST) return;
    updated(viewFor(0));
    if (connected) send(LanProtocol.visibleState(viewFor(1)));
  }

  private void receive(Map<String, Object> m) {
    if (closed) return;
    try {
      String type = Json.string(m.get("type"));
      if (role == Role.HOST) {
        switch (type) {
          case "FLEET" -> {
            if (match.phase() != MatchPhase.SETUP) {
              push();
              break;
            }
            List<Ship> ships = LanProtocol.decodeShips(m.get("ships"));
            Board board = new Board(Ruleset.classic());
            for (Ship ship : ships)
              if (board.ships().containsKey(ship.type()) || !board.place(ship))
                throw new IllegalArgumentException("Invalid fleet");
            if (!board.ready()) throw new IllegalArgumentException("Incomplete fleet");
            match.clearPlacement(1);
            for (Ship ship : ships) match.place(1, ship);
            remoteReady = true;
            status("Guest's fleet ready");
            tryStart();
          }
          case "FIRE" -> {
            long id = Json.longNumber(m.get("requestId"));
            if (id <= lastRequest) {
              push();
              break;
            }
            if (id > lastRequest + 1_000_000)
              throw new IllegalArgumentException("Invalid request sequence");
            lastRequest = id;
            Coordinate at = new Coordinate(Json.integer(m.get("row")), Json.integer(m.get("col")));
            ShotResult res = match.fire(1, at);
            if (!res.valid()) sendError("Invalid shot or turn");
            push();
          }
          case "PING" -> send(LanProtocol.message("PONG"));
          case "RESIGN" -> {
            status("Opponent resigned");
            close();
          }
          default -> sendError("Unknown command");
        }
      } else {
        switch (type) {
          case "STATE" -> updated(LanProtocol.parseView(m, Ruleset.classic()));
          case "ERROR" -> status("Host: " + Json.string(m.get("reason")));
          case "PONG" -> {}
          default -> status("Unsupported host message: " + type);
        }
      }
    } catch (RuntimeException e) {
      sendError("Rejected packet: " + e.getMessage());
    }
  }

  private void sendError(String text) {
    Map<String, Object> error = LanProtocol.message("ERROR");
    error.put("reason", text.length() > 200 ? text.substring(0, 200) : text);
    send(error);
  }

  private void send(Map<String, Object> packet) {
    DataOutputStream out = output;
    if (!connected || out == null) return;
    try {
      LanProtocol.write(out, packet);
    } catch (IOException e) {
      disconnected("Cannot send: " + e.getMessage());
      try {
        if (socket != null) socket.close();
      } catch (IOException ignored) {
      }
    }
  }

  private void readLoop(Socket peer, DataInputStream in) {
    try {
      while (!closed && peer == socket) {
        Map<String, Object> frame = LanProtocol.read(in);
        tasks.execute(() -> receive(frame));
      }
    } catch (IOException e) {
      if (!closed) disconnected("Peer disconnected: " + e.getMessage());
    } finally {
      try {
        peer.close();
      } catch (IOException ignored) {
      }
      if (socket == peer) {
        socket = null;
        output = null;
        connected = false;
      }
    }
  }

  @Override
  public void close() {
    closed = true;
    connected = false;
    tasks.shutdownNow();
    try {
      if (socket != null) socket.close();
    } catch (IOException ignored) {
    }
    try {
      if (server != null) server.close();
    } catch (IOException ignored) {
    }
  }
}
