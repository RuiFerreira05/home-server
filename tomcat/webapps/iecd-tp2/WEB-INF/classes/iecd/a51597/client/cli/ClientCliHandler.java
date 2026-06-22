package iecd.a51597.client.cli;

import iecd.a51597.client.Client;
import iecd.a51597.client.cli.screens.impl.MainMenuScreen;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Main handler for the client-side Command Line Interface.
 */
public class ClientCliHandler {

    private final Client client;

    /**
     * Flag indicating whether the CLI loop is actively running.
     */
    public volatile Boolean running;
    private final StateMachine stateMachine;

    private static final Logger logger = LogManager.getLogger(ClientCliHandler.class);

    /**
     * Creates a new CLI handler.
     * @param client the client instance
     */
    public ClientCliHandler(Client client) {
        this.client = client;
        this.stateMachine = new StateMachine(this);
    }

    /**
     * Starts the main CLI input loop.
     */
    public void loop() {
        running = true;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {

            // Start the application by injecting the first screen
            stateMachine.changeState(new MainMenuScreen(stateMachine, client));

            while (running) {
                System.out.println();
                stateMachine.getCurrentScreen().display();
                System.out.print("\n" + stateMachine.getCurrentScreen().prompt);

                String input = reader.readLine();
                if (input == null) {
                    break;
                }

                input = input.trim();

                stateMachine.getCurrentScreen().handleInput(input);
            }
        } catch (IOException e) {
            if (running) {
                logger.error("CLI read error", e);
            }
        }
    }

    /**
     * Gets the StateMachine driving the CLI screen transitions.
     *
     * @return the state machine
     */
    public StateMachine getStateMachine() {
        return stateMachine;
    }
}