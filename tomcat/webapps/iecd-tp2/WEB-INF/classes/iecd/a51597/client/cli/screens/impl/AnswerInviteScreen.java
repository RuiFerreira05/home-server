package iecd.a51597.client.cli.screens.impl;

import iecd.a51597.client.Client;
import iecd.a51597.client.cli.StateMachine;
import iecd.a51597.client.cli.screens.OptionScreen;
import iecd.a51597.client.cli.screens.Screen;
import iecd.a51597.client.cli.screens.handlers.ClientInviteHandler;
import iecd.a51597.client.game.GameController;
import iecd.a51597.common.game.dotsandboxes.DotsAndBoxesGame;
import iecd.a51597.common.protocol.Message;
import iecd.a51597.common.protocol.MessageBody;

/**
 * CLI Screen for AnswerInviteScreen.
 */
public class AnswerInviteScreen extends OptionScreen {

    private MessageBody.GameInvitePush invite;

        /**
     * Constructs a new AnswerInviteScreen.
     *
     * @param sm the navigation state machine
     * @param client the bootstrap client context
     */
    public AnswerInviteScreen(StateMachine sm, Client client, MessageBody.GameInvitePush messageBody) {
        super(sm, client);
        addOption("Accept", this::accept);
        addOption("Decline", this::decline);
        addOption("Back", sm::back);
        this.invite = messageBody;
    }

    private void accept() {
        ClientInviteHandler.AnswerInviteResponse result = client.getServerConnection().getInviteHandler().answerInvite(invite, true);
        switch (result) {
            case ClientInviteHandler.AnswerInviteResponse.Success success -> {
                GameController controller = new GameController(new DotsAndBoxesGame(
                        invite.gameId(),
                        invite.fromUserId(),
                        client.getSessionManager().getUser().userId()
                ),
                        client.getServerConnection(),
                        client.getSessionManager().getUser().userId(),
                        client.getSessionManager().getUser().username(),
                        invite.fromUsername()
                );
                client.getActiveGames().add(controller);
                sm.changeState(new GameScreen(sm, client, controller));
            }

            case ClientInviteHandler.AnswerInviteResponse.Error error -> {
                logger.error("Failed to accept invite: ", error);
                System.out.println("Failed to accept invite: " + error.message());
                sm.changeState(new MainMenuScreen(sm, client));
            }
        }
    }

    private void decline() {
        ClientInviteHandler.AnswerInviteResponse result = client.getServerConnection().getInviteHandler().answerInvite(invite, false);
        switch (result) {
            case ClientInviteHandler.AnswerInviteResponse.Success ignored -> {
                sm.changeState(new ViewInvitesScreen(sm, client));
            }
            case ClientInviteHandler.AnswerInviteResponse.Error error -> {
                logger.error("Failed to decline invite: ", error);
                System.out.println("Failed to decline invite: " + error.message());
                sm.changeState(new ViewInvitesScreen(sm, client));
            }
        }
    }

        /**
     * Handles incoming server push notifications on this screen.
     *
     * @param message the received push notification message
     */
    @Override
    public void handlePush(Message message) {

    }

        /**
     * Lifecycle hook called when entering this screen.
     */
    @Override
    public void onEnter() {

    }

        /**
     * Lifecycle hook called when exiting this screen.
     */
    @Override
    public void onExit() {

    }
}
