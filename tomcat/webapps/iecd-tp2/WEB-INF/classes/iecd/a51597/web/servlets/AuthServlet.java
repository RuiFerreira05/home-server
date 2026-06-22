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
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.UUID;

/**
 * Handles HTTP requests related to user authentication: login, registration, and logout.
 */
@WebServlet("/auth/*")
public class AuthServlet extends HttpServlet {

    private static final Logger logger = LogManager.getLogger(AuthServlet.class);

    private ServerBridgeManager getBridgeManager() {
        return (ServerBridgeManager) getServletContext().getAttribute(AppContextListener.BRIDGE_MANAGER_KEY);
    }

    /**
     * Handles HTTP GET requests to retrieve authentication-related views (e.g., login or registration page).
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
        
        if ("/register".equals(path)) {
            request.getRequestDispatcher("/WEB-INF/jsp/register.jsp").forward(request, response);
        } else {
            // Default to login page
            request.getRequestDispatcher("/WEB-INF/jsp/login.jsp").forward(request, response);
        }
    }

    /**
     * Handles HTTP POST requests to perform authentication actions (e.g., logging in, registering, or logging out).
     *
     * @param request the HttpServletRequest object
     * @param response the HttpServletResponse object
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        String path = request.getPathInfo();

        if ("/login".equals(path)) {
            handleLogin(request, response);
        } else if ("/register".equals(path)) {
            handleRegister(request, response);
        } else if ("/logout".equals(path)) {
            handleLogout(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private void handleLogin(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        String username = request.getParameter("username");
        String password = request.getParameter("password");

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            request.setAttribute("error", "Username and password are required.");
            request.getRequestDispatcher("/WEB-INF/jsp/login.jsp").forward(request, response);
            return;
        }

        try {
            HttpSession session = request.getSession(true);
            ServerBridge bridge = getBridgeManager().getOrCreateBridge(session);

            // Build and send Login Request
            Message loginReq = MessageFactory.buildLoginRequest(
                    ClientConfiguration.PROTOCOL_VERSION,
                    null,
                    username,
                    password
            );

            Message loginResp = bridge.sendRequest(loginReq);
            if (loginResp.body() instanceof MessageBody.LoginResponse resp) {
                if ("OK".equals(resp.status())) {
                    // Successful authentication! Store session attributes
                    session.setAttribute("user", resp.user());
                    session.setAttribute("sessionToken", resp.session());
                    
                    logger.info("Web user '{}' logged in successfully. TCP Session ID={}", username, resp.session());
                    response.sendRedirect(request.getContextPath() + "/dashboard");
                } else {
                    request.setAttribute("error", "Authentication failed: " + resp.error().message());
                    getBridgeManager().destroyBridge(session); // close TCP socket
                    request.getRequestDispatcher("/WEB-INF/jsp/login.jsp").forward(request, response);
                }
            } else {
                throw new Exception("Unexpected protocol response payload: " + loginResp.body().getClass());
            }

        } catch (Exception e) {
            logger.error("Login process error", e);
            request.setAttribute("error", "An internal error occurred: " + e.getMessage());
            request.getRequestDispatcher("/WEB-INF/jsp/login.jsp").forward(request, response);
        }
    }

    private void handleRegister(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        String username = request.getParameter("username");
        String password = request.getParameter("password");
        String confirmPassword = request.getParameter("confirmPassword");

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            request.setAttribute("error", "Username and password are required.");
            request.getRequestDispatcher("/WEB-INF/jsp/register.jsp").forward(request, response);
            return;
        }

        if (!password.equals(confirmPassword)) {
            request.setAttribute("error", "Passwords do not match.");
            request.getRequestDispatcher("/WEB-INF/jsp/register.jsp").forward(request, response);
            return;
        }

        try {
            // Note: Registration does not require an existing session, 
            // but we must open a TCP connection to speak to the server.
            HttpSession tempSession = request.getSession(true);
            ServerBridge bridge = getBridgeManager().getOrCreateBridge(tempSession);

            Message regReq = MessageFactory.buildRegisterRequest(
                    ClientConfiguration.PROTOCOL_VERSION,
                    null,
                    username,
                    password
            );

            Message regResp = bridge.sendRequest(regReq);
            getBridgeManager().destroyBridge(tempSession); // close the temporary connection immediately
            
            if (regResp.body() instanceof MessageBody.RegisterResponse resp) {
                if ("OK".equals(resp.status())) {
                    response.sendRedirect(request.getContextPath() + "/auth/login?registered=true");
                } else {
                    request.setAttribute("error", "Registration failed: " + resp.error().message());
                    request.getRequestDispatcher("/WEB-INF/jsp/register.jsp").forward(request, response);
                }
            } else {
                throw new Exception("Unexpected protocol response payload: " + regResp.body().getClass());
            }

        } catch (Exception e) {
            logger.error("Registration process error", e);
            request.setAttribute("error", "An internal error occurred: " + e.getMessage());
            request.getRequestDispatcher("/WEB-INF/jsp/register.jsp").forward(request, response);
        }
    }

    private void handleLogout(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            ServerBridge bridge = getBridgeManager().getBridge(session);
            UUID sessionToken = (UUID) session.getAttribute("sessionToken");
            
            if (bridge != null && sessionToken != null) {
                try {
                    Message logoutReq = MessageFactory.buildLogoutRequest(
                            ClientConfiguration.PROTOCOL_VERSION,
                            null,
                            sessionToken
                    );
                    bridge.sendRequest(logoutReq, 3); // short timeout for logout
                } catch (Exception e) {
                    logger.warn("Logout request failed on TCP server: {}", e.getMessage());
                }
            }
            getBridgeManager().destroyBridge(session);
            session.invalidate();
        }
        response.sendRedirect(request.getContextPath() + "/auth/login");
    }
}
