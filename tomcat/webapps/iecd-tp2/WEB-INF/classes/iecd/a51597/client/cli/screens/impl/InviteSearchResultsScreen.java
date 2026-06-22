package iecd.a51597.client.cli.screens.impl;

import iecd.a51597.client.Client;
import iecd.a51597.client.cli.StateMachine;
import iecd.a51597.client.cli.screens.OptionScreen;
import iecd.a51597.common.protocol.Message;
import iecd.a51597.common.store.UserDTO;

import java.util.List;

/**
 * CLI Screen for InviteSearchResultsScreen.
 */
public class InviteSearchResultsScreen extends OptionScreen {

        /**
     * Constructs a new InviteSearchResultsScreen.
     *
     * @param sm the navigation state machine
     * @param client the bootstrap client context
     */
    public InviteSearchResultsScreen(StateMachine sm, Client client, List<UserDTO> users) {
        super(sm, client);
        addOption("back", sm::back);
        for (UserDTO user : users) {
            addOption(user.username(), () -> sm.changeState(new InvitePendingScreen(sm, client, user)));
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
        logger.info("Entering SearchResultsScreen");
    }

        /**
     * Lifecycle hook called when exiting this screen.
     */
    @Override
    public void onExit() {
        logger.info("Exiting SearchResultsScreen");
    }
}
