package iecd.a51597.client.cli.screens.impl;

import iecd.a51597.client.Client;
import iecd.a51597.client.cli.StateMachine;
import iecd.a51597.client.cli.screens.Screen;
import iecd.a51597.client.session.ClientSessionManager;
import iecd.a51597.common.protocol.Message;

/**
 * Screen for user authentication.
 */
public class LoginScreen extends Screen {

    private enum LoginState {
        ENTER_USERNAME,
        ENTER_PASSWORD
    }

    private LoginState currentState = LoginState.ENTER_USERNAME;
    private String tempUsername;
    private String tempPassword;

        /**
     * Constructs a new LoginScreen.
     *
     * @param sm the navigation state machine
     * @param client the bootstrap client context
     */
    public LoginScreen(StateMachine sm, Client client) {
        super(sm, client);
    }

        /**
     * Renders the screen visual interface and content.
     */
    @Override
    public void display() {
        if (currentState == LoginState.ENTER_USERNAME) {
            System.out.println("========================================");
            System.out.println("                 LOGIN                  ");
            System.out.println("========================================");
            System.out.println("Type 'exit' to go back to the main menu.");
            this.prompt = "Username: ";
        } else if (currentState == LoginState.ENTER_PASSWORD) {
            this.prompt = "Password: ";
        }
    }

        /**
     * Processes user console command input on this screen.
     *
     * @param input the raw string input from the user
     */
    @Override
    public void handleInput(String input) {
        if (input.isEmpty()) {
            return;
        }
        switch (currentState) {
            case ENTER_USERNAME -> {
                if (input.equalsIgnoreCase("exit")) {
                    sm.changeState(new MainMenuScreen(sm, client));
                    return;
                }
                // Store username in a temporary variable
                tempUsername = input;
                currentState = LoginState.ENTER_PASSWORD;
            }
            case ENTER_PASSWORD -> {
                if (input.equalsIgnoreCase("exit")) {
                    sm.changeState(new MainMenuScreen(sm, client));
                    return;
                }
                // Store password in a temporary variable
                tempPassword = input;
                attemptLogin();
            }
        }
    }

    private void attemptLogin() {
        switch (client.getSessionManager().login(tempUsername, tempPassword)) {
            case ClientSessionManager.LoginResult.Success ignored -> {
                System.out.println("Login successful! Welcome back, " + client.getSessionManager().getUser().username() + "!");
                sm.changeState(new MainMenuScreen(sm, client));
            }
            case ClientSessionManager.LoginResult.InvalidCredentials ignored -> {
                System.out.println("Invalid username or password. Please try again.");
                resetState();
            }
            case ClientSessionManager.LoginResult.Error ignored -> {
                System.out.println("An error occurred while trying to log in. Please try again.");
                resetState();
            }
        }
    }

    private void resetState() {
        tempUsername = null;
        tempPassword = null;
        currentState = LoginState.ENTER_USERNAME;
    }

        /**
     * Handles incoming server push notifications on this screen.
     *
     * @param message the received push notification message
     */
    @Override
    public void handlePush(Message message) {
        // TODO
    }


        /**
     * Lifecycle hook called when entering this screen.
     */
    @Override
    public void onEnter() {
        logger.info("Entered LoginScreen");
    }

        /**
     * Lifecycle hook called when exiting this screen.
     */
    @Override
    public void onExit() {
        logger.info("Exited LoginScreen");
    }
}
