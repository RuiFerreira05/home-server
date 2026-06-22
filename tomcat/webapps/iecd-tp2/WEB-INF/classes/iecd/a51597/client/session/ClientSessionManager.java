package iecd.a51597.client.session;

import iecd.a51597.client.config.ClientConfiguration;
import iecd.a51597.client.network.ServerConnection;
import iecd.a51597.common.protocol.Message;
import iecd.a51597.common.protocol.MessageBody;
import iecd.a51597.common.protocol.MessageFactory;
import iecd.a51597.common.protocol.types.ErrorCodeType;
import iecd.a51597.common.store.UserDTO;

import java.io.*;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Manages the client-side session state and authentication workflows.
 */
public class ClientSessionManager {

    private final ServerConnection serverConnection;
    private UUID sessionUUID;
    private UserDTO user;

    /**
     * Constructs a ClientSessionManager to coordinate authentication state.
     *
     * @param serverConnection the TCP network socket connection bridge
     */
    public ClientSessionManager(ServerConnection serverConnection) {
        this.serverConnection = serverConnection;
        this.sessionUUID = null;
        this.user = null;
    }

    /**
     * Sealed interface representing outcomes of profile editing operations.
     */
    public sealed interface EditProfileResult {
        record Success() implements EditProfileResult {}
        record UsernameTaken() implements EditProfileResult {}
        record PhotoNotFoundError() implements EditProfileResult {}
        record Error(String message) implements EditProfileResult {}
    }

    /**
     * Sends a profile update request to the server, updating the current user details.
     *
     * @param username the new username string
     * @param password the new password string
     * @param photopath local path to the new profile photo file
     * @param nationality 2-letter ISO country code nationality value
     * @param dob date of birth
     * @return the result profile edit code status
     */
    public EditProfileResult editProfile(String username, String password, String photopath, String nationality, LocalDate dob) {
        byte[] photoBytes = null;
        try {
            photoBytes = photoToBytes(photopath);
        } catch (FileNotFoundException e) {
            return new EditProfileResult.PhotoNotFoundError();
        } catch (IOException e) {
            return new EditProfileResult.Error("Error reading photo file: " + e.getMessage());
        }

        Message request = MessageFactory.buildUpdateProfileRequest(
                ClientConfiguration.PROTOCOL_VERSION,
                sessionUUID,
                username,
                password,
                photoBytes,
                nationality,
                dob
        );

        Message response = null;
        try {
            response = serverConnection.sendRequest(request).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            return new EditProfileResult.Error(e.getMessage());
        }

        if (response.body() instanceof MessageBody.UpdateProfileResponse(String status, UserDTO updatedUser, MessageBody.ErrorDetail error)) {
            if (status.equals("OK")) {
                this.user = updatedUser;
                return new EditProfileResult.Success();
            } else {
                if (error.code() == ErrorCodeType.USERNAME_TAKEN) {
                    return new EditProfileResult.UsernameTaken();
                }
                return new EditProfileResult.Error("Profile update failed: " + error.message());
            }
        } else {
            return new EditProfileResult.Error("Unexpected response type: " + response.body().getClass());
        }
    }

    private byte[] photoToBytes(String photoPath) throws FileNotFoundException, IOException {
        if (photoPath == null) {
            return null;
        }

        File photoFile = new File(photoPath);
        if (!photoFile.exists() || !photoFile.isFile()) {
            throw new FileNotFoundException("Photo file not found: " + photoPath);
        }

        try (FileInputStream fis = new FileInputStream(photoFile)){
            return fis.readAllBytes();
        }
    }

    /**
     * Sealed interface representing outcomes of login requests.
     */
    public sealed interface LoginResult {
        record Success(UUID sessionToken) implements LoginResult {}
        record InvalidCredentials() implements LoginResult {}
        record Error(String message) implements LoginResult {}
    }

