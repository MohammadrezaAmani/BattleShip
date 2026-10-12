package com.battleship;

import com.battleship.ui.MainWindow;
import javax.swing.*;

public final class Main {
  public static void main(String[] args) {
    if (java.awt.GraphicsEnvironment.isHeadless()) {
      System.err.println(
          "GUI requires a desktop display. Use com.battleship.SelfTest for headless validation.");
      return;
    }
    System.setProperty("awt.useSystemAAFontSettings", "on");
    SwingUtilities.invokeLater(
        () -> {
          try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
          } catch (Exception ignored) {
          }
          new MainWindow().setVisible(true);
        });
  }
}
