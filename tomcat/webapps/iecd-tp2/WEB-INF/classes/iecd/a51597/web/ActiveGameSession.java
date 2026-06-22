package iecd.a51597.web;

import java.io.Serializable;
import java.util.*;

/**
 * Thread-safe container to track and serialize the active game state in the user's HttpSession.
 * This is used to reconstruct the Dots & Boxes canvas when switching between pages.
 */
public class ActiveGameSession implements Serializable {
    private static final long serialVersionUID = 1L;

    private final UUID gameId;
    private final String role; // "inviter" or "invitee"
    private final String opponentName;
    
    private final Set<String> drawnLines = new HashSet<>(); // "x1,y1,x2,y2" canonical
    private final Map<String, String> capturedBoxes = new HashMap<>(); // "x,y" -> "me" or "opponent"
    
    private int player1Score = 0; // Score for Inviter
    private int player2Score = 0; // Score for Invitee
    private boolean isMyTurn;

    /**
     * Constructs a new active game session state container.
     *
     * @param gameId the unique match UUID
     * @param role the user's role ("inviter" or "invitee")
     * @param opponentName the opponent's username
     */
    public ActiveGameSession(UUID gameId, String role, String opponentName) {
        this.gameId = gameId;
        this.role = role;
        this.opponentName = opponentName;
        // Inviter starts the game (P1 moves first)
        this.isMyTurn = "inviter".equalsIgnoreCase(role);
    }

    /**
     * Gets the unique match game UUID.
     *
     * @return the game UUID
     */
    public UUID getGameId() {
        return gameId;
    }

    /**
     * Gets the role of the player in this session.
     *
     * @return "inviter" or "invitee"
     */
    public String getRole() {
        return role;
    }

    /**
     * Gets the opponent's username.
     *
     * @return the opponent's username string
     */
    public String getOpponentName() {
        return opponentName;
    }

    /**
     * Gets the copy of current drawn line coordinates.
     *
     * @return the set of drawn lines
     */
    public synchronized Set<String> getDrawnLines() {
        return new HashSet<>(drawnLines);
    }

    /**
     * Gets the copy of currently captured boxes mapped to their captor ("me" or "opponent").
     *
     * @return the map of captured boxes
     */
    public synchronized Map<String, String> getCapturedBoxes() {
        return new HashMap<>(capturedBoxes);
    }

    /**
     * Gets Player 1's score.
     *
     * @return Player 1's score
     */
    public synchronized int getPlayer1Score() {
        return player1Score;
    }

    /**
     * Gets Player 2's score.
     *
     * @return Player 2's score
     */
    public synchronized int getPlayer2Score() {
        return player2Score;
    }

    /**
     * Checks if it is currently the local user's turn in this game session.
     *
     * @return true if it is the local user's turn, false otherwise
     */
    public synchronized boolean isMyTurn() {
        return isMyTurn;
    }

    /**
     * Applies a line move, updates box completions, adjusts scores, and flips the turn.
     * 
     * @param moveStr coordinate representation "x1,y1,x2,y2"
     * @param isMe true if the local user made the move, false if received via push
     */
    public synchronized void applyMove(String moveStr, boolean isMe) {
        if (moveStr == null || moveStr.isBlank()) {
            return;
        }

        String[] pts = moveStr.split(",");
        if (pts.length != 4) {
            return;
        }

        try {
            int x1 = Integer.parseInt(pts[0].trim());
            int y1 = Integer.parseInt(pts[1].trim());
            int x2 = Integer.parseInt(pts[2].trim());
            int y2 = Integer.parseInt(pts[3].trim());

            // Canonicalize coordinates to guarantee x1 <= x2 (and y1 <= y2 when horizontal/vertical)
            if (x1 > x2 || (x1 == x2 && y1 > y2)) {
                int tempX = x1; x1 = x2; x2 = tempX;
                int tempY = y1; y1 = y2; y2 = tempY;
            }
            
            String canonicalKey = x1 + "," + y1 + "," + x2 + "," + y2;
            
            if (drawnLines.contains(canonicalKey)) {
                return; // Line already recorded
            }

            drawnLines.add(canonicalKey);
            
            // Check completed boxes
            int capturedCount = 0;
            String owner = isMe ? "me" : "opponent";

            if (y1 == y2) { // Horizontal line
                if (y1 > 0 && isBoxClosed(x1, y1 - 1)) {
                    if (captureBox(x1, y1 - 1, owner)) capturedCount++;
                }
                if (y1 < 4 && isBoxClosed(x1, y1)) {
                    if (captureBox(x1, y1, owner)) capturedCount++;
                }
            } else { // Vertical line
                if (x1 > 0 && isBoxClosed(x1 - 1, y1)) {
                    if (captureBox(x1 - 1, y1, owner)) capturedCount++;
                }
                if (x1 < 4 && isBoxClosed(x1, y1)) {
                    if (captureBox(x1, y1, owner)) capturedCount++;
                }
            }

            // Update scores based on the roles
            if ("inviter".equalsIgnoreCase(role)) {
                if (isMe) {
                    player1Score += capturedCount;
                } else {
                    player2Score += capturedCount;
                }
            } else { // invitee
                if (isMe) {
                    player2Score += capturedCount;
                } else {
                    player1Score += capturedCount;
                }
            }

            // Turn assignment: if captured, retain turn, otherwise flip
            if (capturedCount > 0) {
                isMyTurn = isMe;
            } else {
                isMyTurn = !isMe;
            }

        } catch (NumberFormatException e) {
            // Ignore bad input formats
        }
    }

    private boolean isBoxClosed(int bx, int by) {
        return drawnLines.contains(bx + "," + by + "," + (bx + 1) + "," + by) &&
               drawnLines.contains(bx + "," + (by + 1) + "," + (bx + 1) + "," + (by + 1)) &&
               drawnLines.contains(bx + "," + by + "," + bx + "," + (by + 1)) &&
               drawnLines.contains((bx + 1) + "," + by + "," + (bx + 1) + "," + (by + 1));
    }

    private boolean captureBox(int bx, int by, String owner) {
        String key = bx + "," + by;
        if (!capturedBoxes.containsKey(key)) {
            capturedBoxes.put(key, owner);
            return true;
        }
        return false;
    }
}
