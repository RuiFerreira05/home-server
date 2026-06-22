package iecd.a51597.client.cli.screens.impl;

import iecd.a51597.client.Client;
import iecd.a51597.client.cli.StateMachine;
import iecd.a51597.client.cli.screens.Screen;
import iecd.a51597.client.game.ClientBoardRenderer;
import iecd.a51597.client.game.GameController;
import iecd.a51597.common.game.dotsandboxes.DotsAndBoxesMove;
import iecd.a51597.common.game.dotsandboxes.DotsAndBoxesMoveCodec;
import iecd.a51597.common.protocol.Message;
import iecd.a51597.common.protocol.MessageBody;
import iecd.a51597.common.protocol.exceptions.MalformedMessageException;
import iecd.a51597.common.protocol.types.ActionType;

import java.util.UUID;

/**
 * Screen displayed during an active game session.
 */
public class GameScreen extends Screen {

    private final GameController controller;

        /**
     * Constructs a new GameScreen.
     *
     * @param sm the navigation state machine
     * @param client the bootstrap client context
     */
    public GameScreen(StateMachine sm, Client client, GameController controller) {
        super(sm, client);
        this.controller = controller;
        this.prompt = "Game> ";
    }

        /**
     * Lifecycle hook called when entering this screen.
     */
    @Override
    public void onEnter() {
        logger.info("Entering Game Screen for game: {}", controller.getState().getGameId());
    }

        /**
     * Renders the screen visual interface and content.
     */
    @Override
    public void display() {
        // Don't prompt for moves if the game is over
        if (controller.getState().isGameOver()) {
            return;
        }

        ClientBoardRenderer.printBoard(controller);

        if (controller.isMyTurn()) {
            System.out.print("\nYour turn! Enter coordinates (x1 y1 x2 y2) or 'surrender': ");
        } else {
            System.out.println("\nWaiting for " + controller.getOpponentUsername() + " to play... (type 'surrender' to forfeit)");
        }
    }

        /**
     * Processes user console command input on this screen.
     *
     * @param input the raw string input from the user
     */
    @Override
    public void handleInput(String input) {
        if (controller.getState().isGameOver()) {
            // Return to active games screen instead of main menu
            sm.changeState(new ViewActiveGamesScreen(sm, client));
            return;
        }

        if ("back".equalsIgnoreCase(input.trim()) || "q".equalsIgnoreCase(input.trim())) {
            System.out.println("Returning to active games list...");
            sm.changeState(new ViewActiveGamesScreen(sm, client));
            return;
        }

        if ("surrender".equalsIgnoreCase(input.trim())) {
            System.out.println("Surrendering...");
            controller.attemptSurrender();
            return;
        }

        if (!controller.isMyTurn()) {
            System.out.println("[!] It is not your turn yet. Please wait for the opponent. (Or type 'q' to switch games, or 'surrender' to forfeit)");
            return;
        }

        try {
            String[] parts = input.trim().split("\\s+");
            if (parts.length != 4) {
                System.out.println("[!] Invalid format. Please enter exactly 4 numbers separated by spaces (e.g., 0 0 1 0).");
                return;
            }

            int x1 = Integer.parseInt(parts[0]);
            int y1 = Integer.parseInt(parts[1]);
            int x2 = Integer.parseInt(parts[2]);
            int y2 = Integer.parseInt(parts[3]);

            DotsAndBoxesMove move = new DotsAndBoxesMove(x1, y1, x2, y2);
            controller.attemptLocalMove(move);

            // Check if that move ended the game locally
            checkGameOver();

        } catch (NumberFormatException e) {
            System.out.println("[!] Coordinates must be valid integers.");
        }
    }

        /**
     * Handles incoming server push notifications on this screen.
     *
     * @param message the received push notification message
     */
    @Override
    public void handlePush(Message message) {
        logger.debug("GameScreen received push notification: {}", message.actionType());

        if (message.actionType() == ActionType.GAME_MOVE) {
            MessageBody.GameMove body = (MessageBody.GameMove) message.body();
            try {
                DotsAndBoxesMove move = (DotsAndBoxesMove) new DotsAndBoxesMoveCodec().deserialize(body.rawMove());
                controller.applyOpponentMove(move);
            } catch (MalformedMessageException e) {
                throw new RuntimeException(e);
            }
        } else if (message.actionType() == ActionType.GAME_OVER) {
            MessageBody.GameOver body = (MessageBody.GameOver) message.body();
            controller.getState().forceGameOver(body.winnerId());
            if ("SURRENDER".equals(body.reason())) {
                System.out.println("\n[The game ended because a player surrendered!]");
            } else if ("TIMEOUT".equals(body.reason())) {
                System.out.println("\n[The game ended because a player ran out of time!]");
            }
            client.getSessionManager().updateUser(body.user());
            client.getActiveGames().remove(controller);
        } else if (message.actionType() == ActionType.GAME_OVER_DRAW) {
            MessageBody.GameOverDraw body = (MessageBody.GameOverDraw) message.body();
            controller.getState().forceGameOver(null);
            client.getSessionManager().updateUser(body.user());
            client.getActiveGames().remove(controller);
        }

        System.out.println("\n[Update received from server]");

        checkGameOver();

        if (!controller.getState().isGameOver()) {
            display();
        }
    }

        /**
     * Lifecycle hook called when exiting this screen.
     */
    @Override
    public void onExit() {
        logger.info("Exiting Game Screen");
    }

    /**
     * Helper method to evaluate end-state and draw the final scoreboard.
     */
    private void checkGameOver() {
        if (controller.getState().isGameOver()) {
            ClientBoardRenderer.printBoard(controller);
            UUID winnerId = controller.getState().getWinnerId();

            System.out.println("\n=================================");
            if (winnerId == null) {
                System.out.println("        IT'S A TIE!");
            } else if (winnerId.equals(controller.getMyUserId())) {
                System.out.println("        YOU WON!");
            } else {
                System.out.println("        YOU LOST!");
            }
            System.out.println("=================================\n");

            System.out.print("Press ENTER to return to the active games menu...");
        }
    }
}