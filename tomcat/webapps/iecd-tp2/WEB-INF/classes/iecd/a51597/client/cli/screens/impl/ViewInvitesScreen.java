package iecd.a51597.client.cli.screens.impl;

import iecd.a51597.client.Client;
import iecd.a51597.client.cli.StateMachine;
import iecd.a51597.client.cli.screens.OptionScreen;
import iecd.a51597.client.cli.screens.Screen;
import iecd.a51597.common.protocol.Message;
import iecd.a51597.common.protocol.MessageBody;

/**
 * CLI Screen for ViewInvitesScreen.
 */
public class ViewInvitesScreen extends OptionScreen {

        /**
     * Constructs a new ViewInvitesScreen.
     *
     * @param sm the navigation state machine
     * @param client the bootstrap client context
     */
    public ViewInvitesScreen(StateMachine sm, Client client) {
        super(sm, client);
        for (MessageBody.GameInvitePush messageBody : client.getPendingInvites()) {
            addOption(messageBody.fromUsername(), () -> sm.changeState(new AnswerInviteScreen(sm, client, messageBody)));
        }
        addOption("Back to game menu", () -> sm.changeState(new GameMenuScreen(sm, client)));
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
