package com.battleship.ui;

import com.battleship.domain.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;

/** Pixel-perfect Java2D board that renders only the visibility supplied to it. */
public final class BoardCanvas extends JPanel {
  public enum Display {
    PLACEMENT,
    OWN,
    ENEMY
  }

  private Display display = Display.PLACEMENT;
  private Board own;
  private OpponentKnowledge enemy;
  private ShipType selected = ShipType.CARRIER;
  private Orientation orientation = Orientation.HORIZONTAL;
  private Consumer<Coordinate> activated = p -> {};
  private Runnable rotationHandler = this::rotate;
  private Coordinate hover;
  private boolean reducedMotion;
  private Coordinate lastShot;
  private long shotTime;
  private final javax.swing.Timer fxTimer;
  private int gridX, gridY, cell;
  private boolean mouseDown;

  public BoardCanvas() {
    setOpaque(true);
    setBackground(Theme.PANEL);
    setPreferredSize(new Dimension(470, 480));
    fxTimer =
        new javax.swing.Timer(
            33,
            e -> {
              if (System.currentTimeMillis() - shotTime > 530) fxTimerStop();
              else repaint();
            });
    MouseAdapter listener =
        new MouseAdapter() {
          @Override
          public void mouseMoved(MouseEvent e) {
            hover = coordinateAt(e.getPoint());
            repaint();
          }

          @Override
          public void mouseExited(MouseEvent e) {
            hover = null;
            repaint();
          }

          @Override
          public void mouseDragged(MouseEvent e) {
            hover = coordinateAt(e.getPoint());
            repaint();
          }

          @Override
          public void mousePressed(MouseEvent e) {
            mouseDown = SwingUtilities.isLeftMouseButton(e);
            if (SwingUtilities.isRightMouseButton(e) && display == Display.PLACEMENT) {
              rotationHandler.run();
            }
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            if (mouseDown && SwingUtilities.isLeftMouseButton(e)) {
              Coordinate at = coordinateAt(e.getPoint());
              if (at != null) activated.accept(at);
            }
            mouseDown = false;
          }
        };
    addMouseListener(listener);
    addMouseMotionListener(listener);
  }

  private void fxTimerStop() {
    fxTimer.stop();
    repaint();
  }

  public void setPlacement(Board board) {
    this.display = Display.PLACEMENT;
    own = board;
    enemy = null;
    repaint();
  }

  public void setOwn(Board board) {
    this.display = Display.OWN;
    own = board;
    enemy = null;
    hover = null;
    repaint();
  }

  public void setEnemy(OpponentKnowledge info) {
    this.display = Display.ENEMY;
    this.enemy = info;
    own = null;
    repaint();
  }

  public void onClick(Consumer<Coordinate> callback) {
    activated = Objects.requireNonNull(callback);
  }

  public void onRotate(Runnable callback) {
    rotationHandler = Objects.requireNonNull(callback);
  }

  public void select(ShipType type) {
    selected = type;
    repaint();
  }

  public void orientation(Orientation dir) {
    orientation = dir;
    repaint();
  }

  public void rotate() {
    orientation =
        orientation == Orientation.HORIZONTAL ? Orientation.VERTICAL : Orientation.HORIZONTAL;
    repaint();
  }

  public Orientation orientation() {
    return orientation;
  }

  public void reduceMotion(boolean enabled) {
    reducedMotion = enabled;
    if (enabled) fxTimer.stop();
  }

  public void flash(Coordinate at) {
    lastShot = at;
    shotTime = System.currentTimeMillis();
    if (!reducedMotion) fxTimer.start();
    repaint();
  }

