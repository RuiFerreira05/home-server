package iecd.a51597.web.servlets;

import iecd.a51597.common.protocol.Message;
import iecd.a51597.common.protocol.MessageBody;
import iecd.a51597.web.AppContextListener;
import iecd.a51597.web.ServerBridge;
import iecd.a51597.web.ServerBridgeManager;
import iecd.a51597.web.ActiveGameSession;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * AJAX endpoint for the client-side background thread to poll for asynchronous server push events.
 * Translates Java record push events into simple JSON packets.
 */
@WebServlet("/api/push")
public class PushServlet extends HttpServlet {

    private static final Logger logger = LogManager.getLogger(PushServlet.class);

    private ServerBridgeManager getBridgeManager() {
        return (ServerBridgeManager) getServletContext().getAttribute(AppContextListener.BRIDGE_MANAGER_KEY);
    }

    /**
     * Handles HTTP GET requests to poll for asynchronous server push events.
     * Returns a JSON array representing the pending push messages drained from the connection.
     *
     * @param request the HttpServletRequest object
     * @param response the HttpServletResponse object
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("[]");
            return;
        }

        ServerBridge bridge = getBridgeManager().getBridge(session);
        if (bridge == null) {
            response.getWriter().write("[]");
            return;
        }

        // Drain the in-memory push message queue
        List<Message> pushMessages = bridge.pollPushMessages();
        
        StringBuilder sb = new StringBuilder();
        sb.append("[");

        for (Message msg : pushMessages) {
            // Intercept match completion pushes and update the HTTP session's UserDTO stats
            // so that dashboards and profiles display updated scores immediately upon page transitions.
            if (msg.body() instanceof MessageBody.GameMove(UUID gameId, String rawMove)) {
                java.util.Map<UUID, ActiveGameSession> activeGames = (java.util.Map<UUID, ActiveGameSession>) session.getAttribute("activeGames");
                if (activeGames != null) {
                    ActiveGameSession activeGame = activeGames.get(gameId);
                    if (activeGame != null) {
                        activeGame.applyMove(rawMove, false);
                        logger.info("Applied opponent move to ActiveGameSession in map: gameId={}, move={}", gameId, rawMove);
                    }
                }
                ActiveGameSession currentActive = (ActiveGameSession) session.getAttribute("activeGame");
                if (currentActive != null && currentActive.getGameId().equals(gameId)) {
                    if (activeGames == null || currentActive != activeGames.get(gameId)) {
                        currentActive.applyMove(rawMove, false);
                    }
                }
            } else if (msg.body() instanceof MessageBody.GameOver go) {
                java.util.Map<UUID, ActiveGameSession> activeGames = (java.util.Map<UUID, ActiveGameSession>) session.getAttribute("activeGames");
                if (activeGames != null) {
                    activeGames.remove(go.gameId());
                }
                ActiveGameSession currentActive = (ActiveGameSession) session.getAttribute("activeGame");
                if (currentActive != null && currentActive.getGameId().equals(go.gameId())) {
                    session.removeAttribute("activeGame");
                    logger.info("Cleared currently viewed activeGame from session due to GAME_OVER push.");
                }

                java.util.Set<UUID> finishedGames = (java.util.Set<UUID>) session.getAttribute("finishedGames");
                if (finishedGames == null) {
                    finishedGames = new java.util.HashSet<>();
                    session.setAttribute("finishedGames", finishedGames);
                }
                finishedGames.add(go.gameId());

                if (go.user() != null) {
                    session.setAttribute("user", go.user());
                    logger.info("Updated HTTP session user DTO stats after match completion: username={}, wins={}",
                            go.user().username(), go.user().stats().gamesWon());
                }
            } else if (msg.body() instanceof MessageBody.GameOverDraw(
                    UUID gameId, iecd.a51597.common.store.UserDTO user
            )) {
                java.util.Map<UUID, ActiveGameSession> activeGames = (java.util.Map<UUID, ActiveGameSession>) session.getAttribute("activeGames");
                if (activeGames != null) {
                    activeGames.remove(gameId);
                }
                ActiveGameSession currentActive = (ActiveGameSession) session.getAttribute("activeGame");
                if (currentActive != null && currentActive.getGameId().equals(gameId)) {
                    session.removeAttribute("activeGame");
                    logger.info("Cleared currently viewed activeGame from session due to GAME_OVER_DRAW push.");
                }

                java.util.Set<UUID> finishedGames = (java.util.Set<UUID>) session.getAttribute("finishedGames");
                if (finishedGames == null) {
                    finishedGames = new java.util.HashSet<>();
                    session.setAttribute("finishedGames", finishedGames);
                }
                finishedGames.add(gameId);

                if (user != null) {
                    session.setAttribute("user", user);
                    logger.info("Updated HTTP session user DTO stats after draw match completion: username={}",
                            user.username());
                }
            }

            String json = serializePushToJson(msg, session);
            if (json != null) {
                if (sb.length() > 1) {
                    sb.append(",");
                }
                sb.append(json);
            }
        }
        
        sb.append("]");
        response.getWriter().write(sb.toString());
    }

    /**
     * Converts a protocol push Message into a browser-friendly JSON string.
     */
    private String serializePushToJson(Message msg, HttpSession session) {
        MessageBody body = msg.body();
        String action = msg.actionType().name();
        
        if (body instanceof MessageBody.GameInvitePush(UUID fromUserId, String fromUsername, UUID gameId)) {
            return String.format(
                "{\"action\":\"%s\",\"fromUserId\":\"%s\",\"fromUsername\":\"%s\",\"gameId\":\"%s\"}",
                action, fromUserId, escapeJson(fromUsername), gameId
            );
        }
        
        if (body instanceof MessageBody.GameInviteCancelPush(UUID gameId)) {
            return String.format(
                "{\"action\":\"%s\",\"gameId\":\"%s\"}",
                action, gameId
            );
        }
        
        if (body instanceof MessageBody.GameInviteResponsePush(UUID gameId, boolean accepted, String opponentUsername)) {
            return String.format(
                "{\"action\":\"%s\",\"gameId\":\"%s\",\"accepted\":%b,\"opponentUsername\":\"%s\"}",
                action, gameId, accepted, escapeJson(opponentUsername)
            );
        }
        
        if (body instanceof MessageBody.GameMove(UUID gameId, String rawMove)) {
            boolean yourTurn = false;
            String opponentName = "Opponent";
            if (session != null) {
                java.util.Map<UUID, ActiveGameSession> activeGames = (java.util.Map<UUID, ActiveGameSession>) session.getAttribute("activeGames");
                if (activeGames != null) {
                    ActiveGameSession activeGame = activeGames.get(gameId);
                    if (activeGame != null) {
                        yourTurn = activeGame.isMyTurn();
                        opponentName = activeGame.getOpponentName();
                    }
                }
            }
            return String.format(
                "{\"action\":\"%s\",\"gameId\":\"%s\",\"move\":\"%s\",\"yourTurn\":%b,\"opponentName\":\"%s\"}",
                action, gameId, escapeJson(rawMove), yourTurn, escapeJson(opponentName)
            );
        }
        
        if (body instanceof MessageBody.GameOver(
                UUID gameId, UUID winnerId, String winnerUsername, String reason, iecd.a51597.common.store.UserDTO user
        )) {
            // Update user stats if returned
            String userJson = "null";
            if (user != null) {
                userJson = String.format("{\"username\":\"%s\"}", escapeJson(user.username()));
            }
            return String.format(
                "{\"action\":\"%s\",\"gameId\":\"%s\",\"winnerId\":\"%s\",\"winnerUsername\":\"%s\",\"reason\":\"%s\",\"user\":%s}",
                action, gameId, winnerId, escapeJson(winnerUsername),
                escapeJson(reason), userJson
            );
        }
        
        if (body instanceof MessageBody.GameOverDraw draw) {
            return String.format(
                "{\"action\":\"%s\",\"gameId\":\"%s\"}",
                action, draw.gameId()
            );
        }

        return null;
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
