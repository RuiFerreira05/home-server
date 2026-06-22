package iecd.a51597.client.game;

import iecd.a51597.common.game.dotsandboxes.DotsAndBoxesGame;
import iecd.a51597.common.game.dotsandboxes.DotsAndBoxesMove;

import java.util.Set;
import java.util.UUID;

/**
 * Helper class for rendering the game board to the console.
 */
public class ClientBoardRenderer {

    private static final int WIDTH = 5;
    private static final int HEIGHT = 5;

    /**
     * Renders the game board and scoreboard to the standard console output.
     *
     * @param controller the active game controller representing the match state
     */
    public static void printBoard(GameController controller) {
        DotsAndBoxesGame state = controller.getState();
        Set<DotsAndBoxesMove> lines = state.getDrawnLines();

        // Determine who is P1 and P2 for the scoreboard
        boolean amIPlayer1 = state.getPlayer1Id().equals(controller.getMyUserId());
        int myScore = amIPlayer1 ? state.getPlayer1Score() : state.getPlayer2Score();
        int opponentScore = amIPlayer1 ? state.getPlayer2Score() : state.getPlayer1Score();

        System.out.println("\n=================================");
        System.out.printf(" %s: %d  |  %s: %d%n", controller.getMyUsername(), myScore, controller.getOpponentUsername(), opponentScore);
        System.out.println("=================================");
        System.out.println("    0   1   2   3   4  (X)");

        for (int y = 0; y < HEIGHT; y++) {
            // Print horizontal lines and dots
            System.out.print(" " + y + "  ");
            for (int x = 0; x < WIDTH; x++) {
                System.out.print("*");
                if (x < WIDTH - 1) {
                    boolean hasLine = lines.contains(new DotsAndBoxesMove(x, y, x + 1, y));
                    System.out.print(hasLine ? "---" : "   ");
                }
            }
            System.out.println();

            // Print vertical lines
            if (y < HEIGHT - 1) {
                System.out.print("    ");
                for (int x = 0; x < WIDTH; x++) {
                    boolean hasLine = lines.contains(new DotsAndBoxesMove(x, y, x, y + 1));
                    UUID owner = controller.getState().getBoxOwner(x, y);
                    String initial = getInitial(controller, owner);
                    System.out.print(hasLine ? "| " + initial + " " : "  " + initial + " ");
                }
                System.out.println();
            }
        }
        System.out.println("(Y)\n");
    }

    /**
     * helper method to identify box owners by their initial, so the cli tui looks nice
     *
     * @param controller game controller
     * @param owner the box owner
     * @return empty space for no owner, "1"/"2" if both players have the same initial, or the owner's initial otherwise
     */
    private static String getInitial(GameController controller, UUID owner) {
        String initial = " ";
        if (owner != null) {
            if (controller.getMyUsername().substring(0, 1).equals(controller.getOpponentUsername().substring(0, 1))) {
                initial = owner.equals(controller.getState().getPlayer1Id()) ? "1" : "2";
            } else {
                initial = owner.equals(controller.getMyUserId()) ? controller.getMyUsername().substring(0, 1) : controller.getOpponentUsername().substring(0, 1);
            }
        }
        return initial;
    }
}