import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class TypefastServer {
    private static final int PORT = 12345;
    private static final int INITIAL_TIME_LIMIT = 30;
    private static final int GROUP_SIZE = 1;
    private static final int WORDS_PER_GAME = 5; // Limit for the number of words per game
    private static final Map<String, String> userDatabase = new ConcurrentHashMap<>();
    private static final Map<String, ClientHandler> authenticatedUsers = new ConcurrentHashMap<>();
    private static final List<ClientHandler> waitingClients = Collections.synchronizedList(new ArrayList<>());
    private static List<String> words = new ArrayList<>();
    private static final ExecutorService pool = Executors.newFixedThreadPool(10);
    private static final String WORDS_FILE_PATH = "C:\\Users\\DANIYAL-PC\\IdeaProjects\\untitled\\src\\words.txt"; // You can change this to an absolute path

    public static void main(String[] args) {
        loadWordsFromFile(WORDS_FILE_PATH);
        System.out.println("Typefast Server started...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                pool.execute(clientHandler);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadWordsFromFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                System.err.println("File not found: " + filePath);
                return;
            }
            words = Files.readAllLines(path);
            System.out.println("Words loaded from file: " + words);
        } catch (IOException e) {
            System.err.println("Error loading words from file: " + filePath);
            e.printStackTrace(); // Print stack trace for more details
        }
    }

    public static synchronized void registerUser(String username, String password, ClientHandler clientHandler) {
        if (userDatabase.containsKey(username)) {
            clientHandler.sendMessage("Username already exists. Please try again.");
        } else {
            userDatabase.put(username, password);
            clientHandler.sendMessage("Registration successful. Please login.");
        }
    }

    public static synchronized void authenticateUser(String username, String password, ClientHandler clientHandler) {
        if (userDatabase.containsKey(username) && userDatabase.get(username).equals(password)) {
            authenticatedUsers.put(username, clientHandler);
            clientHandler.setUsername(username);
            clientHandler.sendMessage("Login successful. Welcome " + username + "!");
        } else {
            clientHandler.sendMessage("Invalid username or password. Please try again.");
        }
    }

    public static synchronized void logoutUser(String username) {
        if (username != null) {
            authenticatedUsers.remove(username);
        }
    }

    public static synchronized void addClientToWaitingList(ClientHandler clientHandler) {
        if (!waitingClients.contains(clientHandler)) {
            waitingClients.add(clientHandler);
            broadcastWaitingListSize();
            clientHandler.sendMessage("Added to waiting list. Waiting for other players...");
            if (waitingClients.size() >= GROUP_SIZE) {
                List<ClientHandler> group = new ArrayList<>();
                for (int i = 0; i < GROUP_SIZE; i++) {
                    group.add(waitingClients.remove(0));
                }
                startGame(group);
            }
        } else {
            clientHandler.sendMessage("You are already in the waiting list.");
        }
    }

    public static synchronized void broadcastWaitingListSize() {
        String message = "Players in waiting list: " + waitingClients.size();
        for (ClientHandler client : waitingClients) {
            client.sendMessage(message);
        }
    }

    public static void startGame(List<ClientHandler> group) {
        new Thread(() -> {
            try {
                int timeLimit = INITIAL_TIME_LIMIT;
                Random random = new Random();
                int wordsSent = 0;
                outerLoop:
                while (wordsSent < WORDS_PER_GAME) {
                    // Select a random word from the list
                    String word = words.get(random.nextInt(words.size()));
                    boolean wordTypedCorrectly = false;

                    // Introduce a 3-second delay before sending the new word
                    Thread.sleep(3000);

                    // Check if any client has set the exit flag
                    for (ClientHandler client : group) {
                        if (client.getExitFlag()) {
                            break outerLoop;
                        }
                    }

                    // Reset the time limit to INITIAL_TIME_LIMIT before sending a new word
                    timeLimit = INITIAL_TIME_LIMIT;

                    for (ClientHandler client : group) {
                        client.sendMessage("New word: " + word);
                        client.setWord(word);
                        client.setStartTime(System.currentTimeMillis());
                    }
                    for (int i = timeLimit; i > 0 && !wordTypedCorrectly; i--) {
                        // Check if any client has set the exit flag
                        for (ClientHandler client : group) {
                            if (client.getExitFlag()) {
                                break outerLoop;
                            }
                        }

                        for (ClientHandler client : group) {
                            client.sendMessage("Time remaining: " + i + " seconds");
                        }
                        Thread.sleep(1000);
                        boolean anyClientTypedCorrectly = false;
                        for (ClientHandler client : group) {
                            if (client.isWordTypedCorrectly()) {
                                anyClientTypedCorrectly = true;
                                break;
                            }
                        }
                        if (anyClientTypedCorrectly) {
                            break; // Break out of the loop if any client typed the word correctly
                        }
                    }

                    for (ClientHandler client : group) {
                        if (!client.isWordTypedCorrectly()) {
                            client.sendMessage("Time's up! You did not type the word correctly.");
                        }
                    }

                    wordsSent++; // Increment the count of words sent
                }
                for (ClientHandler client : group) {
                    client.sendMessage("Game over. Thanks for playing!");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                for (ClientHandler client : group) {
                    if (client.getExitFlag()) {
                        client.sendMessage("Exiting game. Welcome to dashboard.");
                        client.resetExitFlag(); // Reset the exit flag for future games
                    }
                }
            }
        }).start();
    }

    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private final BufferedReader in;
        private final PrintWriter out;
        private String username;
        private String currentWord;
        private long startTime;
        private boolean wordTypedCorrectly;
        private double totalScore;
        private boolean exitFlag = false;

        public ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new PrintWriter(socket.getOutputStream(), true);
            sendMessage("Welcome to Typefast! Please register or login to play.");
            totalScore = 0;
        }

        public void setExitFlag() {
            this.exitFlag = true;
        }

        public boolean getExitFlag() {
            return this.exitFlag;
        }

        public void resetExitFlag() {
            this.exitFlag = false;
        }

        public void resetScore() {
            this.totalScore = 0;
        }

        public void run() {
            try {
                while (true) {
                    String message = in.readLine();
                    if (message == null) {
                        break;
                    }
                    handleClientMessage(message);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void handleClientMessage(String message) {
            String[] tokens = message.split(" ");
            String command = tokens[0];
            switch (command) {
                case "REGISTER":
                    if (tokens.length == 3) {
                        String username = tokens[1];
                        String password = tokens[2];
                        registerUser(username, password, this);
                    } else {
                        sendMessage("Invalid registration command.");
                    }
                    break;
                case "LOGIN":
                    if (tokens.length == 3) {
                        String username = tokens[1];
                        String password = tokens[2];
                        authenticateUser(username, password, this);
                    } else {
                        sendMessage("Invalid login command.");
                    }
                    break;
                case "LOGOUT":
                    if (username != null) {
                        logoutUser(username);
                        sendMessage("Logout successful. Please login or register.");
                        username = null;
                        wordTypedCorrectly = false;
                    } else {
                        sendMessage("You are not logged in.");
                    }
                    break;
                case "JOIN":
                    if (username != null) {
                        addClientToWaitingList(this);
                    } else {
                        sendMessage("You must be logged in to join the game.");
                    }
                    break;
                case "SCOREBOARD":
                    sendScoreboard(this);
                    break;
                case "EXIT":
                    setExitFlag();
                    resetScore();
                    sendMessage("Exiting game. Welcome to dashboard.");
                    break;
                default:
                    if (currentWord != null && message.equals(currentWord)) {
                        long endTime = System.currentTimeMillis();
                        long timeTaken = endTime - startTime;
                        int timeTakenInSeconds = (int) (timeTaken / 1000); // Convert milliseconds to whole seconds
                        wordTypedCorrectly = true;
                        sendMessage("Correct! Time taken: " + timeTakenInSeconds + " s.");
                        double score = calculateScore(timeTaken); // Call calculateScore method here
                        totalScore += score;
                        sendMessage("Correct! Your score for this word: " + score);

                        currentWord = null;
                    } else {
                        sendMessage("Incorrect word. Try again.");
                    }
                    break;
            }
        }

        public void sendMessage(String message) {
            out.println(message);
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public void setWord(String word) {
            this.currentWord = word;
            this.wordTypedCorrectly = false;
        }

        public void setStartTime(long startTime) {
            this.startTime = startTime;
        }

        public boolean isWordTypedCorrectly() {
            return wordTypedCorrectly;
        }

        public double getTotalScore() {
            return totalScore;
        }

        private static double calculateScore(long timeTaken) {
            // Max marks for the word
            int maxMarks = 3;

            // Calculate deducted score
            double deductedScore = (timeTaken / 1000.0) / 10.0; // Convert milliseconds to seconds, then divide by 10
            System.out.println("Time taken (seconds): " + timeTaken / 1000.0);
            System.out.println("Deducted score: " + deductedScore);

            // Calculate final score
            double score = maxMarks - deductedScore;
            System.out.println("Score before adjustment: " + score);

            // Ensure the score does not go below 0
            score = Math.max(0, score);
            System.out.println("Final score: " + score);

            return (double) score;
        }

        public static synchronized void sendScoreboard(ClientHandler clientHandler) {
            StringBuilder scoreboard = new StringBuilder("Scoreboard: ");
            for (Map.Entry<String, ClientHandler> entry : authenticatedUsers.entrySet()) {
                String username = entry.getKey();
                ClientHandler handler = entry.getValue();
                scoreboard.append(username).append(": ").append(handler.getTotalScore()).append("\n");
            }
            String formattedScoreboard = scoreboard.toString().trim();

            // Add debug statement to print the formatted scoreboard
            System.out.println("Sending scoreboard data: " + formattedScoreboard);

            clientHandler.sendMessage(formattedScoreboard);
        }
    }
}
