package iecd.a51597.web.servlets;

import iecd.a51597.client.config.ClientConfiguration;
import iecd.a51597.common.protocol.Message;
import iecd.a51597.common.protocol.MessageBody;
import iecd.a51597.common.protocol.MessageFactory;
import iecd.a51597.web.AppContextListener;
import iecd.a51597.web.ServerBridge;
import iecd.a51597.web.ServerBridgeManager;
import iecd.a51597.web.ActiveGameSession;
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
 * Manages game controller interactions: starting matches, sending/canceling invites, 
 * making moves, and surrendering. Serves JSON responses for interactive AJAX queries.
 */
@WebServlet("/game/*")
public class GameServlet extends HttpServlet {

    private static final Logger logger = LogManager.getLogger(GameServlet.class);

    private ServerBridgeManager getBridgeManager() {
        return (ServerBridgeManager) getServletContext().getAttribute(AppContextListener.BRIDGE_MANAGER_KEY);
    }

    /**
     * Handles HTTP GET requests for game-related tasks. In particular, it initializes
     * or retrieves an active game session for playing a match, and forwards to the game view.
     *
     * @param request the HttpServletRequest object
     * @param response the HttpServletResponse object
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            response.sendRedirect(request.getContextPath() + "/auth/login");
            return;
        }

        String path = request.getPathInfo();
        if ("/play".equals(path)) {
            String gameIdStr = request.getParameter("gameId");
            String role = request.getParameter("role");
            String opponent = request.getParameter("opponent");

            if (gameIdStr != null && !gameIdStr.isBlank()) {
                try {
                    UUID gameId = UUID.fromString(gameIdStr);
                    
                    // Check if this game is already finished to prevent access
                    java.util.Set<UUID> finishedGames = (java.util.Set<UUID>) session.getAttribute("finishedGames");
                    if (finishedGames != null && finishedGames.contains(gameId)) {
                        logger.info("Game already finished. Redirecting gameId={} back to dashboard.", gameId);
                        response.sendRedirect(request.getContextPath() + "/dashboard?msg=This+match+is+already+finished");
                        return;
                    }

                    java.util.Map<UUID, ActiveGameSession> activeGames = (java.util.Map<UUID, ActiveGameSession>) session.getAttribute("activeGames");
                    if (activeGames == null) {
                        activeGames = new java.util.concurrent.ConcurrentHashMap<>();
                        session.setAttribute("activeGames", activeGames);
                    }

                    ActiveGameSession activeGame = activeGames.get(gameId);
                    if (activeGame == null || !activeGame.getOpponentName().equalsIgnoreCase(opponent)) {
                        activeGame = new ActiveGameSession(gameId, role, opponent);
                        activeGames.put(gameId, activeGame);
                        logger.info("Initialized ActiveGameSession in activeGames map for gameId: {}, role: {}", gameId, role);
                    }
                    session.setAttribute("activeGame", activeGame);
                } catch (Exception e) {
                    logger.error("Failed to initialize ActiveGameSession", e);
                }
            }
            
            // Forward to protected game.jsp view
            request.getRequestDispatcher("/WEB-INF/jsp/game.jsp").forward(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    /**
     * Handles HTTP POST requests for active gameplay actions, such as sending invites,
     * responding to invites, canceling invites, making moves, or surrendering.
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
            sendJsonError(response, "Not authenticated");
            return;
        }

        String path = request.getPathInfo();
        try {
            ServerBridge bridge = getBridgeManager().getOrCreateBridge(session);
            UUID sessionToken = (UUID) session.getAttribute("sessionToken");

            if ("/invite".equals(path)) {
                handleInvite(request, response, bridge, sessionToken);
            } else if ("/invite/respond".equals(path)) {
                handleInviteResponse(request, response, bridge, sessionToken);
            } else if ("/invite/cancel".equals(path)) {
                handleInviteCancel(request, response, bridge, sessionToken);
            } else if ("/move".equals(path)) {
                handleMove(request, response, bridge, sessionToken);
            } else if ("/surrender".equals(path)) {
                handleSurrender(request, response, bridge, sessionToken);
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (Exception e) {
            logger.error("Error executing game servlet action", e);
            sendJsonError(response, "Action failed: " + e.getMessage());
        }
    }

    private void handleInvite(HttpServletRequest request, HttpServletResponse response, ServerBridge bridge, UUID sessionToken) 
            throws Exception {
        String targetUserIdStr = request.getParameter("targetUserId");
        if (targetUserIdStr == null || targetUserIdStr.isBlank()) {
            sendJsonError(response, "Target user ID is required");
            return;
        }

        UUID targetUserId = UUID.fromString(targetUserIdStr);
        Message inviteReq = MessageFactory.buildSendInviteRequest(
                ClientConfiguration.PROTOCOL_VERSION,
                sessionToken,
                targetUserId
        );

        Message inviteResp = bridge.sendRequest(inviteReq);
        if (inviteResp.body() instanceof MessageBody.GameInviteResponse resp) {
            if ("OK".equals(resp.status())) {
                sendJsonSuccess(response, String.format("{\"status\":\"OK\",\"gameId\":\"%s\"}", resp.gameId()));
            } else {
                sendJsonError(response, resp.error().message());
            }
        } else {
            throw new Exception("Unexpected response: " + inviteResp.body().getClass());
        }
    }

    private void handleInviteResponse(HttpServletRequest request, HttpServletResponse response, ServerBridge bridge, UUID sessionToken) 
            throws Exception {
        String gameIdStr = request.getParameter("gameId");
        String acceptStr = request.getParameter("accept");

        if (gameIdStr == null || acceptStr == null) {
            sendJsonError(response, "Game ID and acceptance status are required");
            return;
        }

        UUID gameId = UUID.fromString(gameIdStr);
        boolean accept = Boolean.parseBoolean(acceptStr);

        Message responseReq = MessageFactory.buildAcceptInviteRequest(
                ClientConfiguration.PROTOCOL_VERSION,
                sessionToken,
                gameId,
                accept
        );

        Message responseResp = bridge.sendRequest(responseReq);
        if (responseResp.body() instanceof MessageBody.GameInviteResponseResult resp) {
            if ("OK".equals(resp.status())) {
                sendJsonSuccess(response, "{\"status\":\"OK\"}");
            } else {
                sendJsonError(response, resp.error().message());
            }
        } else {
            throw new Exception("Unexpected response: " + responseResp.body().getClass());
        }
    }

    private void handleInviteCancel(HttpServletRequest request, HttpServletResponse response, ServerBridge bridge, UUID sessionToken) 
            throws Exception {
        String gameIdStr = request.getParameter("gameId");
        if (gameIdStr == null) {
            sendJsonError(response, "Game ID is required");
            return;
        }

        UUID gameId = UUID.fromString(gameIdStr);
        Message cancelReq = MessageFactory.buildCancelInviteRequest(
                ClientConfiguration.PROTOCOL_VERSION,
                sessionToken,
                gameId
        );

        Message cancelResp = bridge.sendRequest(cancelReq);
        if (cancelResp.body() instanceof MessageBody.GameInviteCancelResponse resp) {
            if ("OK".equals(resp.status())) {
                sendJsonSuccess(response, "{\"status\":\"OK\"}");
            } else {
                sendJsonError(response, resp.error().message());
            }
        } else {
            throw new Exception("Unexpected response: " + cancelResp.body().getClass());
        }
    }

    private void handleMove(HttpServletRequest request, HttpServletResponse response, ServerBridge bridge, UUID sessionToken) 
            throws Exception {
        String gameIdStr = request.getParameter("gameId");
        String rawMove = request.getParameter("move"); // e.g. "0,0,1,0"

        if (gameIdStr == null || rawMove == null) {
            sendJsonError(response, "Game ID and move coordinates are required");
            return;
        }

        UUID gameId = UUID.fromString(gameIdStr);
        Message moveReq = MessageFactory.createMoveRequest(
                ClientConfiguration.PROTOCOL_VERSION,
                sessionToken,
                gameId,
                rawMove
        );

        Message moveResp = bridge.sendRequest(moveReq);
        if (moveResp.body() instanceof MessageBody.GameMoveResponse resp) {
            if ("OK".equals(resp.status())) {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    java.util.Map<UUID, ActiveGameSession> activeGames = (java.util.Map<UUID, ActiveGameSession>) session.getAttribute("activeGames");
                    if (activeGames != null) {
                        ActiveGameSession targetGame = activeGames.get(gameId);
                        if (targetGame != null) {
                            targetGame.applyMove(rawMove, true);
                        }
                    }
                    ActiveGameSession activeGame = (ActiveGameSession) session.getAttribute("activeGame");
                    if (activeGame != null && activeGame.getGameId().equals(gameId)) {
                        // Make sure currently active game gets updated in case it references a separate block
                        if (activeGame != activeGames.get(gameId)) {
                            activeGame.applyMove(rawMove, true);
                        }
                    }
                }
                sendJsonSuccess(response, "{\"status\":\"OK\"}");
            } else {
                sendJsonError(response, resp.error().message());
            }
        } else {
            throw new Exception("Unexpected response: " + moveResp.body().getClass());
        }
    }

    private void handleSurrender(HttpServletRequest request, HttpServletResponse response, ServerBridge bridge, UUID sessionToken) 
            throws Exception {
        String gameIdStr = request.getParameter("gameId");
        if (gameIdStr == null) {
            sendJsonError(response, "Game ID is required");
            return;
        }

        UUID gameId = UUID.fromString(gameIdStr);
        Message surrenderReq = MessageFactory.buildSurrenderRequest(
                ClientConfiguration.PROTOCOL_VERSION,
                sessionToken,
                gameId
        );

        Message surrenderResp = bridge.sendRequest(surrenderReq);
        // Note: Surrender response is a GenericResponse or similar, let's verify if there is a spec
        // From schema, surrender returns GenericResponse (OK or status details)
        if (surrenderResp.body() instanceof MessageBody.GenericResponse resp) {
            if ("OK".equals(resp.status())) {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.removeAttribute("activeGame");
                    java.util.Map<UUID, ActiveGameSession> activeGames = (java.util.Map<UUID, ActiveGameSession>) session.getAttribute("activeGames");
                    if (activeGames != null) {
                        activeGames.remove(gameId);
                    }
                    
                    java.util.Set<UUID> finishedGames = (java.util.Set<UUID>) session.getAttribute("finishedGames");
                    if (finishedGames == null) {
                        finishedGames = new java.util.HashSet<>();
                        session.setAttribute("finishedGames", finishedGames);
                    }
                    finishedGames.add(gameId);
                }
                sendJsonSuccess(response, "{\"status\":\"OK\"}");
            } else {
                sendJsonError(response, resp.error().message());
            }
        } else {
            // Support alternate mappings just in case
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.removeAttribute("activeGame");
                java.util.Map<UUID, ActiveGameSession> activeGames = (java.util.Map<UUID, ActiveGameSession>) session.getAttribute("activeGames");
                if (activeGames != null) {
                    activeGames.remove(gameId);
                }
                
                java.util.Set<UUID> finishedGames = (java.util.Set<UUID>) session.getAttribute("finishedGames");
                if (finishedGames == null) {
                    finishedGames = new java.util.HashSet<>();
                    session.setAttribute("finishedGames", finishedGames);
                }
                finishedGames.add(gameId);
            }
            sendJsonSuccess(response, "{\"status\":\"OK\"}");
        }
    }

    private void sendJsonSuccess(HttpServletResponse response, String json) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json);
    }

    private void sendJsonError(HttpServletResponse response, String message) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.getWriter().write(String.format("{\"status\":\"ERROR\",\"message\":\"%s\"}", message.replace("\"", "\\\"")));
    }
}
