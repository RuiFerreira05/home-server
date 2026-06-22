package iecd.a51597.client.cli;

import iecd.a51597.client.cli.screens.Screen;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Stack;

/**
 * Manages the navigation state of the client CLI.
 */
public class StateMachine {

    private Screen currentScreen;
    private final Stack<Screen> history;
    private final ClientCliHandler cliHandler;

    private static final Logger logger = LogManager.getLogger(StateMachine.class);

    /**
     * Creates a new state machine.
     * @param cliHandler the parent CLI handler
     */
    public StateMachine(ClientCliHandler cliHandler) {
        this.cliHandler = cliHandler;
        this.history = new Stack<>();
    }

    /**
     * Transitions to a newly instantiated screen.
     * @param nextScreen the screen to transition to
     */
    public void changeState(Screen nextScreen) {
        if (nextScreen != null) {
            if (currentScreen != null) {
                currentScreen.onExit();
                history.push(currentScreen);
            }
            currentScreen = nextScreen;
            currentScreen.onEnter();
        } else {
            logger.warn("Attempted to transition to a null screen.");
        }
    }

    /**
     * Clears the history stack. Useful when transitioning to a root screen
     * (like MainMenu) where you don't want the user to be able to go "back" to Login.
     */
    public void clearHistory() {
        history.clear();
    }

    /**
     * Navigates back to the previous screen in the history stack, exiting the current screen.
     *
     * @return the previous screen, or null if history is empty
     */
    public Screen back() {
        if (history.isEmpty()) {
            return null;
        }

        if (currentScreen != null) {
            currentScreen.onExit();
        }
        currentScreen = history.pop();
        currentScreen.onEnter();
        return currentScreen;
    }

    /**
     * Gets the currently active CLI screen.
     *
     * @return the current screen
     */
    public Screen getCurrentScreen() {
        return currentScreen;
    }

    /**
     * Gets the Logger instance helper for this state machine.
     *
     * @return the logger
     */
    public Logger getLogger() {
        return logger;
    }
}