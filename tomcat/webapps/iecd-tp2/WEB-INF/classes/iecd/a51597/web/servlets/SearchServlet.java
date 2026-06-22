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
 * Handles asynchronous search autocomplete request queries from the dashboard.
 * Contacts the TCP server and returns a manual lightweight JSON serialization of matching UserDTOs.
 */
@WebServlet("/api/search")
public class SearchServlet extends HttpServlet {

    private static final Logger logger = LogManager.getLogger(SearchServlet.class);

    private ServerBridgeManager getBridgeManager() {
        return (ServerBridgeManager) getServletContext().getAttribute(AppContextListener.BRIDGE_MANAGER_KEY);
    }

    /**
     * Handles HTTP GET requests for user search. Performs autocomplete search requests
     * via the TCP server and returns a lightweight JSON array of matched users.
     *
     * @param request the HttpServletRequest object
     * @param response the HttpServletResponse object
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Ensure a session exists so a ServerBridge can be mapped to it
        HttpSession session = request.getSession(true);

        String query = request.getParameter("q");
        if (query == null || query.trim().isEmpty()) {
            response.getWriter().write("[]");
            return;
        }

        try {
            ServerBridge bridge = getBridgeManager().getOrCreateBridge(session);
            
            // Build and send user search query request
            Message searchReq = MessageFactory.buildSearchRequest(
                    ClientConfiguration.PROTOCOL_VERSION,
                    query.trim()
            );

            Message searchResp = bridge.sendRequest(searchReq);
            if (searchResp.body() instanceof MessageBody.SearchUsersResponse resp) {
                if ("OK".equals(resp.status())) {
                    List<UserDTO> results = resp.results();
                    UserDTO currentUser = (UserDTO) session.getAttribute("user");
                    if (currentUser != null) {
                        List<UserDTO> filtered = new ArrayList<>();
                        for (UserDTO u : results) {
                            if (!u.userId().equals(currentUser.userId())) {
                                filtered.add(u);
                            }
                        }
                        results = filtered;
                    }
                    String json = serializeUserListToJson(results);
                    response.getWriter().write(json);
                } else {
                    logger.warn("User search request returned non-OK status: {}", resp.error().message());
                    response.getWriter().write("[]");
                }
            } else {
                throw new Exception("Unexpected response: " + searchResp.body().getClass());
            }

        } catch (Exception e) {
            logger.error("User search process failed", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("[]");
        }
    }

    /**
     * Serializes a List of UserDTO objects into a lightweight JSON array string manually.
     */
    private String serializeUserListToJson(List<UserDTO> users) {
        if (users == null || users.isEmpty()) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < users.size(); i++) {
            UserDTO user = users.get(i);
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{");
            sb.append("\"userId\":\"").append(user.userId().toString()).append("\",");
            sb.append("\"username\":\"").append(escapeJson(user.username())).append("\",");
            sb.append("\"photo\":\"").append(user.photo() != null ? escapeJson(user.photo()) : "").append("\",");
            sb.append("\"nationality\":\"").append(user.nationality() != null ? escapeJson(user.nationality()) : "").append("\",");
            sb.append("\"dob\":\"").append(user.dob() != null ? user.dob().toString() : "").append("\",");
            sb.append("\"online\":").append(user.online());
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\b", "\\b")
                  .replace("\f", "\\f")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
