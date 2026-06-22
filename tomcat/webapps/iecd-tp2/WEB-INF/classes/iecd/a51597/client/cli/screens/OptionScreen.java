package iecd.a51597.client.cli.screens;

import iecd.a51597.client.Client;
import iecd.a51597.client.cli.StateMachine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A screen that displays a list of numbered options to the user.
 */
public abstract class OptionScreen extends Screen {

    /**
     * List of menu options available on this screen.
     */
    protected List<ScreenOption> options;

    /**
     * Creates a new option screen.
     * @param sm the state machine
     * @param client the client instance
     */
    protected OptionScreen(StateMachine sm, Client client) {
        super(sm, client);
        this.options = new ArrayList<>();
    }

    /**
     * Removes all current options from the screen.
     */
    protected void clearOptions() {
        this.options.clear();
    }

    /**
     * Displays the screen header and the list of numbered menu options.
     */
    @Override
    public void display() {
        System.out.println("=== " + this.getClass().getSimpleName() + " ===");
        displayOptions();
    };

    /**
     * Prints the list of visible options.
     */
    public void displayOptions() {
        List<ScreenOption> visibleOptions = getVisibleOptions();
        int counter = 1;
        for (ScreenOption option : visibleOptions) {
            System.out.println(counter + ". " + option);
            counter++;
        }
    }

    /**
     * Parses the CLI input to match and execute one of the visible screen options.
     *
     * @param input the raw string input from user
     */
    @Override
    public void handleInput(String input) {
        try {
            int option = Integer.parseInt(input);
            List<ScreenOption> visibleOptions = getVisibleOptions();
            if (option < 1 || option > visibleOptions.size()) {
                System.out.println("Invalid option. Please enter a number between 1 and " + visibleOptions.size() + ".");
            } else {
                visibleOptions.get(option - 1).execute();
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid input. Please enter a number.");
        }
    }

    /**
     * Adds a conditional option to the screen.
     * @param description label of the option
     * @param action behavior when selected
     * @param condition visibility condition
     */
    protected void addOption(String description, Runnable action, BooleanSupplier condition) {
        options.add(new ScreenOption(description, action, condition));
    }

    /**
     * Adds an always-visible option to the screen.
     * @param description label of the option
     * @param action behavior when selected
     */
    protected void addOption(String description, Runnable action) {
        addOption(description, action, () -> true);
    }

    private List<ScreenOption> getVisibleOptions() {
        return options.stream()
                .filter(ScreenOption::isVisible)
                .toList();
    }

    /**
     * Represents a single menu option.
     */
    protected record ScreenOption(String description, Runnable action, BooleanSupplier condition) {
        private boolean isVisible() {
            return condition.getAsBoolean();
        }

        /**
         * Returns the text description of the screen option.
         *
         * @return the option description
         */
        @Override
        public String toString() {
            return description;
        }

        /**
         * Runs the action associated with this option.
         */
        public void execute() {
            action.run();
        }
    }
}
