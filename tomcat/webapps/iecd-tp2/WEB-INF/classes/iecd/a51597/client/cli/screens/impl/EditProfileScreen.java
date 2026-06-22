package iecd.a51597.client.cli.screens.impl;

import iecd.a51597.client.Client;
import iecd.a51597.client.cli.StateMachine;
import iecd.a51597.client.cli.screens.Screen;
import iecd.a51597.client.session.ClientSessionManager;
import iecd.a51597.common.protocol.Message;

import java.time.LocalDate;

/**
 * Screen for editing user profile information.
 */
public class EditProfileScreen extends Screen {

    public enum State {
        USERNAME,
        PASSWORD,
        PHOTO,
        NATIONALITY,
        DOB
    }

    private State currentState = State.USERNAME;
    private String tempUsername;
    private String tempPassword;
    private String tempPhoto;
    private String tempNationality;
    private LocalDate tempDob;

        /**
     * Constructs a new EditProfileScreen.
     *
     * @param sm the navigation state machine
     * @param client the bootstrap client context
     */
    public EditProfileScreen(StateMachine sm, Client client) {
        super(sm, client);
    }

        /**
     * Renders the screen visual interface and content.
     */
    @Override
    public void display() {
        if (currentState == State.USERNAME) {
            System.out.println("========================================");
            System.out.println("              EDIT PROFILE              ");
            System.out.println("========================================");
            System.out.println("Leave empty and press enter to keep current value.");
        }
        switch (currentState) {
            case USERNAME -> this.prompt = "New username: ";
            case PASSWORD -> this.prompt = "New password: ";
            case PHOTO -> this.prompt = "New photo URL: ";
            case NATIONALITY -> this.prompt = "New nationality: ";
            case DOB -> this.prompt = "New date of birth (YYYY-MM-DD): ";
        }
    }

        /**
     * Processes user console command input on this screen.
     *
     * @param input the raw string input from the user
     */
    @Override
    public void handleInput(String input) {
        switch (currentState) {
            case USERNAME -> {
                if (!input.isEmpty()) {
                    tempUsername = input;
                }
                currentState = State.PASSWORD;
            }
            case PASSWORD -> {
                if (!input.isEmpty()) {
                    tempPassword = input;
                }
                currentState = State.PHOTO;
            }
            case PHOTO -> {
                if (!input.isEmpty()) {
                    tempPhoto = input;
                }
                currentState = State.NATIONALITY;
            }
            case NATIONALITY -> {
                if (!input.isEmpty()) {
                    tempNationality = input;
                }
                currentState = State.DOB;
            }
            case DOB -> {
                if (!input.isEmpty()) {
                    try {
                        tempDob = LocalDate.parse(input);
                        if (tempDob.isAfter(LocalDate.now())) {
                            System.out.println("Date of birth cannot be in the future.");
                            tempDob = null;
                            return; // Stay on the DOB state to allow re-entry
                        }
                    } catch (Exception e) {
                        System.out.println("Invalid date format. Please use YYYY-MM-DD.");
                        return; // Stay on the DOB state to allow re-entry
                    }
                }
                editProfile();
            }
        }
    }

    private void editProfile() {
        switch (client.getSessionManager().editProfile(tempUsername, tempPassword, tempPhoto, tempNationality, tempDob)) {
            case ClientSessionManager.EditProfileResult.Success ignored -> {
                System.out.println("Profile updated successfully!");
                sm.changeState(new ViewProfileScreen(sm, client));
            }
            case ClientSessionManager.EditProfileResult.UsernameTaken ignored -> {
                System.out.println("Username is already taken. Please choose a different username.");
            }
            case ClientSessionManager.EditProfileResult.PhotoNotFoundError ignored -> {
                System.out.println("Photo file not found. Please check the provided path and try again.");
            }
            case ClientSessionManager.EditProfileResult.Error message -> {
                System.out.println("Failed to update profile: " + message);
                sm.changeState(new ViewProfileScreen(sm, client));
            }
        }
        resetState();
    }

        /**
     * Handles incoming server push notifications on this screen.
     *
     * @param message the received push notification message
     */
    @Override
    public void handlePush(Message message) {

    }

    private void resetState() {
        currentState = State.USERNAME;
        tempUsername = null;
        tempPassword = null;
        tempPhoto = null;
        tempNationality = null;
        tempDob = null;
    }

        /**
     * Lifecycle hook called when entering this screen.
     */
    @Override
    public void onEnter() {
        resetState();
    }

        /**
     * Lifecycle hook called when exiting this screen.
     */
    @Override
    public void onExit() {

    }
}
