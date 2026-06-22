package iecd.a51597.client.cli.screens.impl;

import iecd.a51597.client.Client;
import iecd.a51597.client.cli.StateMachine;
import iecd.a51597.client.cli.screens.OptionScreen;
import iecd.a51597.client.cli.screens.Screen;
import iecd.a51597.common.protocol.Message;

/**
 * CLI Screen for GameMenuScreen.
 */
public class GameMenuScreen extends OptionScreen {

    protected GameMenuScreen(StateMachine sm, Client client) {
        super(sm, client);
        addOption("Invite another player", () -> sm.changeState(new SearchInviteScreen(sm, client)));
        addOption("View Invites (" + client.getPendingInvites().size() + ")", () -> sm.changeState(new ViewInvitesScreen(sm, client)));
        addOption("View Active Games (" + client.getActiveGames().size() + ")", () -> sm.changeState(new ViewActiveGamesScreen(sm, client)));
        addOption("Back to main menu", () -> sm.changeState(new MainMenuScreen(sm, client)));
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