  public Coordinate coordinateAt(Point p) {
    if (cell < 1
        || p.x < gridX
        || p.y < gridY
        || p.x >= gridX + cell * 10
        || p.y >= gridY + cell * 10) return null;
    return new Coordinate((p.y - gridY) / cell, (p.x - gridX) / cell);
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    super.paintComponent(graphics);
    Graphics2D g = (Graphics2D) graphics.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    int w = getWidth(), h = getHeight();
    g.setPaint(new GradientPaint(0, 0, new Color(15, 50, 72), w, h, new Color(6, 27, 49)));
    g.fillRoundRect(0, 0, w, h, 18, 18);
    cell = Math.max(1, Math.min((w - 74) / 10, (h - 76) / 10));
    gridX = (w - cell * 10) / 2 + 9;
    gridY = (h - cell * 10) / 2 + 8;
    int sx = gridX, sy = gridY, sz = cell * 10;
    g.setPaint(
        new GradientPaint(sx, sy, new Color(20, 86, 113), sx + sz, sy + sz, new Color(11, 48, 74)));
    g.fillRoundRect(sx - 2, sy - 2, sz + 4, sz + 4, 10, 10);
    g.setColor(new Color(255, 255, 255, 15));
    for (int i = 0; i < 40; i++) {
      int yy = sy + (i * 37 + 11) % Math.max(1, sz);
      int xx = sx + (i * 61 + 29) % Math.max(1, sz);
      g.draw(
          new Arc2D.Double(
              xx, yy, Math.max(8, cell * .7), Math.max(3, cell * .2), 10, 155, Arc2D.OPEN));
    }
    g.setStroke(new BasicStroke(1f));
    g.setColor(new Color(100, 197, 210, 58));
    for (int i = 0; i <= 10; i++) {
      int x = sx + i * cell, y = sy + i * cell;
      g.drawLine(x, sy, x, sy + sz);
      g.drawLine(sx, y, sx + sz, y);
    }
    g.setColor(Theme.MUTED);
    g.setFont(Theme.FONT.deriveFont(Font.BOLD, Math.max(10f, Math.min(14f, cell * .31f))));
    FontMetrics fm = g.getFontMetrics();
    for (int i = 0; i < 10; i++) {
      String col = Integer.toString(i + 1), row = Character.toString((char) ('A' + i));
      int cx = sx + i * cell + cell / 2, cy = sy + i * cell + cell / 2;
      g.drawString(col, cx - fm.stringWidth(col) / 2, sy - 12);
      g.drawString(row, sx - 16 - fm.stringWidth(row) / 2, cy + fm.getAscent() / 3);
    }
    if (display != Display.ENEMY && own != null) {
      for (Ship ship : own.ships().values()) drawShip(g, ship, sx, sy, cell, own);
      for (Coordinate p : own.shots())
        drawMark(g, p, own.shipAt(p).isPresent(), false, sx, sy, cell);
    }
    if (display == Display.ENEMY && enemy != null) {
      for (List<Coordinate> sunk : enemy.sunk().values())
        for (Coordinate p : sunk) drawSunk(g, p, sx, sy, cell);
      for (Coordinate p : enemy.misses()) drawMark(g, p, false, false, sx, sy, cell);
      for (Coordinate p : enemy.hits()) drawMark(g, p, true, false, sx, sy, cell);
    }
    if (hover != null) {
      boolean placement = display == Display.PLACEMENT;
      boolean valid =
          placement && own != null && own.canPlace(new Ship(selected, hover, orientation));
      if (placement) {
        Ship s = new Ship(selected, hover, orientation);
        for (Coordinate p : s.cells())
          if (p.inside()) {
            g.setColor(valid ? new Color(36, 209, 185, 100) : new Color(255, 113, 101, 115));
            g.fillRoundRect(
                sx + p.col() * cell + 2, sy + p.row() * cell + 2, cell - 4, cell - 4, 6, 6);
          }
      } else if (display == Display.ENEMY && enemy != null && enemy.available(hover)) {
        g.setColor(new Color(36, 209, 185, 63));
        g.fillRoundRect(
            sx + hover.col() * cell + 2, sy + hover.row() * cell + 2, cell - 4, cell - 4, 6, 6);
        g.setColor(Theme.ACCENT);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(
            sx + hover.col() * cell + 2, sy + hover.row() * cell + 2, cell - 4, cell - 4, 6, 6);
      }
    }
    if (lastShot != null && !reducedMotion) {
      double t = Math.min(1, (System.currentTimeMillis() - shotTime) / 530.0);
      if (t < 1) {
        float alpha = (float) ((1 - t) * .8);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g.setColor(Theme.GOLD);
        g.setStroke(new BasicStroke(2.4f));
        double cx = sx + (lastShot.col() + .5) * cell,
            cy = sy + (lastShot.row() + .5) * cell,
            r = cell * (.15 + t * .85);
        g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        g.setComposite(AlphaComposite.SrcOver);
      }
    }
    g.dispose();
  }

