package iecd.a51597.client.cli.screens;

import iecd.a51597.client.Client;
import iecd.a51597.client.cli.StateMachine;
import iecd.a51597.client.config.ClientConfiguration;
import iecd.a51597.common.protocol.Message;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Base class for all CLI screens.
 */
public abstract class Screen {

    /**
     * State machine managing the active CLI screens.
     */
    protected StateMachine sm;

    /**
     * Singleton client instance representing local state.
     */
    protected Client client;

    /**
     * The input prompt string shown to the user on this screen.
     */
    public String prompt;

    /**
     * Logger configured for subclass logging context.
     */
    protected Logger logger = LogManager.getLogger(this.getClass());

    /**
     * Creates a new screen.
     * @param sm the state machine
     * @param client the client instance
     */
    protected Screen(StateMachine sm, Client client){
        this.sm = sm;
        this.client = client;
        this.prompt = ClientConfiguration.DEFAULT_PROMPT;
    }

    /**
     * Renders the screen content to the console.
     */
    abstract public void display();

    /**
     * Processes user input.
     * @param input the raw input string
     */
    abstract public void handleInput(String input);

    /**
     * Handles server push notifications while on this screen.
     * @param message the received message
     */
    abstract public void handlePush(Message message);

    /**
     * Called when the screen becomes the active screen.
     */
    abstract public void onEnter();

    /**
     * Called when the screen is being left.
     */
    abstract public void onExit();
}
