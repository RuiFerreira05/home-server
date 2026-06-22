package iecd.a51597.web.servlets;

import iecd.a51597.common.store.UserDTO;
import iecd.a51597.web.ServerBridge;
import iecd.a51597.web.ServerBridgeManager;
import iecd.a51597.web.AppContextListener;
import iecd.a51597.web.ActiveGameSession;
import iecd.a51597.common.protocol.Message;
import iecd.a51597.common.protocol.MessageBody;
import iecd.a51597.common.protocol.MessageFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Handles rendering the main user landing page (Dashboard).
 */
@WebServlet("/dashboard")
public class DashboardServlet extends HttpServlet {

    private static final Logger logger = LogManager.getLogger(DashboardServlet.class);

    private ServerBridgeManager getBridgeManager() {
        return (ServerBridgeManager) getServletContext().getAttribute(AppContextListener.BRIDGE_MANAGER_KEY);
    }

    /**
     * Handles HTTP GET requests to render the dashboard. Fetches active rankings,
     * updates user stats, and checks for any active game sessions to display.
     *
     * @param request the HttpServletRequest object
     * @param response the HttpServletResponse object
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        // Ensure session exists so a ServerBridge can be mapped to it
        HttpSession session = request.getSession(true);
        UserDTO user = (UserDTO) session.getAttribute("user");

        // 1. If user is logged in, attempt to refresh their profile statistics
        if (user != null) {
            try {
                ServerBridgeManager sbm = getBridgeManager();
                ServerBridge bridge = sbm.getBridge(session);
                if (bridge != null && bridge.isConnected()) {
                    Message searchReq = MessageFactory.buildSearchRequest(
                            iecd.a51597.client.config.ClientConfiguration.PROTOCOL_VERSION,
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
            request.setAttribute("user", user);
        }

        // 2. Fetch active leaderboard rankings list to render directly on the dashboard
        java.util.List<UserDTO> rankedUsers = new java.util.ArrayList<>();
        try {
            ServerBridgeManager sbm = getBridgeManager();
            ServerBridge bridge = sbm.getOrCreateBridge(session);
            if (bridge != null && bridge.isConnected()) {
                Message searchReq = MessageFactory.buildSearchRequest(
                        iecd.a51597.client.config.ClientConfiguration.PROTOCOL_VERSION,
                        ""
                );
                Message searchResp = bridge.sendRequest(searchReq);
                if (searchResp.body() instanceof MessageBody.SearchUsersResponse resp && "OK".equals(resp.status())) {
                    rankedUsers.addAll(resp.results());
                    
                    // Sort the players: wins descending, then total play time ascending
                    rankedUsers.sort((u1, u2) -> {
                        int w1 = u1.stats() != null ? u1.stats().gamesWon() : 0;
                        int w2 = u2.stats() != null ? u2.stats().gamesWon() : 0;
                        if (w1 != w2) {
                            return Integer.compare(w2, w1); // descending
                        }
                        double t1 = u1.stats() != null ? u1.stats().totalPlayTimeSecs() : 0.0;
                        double t2 = u2.stats() != null ? u2.stats().totalPlayTimeSecs() : 0.0;
                        return Double.compare(t1, t2); // ascending
                    });
                }
            }
        } catch (Exception e) {
            logger.error("Failed to compile player rankings for dashboard: {}", e.getMessage());
        }
        request.setAttribute("leaderboard", rankedUsers);

        // Pass activeGame session attribute to request scope
        ActiveGameSession activeGame = (ActiveGameSession) session.getAttribute("activeGame");
        if (activeGame != null) {
            request.setAttribute("activeGame", activeGame);
            logger.info("Found activeGame in session, exposing to dashboard JSP: gameId={}", activeGame.getGameId());
        }

        // 3. Pass optional query parameters (like notification messages) to the JSP
        String msg = request.getParameter("msg");
        if (msg != null && !msg.isBlank()) {
            request.setAttribute("msg", msg);
        }

        request.getRequestDispatcher("/WEB-INF/jsp/dashboard.jsp").forward(request, response);
    }
}
