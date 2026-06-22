package iecd.a51597.client.cli.screens.impl;

import iecd.a51597.client.Client;
import iecd.a51597.client.cli.StateMachine;
import iecd.a51597.client.cli.screens.OptionScreen;
import iecd.a51597.client.game.GameController;
import iecd.a51597.common.protocol.Message;

import java.util.List;

/**
 * Screen displaying the user's active concurrent matches, allowing them to switch between games.
 */
public class ViewActiveGamesScreen extends OptionScreen {

        /**
     * Constructs a new ViewActiveGamesScreen.
     *
     * @param sm the navigation state machine
     * @param client the bootstrap client context
     */
    public ViewActiveGamesScreen(StateMachine sm, Client client) {
        super(sm, client);
        this.prompt = "Games> ";
    }

    private void rebuildOptions() {
        clearOptions();
        List<GameController> games = client.getActiveGames();
        for (int i = 0; i < games.size(); i++) {
            final GameController gc = games.get(i);
            String opponent = gc.getOpponentUsername();
            String turn = gc.isMyTurn() ? "YOUR TURN! (30s limit)" : "Opponent's turn";
            int myScore = gc.getState().getPlayer1Id().equals(gc.getMyUserId()) ? gc.getState().getPlayer1Score() : gc.getState().getPlayer2Score();
            int oppScore = gc.getState().getPlayer1Id().equals(gc.getMyUserId()) ? gc.getState().getPlayer2Score() : gc.getState().getPlayer1Score();
            String label = "VS " + opponent + " (" + myScore + " - " + oppScore + ") â€” " + turn;
            addOption(label, () -> sm.changeState(new GameScreen(sm, client, gc)));
        }
        addOption("Back to game menu", () -> sm.changeState(new GameMenuScreen(sm, client)));
    }

        /**
     * Renders the screen visual interface and content.
     */
    @Override
    public void display() {
        rebuildOptions();
        System.out.println("=== Active Concurrent Games ===");
        if (client.getActiveGames().isEmpty()) {
            System.out.println("[No active games currently in progress]");
        }
        displayOptions();
    }

        /**
     * Handles incoming server push notifications on this screen.
     *
     * @param message the received push notification message
     */
    @Override
    public void handlePush(Message message) {
        // Refresh display if push notification is received
        System.out.println("\n[Active games list updated from server push]");
        rebuildOptions();
        display();
    }

        /**
     * Lifecycle hook called when entering this screen.
     */
    @Override
    public void onEnter() {
        logger.info("Entered ViewActiveGamesScreen");
    }

        /**
     * Lifecycle hook called when exiting this screen.
     */
    @Override
    public void onExit() {
        logger.info("Exited ViewActiveGamesScreen");
    }
}
