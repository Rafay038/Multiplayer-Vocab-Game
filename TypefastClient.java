import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

public class TypefastClient {
    private static final String SERVER_ADDRESS = "localhost";  // Change this to the server's public IP if needed
    private static final int SERVER_PORT = 12345;
    private static final String LOGIN_CMD = "LOGIN";
    private static final String REGISTER_CMD = "REGISTER";
    private static final String JOIN_CMD = "JOIN";
    private static final String LOGOUT_CMD = "LOGOUT";
    private static final String SCOREBOARD_CMD = "SCOREBOARD";
    private static final String EXIT_CMD = "EXIT";
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private JFrame frame;
    private JTextArea messageArea;
    private JButton loginButton;
    private JButton registerButton;
    private JButton joinButton;
    private JButton logoutButton;
    private JButton scoreboardButton;
    private JTextField userInputField;
    private JButton exitButton; // New button for exiting


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new TypefastClient().createAndShowGUI();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public TypefastClient() throws IOException {
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Unable to connect to the server.", "Connection Error", JOptionPane.ERROR_MESSAGE);
            throw e;
        }
    }

    private void createAndShowGUI() {
        frame = new JFrame("Typefast Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 300);

        messageArea = new JTextArea();
        messageArea.setEditable(false);
        frame.add(new JScrollPane(messageArea), BorderLayout.CENTER);

        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new BorderLayout());

        userInputField = new JTextField();
        userInputField.setEnabled(false);
        userInputField.addActionListener(e -> {
            String userInput = userInputField.getText().trim();
            if (!userInput.isEmpty()) {
                out.println(userInput);
                userInputField.setText(""); // Clear the input field after sending the message
            }
        });

        inputPanel.add(userInputField, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout());

        loginButton = new JButton("Login");
        registerButton = new JButton("Register");
        joinButton = new JButton("Join");
        logoutButton = new JButton("Logout");
        scoreboardButton = new JButton("Scoreboard");
        exitButton = new JButton("Exit");

        // Initially disable Join, Logout, and Scoreboard buttons
        joinButton.setVisible(false);
        logoutButton.setVisible(false);
        scoreboardButton.setVisible(false);
        exitButton.setVisible(false);
        loginButton.addActionListener(e -> showLoginForm());
        registerButton.addActionListener(e -> showRegisterForm());
        joinButton.addActionListener(e -> out.println(JOIN_CMD));
        logoutButton.addActionListener(e -> out.println(LOGOUT_CMD));
        scoreboardButton.addActionListener(e -> out.println(SCOREBOARD_CMD));
        exitButton.addActionListener(e -> out.println(EXIT_CMD));

        buttonPanel.add(loginButton);
        buttonPanel.add(registerButton);
        buttonPanel.add(joinButton);
        buttonPanel.add(logoutButton);
        buttonPanel.add(scoreboardButton);
        buttonPanel.add(exitButton);

        inputPanel.add(buttonPanel, BorderLayout.SOUTH);

        frame.add(inputPanel, BorderLayout.SOUTH);
        frame.setVisible(true);

        Thread readerThread = new Thread(() -> {
            try {
                String message;
                while ((message = in.readLine()) != null) {
                    String finalMessage = message;
                    SwingUtilities.invokeLater(() -> {
                        messageArea.append(finalMessage + "\n");
                        handleServerMessage(finalMessage);
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        readerThread.start();
    }

    private void handleServerMessage(String message) {
        if (message.startsWith("Login successful")) {
            loginButton.setVisible(false);
            registerButton.setVisible(false);
            joinButton.setVisible(true);
            logoutButton.setVisible(true);
            scoreboardButton.setVisible(true);
            userInputField.setEnabled(true);
        } else if (message.startsWith("Game over")) {
            joinButton.setVisible(false);
            scoreboardButton.setVisible(true);
            logoutButton.setVisible(false);
        } else if (message.startsWith("Logout successful")) {
            loginButton.setVisible(true);
            registerButton.setVisible(true);
            joinButton.setVisible(false);
            logoutButton.setVisible(false);
            scoreboardButton.setVisible(false);

        }else if (message.startsWith("Added to waiting list. Waiting for other players...")) {
            loginButton.setVisible(false);
            registerButton.setVisible(false);
            joinButton.setVisible(false);
            logoutButton.setVisible(false);
            scoreboardButton.setVisible(true);
            exitButton.setVisible(true);
        }else if (message.startsWith("Exiting game. Welcome to dashboard.")) {
            loginButton.setVisible(false);
            registerButton.setVisible(false);
            joinButton.setVisible(true);
            logoutButton.setVisible(true);
            scoreboardButton.setVisible(true);
            exitButton.setVisible(false);
        }
        else if (message.startsWith("Score for this word:")) {
            // Extract the score from the message
            String scoreStr = message.substring("Score for this word:".length()).trim();
            double score = Double.parseDouble(scoreStr);
            // Display the score
            JOptionPane.showMessageDialog(frame, "Score for this word: " + score, "Score", JOptionPane.INFORMATION_MESSAGE);
        } else if (message.startsWith("Scoreboard:")) {
            showScoreboardPopup(message.substring("Scoreboard:".length()).trim());
        }
    }

    private void showScoreboardPopup(String formattedScoreboard) {
        System.out.println("Received scoreboard data: " + formattedScoreboard); // Debug

        // Split the scoreboard response into lines (each line represents a user's score)
        String[] lines = formattedScoreboard.split("\n");

        // Prepare a StringBuilder to build the popup message
        StringBuilder popupMessage = new StringBuilder();
        popupMessage.append("Scoreboard: ");

        // Iterate over each line to append user's name and score to the popup message
        for (String line : lines) {
            if (line != null && !line.trim().isEmpty()) { // Ignore empty lines
                String[] parts = line.split(":");
                if (parts.length == 2) { // Assuming the format is "Username: Score"
                    String username = parts[0].trim();
                    String score = parts[1].trim();
                    if (username != null && score != null) { // Ignore null values
                        popupMessage.append(username).append(": ").append(score).append("\n");
                    }
                }
            }
        }

        System.out.println("Popup message: " + popupMessage.toString()); // Debug

        // Show the popup message
        JOptionPane.showMessageDialog(frame, popupMessage.toString(), "Scoreboard", JOptionPane.INFORMATION_MESSAGE);
    }






    private void showLoginForm() {
        JDialog dialog = new JDialog(frame, "Login", true);
        dialog.setLayout(new GridLayout(3, 2));

        dialog.add(new JLabel("Username:"));
        JTextField usernameField = new JTextField();
        dialog.add(usernameField);

        dialog.add(new JLabel("Password:"));
        JPasswordField passwordField = new JPasswordField();
        dialog.add(passwordField);

        JButton submitButton = new JButton("Submit");
        submitButton.addActionListener(e -> {
            String username = usernameField.getText();
            String password = new String(passwordField.getPassword());
            if (!username.isEmpty() && !password.isEmpty()) {
                out.println(LOGIN_CMD + " " + username + " " + password);
                dialog.dispose();
            } else {
                JOptionPane.showMessageDialog(frame, "Username and Password cannot be empty", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        dialog.add(submitButton);

        dialog.setSize(300, 150);
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private void showRegisterForm() {
        JDialog dialog = new JDialog(frame, "Register", true);
        dialog.setLayout(new GridLayout(3, 2));

        dialog.add(new JLabel("Username:"));
        JTextField usernameField = new JTextField();
        dialog.add(usernameField);

        dialog.add(new JLabel("Password:"));
        JPasswordField passwordField = new JPasswordField();
        dialog.add(passwordField);

        JButton submitButton = new JButton("Submit");
        submitButton.addActionListener(e -> {
            String username = usernameField.getText();
            String password = new String(passwordField.getPassword());
            if (!username.isEmpty() && !password.isEmpty()) {
                out.println(REGISTER_CMD + " " + username + " " + password);
                dialog.dispose();
            } else {
                JOptionPane.showMessageDialog(frame, "Username and Password cannot be empty", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        dialog.add(submitButton);

        dialog.setSize(300, 150);
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }
}
