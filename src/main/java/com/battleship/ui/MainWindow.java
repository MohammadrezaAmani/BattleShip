package com.battleship.ui;

import com.battleship.ai.BattleshipAI;
import com.battleship.app.*;
import com.battleship.domain.*;
import com.battleship.engine.Match;
import com.battleship.lan.LanWindow;
import com.battleship.persistence.SaveManager;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Responsive desktop flow: menu -> setup -> fleet placement -> battle -> results. */
public final class MainWindow extends JFrame {
  private final JPanel root = new JPanel(new CardLayout());
  private final ExecutorService worker =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "battleship-ai-and-io");
            t.setDaemon(true);
            return t;
          });
  private GameSession session;
  private ShipType selected = ShipType.CARRIER;
  private Orientation orientation = Orientation.HORIZONTAL;
  private boolean reducedMotion = SaveManager.loadSettingReducedMotion();
  private long generation = 0;
  private int visiblePlayer = 0;
  private boolean aiPending = false;
  private BoardCanvas placementCanvas;
  private BoardCanvas enemyCanvas;
  private JLabel feedback;
  private JLabel placementStatus;

  public MainWindow() {
    super("BATTLESHIP  |  REBORN");
    setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    setMinimumSize(new Dimension(920, 665));
    setSize(1220, 795);
    setLocationRelativeTo(null);
    setContentPane(root);
    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            confirmExit();
          }
        });
    bindKey("ESCAPE", "escape", () -> showMenuConfirm());
    showMenu();
  }

  private void bindKey(String key, String action, Runnable runnable) {
    KeyStroke stroke = KeyStroke.getKeyStroke(key);
    root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(stroke, action);
    root.getActionMap()
        .put(
            action,
            new AbstractAction() {
              @Override
              public void actionPerformed(ActionEvent e) {
                runnable.run();
              }
            });
  }

  private void screen(JComponent view) {
    root.removeAll();
    root.add(view, "screen");
    ((CardLayout) root.getLayout()).show(root, "screen");
    root.revalidate();
    root.repaint();
  }

  private JPanel vertical(int space) {
    JPanel p = Theme.panel(new GridBagLayout());
    return p;
  }

  private JPanel card() {
    JPanel p = new JPanel();
    p.setBackground(Theme.PANEL);
    p.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(54, 91, 108)),
            new EmptyBorder(24, 26, 24, 26)));
    return p;
  }

  private JPanel column() {
    JPanel p = Theme.panel(new BoxLayoutProxy());
    return p;
  }

  /** A simple predictable vertical layout with natural component widths. */
  private static class BoxLayoutProxy implements LayoutManager {
    public void addLayoutComponent(String name, Component comp) {}

    public void removeLayoutComponent(Component comp) {}

    public Dimension preferredLayoutSize(Container c) {
      int w = 0, h = 0;
      for (Component x : c.getComponents())
        if (x.isVisible()) {
          Dimension d = x.getPreferredSize();
          w = Math.max(w, d.width);
          h += d.height + 10;
        }
      return new Dimension(w, h);
    }

    public Dimension minimumLayoutSize(Container c) {
      return preferredLayoutSize(c);
    }

    public void layoutContainer(Container c) {
      int y = 0;
      for (Component x : c.getComponents())
        if (x.isVisible()) {
          Dimension d = x.getPreferredSize();
          x.setBounds(0, y, c.getWidth(), d.height);
          y += d.height + 10;
        }
    }
  }

  private JPanel heading(String title, String subtitle, Runnable back) {
    JPanel row = Theme.panel(new BorderLayout(18, 0));
    Theme.pad(row, 20, 27, 12, 27);
    JPanel labels = Theme.panel(new GridLayout(2, 1, 0, 3));
    labels.add(Theme.label(title, 25, Theme.TEXT));
    labels.add(Theme.label(subtitle, 13, Theme.MUTED));
    row.add(labels, BorderLayout.CENTER);
    if (back != null) {
      JButton b = Theme.button("←  BACK", false);
      b.addActionListener(e -> back.run());
      row.add(b, BorderLayout.WEST);
    }
    return row;
  }

  private static void gap(JPanel panel, int pixels) {
    panel.add(Box.createVerticalStrut(pixels));
  }

  private static void centered(JPanel container, Component component) {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 1;
    gbc.weighty = 1;
    gbc.anchor = GridBagConstraints.CENTER;
    container.add(component, gbc);
  }

  private void showMenu() {
    generation++;
    aiPending = false;
    session = null;
    JPanel background =
        new JPanel(new GridBagLayout()) {
          @Override
          protected void paintComponent(Graphics gr) {
            super.paintComponent(gr);
            Graphics2D g = (Graphics2D) gr.create();
            g.setPaint(
                new GradientPaint(0, 0, new Color(8, 35, 56), getWidth(), getHeight(), Theme.BG));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(new Color(54, 154, 173, 40));
            int unit = 49;
            for (int x = 0; x < getWidth(); x += unit) g.drawLine(x, 0, x, getHeight());
            for (int y = 0; y < getHeight(); y += unit) g.drawLine(0, y, getWidth(), y);
            g.dispose();
          }
        };
    JPanel hero = new JPanel();
    hero.setOpaque(false);
    hero.setLayout(new BoxLayout(hero, BoxLayout.Y_AXIS));
    hero.setPreferredSize(new Dimension(460, 635));
    JLabel top = Theme.label("TACTICAL NAVAL COMBAT", 13, Theme.ACCENT);
    top.setAlignmentX(.5f);
    hero.add(top);
    gap(hero, 16);
    JLabel name = Theme.label("BATTLESHIP", 55, Theme.TEXT);
    name.setFont(new Font("Dialog", Font.BOLD, 55));
    name.setAlignmentX(.5f);
    hero.add(name);
    JLabel sub = Theme.label("R E B O R N", 23, Theme.ACCENT);
    sub.setAlignmentX(.5f);
    hero.add(sub);
    gap(hero, 20);
    JLabel desc = Theme.label("Plan your fleet. Hunt the enemy. Own the ocean.", 14, Theme.MUTED);
    desc.setAlignmentX(.5f);
    hero.add(desc);
    gap(hero, 30);
    menuButton(hero, "NEW CAMPAIGN   ›", true, () -> showNewGame());
    JButton resume = menuButton(hero, "CONTINUE SAVED MATCH", false, () -> loadGame());
    resume.setEnabled(SaveManager.hasSave());
    menuButton(hero, "TWO PLAYERS  ·  LOCAL", false, () -> newLocal(false));
    menuButton(hero, "TWO PLAYERS  ·  DIRECT IP", false, this::showLanChoice);
    menuButton(hero, "HOW TO PLAY", false, this::showHelp);
    menuButton(hero, "SETTINGS", false, this::showSettings);
    menuButton(hero, "EXIT", false, this::confirmExit);
    gap(hero, 14);
    JLabel foot = Theme.label("JAVA 21  /  SWING  /  OFFLINE  /  NO AUDIO", 11, Theme.MUTED);
    foot.setAlignmentX(.5f);
    hero.add(foot);
    centered(background, hero);
    screen(background);
  }

  private JButton menuButton(JPanel parent, String name, boolean primary, Runnable action) {
    JButton b = Theme.button(name, primary);
    b.setMaximumSize(new Dimension(400, 46));
    b.setPreferredSize(new Dimension(380, 45));
    b.setAlignmentX(.5f);
    b.addActionListener(e -> action.run());
    parent.add(b);
    gap(parent, 6);
    return b;
  }

  private void showLanChoice() {
    String[] choices = {"HOST GAME", "JOIN BY IP", "CANCEL"};
    int selection =
        JOptionPane.showOptionDialog(
            this,
            "Play directly over a trusted local network.\nThe host must allow the selected TCP port in their firewall.",
            "Direct IP multiplayer",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            choices,
            choices[0]);
    if (selection == 2 || selection == JOptionPane.CLOSED_OPTION) return;
    JTextField portField = new JTextField("50505", 9);
    JTextField hostField = new JTextField("192.168.1.100", 17);
    JPanel fields = new JPanel(new GridLayout(selection == 0 ? 1 : 2, 2, 7, 8));
    if (selection == 1) {
      fields.add(new JLabel("Host IP:"));
      fields.add(hostField);
    }
    fields.add(new JLabel("TCP port:"));
    fields.add(portField);
    if (JOptionPane.showConfirmDialog(
            this,
            fields,
            selection == 0 ? "Host LAN game" : "Join LAN game",
            JOptionPane.OK_CANCEL_OPTION)
        != JOptionPane.OK_OPTION) return;
    try {
      int port = Integer.parseInt(portField.getText().trim());
      if (selection == 0) LanWindow.host(port);
      else LanWindow.join(hostField.getText().trim(), port);
    } catch (Exception error) {
      showError("Unable to start LAN mode: " + error.getMessage());
    }
  }

  private void showNewGame() {
    JPanel container = Theme.panel(new BorderLayout());
    container.add(
        heading(
            "MISSION CONFIGURATION",
            "Choose your opponent and the rules of engagement",
            this::showMenu),
        BorderLayout.NORTH);
    JPanel inner = card();
    inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
    inner.setPreferredSize(new Dimension(430, 390));
    inner.add(Theme.label("AI DIFFICULTY", 14, Theme.ACCENT));
    gap(inner, 10);
    JComboBox<Difficulty> ai = new JComboBox<>(Difficulty.values());
    ai.setSelectedItem(Difficulty.NORMAL);
    ai.setMaximumSize(new Dimension(410, 42));
    ai.setFont(Theme.FONT);
    inner.add(ai);
    gap(inner, 24);
    JCheckBox noTouch = new JCheckBox("Ships must not touch (including corners)");
    noTouch.setForeground(Theme.TEXT);
    noTouch.setBackground(Theme.PANEL);
    noTouch.setAlignmentX(0f);
    inner.add(noTouch);
    gap(inner, 12);
    JLabel hint = Theme.label("Classic: 10×10 grid · 5 ships · 1 shot per turn", 13, Theme.MUTED);
    inner.add(hint);
    gap(inner, 28);
    JButton start = Theme.button("DEPLOY YOUR FLEET  →", true);
    start.setAlignmentX(0f);
    start.addActionListener(
        e -> {
          session =
              new GameSession(
                  GameMode.SOLO,
                  (Difficulty) ai.getSelectedItem(),
                  noTouch.isSelected(),
                  new SecureRandom().nextLong());
          session.reducedMotion = reducedMotion;
          selected = ShipType.CARRIER;
          orientation = Orientation.HORIZONTAL;
          showPlacement();
        });
    inner.add(start);
    JPanel center = Theme.panel(new GridBagLayout());
    centered(center, inner);
    container.add(center, BorderLayout.CENTER);
    screen(container);
  }

  private void newLocal(boolean noTouch) {
    session =
        new GameSession(GameMode.LOCAL, Difficulty.NORMAL, noTouch, new SecureRandom().nextLong());
    session.reducedMotion = reducedMotion;
    selected = ShipType.CARRIER;
    orientation = Orientation.HORIZONTAL;
    showPlacement();
  }

  private void showPlacement() {
    if (session == null) return;
    int player = session.setupPlayer;
    Board board = session.match.board(player);
    JPanel page = Theme.panel(new BorderLayout(8, 5));
    page.add(
        heading(
            "FLEET DEPLOYMENT  /  "
                + (session.mode == GameMode.SOLO ? "COMMANDER" : ("PLAYER " + (player + 1))),
            "Select a ship · Click or drag to place · Right-click / R to rotate · Click an existing ship to reposition",
            () -> showMenuConfirm()),
        BorderLayout.NORTH);
    placementCanvas = new BoardCanvas();
    placementCanvas.reduceMotion(reducedMotion);
    placementCanvas.setPlacement(board);
    placementCanvas.select(selected);
    placementCanvas.orientation(orientation);
    placementCanvas.onClick(this::placementClicked);
    placementCanvas.onRotate(
        () -> {
          orientation =
              orientation == Orientation.HORIZONTAL ? Orientation.VERTICAL : Orientation.HORIZONTAL;
          showPlacement();
        });
    JPanel controls = card();
    controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
    controls.setPreferredSize(new Dimension(295, 480));
    controls.add(Theme.label("YOUR FLEET", 19, Theme.TEXT));
    gap(controls, 18);
    for (ShipType type : ShipType.values()) {
      boolean placed = board.ships().containsKey(type);
      JButton item =
          Theme.button(
              (placed ? "✓  " : "○  ") + type.label() + "    " + type.length(), selected == type);
      item.setAlignmentX(0f);
      item.setMaximumSize(new Dimension(260, 44));
      item.addActionListener(
          e -> {
            selected = type;
            placementCanvas.select(type);
            showPlacement();
          });
      controls.add(item);
      gap(controls, 5);
    }
    gap(controls, 12);
    JLabel direction = Theme.label("Orientation: " + orientation.name(), 12, Theme.MUTED);
    controls.add(direction);
    gap(controls, 8);
    JButton rotate = Theme.button("ROTATE   [ R ]", false);
    rotate.setAlignmentX(0f);
    rotate.addActionListener(
        e -> {
          orientation =
              orientation == Orientation.HORIZONTAL ? Orientation.VERTICAL : Orientation.HORIZONTAL;
          showPlacement();
        });
    controls.add(rotate);
    gap(controls, 8);
    JPanel buttons = Theme.panel(new GridLayout(1, 2, 8, 0));
    buttons.setMaximumSize(new Dimension(260, 43));
    JButton auto = Theme.button("AUTO", false);
    auto.addActionListener(
        e -> {
          session.match.randomize(player, session.random);
          showPlacement();
        });
    JButton reset = Theme.button("RESET", false);
    reset.addActionListener(
        e -> {
          session.match.clearPlacement(player);
          selected = ShipType.CARRIER;
          showPlacement();
        });
    buttons.add(auto);
    buttons.add(reset);
    controls.add(buttons);
    gap(controls, 18);
    JLabel count =
        Theme.label(
            board.ships().size() + " / 5 SHIPS READY",
            14,
            board.ready() ? Theme.ACCENT : Theme.GOLD);
    controls.add(count);
    gap(controls, 10);
    placementStatus = Theme.label("Choose a location on the grid", 12, Theme.MUTED);
    controls.add(placementStatus);
    gap(controls, 11);
    JButton ready = Theme.button("CONFIRM DEPLOYMENT  →", true);
    ready.setEnabled(board.ready());
    ready.setAlignmentX(0f);
    ready.addActionListener(e -> fleetReady());
    controls.add(ready);
    for (Component c : controls.getComponents()) {
      if (c instanceof JButton b) {
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setPreferredSize(new Dimension(240, 42));
        b.setMaximumSize(new Dimension(240, 42));
      } else if (c instanceof JLabel l) {
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setPreferredSize(new Dimension(240, 25));
        l.setMaximumSize(new Dimension(240, 25));
      } else if (c == buttons) {
        ((JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setPreferredSize(new Dimension(240, 42));
        ((JComponent) c).setMaximumSize(new Dimension(240, 42));
      }
    }
    JPanel field = Theme.panel(new BorderLayout());
    Theme.pad(field, 4, 25, 25, 10);
    field.add(placementCanvas, BorderLayout.CENTER);
    JPanel side = Theme.panel(new BorderLayout());
    Theme.pad(side, 4, 8, 25, 26);
    side.add(controls, BorderLayout.CENTER);
    field.add(side, BorderLayout.EAST);
    page.add(field, BorderLayout.CENTER);
    bindKey(
        "R",
        "rotate",
        () -> {
          if (session != null
              && session.match.phase() == MatchPhase.SETUP
              && placementCanvas != null) {
            orientation =
                orientation == Orientation.HORIZONTAL
                    ? Orientation.VERTICAL
                    : Orientation.HORIZONTAL;
            showPlacement();
          }
        });
    screen(page);
  }

  private void placementClicked(Coordinate p) {
    if (session == null || session.match.phase() != MatchPhase.SETUP) return;
    Board board = session.match.board(session.setupPlayer);
    Optional<Ship> existing = board.shipAt(p);
    if (existing.isPresent()) {
      selected = existing.get().type();
      session.match.remove(session.setupPlayer, selected);
      showPlacement();
      return;
    }
    if (session.match.place(session.setupPlayer, new Ship(selected, p, orientation))) {
      selected =
          Arrays.stream(ShipType.values())
              .filter(t -> !session.match.board(session.setupPlayer).ships().containsKey(t))
              .findFirst()
              .orElse(selected);
      showPlacement();
    } else if (placementStatus != null) {
      placementStatus.setForeground(Theme.ALERT);
      placementStatus.setText("Invalid position or overlapping ship");
    }
  }

  private void fleetReady() {
    if (session == null || !session.match.board(session.setupPlayer).ready()) return;
    if (session.mode == GameMode.LOCAL && session.setupPlayer == 0) {
      session.setupPlayer = 1;
      selected = ShipType.CARRIER;
      orientation = Orientation.HORIZONTAL;
      privacy(
          "PLAYER 2",
          "Player 1's fleet is ready. Pass the computer to Player 2.",
          "SET UP PLAYER 2",
          this::showPlacement);
      return;
    }
    session.match.start((int) (session.seed & 1L));
    if (session.mode == GameMode.LOCAL) showTurnPrivacy();
    else showBattle();
  }

  private void showTurnPrivacy() {
    if (session == null || session.match.phase() != MatchPhase.ACTIVE) return;
    int player = session.match.turn();
    privacy(
        "PLAYER " + (player + 1) + " TURN",
        "Keep the opponent's fleet confidential. Hand the device to the next commander.",
        "I'M READY  →",
        () -> {
          visiblePlayer = player;
          showBattle();
        });
  }

  private void privacy(String title, String description, String action, Runnable next) {
    JPanel page = Theme.panel(new GridBagLayout());
    JPanel card = card();
    card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
    card.setPreferredSize(new Dimension(520, 290));
    JLabel lock = Theme.label("◈  PRIVATE HANDOVER", 14, Theme.ACCENT);
    lock.setAlignmentX(.5f);
    card.add(lock);
    gap(card, 25);
    JLabel text = Theme.label(title, 32, Theme.TEXT);
    text.setAlignmentX(.5f);
    card.add(text);
    gap(card, 15);
    JLabel detail =
        Theme.label("No opponent information is displayed on this screen.", 13, Theme.MUTED);
    detail.setAlignmentX(.5f);
    card.add(detail);
    gap(card, 8);
    JLabel instructions = Theme.label(description, 12, Theme.MUTED);
    instructions.setAlignmentX(.5f);
    card.add(instructions);
    gap(card, 16);
    JButton ready = Theme.button(action, true);
    ready.setAlignmentX(.5f);
    ready.addActionListener(e -> next.run());
    card.add(ready);
    centered(page, card);
    screen(page);
  }

  private void showBattle() {
    if (session == null) return;
    Match m = session.match;
    if (m.phase() == MatchPhase.FINISHED) {
      showResults();
      return;
    }
    int viewer = session.mode == GameMode.SOLO ? 0 : visiblePlayer;
    JPanel page = Theme.panel(new BorderLayout(0, 0));
    JPanel header =
        heading(
            "NAVAL ENGAGEMENT",
            session.mode == GameMode.SOLO
                ? "SINGLE PLAYER  /  " + session.difficulty
                : "LOCAL MULTIPLAYER  /  PLAYER " + (viewer + 1),
            () -> showMenuConfirm());
    JPanel actions = Theme.panel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
    JButton save = Theme.button("SAVE", false);
    save.addActionListener(e -> saveGame());
    actions.add(save);
    JButton exit = Theme.button("MENU", false);
    exit.addActionListener(e -> showMenuConfirm());
    actions.add(exit);
    header.add(actions, BorderLayout.EAST);
    page.add(header, BorderLayout.NORTH);
    JPanel center = Theme.panel(new GridLayout(1, 2, 15, 0));
    Theme.pad(center, 8, 23, 8, 23);
    JPanel target = boardArea("ENEMY WATERS  /  TARGETING GRID", Theme.ACCENT);
    enemyCanvas = new BoardCanvas();
    enemyCanvas.reduceMotion(reducedMotion);
    enemyCanvas.setEnemy(m.knowledge(viewer));
    enemyCanvas.onClick(this::playerFire);
    target.add(enemyCanvas, BorderLayout.CENTER);
    JPanel home = boardArea("FRIENDLY FLEET  /  DEFENSE", Theme.MUTED);
    BoardCanvas own = new BoardCanvas();
    own.setOwn(m.board(viewer));
    own.reduceMotion(reducedMotion);
    home.add(own, BorderLayout.CENTER);
    center.add(target);
    center.add(home);
    page.add(center, BorderLayout.CENTER);
    JPanel footer = Theme.panel(new BorderLayout(12, 7));
    Theme.pad(footer, 7, 26, 17, 26);
    JPanel stats = Theme.panel(new GridLayout(1, 3, 10, 0));
    int shots = m.board(1 - viewer).shots().size(), hits = m.board(1 - viewer).hits();
    stats.add(stat("YOUR ACCURACY", shots == 0 ? "—" : (100 * hits / shots) + "%"));
    stats.add(stat("ENEMY SHIPS", Integer.toString(m.board(1 - viewer).afloat()) + " / 5"));
    stats.add(stat("YOUR SHIPS", Integer.toString(m.board(viewer).afloat()) + " / 5"));
    footer.add(stats, BorderLayout.CENTER);
    String message;
    if (m.history().isEmpty())
      message = "Choose a coordinate on the enemy grid to launch a strike.";
    else {
      Match.Turn last = m.history().get(m.history().size() - 1);
      message =
          "LAST SHOT  /  PLAYER "
              + (last.player() + 1)
              + "  /  "
              + last.target()
              + "  /  "
              + last.outcome();
    }
    boolean yourTurn = m.turn() == viewer;
    feedback =
        Theme.label(
            (yourTurn ? "● YOUR TURN    " : "◌ ENEMY TURN    ") + message,
            13,
            yourTurn ? Theme.ACCENT : Theme.GOLD);
    Theme.pad(footer, 10, 25, 20, 25);
    footer.add(feedback, BorderLayout.SOUTH);
    page.add(footer, BorderLayout.SOUTH);
    screen(page);
    if (session.mode == GameMode.SOLO && m.turn() == 1) requestAiTurn();
  }

  private JPanel stat(String name, String value) {
    JPanel box = new JPanel(new GridLayout(2, 1));
    box.setBackground(Theme.PANEL);
    box.setBorder(new EmptyBorder(7, 12, 7, 12));
    box.add(Theme.label(name, 12, Theme.MUTED));
    box.add(Theme.label(value, 21, Theme.TEXT));
    return box;
  }

  private JPanel boardArea(String title, Color accent) {
    JPanel p = Theme.panel(new BorderLayout(0, 9));
    JLabel label = Theme.label(title, 15, accent);
    Theme.pad(label, 0, 10, 2, 0);
    p.add(label, BorderLayout.NORTH);
    return p;
  }

  private void playerFire(Coordinate p) {
    if (session == null || session.match.phase() != MatchPhase.ACTIVE) return;
    int player = session.mode == GameMode.SOLO ? 0 : visiblePlayer;
    if (session.match.turn() != player || aiPending) return;
    ShotResult result = session.match.fire(player, p);
    if (!result.valid()) return;
    if (session.match.phase() == MatchPhase.FINISHED) {
      showResults();
      return;
    }
    if (session.mode == GameMode.LOCAL) {
      // Show strike result briefly only to the acting player, then hide all boards.
      showBattle();
      long token = generation;
      GameSession previous = session;
      javax.swing.Timer t =
          new javax.swing.Timer(
              reducedMotion ? 350 : 950,
              e -> {
                if (generation == token
                    && session == previous
                    && session.match.phase() == MatchPhase.ACTIVE) showTurnPrivacy();
              });
      t.setRepeats(false);
      t.start();
    } else {
      showBattle();
      if (enemyCanvas != null) enemyCanvas.flash(p);
    }
  }

  private void requestAiTurn() {
    if (session == null
        || session.match.phase() != MatchPhase.ACTIVE
        || session.match.turn() != 1
        || aiPending) return;
    aiPending = true;
    GameSession s = session;
    long token = generation;
    OpponentKnowledge knowledge = s.match.knowledge(1);
    int shotIndex = s.match.history().size();
    BattleshipAI strategy = s.difficulty.create();
    worker.submit(
        () -> {
          Coordinate target;
          try {
            target =
                strategy.choose(knowledge, new Random(s.seed + shotIndex * 0x9E3779B97F4A7C15L));
            if (!reducedMotion) Thread.sleep(400);
          } catch (Exception e) {
            SwingUtilities.invokeLater(
                () -> {
                  aiPending = false;
                  if (generation == token) showError("AI failed: " + e.getMessage());
                });
            return;
          }
          SwingUtilities.invokeLater(
              () -> {
                if (token != generation
                    || session != s
                    || s.match.phase() != MatchPhase.ACTIVE
                    || s.match.turn() != 1) return;
                s.match.fire(1, target);
                aiPending = false;
                if (s.match.phase() == MatchPhase.FINISHED) showResults();
                else showBattle();
              });
        });
  }

  private void showResults() {
    if (session == null) return;
    Match m = session.match;
    int victor = m.winner();
    JPanel page = Theme.panel(new GridBagLayout());
    JPanel result = card();
    result.setLayout(new BoxLayout(result, BoxLayout.Y_AXIS));
    result.setPreferredSize(new Dimension(530, 425));
    JLabel eyebrow = Theme.label("ENGAGEMENT COMPLETE", 15, Theme.ACCENT);
    eyebrow.setAlignmentX(.5f);
    result.add(eyebrow);
    gap(result, 24);
    boolean win = victor == 0;
    String winner =
        session.mode == GameMode.SOLO
            ? (win ? "VICTORY" : "DEFEAT")
            : ("PLAYER " + (victor + 1) + " WINS");
    JLabel title = Theme.label(winner, 42, win ? Theme.ACCENT : Theme.GOLD);
    title.setAlignmentX(.5f);
    result.add(title);
    gap(result, 20);
    int n0 = (int) m.history().stream().filter(t -> t.player() == 0).count();
    int n1 = (int) m.history().stream().filter(t -> t.player() == 1).count();
    JLabel score =
        Theme.label(
            "Player 1: " + n0 + " shots     /     Player 2: " + n1 + " shots", 15, Theme.TEXT);
    score.setAlignmentX(.5f);
    result.add(score);
    gap(result, 10);
    JLabel accuracy =
        Theme.label(
            "Player 1 accuracy: " + (n0 == 0 ? 0 : 100 * m.board(1).hits() / n0) + "%",
            14,
            Theme.MUTED);
    accuracy.setAlignmentX(.5f);
    result.add(accuracy);
    gap(result, 35);
    JButton again = Theme.button("PLAY AGAIN", true);
    again.setAlignmentX(.5f);
    again.addActionListener(
        e -> {
          GameMode mode = session.mode;
          Difficulty diff = session.difficulty;
          boolean touch = session.match.rules().noTouching();
          session = new GameSession(mode, diff, touch, new SecureRandom().nextLong());
          session.reducedMotion = reducedMotion;
          selected = ShipType.CARRIER;
          orientation = Orientation.HORIZONTAL;
          showPlacement();
        });
    result.add(again);
    gap(result, 10);
    JButton menu = Theme.button("MAIN MENU", false);
    menu.setAlignmentX(.5f);
    menu.addActionListener(e -> showMenu());
    result.add(menu);
    centered(page, result);
    screen(page);
  }

  private void saveGame() {
    if (session == null) return;
    String snapshot = SaveManager.serialize(session); // snapshot captured only on the EDT
    worker.submit(
        () -> {
          try {
            SaveManager.writeJson(snapshot);
            SwingUtilities.invokeLater(
                () ->
                    JOptionPane.showMessageDialog(
                        this,
                        "Match saved successfully.\n" + SaveManager.savePath(),
                        "Saved",
                        JOptionPane.INFORMATION_MESSAGE));
          } catch (IOException e) {
            SwingUtilities.invokeLater(() -> showError("Unable to save: " + e.getMessage()));
          }
        });
  }

  private void loadGame() {
    try {
      session = SaveManager.load();
      generation++;
      aiPending = false;
      reducedMotion = session.reducedMotion;
      if (session.match.phase() == MatchPhase.SETUP) showPlacement();
      else if (session.match.phase() == MatchPhase.FINISHED) showResults();
      else if (session.mode == GameMode.LOCAL) showTurnPrivacy();
      else showBattle();
    } catch (IOException e) {
      showError(e.getMessage() + "\nIf needed, restore savegame.json.bak.");
    }
  }

  private void showSettings() {
    JPanel page = Theme.panel(new BorderLayout());
    page.add(
        heading("SETTINGS", "Local preferences — no internet connection required", this::showMenu),
        BorderLayout.NORTH);
    JPanel options = card();
    options.setLayout(new BoxLayout(options, BoxLayout.Y_AXIS));
    options.setPreferredSize(new Dimension(470, 240));
    JCheckBox reduced = new JCheckBox("Reduced motion (minimal visual effects)", reducedMotion);
    reduced.setForeground(Theme.TEXT);
    reduced.setBackground(Theme.PANEL);
    options.add(reduced);
    gap(options, 18);
    JLabel explain =
        Theme.label("The game never plays audio or requires an account.", 13, Theme.MUTED);
    options.add(explain);
    gap(options, 27);
    JButton apply = Theme.button("SAVE PREFERENCES", true);
    apply.addActionListener(
        e -> {
          reducedMotion = reduced.isSelected();
          try {
            SaveManager.saveSettingReducedMotion(reducedMotion);
            showMenu();
          } catch (IOException ex) {
            showError(ex.getMessage());
          }
        });
    options.add(apply);
    JPanel center = Theme.panel(new GridBagLayout());
    centered(center, options);
    page.add(center, BorderLayout.CENTER);
    screen(page);
  }

  private void showHelp() {
    JPanel page = Theme.panel(new BorderLayout());
    page.add(heading("FIELD MANUAL", "Rules of engagement", this::showMenu), BorderLayout.NORTH);
    JTextArea text =
        new JTextArea(
            """
            OBJECTIVE
            Sink all five enemy ships before your own fleet is destroyed.

            THE FLEET
            Carrier (5), Battleship (4), Cruiser (3), Submarine (3), Destroyer (2).
            Position every ship horizontally or vertically on a 10×10 grid.

            DEPLOYMENT
            Choose a ship, then click or drag it onto the ocean grid. Press R or
            right-click to rotate. Click an existing ship to pick it up again.
            Randomize quickly positions your entire fleet.

            COMBAT
            Target an unknown cell on the enemy board. A hollow ring means MISS.
            A red cross means HIT. Every successful shot reveals a ship segment.
            SUNK means all cells of a ship have been hit. One shot per turn.
            All five sunk = victory. You cannot shoot the same cell twice.

            DIFFICULTY
            Easy fires randomly. Normal tracks damaged ships. Hard uses a
            probability map built only from hits, misses and sunken ships.

            LOCAL MULTIPLAYER
            Use the privacy handover screen when passing the computer to a friend.
            No account, server, sound or internet is required.
            """);
    text.setEditable(false);
    text.setFont(new Font("Monospaced", Font.PLAIN, 15));
    text.setForeground(Theme.TEXT);
    text.setBackground(Theme.PANEL);
    text.setBorder(new EmptyBorder(25, 40, 20, 20));
    JScrollPane pane = new JScrollPane(text);
    pane.setBorder(null);
    page.add(pane, BorderLayout.CENTER);
    screen(page);
  }

  private void showMenuConfirm() {
    if (session == null) {
      showMenu();
      return;
    }
    int answer =
        JOptionPane.showConfirmDialog(
            this,
            "Return to the menu? Unsaved progress will be lost.\nUse SAVE from the battle screen to keep your progress.",
            "Leave game?",
            JOptionPane.YES_NO_OPTION);
    if (answer == JOptionPane.YES_OPTION) showMenu();
  }

  private void confirmExit() {
    if (session != null
        && JOptionPane.showConfirmDialog(
                this,
                "Exit BattleShip? Unsaved progress will be lost.",
                "Exit",
                JOptionPane.YES_NO_OPTION)
            != JOptionPane.YES_OPTION) return;
    generation++;
    worker.shutdownNow();
    dispose();
  }

  private void showError(String message) {
    JOptionPane.showMessageDialog(this, message, "BattleShip", JOptionPane.ERROR_MESSAGE);
  }
}
