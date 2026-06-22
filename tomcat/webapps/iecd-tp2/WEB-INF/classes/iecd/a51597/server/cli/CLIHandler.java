package iecd.a51597.server.cli;

import iecd.a51597.server.Server;
import iecd.a51597.server.config.ServerConfiguration;
import iecd.a51597.server.session.Session;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Interactive administrative command-line interface for runtime server control.
 */
public class CLIHandler {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    private final Server server;
    private final Instant startedAt = Instant.now();
    private boolean running = false;

    private static final Logger logger = LogManager.getLogger(CLIHandler.class);

    private final Map<String, Command> commands = new HashMap<>();

    /**
     * Creates a CLI handler and registers supported commands.
     *
     * @param server server instance controlled by this CLI
     */
    public CLIHandler(Server server) {
        this.server = server;
        commands.put("help", new Command(this::help, null, "Show this help message"));
        commands.put("status", new Command(this::status, null, "Print server status"));
        commands.put("start", new Command(this::start, "[port]", "Start the server on the given port (default: configured port)"));
        commands.put("stop", new Command(this::stop, null, "Stop the server from accepting new connections"));
        commands.put("exit", new Command(this::exit, null, "Shutdown the server and exit"));
        commands.put("sessions", new Command(this::sessions, null, "List all active sessions"));
        commands.put("users", new Command(this::users, null, "List all registered users"));
        commands.put("connections", new Command(this::connections, null, "List all open connections"));
        commands.put("kick", new Command(this::kick, "<username>", "Close a user's connection and invalidate their session"));
        commands.put("leaderboard", new Command(this::leaderboard, "[limit]", "Show the player leaderboard"));
        commands.put("see-user", new Command(this::seeUser, "<username>", "Show detailed info about a user"));
    }

