package iecd.a51597.client.cli.screens.impl;

import iecd.a51597.client.Client;
import iecd.a51597.client.cli.StateMachine;
import iecd.a51597.client.cli.screens.Screen;
import iecd.a51597.client.session.ClientSessionManager;
import iecd.a51597.common.protocol.Message;

/**
 * Screen for new user registration.
 */
public class RegisterScreen extends Screen {

    private enum RegisterState {
        ENTER_USERNAME,
        ENTER_PASSWORD
    }

    private RegisterState currentState = RegisterState.ENTER_USERNAME;
    private String tempUsername;
    private String tempPassword;

        /**
     * Constructs a new RegisterScreen.
     *
     * @param sm the navigation state machine
     * @param client the bootstrap client context
     */
    public RegisterScreen(StateMachine sm, Client client) {
        super(sm, client);
    }

        /**
     * Renders the screen visual interface and content.
     */
    @Override
    public void display() {
        if (currentState == RegisterState.ENTER_USERNAME) {
            System.out.println("========================================");
            System.out.println("                REGISTER                ");
            System.out.println("========================================");
            System.out.println("Type 'exit' to go back to the main menu.");
            this.prompt = "Username: ";
        } else if (currentState == RegisterState.ENTER_PASSWORD) {
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
                currentState = RegisterState.ENTER_PASSWORD;
            }
            case ENTER_PASSWORD -> {
                if (input.equalsIgnoreCase("exit")) {
                    sm.changeState(new MainMenuScreen(sm, client));
                    return;
                }
                // Store password in a temporary variable
                tempPassword = input;
                attemptRegister();
            }
        }
    }

    private void attemptRegister() {
        switch (client.getSessionManager().register(tempUsername, tempPassword)) {
            case ClientSessionManager.RegisterResult.Success ignored -> {
                if (client.getSessionManager().login(tempUsername, tempPassword) instanceof ClientSessionManager.LoginResult.Success) {
                    System.out.println("Registration successful! Welcome, " + client.getSessionManager().getUser().username() + "!");
                    sm.changeState(new MainMenuScreen(sm, client));
                    resetState();
                } else {
                    System.out.println("Registration succeeded but login failed. Please try logging in from the main menu.");
                    sm.changeState(new MainMenuScreen(sm, client));
                    resetState();
                }
            }
            case ClientSessionManager.RegisterResult.UsernameTaken ignored -> {
                System.out.println("That username is already taken. Please try a different one.");
                resetState();
            }
            case ClientSessionManager.RegisterResult.Error ignored -> {
                System.out.println("An error occurred during registration. Please try again.");
                resetState();
            }
        }
    }

    private void resetState() {
        tempUsername = null;
        tempPassword = null;
        currentState = RegisterState.ENTER_USERNAME;
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
        logger.info("Entered RegisterScreen");
    }

        /**
     * Lifecycle hook called when exiting this screen.
     */
    @Override
    public void onExit() {
        logger.info("Exited RegisterScreen");
    }
}
