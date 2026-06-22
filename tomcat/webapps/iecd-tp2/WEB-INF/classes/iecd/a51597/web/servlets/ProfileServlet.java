package iecd.a51597.web.servlets;

import iecd.a51597.client.config.ClientConfiguration;
import iecd.a51597.common.protocol.Message;
import iecd.a51597.common.protocol.MessageBody;
import iecd.a51597.common.protocol.MessageFactory;
import iecd.a51597.common.store.UserDTO;
import iecd.a51597.web.AppContextListener;
import iecd.a51597.web.ServerBridge;
import iecd.a51597.web.ServerBridgeManager;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Handles profile management: viewing details and updating info (including photo file upload).
 */
@WebServlet("/profile/*")
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024,      // 1 MB buffer
    maxFileSize = 1024 * 1024 * 5,       // 5 MB max file size
    maxRequestSize = 1024 * 1024 * 10    // 10 MB max request size
)
public class ProfileServlet extends HttpServlet {

    private static final Logger logger = LogManager.getLogger(ProfileServlet.class);

    private ServerBridgeManager getBridgeManager() {
        return (ServerBridgeManager) getServletContext().getAttribute(AppContextListener.BRIDGE_MANAGER_KEY);
    }

    /**
     * Handles HTTP GET requests to display profile details (view mode or edit mode).
     *
     * @param request the HttpServletRequest object
     * @param response the HttpServletResponse object
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        String path = request.getPathInfo();
        String targetUsername = request.getParameter("username");

        if ("/edit".equals(path)) {
            // Edit profile requires authentication
            HttpSession session = request.getSession(false);
            if (session == null || session.getAttribute("user") == null) {
                response.sendRedirect(request.getContextPath() + "/auth/login");
                return;
            }
            UserDTO user = (UserDTO) session.getAttribute("user");
            request.setAttribute("user", user);
            request.getRequestDispatcher("/WEB-INF/jsp/edit-profile.jsp").forward(request, response);
            return;
        }

        // View profile details: can be unauthenticated if targetUsername is provided
        UserDTO user = null;
        if (targetUsername != null && !targetUsername.trim().isEmpty()) {
            // Query details for another player
            try {
                HttpSession session = request.getSession(true);
                ServerBridge bridge = getBridgeManager().getOrCreateBridge(session);
                if (bridge != null && bridge.isConnected()) {
                    Message searchReq = MessageFactory.buildSearchRequest(
                            ClientConfiguration.PROTOCOL_VERSION,
                            targetUsername.trim()
                    );
                    Message searchResp = bridge.sendRequest(searchReq);
                    if (searchResp.body() instanceof MessageBody.SearchUsersResponse resp && "OK".equals(resp.status())) {
                        for (UserDTO match : resp.results()) {
                            if (match.username().equalsIgnoreCase(targetUsername.trim())) {
                                user = match;
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to query target profile username: " + targetUsername, e);
            }
            
            if (user == null) {
                request.setAttribute("error", "Player '" + targetUsername + "' was not found on this server.");
            }
        } else {
            // Viewing own profile (requires authentication)
            HttpSession session = request.getSession(false);
            if (session == null || session.getAttribute("user") == null) {
                response.sendRedirect(request.getContextPath() + "/auth/login");
                return;
            }
            user = (UserDTO) session.getAttribute("user");

            // Refresh player stats from the TCP server if available
            try {
                ServerBridgeManager sbm = getBridgeManager();
                ServerBridge bridge = sbm.getBridge(session);
                if (bridge != null && bridge.isConnected()) {
                    Message searchReq = MessageFactory.buildSearchRequest(
                            ClientConfiguration.PROTOCOL_VERSION,
                            user.username()
                    );
                    Message searchResp = bridge.sendRequest(searchReq);
                    if (searchResp.body() instanceof MessageBody.SearchUsersResponse resp && "OK".equals(resp.status())) {
                        for (UserDTO match : resp.results()) {
                            if (match.userId().equals(user.userId())) {
                                session.setAttribute("user", match);
                                user = match;
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to refresh user profile from TCP server: {}", e.getMessage());
            }
        }

        request.setAttribute("user", user);
        // View profile details
        request.getRequestDispatcher("/WEB-INF/jsp/profile.jsp").forward(request, response);
    }

    /**
     * Handles HTTP POST requests to process profile updates, supporting multipart profile photo uploads.
     *
     * @param request the HttpServletRequest object
     * @param response the HttpServletResponse object
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            response.sendRedirect(request.getContextPath() + "/auth/login");
            return;
        }

        String path = request.getPathInfo();
        if (!"/edit".equals(path)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // Handle profile update form
        String username = request.getParameter("username");
        String password = request.getParameter("password");
        String nationality = request.getParameter("nationality");
        String dobStr = request.getParameter("dob");
        String favoriteColor = request.getParameter("favoriteColor");
        
        UserDTO currentUser = (UserDTO) session.getAttribute("user");
        
        // If the username has not changed, pass null to the server so it skips updating it.
        // This avoids triggering the server's buggy uniqueness check when re-submitting the same name.
        String usernameToUpdate = null;
        if (username != null && !username.trim().isEmpty() && !username.trim().equals(currentUser.username())) {
            usernameToUpdate = username.trim();
        }
        
        LocalDate dob = null;
        if (dobStr != null && !dobStr.isBlank()) {
            try {
                dob = LocalDate.parse(dobStr);
            } catch (Exception e) {
                request.setAttribute("error", "Invalid date format. Use YYYY-MM-DD.");
                request.getRequestDispatcher("/WEB-INF/jsp/edit-profile.jsp").forward(request, response);
                return;
            }
        }

        byte[] photoBytes = null;
        Part filePart = request.getPart("photo");
        if (filePart != null && filePart.getSize() > 0) {
            try (InputStream is = filePart.getInputStream()) {
                photoBytes = is.readAllBytes();
                logger.info("Uploaded profile photo size: {} bytes", photoBytes.length);
            }
        }

        try {
            ServerBridge bridge = getBridgeManager().getOrCreateBridge(session);
            UUID sessionToken = (UUID) session.getAttribute("sessionToken");

            Message updateReq = MessageFactory.buildUpdateProfileRequest(
                    ClientConfiguration.PROTOCOL_VERSION,
                    sessionToken,
                    usernameToUpdate,
                    password,
                    photoBytes,
                    nationality,
                    dob,
                    favoriteColor
            );

            Message updateResp = bridge.sendRequest(updateReq);
            if (updateResp.body() instanceof MessageBody.UpdateProfileResponse resp) {
                if ("OK".equals(resp.status())) {
                    // Update user in session
                    session.setAttribute("user", resp.user());
                    logger.info("Profile updated successfully for user ID: {}", resp.user().userId());
                    response.sendRedirect(request.getContextPath() + "/profile?msg=Profile+updated+successfully");
                } else {
                    request.setAttribute("error", "Update failed: " + resp.error().message());
                    request.getRequestDispatcher("/WEB-INF/jsp/edit-profile.jsp").forward(request, response);
                }
            } else {
                throw new Exception("Unexpected response type: " + updateResp.body().getClass());
            }

        } catch (Exception e) {
            logger.error("Error processing profile edit request", e);
            request.setAttribute("error", "Internal error updating profile: " + e.getMessage());
            request.getRequestDispatcher("/WEB-INF/jsp/edit-profile.jsp").forward(request, response);
        }
    }
}