    /**
     * Starts the command-processing loop.
     */
    public void loop() {
        running = true;
        printStatusHeader();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            while (running) {
                System.out.print(">> ");
                String line = reader.readLine();
                if (line == null) break;
                handleCommand(line);
            }
        } catch (IOException e) {
            if (running) {
                logger.error("CLI read error", e);
            }
        }
    }

    private void seeUser(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: see-user <username>");
            return;
        }
        String username = args[0];
        server.getUserStore().findByUsername(username).ifPresentOrElse(
                user -> {
                    System.out.println("User ID: " + user.getUserId());
                    System.out.println("Username: " + user.getUsername());
                    if (user.getPhoto() != null) System.out.println("photo reference: " + user.getPhoto());
                    if (user.getNationality() != null) System.out.println("Nationality: " + user.getNationality());
                    if (user.getDob() != null) System.out.println("Dob: " + user.getDob());
                    if (!user.getStats().matches().isEmpty()) {
                        System.out.println("Matches played: " + user.getStats().gamesPlayed());
                        System.out.println("Matches won: " + user.getStats().gamesWon());
                        System.out.println("Matches lost: " + user.getStats().gamesLost());
                        System.out.println("Total play time: " + user.getStats().totalPlayTimeSecs() + "s");
                        System.out.println("Win-rate: " + user.getStats().winRate()*100 + "%");
                    }
                },
                () -> System.out.println("No user found with username: " + username)
        );
    }

    private void sessions(String[] args) {
        var sessions = server.getSessionManager().getAllSessions();
        System.out.printf("Active sessions: %d%n", sessions.size());
        if (sessions.isEmpty()) return;
        System.out.printf("  %-10s  %-16s  %-10s  %s%n", "TOKEN", "USERNAME", "LAST ACTIVE", "EXPIRES IN");
        for (var s : sessions) {
            System.out.printf("  %-10s  %-16s  %-10s  %s%n",
                    shortId(s.getToken()),
                    s.getUser().getUsername(),
                    TIME_FMT.format(s.getLastActivity()),
                    formatExpiry(s)
            );
        }
    }

    private void users(String[] args) {
        var users = server.getUserStore().getAllUsers();
        System.out.printf("Registered users: %d%n", users.size());
        if (users.isEmpty()) return;
        System.out.printf("  %-10s  %-16s  %s%n", "ID", "USERNAME", "STATUS");
        for (var u : users) {
            boolean online = server.getSessionManager().getSessionByUserId(u.getUserId()).isPresent();
            System.out.printf("  %-10s  %-16s  %s%n",
                    shortId(u.getUserId()),
                    u.getUsername(),
                    online ? "● online" : "○ offline"
            );
        }
    }

    private void connections(String[] args) {
        var cons = server.getConnections();
        System.out.printf("Open connections: %d%n", cons.size());
        if (cons.isEmpty()) return;
        System.out.printf("  %-26s  %-13s  %s%n", "REMOTE ADDRESS", "AUTHENTICATED", "USER");
        for (var c : cons) {
            String remote = c.getClientSocket().getRemoteSocketAddress().toString();
            var sessionOpt = server.getSessionManager().getAllSessions().stream()
                    .filter(s -> s.getConnection() == c)
                    .findFirst();
            System.out.printf("  %-26s  %-13s  %s%n",
                    remote,
                    sessionOpt.isPresent() ? "yes" : "no",
                    sessionOpt.map(s -> s.getUser().getUsername()).orElse("—")
            );
        }
    }

    private void leaderboard(String[] args) {
        int limit = 10;
        if (args.length > 0) {
            try {
                limit = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid limit");
                return;
            }
        }

        var entries = server.getLeaderboard().getTopPlayers(limit);
        if (entries.isEmpty()) {
            System.out.println("No players yet.");
            return;
        }

        System.out.printf("  %-4s  %-16s  %-6s  %-6s  %s%n", "RANK", "USERNAME", "WON", "LOST", "TOTAL TIME");
        for (int i = 0; i < entries.size(); i++) {
            var e = entries.get(i);
            System.out.printf("  %-4d  %-16s  %-6d  %-6d  %.1fs%n",
                    i + 1, e.username(), e.gamesWon(), e.gamesLost(), e.totalPlayTimeSecs());
        }
    }

    private void kick(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: kick <username>");
            return;
        }
        String username = args[0];

        server.getUserStore().findByUsername(username).ifPresentOrElse(
                user -> server.getSessionManager().getSessionByUserId(user.getUserId()).ifPresentOrElse(
                        session -> {
                            session.getConnection().closeConnection(); // invalidates session as a side effect
                            System.out.println("Kicked " + username);
                            logger.info("Admin kicked user: {}", username);
                        },
                        () -> System.out.println(username + " is not online")
                ),
                () -> System.out.println("No user found with username: " + username)
        );
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8) + "…";
    }

    private static String formatExpiry(Session session) {
        long elapsed = Duration.between(session.getLastActivity(), Instant.now()).getSeconds();
        long remaining = ServerConfiguration.SESSION_TIMEOUT_SECONDS - elapsed;
        if (remaining <= 0) return "expired";
        return String.format("%dm %02ds", remaining / 60, remaining % 60);
    }

    /**
     * Prints a boxed high-level status summary.
     */
    public void printStatusHeader() {
        final int W = ServerConfiguration.STATUS_BOX_WIDTH;

        boolean listening = server.isListening();
        int connections = server.getConnections().size();
        int sessions = server.getSessionManager().activeSessionCount();
        Instant now = Instant.now();

        String statusLine = listening
                ? "● LISTENING  (port " + server.getListener().getPort() + ")"
                : "○ IDLE";

        Duration uptime = Duration.between(startedAt, now);
        String uptimeLine = String.format("%dd %02dh %02dm %02ds",
                uptime.toDaysPart(),
                uptime.toHoursPart(),
                uptime.toMinutesPart(),
                uptime.toSecondsPart());

        String timeLine = DATE_FMT.format(now) + "  " + TIME_FMT.format(now);

        String border = "═".repeat(W);
        String top = "╔" + border + "╗";
        String mid = "╠" + border + "╣";
        String bot = "╚" + border + "╝";
        String title = centre("IECD-TP1 - SERVER STATUS", W);

        System.out.println(top);
        System.out.println("║" + title + "║");
        System.out.println(mid);
        System.out.println(row("Status", statusLine, W));
        System.out.println(row("Connections", String.valueOf(connections), W));
        System.out.println(row("Sessions", String.valueOf(sessions), W));
        System.out.println(row("Uptime", uptimeLine, W));
        System.out.println(row("Time", timeLine, W));
        System.out.println(bot);
    }

    private static String centre(String text, int width) {
        int padding = width - text.length();
        int left = padding / 2;
        int right = padding - left;
        return " ".repeat(left) + text + " ".repeat(right);
    }

    private static String row(String label, String value, int innerWidth) {
        String content = String.format("  %-12s: %s", label, value);
        if (content.length() < innerWidth) {
            content = content + " ".repeat(innerWidth - content.length());
        } else if (content.length() > innerWidth) {
            content = content.substring(0, innerWidth);
        }
        return "║" + content + "║";
    }

    private void handleCommand(String input) {
        String[] parts = input.trim().split("\\s+");
        String name = parts[0].toLowerCase();
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);

        Command command = commands.get(name);
        if (command == null) {
            System.out.println("Unknown command: " + name);
            System.out.println("Type 'help' for available commands.");
            logger.warn("Unknown command entered: {}", name);
        } else {
            try {
                command.execute(args);
            } catch (Exception e) {
                System.out.println("Error executing command '" + name + "': " + e.getMessage());
                logger.error("Error executing command '{}'", name, e);
            }
        }
    }

    private void help(String[] args) {
        commands.forEach((name, cmd) -> {
            String usage = cmd.usage() != null ? " " + cmd.usage() : "";
            System.out.printf("  %-15s %-15s  %s%n", name, usage, cmd.description());
        });
    }

    private void status(String[] args) {
        printStatusHeader();
    }

    private void start(String[] args) {
        int port = server.getStartupPort();

        if (args.length != 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid port number: " + args[0]);
                logger.error("Invalid port number in start command: {}", args[0]);
                return;
            }
        }

        if (!server.isListening()) {
            server.startListener(port);
            System.out.println("Server started on port: " + port);
            logger.info("Server started on port: {}", port);
        } else {
            System.out.println("Server is already listening for connections");
        }
    }

    private void stop(String[] args) {
        if (server.isListening()) {
            server.stopListener();
            System.out.println("Server stopped listening for connections");
            logger.info("Server stopped listening for connections");
        } else {
            System.out.println("Server is already not listening for connections");
        }
    }

    private void exit(String[] args) {
        running = false;
        logger.info("Server shutting down");
        System.out.println("Shutting down Server...");
        server.shutdown();
    }

    private record Command(Consumer<String[]> action, String usage, String description) {
        void execute(String[] args) {
            action.accept(args);
        }
    }
}