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
import java.util.ArrayList;
import java.util.List;

/**
 * Handles compiling and displaying the global player leaderboard.
 */
@WebServlet("/leaderboard")
public class LeaderboardServlet extends HttpServlet {

    private static final Logger logger = LogManager.getLogger(LeaderboardServlet.class);

    private ServerBridgeManager getBridgeManager() {
        return (ServerBridgeManager) getServletContext().getAttribute(AppContextListener.BRIDGE_MANAGER_KEY);
    }

    /**
     * Handles HTTP GET requests to fetch and display the global player leaderboard.
     * Compiles rankings based on wins and playtime.
     *
     * @param request the HttpServletRequest object
     * @param response the HttpServletResponse object
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        // Ensure a session exists so a ServerBridge can be mapped to it
        HttpSession session = request.getSession(true);
        List<UserDTO> rankedUsers = new ArrayList<>();

        try {
            ServerBridgeManager sbm = getBridgeManager();
            ServerBridge bridge = sbm.getOrCreateBridge(session);
            if (bridge != null && bridge.isConnected()) {
                // An empty search query returns all registered users on the server
                Message searchReq = MessageFactory.buildSearchRequest(
                        ClientConfiguration.PROTOCOL_VERSION,
                        ""
                );
                Message searchResp = bridge.sendRequest(searchReq);
                if (searchResp.body() instanceof MessageBody.SearchUsersResponse resp && "OK".equals(resp.status())) {
                    rankedUsers.addAll(resp.results());
                    
                    // Sort the players: wins descending, then average play time ascending
                    rankedUsers.sort((u1, u2) -> {
                        int w1 = u1.stats() != null ? u1.stats().gamesWon() : 0;
                        int w2 = u2.stats() != null ? u2.stats().gamesWon() : 0;
                        if (w1 != w2) {
                            return Integer.compare(w2, w1); // descending
                        }
                        double a1 = u1.stats() != null ? u1.stats().averagePlayTimeSecs() : 0.0;
                        double a2 = u2.stats() != null ? u2.stats().averagePlayTimeSecs() : 0.0;
                        return Double.compare(a1, a2); // ascending (faster average playtime ranks higher)
                    });
                }
            }
        } catch (Exception e) {
            logger.error("Failed to compile player leaderboard from server: {}", e.getMessage(), e);
            request.setAttribute("error", "Failed to retrieve leaderboard from server.");
        }

        request.setAttribute("leaderboard", rankedUsers);
        request.setAttribute("pageActive", "leaderboard");
        request.setAttribute("pageStyle", "leaderboard.css");

        request.getRequestDispatcher("/WEB-INF/jsp/leaderboard.jsp").forward(request, response);
    }
}
