package com.battleship.ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public final class Theme {
  private Theme() {}

  public static final Color BG = new Color(9, 20, 35),
      PANEL = new Color(16, 36, 54),
      CARD = new Color(23, 49, 69),
      OCEAN = new Color(18, 69, 93),
      TEXT = new Color(231, 243, 248),
      MUTED = new Color(148, 178, 193),
      ACCENT = new Color(36, 209, 185),
      ALERT = new Color(255, 126, 101),
      GOLD = new Color(255, 203, 116);
  public static final Font FONT = new Font("Dialog", Font.PLAIN, 14);

  public static JButton button(String text, boolean primary) {
    JButton b = new JButton(text);
    b.setFont(FONT.deriveFont(Font.BOLD, 14f));
    b.setFocusPainted(false);
    b.setForeground(primary ? BG : TEXT);
    b.setBackground(primary ? ACCENT : CARD);
    b.setOpaque(true);
    b.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(primary ? ACCENT : new Color(56, 84, 100)),
            new EmptyBorder(11, 17, 11, 17)));
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    return b;
  }

  public static JLabel label(String text, int size, Color color) {
    JLabel l = new JLabel(text);
    l.setForeground(color);
    l.setFont(FONT.deriveFont((float) size));
    return l;
  }

  public static JPanel panel(LayoutManager layout) {
    JPanel p = new JPanel(layout);
    p.setBackground(BG);
    return p;
  }

  public static void pad(JComponent c, int top, int left, int bottom, int right) {
    c.setBorder(new EmptyBorder(top, left, bottom, right));
  }
}
