
// gui for the game, it will be the main gui for the game
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
// import java.util.List;
// import java.util.ArrayList;
// import java.util.Random;
// import java.util.logging.Level;

public class GUI {
    private JFrame frame;
    private JPanel panel;
    private JPanel panel2;

    // private JButton startButton;
    // private JButton resetButton;
    private JButton exitButton;
    private JButton[][] buttons;
    private JButton[][] buttons2;

    private GameBoard gameBoard;
    private GameBoard gameBoard2;
    private JSplitPane splitter;

    // private GameBoard gameBoard;

    public GUI() {
        gameBoard = new GameBoard();
        gameBoard.createGameBoard(10);
        gameBoard2 = new GameBoard();
        gameBoard2.createGameBoard(10);
        frame = new JFrame("Battle Ship");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 720);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);
        frame.setLayout(new BorderLayout());

        panel = new JPanel();
        panel.setLayout(new GridLayout(10, 10));
        panel2 = new JPanel();
        panel2.setLayout(new GridLayout(10, 10));
        // startButton = new JButton("Start");
        // resetButton = new JButton("Reset");
        exitButton = new JButton("Exit");

        buttons = new JButton[10][10];
        // change background color of panel
        panel.setBackground(Color.ORANGE);
        panel2.setBackground(Color.ORANGE);
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                buttons[i][j] = new JButton(" ");
                buttons[i][j].setBackground(Color.BLUE);
                buttons[i][j].addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        JButton button = (JButton) e.getSource();
                        String buttonText = button.getText();
                        if (buttonText.equals(" ")) {
                            button.setText("X");
                            button.setBackground(Color.BLACK);
                        } else if (buttonText.equals("X")) {
                            button.setText(" ");
                            button.setBackground(Color.BLUE);
                        }
                    }
                });
                panel.add(buttons[i][j]);
            }
        }
        // i want to split panels with a big line in the middle4
        splitter = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, panel, panel2);
        splitter.setDividerLocation(450);
        splitter.setDividerSize(0);
        splitter.setBackground(Color.orange);
        splitter.setEnabled(false);
        frame.add(splitter, BorderLayout.CENTER);
        buttons2 = new JButton[10][10];
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                buttons2[i][j] = new JButton(" ");
                // i want a medium blue color
                buttons2[i][j].setBackground(Color.BLUE);
                // add border radius
                // buttons2[i][j].setBorder(BorderFactory.createLineBorder(Color.BLACK));
                buttons2[i][j].addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        JButton button = (JButton) e.getSource();
                        String buttonText = button.getText();
                        if (buttonText.equals(" ")) {
                            button.setText("X");
                            button.setBackground(Color.BLACK);
                        } else if (buttonText.equals("X")) {
                            button.setText(" ");
                            button.setBackground(Color.BLUE);
                        }
                    }
                });
                panel2.add(buttons2[i][j]);
            }
        }
        exitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });
        exitButton.setBackground(Color.RED);
        frame.add(panel, BorderLayout.EAST);
        frame.add(panel2, BorderLayout.WEST);
        // frame.add(startButton, BorderLayout.NORTH);
        // frame.add(resetButton, BorderLayout.SOUTH);
        frame.add(exitButton, BorderLayout.SOUTH);

        frame.setVisible(true);
    }
}