  private void drawShip(Graphics2D g, Ship ship, int sx, int sy, int c, Board b) {
    int x = sx + ship.origin().col() * c + 3, y = sy + ship.origin().row() * c + 3;
    int w = (ship.orientation() == Orientation.HORIZONTAL ? ship.type().length() : 1) * c - 6;
    int h = (ship.orientation() == Orientation.VERTICAL ? ship.type().length() : 1) * c - 6;
    boolean sunk = b.sunk(ship);
    g.setColor(new Color(0, 0, 0, 60));
    g.fillRoundRect(x + 3, y + 4, w, h, Math.min(c, 20), Math.min(c, 20));
    g.setPaint(
        new GradientPaint(
            x,
            y,
            sunk ? new Color(126, 64, 68) : new Color(117, 165, 182),
            x + w,
            y + h,
            sunk ? new Color(77, 40, 50) : new Color(45, 99, 122)));
    g.fillRoundRect(x, y, w, h, Math.min(c, 20), Math.min(c, 20));
    g.setColor(sunk ? new Color(255, 137, 118) : new Color(185, 223, 231));
    g.setStroke(new BasicStroke(1.4f));
    g.drawRoundRect(x, y, w, h, Math.min(c, 20), Math.min(c, 20));
    for (int i = 1; i < ship.type().length(); i++) {
      int px = x + (ship.orientation() == Orientation.HORIZONTAL ? i * c : 0);
      int py = y + (ship.orientation() == Orientation.VERTICAL ? i * c : 0);
      g.setColor(new Color(190, 222, 229, 80));
      if (ship.orientation() == Orientation.HORIZONTAL) g.drawLine(px, y + 6, px, y + h - 6);
      else g.drawLine(x + 6, py, x + w - 6, py);
    }
    int mx = x + w / 2, my = y + h / 2;
    g.setColor(new Color(12, 43, 62, 130));
    g.fillOval(
        mx - Math.min(8, c / 5),
        my - Math.min(8, c / 5),
        Math.min(16, c * 2 / 5),
        Math.min(16, c * 2 / 5));
  }

  private void drawSunk(Graphics2D g, Coordinate p, int sx, int sy, int c) {
    int x = sx + p.col() * c, y = sy + p.row() * c;
    g.setColor(new Color(164, 63, 67, 150));
    g.fillRoundRect(x + 2, y + 2, c - 4, c - 4, 5, 5);
    drawMark(g, p, true, true, sx, sy, c);
  }

  private void drawMark(
      Graphics2D g, Coordinate p, boolean hit, boolean sunk, int sx, int sy, int c) {
    int cx = sx + p.col() * c + c / 2, cy = sy + p.row() * c + c / 2, r = Math.max(3, c / 8);
    g.setStroke(
        new BasicStroke(Math.max(2f, c * .055f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    if (hit) {
      g.setColor(sunk ? new Color(255, 190, 157) : Theme.ALERT);
      g.drawLine(cx - r, cy - r, cx + r, cy + r);
      g.drawLine(cx + r, cy - r, cx - r, cy + r);
      g.drawOval(cx - r - 3, cy - r - 3, r * 2 + 6, r * 2 + 6);
    } else {
      g.setColor(new Color(181, 228, 239));
      g.drawOval(cx - r, cy - r, r * 2, r * 2);
      g.fillOval(cx - 2, cy - 2, 4, 4);
    }
  }
}