    /**
     * Logs in a user by sending a credential verification request to the server.
     *
     * @param username the username credential string
     * @param password the raw password credential string
     * @return the login result outcome containing the session token on success
     */
    public LoginResult login(String username, String password) {
        Message request = MessageFactory.buildLoginRequest(
                ClientConfiguration.PROTOCOL_VERSION,
                null,
                username,
                password
        );

        Message response = null;
        try {
            response = serverConnection.sendRequest(request).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            return new LoginResult.Error(e.getMessage());
        }

        if (response.body() instanceof MessageBody.LoginResponse(
                String status, UUID session, UserDTO userDTO, MessageBody.ErrorDetail error
        )) {
            if (status.equals("OK")) {
                this.user = userDTO;
                this.sessionUUID = session;
                return new LoginResult.Success(session);
            } else {
                if (error.code() == ErrorCodeType.AUTH_FAILED) {
                    return new LoginResult.InvalidCredentials();
                }
            }
        } else {
            return new LoginResult.Error("Unexpected response type: " + response.body().getClass());
        }

        return new LoginResult.Error("Something went wrong");
    }

    /**
     * Retrieves the current authenticated user DTO.
     *
     * @return the logged-in user profile DTO, or null if unauthenticated
     */
    public UserDTO getUser() {
        return user;
    }

    /**
     * Sealed interface representing outcomes of logout requests.
     */
    public sealed interface LogoutResult {
        record Success() implements LogoutResult {}
        record NotLoggedIn() implements LogoutResult {}
        record Error(String message) implements LogoutResult {}
    }

    /**
     * Logs out the current user, clearing the local session UUID and user model.
     *
     * @return the logout result status
     */
    public LogoutResult logout() {
        if (sessionUUID == null) {
            return new LogoutResult.NotLoggedIn();
        }

        Message request = MessageFactory.buildLogoutRequest(
                ClientConfiguration.PROTOCOL_VERSION,
                null,
                sessionUUID
                );

        Message response = null;
        try {
            response = serverConnection.sendRequest(request).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            return new LogoutResult.Error(e.getMessage());
        }

        if (response.body() instanceof MessageBody.LogoutResponse(String status, MessageBody.ErrorDetail error)) {
            if (status.equals("OK")) {
                this.sessionUUID = null;
                this.user = null;
                return new LogoutResult.Success();
            }
            return new LogoutResult.Error("Logout failed " + error.message());
        } else {
            return new LogoutResult.Error("Unexpected response type: " + response.body().getClass());
        }
    }

    /**
     * Sealed interface representing outcomes of registration requests.
     */
    public sealed interface RegisterResult {
        record Success() implements RegisterResult {}
        record UsernameTaken() implements RegisterResult {}
        record Error(String message) implements RegisterResult {}
    }

    /**
     * Registers a new user account on the server.
     *
     * @param username the username string
     * @param password the password string
     * @return the registration result status
     */
    public RegisterResult register(String username, String password) {
        Message request = MessageFactory.buildRegisterRequest(
                ClientConfiguration.PROTOCOL_VERSION,
                null,
                username,
                password
        );

        Message response = null;
        try {
            response = serverConnection.sendRequest(request).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            return new RegisterResult.Error(e.getMessage());
        }

        if (response.body() instanceof MessageBody.RegisterResponse(String status, MessageBody.ErrorDetail error)) {
            if (status.equals("OK")) {
                return new RegisterResult.Success();
            } else {
                if (error.code() == ErrorCodeType.USERNAME_TAKEN) {
                    return new RegisterResult.UsernameTaken();
                } else {
                    return new RegisterResult.Error("Registration failed: " + error.message());
                }
            }
        } else {
            return new RegisterResult.Error("Unexpected response type: " + response.body().getClass());
        }
    }

    /**
     * Checks if a user is currently logged in.
     *
     * @return true if logged in, false otherwise
     */
    public boolean isLoggedIn() {
        return sessionUUID != null;
    }

    /**
     * Updates the cached user profile DTO.
     *
     * @param user the new user details DTO
     */
    public void updateUser(UserDTO user) {
        this.user = user;
    }

    /**
     * Gets the unique UUID token representing the current active server session.
     *
     * @return the session UUID
     */
    public UUID getSessionUUID() {
        return sessionUUID;
    }
}
