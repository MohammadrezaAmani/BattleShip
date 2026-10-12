package com.battleship.lan;

import com.battleship.domain.*;
import com.battleship.ui.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.net.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Independent Swing view for direct-IP play. Host remains authoritative. */
public final class LanWindow extends JFrame implements LanSession.Listener {
  private LanSession link;
  private final boolean host;
  private final Board draft = new Board(Ruleset.classic());
  private LanProtocol.MatchView current;
  private ShipType selected = ShipType.CARRIER;
  private Orientation orientation = Orientation.HORIZONTAL;
  private boolean ready = false;
  private JLabel connectionLabel;
  private String connectionText = "Preparing connection…";
  private final JPanel root = new JPanel(new BorderLayout());

  private LanWindow(boolean host) {
    super("BATTLESHIP / DIRECT IP  /  " + (host ? "HOST" : "GUEST"));
    this.host = host;
    setSize(1200, 765);
    setMinimumSize(new Dimension(950, 660));
    setLocationByPlatform(true);
    setContentPane(root);
    setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            close();
          }
        });
    getRootPane()
        .getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        .put(KeyStroke.getKeyStroke('R'), "rotate");
    getRootPane()
        .getActionMap()
        .put(
            "rotate",
            new AbstractAction() {
              @Override
              public void actionPerformed(ActionEvent e) {
                if (!ready) {
                  orientation =
                      orientation == Orientation.HORIZONTAL
                          ? Orientation.VERTICAL
                          : Orientation.HORIZONTAL;
                  render();
                }
              }
            });
    render();
  }

  public static LanWindow host(int port) throws IOException {
    LanWindow window = new LanWindow(true);
    window.link = LanSession.host(port, window);
    window.setVisible(true);
    return window;
  }

  public static LanWindow join(String host, int port) {
    LanWindow window = new LanWindow(false);
    window.link = LanSession.join(host, port, window);
    window.setVisible(true);
    return window;
  }

  private JPanel container() {
    JPanel panel = new JPanel(new BorderLayout(12, 10));
    panel.setBackground(Theme.BG);
    return panel;
  }

  private JLabel label(String text, int size, Color color) {
    return Theme.label(text, size, color);
  }

  private JButton button(String title, boolean primary, Runnable command) {
    JButton b = Theme.button(title, primary);
    b.addActionListener(e -> command.run());
    return b;
  }

  private void close() {
    if (link != null) link.close();
    dispose();
  }

  private JPanel header(String heading) {
    JPanel panel = container();
    Theme.pad(panel, 18, 24, 11, 24);
    JPanel titles = container();
    titles.add(label(heading, 23, Theme.TEXT), BorderLayout.NORTH);
    connectionLabel = label(connectionText, 13, Theme.MUTED);
    titles.add(connectionLabel, BorderLayout.SOUTH);
    panel.add(titles, BorderLayout.CENTER);
    panel.add(button("DISCONNECT", false, this::close), BorderLayout.EAST);
    return panel;
  }

  private JPanel sidePanel() {
    JPanel p = new JPanel();
    p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
    p.setBackground(Theme.PANEL);
    p.setBorder(new EmptyBorder(20, 19, 20, 19));
    p.setPreferredSize(new Dimension(290, 470));
    p.add(label("FLEET MANIFEST", 17, Theme.TEXT));
    p.add(Box.createVerticalStrut(15));
    for (ShipType type : ShipType.values()) {
      boolean isPlaced = draft.ships().containsKey(type);
      JButton b =
          button(
              (isPlaced ? "✓  " : "○  ") + type.label() + "  /  " + type.length(),
              selected == type,
              () -> {
                selected = type;
                render();
              });
      b.setMaximumSize(new Dimension(270, 40));
      b.setAlignmentX(0f);
      p.add(b);
      p.add(Box.createVerticalStrut(5));
    }
    p.add(Box.createVerticalStrut(14));
    JButton rotate =
        button(
            "ROTATE [R]",
            false,
            () -> {
              orientation =
                  orientation == Orientation.HORIZONTAL
                      ? Orientation.VERTICAL
                      : Orientation.HORIZONTAL;
              render();
            });
    rotate.setAlignmentX(0f);
    p.add(rotate);
    p.add(Box.createVerticalStrut(12));
    JButton random =
        button(
            "RANDOMIZE",
            false,
            () -> {
              draft.randomize(new Random());
              render();
            });
    random.setAlignmentX(0f);
    p.add(random);
    p.add(Box.createVerticalStrut(8));
    JButton reset =
        button(
            "CLEAR FLEET",
            false,
            () -> {
              draft.clearPlacement();
              selected = ShipType.CARRIER;
              render();
            });
    reset.setAlignmentX(0f);
    p.add(reset);
    p.add(Box.createVerticalStrut(20));
    p.add(
        label(
            draft.ships().size() + " OF 5 SHIPS DEPLOYED",
            13,
            draft.ready() ? Theme.ACCENT : Theme.GOLD));
    p.add(Box.createVerticalStrut(12));
    JButton confirm =
        button(
            "READY TO ENGAGE →",
            true,
            () -> {
              try {
                link.submitFleet(draft.ships().values());
                ready = true;
                render();
              } catch (Exception exception) {
                JOptionPane.showMessageDialog(
                    this, exception.getMessage(), "Invalid fleet", JOptionPane.ERROR_MESSAGE);
              }
            });
    confirm.setEnabled(draft.ready() && link != null);
    confirm.setAlignmentX(0f);
    p.add(confirm);
    return p;
  }

  private void render() {
    root.removeAll();
    if (current != null && current.phase() != MatchPhase.SETUP) {
      renderBattle();
    } else if (ready) renderWaiting();
    else renderPlacement();
    root.revalidate();
    root.repaint();
  }

  private void renderPlacement() {
    root.add(
        header("DEPLOYMENT  /  " + (host ? "HOST · PLAYER 1" : "GUEST · PLAYER 2")),
        BorderLayout.NORTH);
    JPanel content = container();
    Theme.pad(content, 10, 25, 25, 25);
    BoardCanvas canvas = new BoardCanvas();
    canvas.setPlacement(draft);
    canvas.select(selected);
    canvas.orientation(orientation);
    canvas.onRotate(
        () -> {
          orientation =
              orientation == Orientation.HORIZONTAL ? Orientation.VERTICAL : Orientation.HORIZONTAL;
          render();
        });
    canvas.onClick(
        p -> {
          Optional<Ship> existing = draft.shipAt(p);
          if (existing.isPresent()) {
            selected = existing.get().type();
            draft.remove(selected);
            render();
            return;
          }
          if (draft.place(new Ship(selected, p, orientation))) {
            selected =
                Arrays.stream(ShipType.values())
                    .filter(t -> !draft.ships().containsKey(t))
                    .findFirst()
                    .orElse(selected);
            render();
          }
        });
    content.add(canvas, BorderLayout.CENTER);
    content.add(sidePanel(), BorderLayout.EAST);
    root.add(content, BorderLayout.CENTER);
  }

  private void renderWaiting() {
    root.add(header(host ? "AWAITING REMOTE FLEET" : "FLEET TRANSMITTED"), BorderLayout.NORTH);
    JPanel content = container();
    content.setLayout(new GridBagLayout());
    JPanel card = container();
    card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
    card.setPreferredSize(new Dimension(510, 290));
    JLabel a = label("◉  FLEET READY", 24, Theme.ACCENT);
    a.setAlignmentX(.5f);
    card.add(a);
    card.add(Box.createVerticalStrut(23));
    JLabel b = label("The battle starts when both commanders are ready.", 14, Theme.TEXT);
    b.setAlignmentX(.5f);
    card.add(b);
    card.add(Box.createVerticalStrut(20));
    JLabel info = label(connectionText, 13, Theme.MUTED);
    info.setAlignmentX(.5f);
    card.add(info);
    card.add(Box.createVerticalStrut(26));
    if (!host) {
      JButton retry = button("RECONNECT", false, () -> link.connect());
      retry.setAlignmentX(.5f);
      card.add(retry);
    }
    content.add(card);
    root.add(content, BorderLayout.CENTER);
  }

  private void renderBattle() {
    int me = host ? 0 : 1;
    if (current.phase() == MatchPhase.FINISHED) {
      renderResult();
      return;
    }
    root.add(
        header("DIRECT IP  /  " + (host ? "HOST" : "GUEST") + "  /  PLAYER " + (me + 1)),
        BorderLayout.NORTH);
    JPanel body = container();
    body.setLayout(new GridLayout(1, 2, 12, 0));
    Theme.pad(body, 10, 23, 10, 23);
    JPanel target = container();
    target.add(label("ENEMY WATERS / FIRE HERE", 15, Theme.ACCENT), BorderLayout.NORTH);
    BoardCanvas enemy = new BoardCanvas();
    enemy.setEnemy(current.enemy());
    enemy.onClick(
        p -> {
          if (link != null && link.connected() && current != null && current.turn() == me)
            link.fire(p);
        });
    target.add(enemy, BorderLayout.CENTER);
    JPanel own = container();
    own.add(label("OWN FLEET / DEFEND", 15, Theme.MUTED), BorderLayout.NORTH);
    BoardCanvas defense = new BoardCanvas();
    defense.setOwn(current.own());
    own.add(defense, BorderLayout.CENTER);
    body.add(target);
    body.add(own);
    root.add(body, BorderLayout.CENTER);
    JPanel footer = container();
    Theme.pad(footer, 6, 26, 22, 26);
    String turnMessage =
        current.turn() == me
            ? "● YOUR TURN · Choose a target"
            : "◌ OPPONENT TURN · Awaiting action";
    JLabel state =
        label(
            turnMessage + "     |     " + current.last(),
            14,
            current.turn() == me ? Theme.ACCENT : Theme.GOLD);
    footer.add(state, BorderLayout.CENTER);
    if (!link.connected() && !host)
      footer.add(button("RECONNECT", false, () -> link.connect()), BorderLayout.EAST);
    root.add(footer, BorderLayout.SOUTH);
  }

  private void renderResult() {
    root.add(header("BATTLE FINISHED"), BorderLayout.NORTH);
    int me = host ? 0 : 1;
    JPanel area = container();
    area.setLayout(new GridBagLayout());
    JPanel card = container();
    card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
    card.setPreferredSize(new Dimension(530, 240));
    JLabel result =
        label(
            current.winner() == me ? "VICTORY" : "DEFEAT",
            44,
            current.winner() == me ? Theme.ACCENT : Theme.GOLD);
    result.setAlignmentX(.5f);
    card.add(result);
    card.add(Box.createVerticalStrut(17));
    JLabel info = label(current.last(), 14, Theme.TEXT);
    info.setAlignmentX(.5f);
    card.add(info);
    card.add(Box.createVerticalStrut(24));
    JButton close = button("MAIN MENU", true, this::close);
    close.setAlignmentX(.5f);
    card.add(close);
    area.add(card);
    root.add(area, BorderLayout.CENTER);
  }

  @Override
  public void status(String message) {
    connectionText = message;
    if (connectionLabel != null) connectionLabel.setText(message);
  }

  @Override
  public void view(LanProtocol.MatchView snapshot) {
    current = snapshot;
    render();
  }

  @Override
  public void disconnected(String reason) {
    connectionText = reason;
    if (connectionLabel != null) connectionLabel.setText(reason);
    if (ready) render();
  }
}
