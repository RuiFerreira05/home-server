package iecd.a51597.client.cli.screens.impl;

import iecd.a51597.client.Client;
import iecd.a51597.client.cli.StateMachine;
import iecd.a51597.client.cli.screens.Screen;
import iecd.a51597.client.cli.screens.handlers.ClientSearchHandler;
import iecd.a51597.common.protocol.Message;
import iecd.a51597.common.store.UserDTO;

import java.util.List;

/**
 * CLI Screen for SearchInviteScreen.
 */
public class SearchInviteScreen extends Screen {

        /**
     * Constructs a new SearchInviteScreen.
     *
     * @param sm the navigation state machine
     * @param client the bootstrap client context
     */
    public SearchInviteScreen(StateMachine sm, Client client) {
        super(sm, client);
    }

        /**
     * Renders the screen visual interface and content.
     */
    @Override
    public void display() {
        System.out.println("Type to search for new players (\"back\" to go to menu):");
    }

        /**
     * Processes user console command input on this screen.
     *
     * @param input the raw string input from the user
     */
    @Override
    public void handleInput(String input) {
        if (input.equals("back")) {
            sm.changeState(new MainMenuScreen(sm, client));
            return;
        }
        if (client.getServerConnection().getSearchHandler().searchPlayers(input) instanceof ClientSearchHandler.SearchPlayerResult.SUCCESS(List<UserDTO> newUsers)) {
            sm.changeState(new InviteSearchResultsScreen(sm, client, newUsers));
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
        logger.info("Entering SearchForPlayerScreen");
    }

        /**
     * Lifecycle hook called when exiting this screen.
     */
    @Override
    public void onExit() {
        logger.info("Exiting SearchForPlayerScreen");
    }
}
