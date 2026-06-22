package iecd.a51597.client.cli.screens.impl;

import iecd.a51597.client.Client;
import iecd.a51597.client.cli.StateMachine;
import iecd.a51597.client.cli.screens.Screen;
import iecd.a51597.client.cli.screens.handlers.ClientInviteHandler;
import iecd.a51597.client.game.GameController;
import iecd.a51597.common.game.dotsandboxes.DotsAndBoxesGame;
import iecd.a51597.common.protocol.Message;
import iecd.a51597.common.protocol.MessageBody;
import iecd.a51597.common.store.UserDTO;

import java.util.UUID;

/**
 * CLI Screen for InvitePendingScreen.
 */
public class InvitePendingScreen extends Screen {

    private final UserDTO target;

    private UUID pendingGameId;

        /**
     * Constructs a new InvitePendingScreen.
     *
     * @param sm the navigation state machine
     * @param client the bootstrap client context
     */
    public InvitePendingScreen(StateMachine sm, Client client, UserDTO target) {
        super(sm, client);
        this.target = target;
        this.prompt = "Waiting> ";
    }

        /**
     * Renders the screen visual interface and content.
     */
    @Override
    public void display() {
        System.out.println("You can type 'cancel' to cancel the invitation.");
    }

        /**
     * Processes user console command input on this screen.
     *
     * @param input the raw string input from the user
     */
    @Override
    public void handleInput(String input) {
        if ("cancel".equalsIgnoreCase(input.trim()) && pendingGameId != null) {
            System.out.println("Cancelling invite...");
            client.getServerConnection().getInviteHandler().cancelInvite(pendingGameId);
            sm.back();
        }
    }

        /**
     * Handles incoming server push notifications on this screen.
     *
     * @param message the received push notification message
     */
    @Override
    public void handlePush(Message message) {
        if (message.body() instanceof MessageBody.GameInviteResponsePush(
                UUID gameId, boolean accepted, String opponentUsername
        )) {
            if (accepted) {
                System.out.println(opponentUsername + " accepted your invite! Press Enter to start the game");
                GameController controller = new GameController(new DotsAndBoxesGame(
                        gameId,
                        client.getSessionManager().getUser().userId(),
                        target.userId()
                ),
                        client.getServerConnection(),
                        client.getSessionManager().getUser().userId(),
                        client.getSessionManager().getUser().username(),
                        target.username()
                );
                client.getActiveGames().add(controller);
                sm.changeState(new GameScreen(sm, client, controller));
            } else {
                System.out.println(opponentUsername + " rejected your invite. Press Enter to return to the main menu.");
                sm.changeState(new MainMenuScreen(sm, client));
            }
        }
    }

        /**
     * Lifecycle hook called when entering this screen.
     */
    @Override
    public void onEnter() {
        switch (client.getServerConnection().getInviteHandler().sendInvite(target)) {
            case ClientInviteHandler.InviteResult.Success(UUID gameId) -> {
                this.pendingGameId = gameId;
                System.out.println("Invite sent to " + target.username() + ", waiting for response...");
            }
            case ClientInviteHandler.InviteResult.Error(String message) -> {
                System.out.println("Failed to send invite to " + target.username() + ": " + message);
                sm.back();
            }
        }
    }

        /**
     * Lifecycle hook called when exiting this screen.
     */
    @Override
    public void onExit() {

    }
}
