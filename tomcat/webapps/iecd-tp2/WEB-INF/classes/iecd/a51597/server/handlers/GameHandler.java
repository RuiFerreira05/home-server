package iecd.a51597.server.handlers;

import iecd.a51597.common.game.Move;
import iecd.a51597.server.network.Connection;
import iecd.a51597.server.game.GameManager;
import iecd.a51597.common.protocol.exceptions.MalformedMessageException;
import iecd.a51597.server.persistence.PersistenceManager;
import iecd.a51597.server.session.Session;
import iecd.a51597.server.session.SessionManager;
import iecd.a51597.common.game.Game;
import iecd.a51597.common.game.MoveResult;
import iecd.a51597.common.protocol.Message;
import iecd.a51597.common.protocol.MessageBody;
import iecd.a51597.common.protocol.builders.server.ServerMessageBuilder;
import iecd.a51597.common.protocol.types.ErrorCodeType;
import iecd.a51597.server.store.entities.User;
import iecd.a51597.server.store.UserStore;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import iecd.a51597.common.game.dotsandboxes.DotsAndBoxesGame;
import iecd.a51597.server.config.ServerConfiguration;

/**
 * Handles multiplayer game invitation and move lifecycle actions.
 */
public class GameHandler extends BaseHandler {

    private final UserStore userStore;
    private final GameManager gameManager;
    private final ScheduledExecutorService timerScheduler = Executors.newScheduledThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        thread.setName("game-timer-thread-" + thread.getId());
        return thread;
    });
    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> activeTimers = new ConcurrentHashMap<>();

    /**
     * Creates a game handler.
     */
    public GameHandler(ServerMessageBuilder messageBuilder, SessionManager sessionManager, UserStore userStore, GameManager gameManager, PersistenceManager persistenceManager) {
        super(messageBuilder, sessionManager, persistenceManager);
        this.userStore = userStore;
        this.gameManager = gameManager;
    }

    /**
     * Handles game invitation requests.
     * @param message the invite request message
     * @param connection the client connection
     */
    public void gameInvite(Message message, Connection connection) {
        if (!requireConfiguredGame(message, connection)) return;
        Optional<Session> sessionOpt = requireSession(message, connection);
        if (sessionOpt.isEmpty()) return;
        Session session = sessionOpt.get();

        User sender = session.getUser();
        MessageBody.GameInviteRequest body = (MessageBody.GameInviteRequest) message.body();

        logger.debug(
                "Processing game invite request: messageId={}, senderUserId={}, targetUserId={}",
                message.messageId(),
                sender.getUserId(),
                body.targetUserId()
        );

        if (gameManager.isInGame(sender.getUserId())) {
            logger.warn(
                    "Rejected game invite: sender already in game: messageId={}, senderUserId={}",
                    message.messageId(),
                    sender.getUserId()
            );
            sendError(message, connection, ErrorCodeType.ALREADY_IN_GAME, "You are already in a game");
            return;
        }

        if (body.targetUserId().equals(sender.getUserId())) {
            logger.warn(
                    "Rejected game invite: sender attempted to invite self: messageId={}, senderUserId={}",
                    message.messageId(),
                    sender.getUserId()
            );
            sendError(message, connection, ErrorCodeType.UNEXPECTED_MESSAGE_ACTION, "Cannot invite yourself to a game");
            return;
        }

        Optional<User> targetOpt = userStore.findById(body.targetUserId());
        if (targetOpt.isEmpty()) {
            logger.warn(
                    "Rejected game invite: target user not found: messageId={}, senderUserId={}, targetUserId={}",
                    message.messageId(),
                    sender.getUserId(),
                    body.targetUserId()
            );
            sendError(message, connection, ErrorCodeType.USER_NOT_FOUND, "Target user does not exist");
            return;
        }
        User target = targetOpt.get();

        Optional<Session> targetSessionOpt = sessionManager.getSessionByUserId(target.getUserId());
        if (targetSessionOpt.isEmpty()) {
            logger.info(
                    "Rejected game invite: target user offline: messageId={}, senderUserId={}, targetUserId={}",
                    message.messageId(),
                    sender.getUserId(),
                    target.getUserId()
            );
            sendError(message, connection, ErrorCodeType.USER_NOT_ONLINE, "Target user is not online");
            return;
        }
        Session targetSession = targetSessionOpt.get();

        if (gameManager.isInGame(target.getUserId())) {
            logger.info(
                    "Rejected game invite: target already in game: messageId={}, senderUserId={}, targetUserId={}",
                    message.messageId(),
                    sender.getUserId(),
                    target.getUserId()
            );
            sendError(message, connection, ErrorCodeType.ALREADY_IN_GAME, "Target user is already in a game");
            return;
        }

        Game game = gameManager.createPendingGame(sender.getUserId(), target.getUserId());

        logger.info(
                "Created pending game invitation: messageId={}, gameId={}, inviterUserId={}, invitedUserId={}",
                message.messageId(),
                game.getGameId(),
                sender.getUserId(),
                target.getUserId()
        );

        connection.sendMessage(messageBuilder.gameInviteResponse(message.messageId(), game.getGameId()));
        targetSession.getConnection().sendMessage(
                messageBuilder.gameInvitePush(game.getGameId(), sender)
        );
    }

    /**
     * Handles game invite cancellation.
     * @param message the cancel request message
     * @param connection the client connection
     */
    public void gameInviteCancel(Message message, Connection connection) {
        Optional<Session> sessionOpt = requireSession(message, connection);
        if (sessionOpt.isEmpty()) return;
        Session session = sessionOpt.get();
        MessageBody.GameInviteCancelRequest body = (MessageBody.GameInviteCancelRequest) message.body();

        logger.debug(
                "Processing game invite cancel: messageId={}, senderUserId={}, gameId={}",
                message.messageId(),
                session.getUserId(),
                body.gameId()
        );

        Optional<Game> gameOpt = gameManager.getPendingGame(body.gameId());
        if (gameOpt.isEmpty()) {
            logger.warn(
                    "Rejected game invite cancel: pending game not found: messageId={}, gameId={}",
                    message.messageId(),
                    body.gameId()
            );
            sendError(message, connection, ErrorCodeType.GAME_NOT_FOUND, "Game not found");
            return;
        }

        Game game = gameOpt.get();
        if (!game.getPlayer1Id().equals(session.getUserId())) {
            logger.warn(
                    "Rejected game invite cancel: not the inviter: messageId={}, gameId={}, userId={}",
                    message.messageId(),
                    body.gameId(),
                    session.getUserId()
            );
            sendError(message, connection, ErrorCodeType.UNEXPECTED_MESSAGE_ACTION, "Only the inviter can cancel the invite");
            return;
        }

        gameManager.declineGame(game.getGameId());
        connection.sendMessage(messageBuilder.ok(message.messageId(), message.actionType()));
        
        sessionManager.getSessionByUserId(game.getPlayer2Id()).ifPresent(s ->
                s.getConnection().sendMessage(messageBuilder.gameInviteCancelPush(game.getGameId()))
        );
        logger.info("Game invite cancelled: gameId={}", game.getGameId());
    }

    /**
     * Handles invitation acceptance/decline responses.
     * @param message the response message
     * @param connection the client connection
     */
    public void gameInviteResponse(Message message, Connection connection) {
        Optional<Session> sessionOpt = requireSession(message, connection);
        if (sessionOpt.isEmpty()) return;
        Session session = sessionOpt.get();
        User responder = session.getUser();
        MessageBody.GameInviteResponseRequest body = (MessageBody.GameInviteResponseRequest) message.body();

        logger.debug(
                "Processing game invite response: messageId={}, responderUserId={}, gameId={}, accept={}",
                message.messageId(),
                responder.getUserId(),
                body.gameId(),
                body.accept()
        );

        if (gameManager.isInGame(session.getUserId())) {
            logger.warn(
                    "Rejected game invite response: responder already in game: messageId={}, responderUserId={}",
                    message.messageId(),
                    responder.getUserId()
            );
            sendError(message, connection, ErrorCodeType.ALREADY_IN_GAME, "You are already in a game");
            return;
        }

        Optional<Game> gameOpt = gameManager.getPendingGame(body.gameId());
        if (gameOpt.isEmpty()) {
            logger.warn(
                    "Rejected game invite response: pending game not found: messageId={}, responderUserId={}, gameId={}",
                    message.messageId(),
                    responder.getUserId(),
                    body.gameId()
            );
            sendError(message, connection, ErrorCodeType.GAME_NOT_FOUND, "Game not found");
            return;
        }
        Game game = gameOpt.get();

        UUID inviterId = game.getPlayer1Id();

        // Edge case just to prevent game invite high-jacking, not even sure if it would trigger but might as well
        if (!responder.getUserId().equals(game.getPlayer2Id())) {
            logger.warn(
                    "Rejected game invite response: responder is not invited player: messageId={}, responderUserId={}, gameId={}, invitedUserId={}",
                    message.messageId(),
                    responder.getUserId(),
                    game.getGameId(),
                    game.getPlayer2Id()
            );
            sendError(message, connection, ErrorCodeType.UNEXPECTED_MESSAGE_ACTION, "You are not the invited player");
            return;
        }

        Optional<Session> inviterSessionOpt = sessionManager.getSessionByUserId(inviterId);

        if (inviterSessionOpt.isEmpty()) {
            gameManager.declineGame(game.getGameId());
            logger.warn(
                    "Declined pending game because inviter went offline: messageId={}, gameId={}, inviterUserId={}, responderUserId={}",
                    message.messageId(),
                    game.getGameId(),
                    inviterId,
                    responder.getUserId()
            );
            sendError(message, connection, ErrorCodeType.USER_NOT_ONLINE, "The inviting player is no longer online");
            return;
        }

        if (!body.accept()) {
            gameManager.declineGame(game.getGameId());
            logger.info(
                    "Invite declined: messageId={}, gameId={}, inviterUserId={}, responderUserId={}",
                    message.messageId(),
                    game.getGameId(),
                    inviterId,
                    responder.getUserId()
            );
            connection.sendMessage(messageBuilder.ok(message.messageId(), message.actionType()));
            inviterSessionOpt.ifPresent(s ->
                    s.getConnection().sendMessage(messageBuilder.gameInviteDeclinedPush(game.getGameId(), responder))
            );
            return;
        }

        gameManager.acceptGame(game.getGameId());
        logger.info(
                "Invite accepted and game activated: messageId={}, gameId={}, inviterUserId={}, responderUserId={}",
                message.messageId(),
                game.getGameId(),
                inviterId,
                responder.getUserId()
        );
        connection.sendMessage(messageBuilder.ok(message.messageId(), message.actionType()));
        inviterSessionOpt.ifPresent(s ->
                s.getConnection().sendMessage(messageBuilder.gameInviteAcceptedPush(game.getGameId(), responder))
        );
        scheduleTurnTimer(game.getGameId(), game.getPlayer1Id());
    }

    /**
     * Handles game move requests for active games.
     * @param message the move request message
     * @param connection the client connection
     */
    public void gameMove(Message message, Connection connection) {
        if (!requireConfiguredGame(message, connection)) return;
        Optional<Session> sessionOpt = requireSession(message, connection);
        if (sessionOpt.isEmpty()) return;
        Session session = sessionOpt.get();

        UUID playerId = session.getUserId();
        MessageBody.GameMove body = (MessageBody.GameMove) message.body();

        logger.debug(
                "Processing game move request: messageId={}, gameId={}, playerUserId={}, rawMoveBytes={}",
                message.messageId(),
                body.gameId(),
                playerId,
                body.rawMove().length()
        );

        Optional<Game> gameOpt = gameManager.getGame(body.gameId());
        if (gameOpt.isEmpty()) {
            logger.warn(
                    "Rejected game move: game not found: messageId={}, gameId={}, playerUserId={}",
                    message.messageId(),
                    body.gameId(),
                    playerId
            );
            sendError(message, connection, ErrorCodeType.GAME_NOT_FOUND, "Game not found");
            return;
        }

        Game game = gameOpt.get();

        // This guard prevents game move injection from third parties, a bit overkill for a uni project, but
        // I'm kinda overkill
        if (!playerId.equals(game.getPlayer1Id())
                && !playerId.equals(game.getPlayer2Id())) {
            logger.warn(
                    "Rejected game move: player is not part of game: messageId={}, gameId={}, playerUserId={}, player1UserId={}, player2UserId={}",
                    message.messageId(),
                    game.getGameId(),
                    playerId,
                    game.getPlayer1Id(),
                    game.getPlayer2Id()
            );
            sendError(message, connection, ErrorCodeType.UNEXPECTED_MESSAGE_ACTION, "You are not a player in this game");
            return;
        }

        Move move;
        try {
            move = gameManager.getCodec().deserialize(body.rawMove());
        } catch (MalformedMessageException e) {
            logger.warn(
                    "Rejected game move: malformed move payload: messageId={}, gameId={}, playerUserId={}, rawMoveBytes={}",
                    message.messageId(),
                    game.getGameId(),
                    playerId,
                    body.rawMove().length()
            );
            sendError(message, connection, ErrorCodeType.MALFORMED_REQUEST, "Invalid move payload");
            return;
        }

        switch (game.applyMove(playerId, move)) {
            case MoveResult.Accepted() -> {
                logger.debug(
                        "Accepted game move: messageId={}, gameId={}, playerUserId={}",
                        message.messageId(),
                        game.getGameId(),
                        playerId
                );
                connection.sendMessage(messageBuilder.ok(message.messageId(), message.actionType()));
                UUID opponent = game.getPlayer1Id().equals(playerId)
                        ? game.getPlayer2Id() : game.getPlayer1Id();
                sessionManager.getSessionByUserId(opponent).ifPresent(s ->
                        s.getConnection().sendMessage(
                                messageBuilder.gameMovePush(game.getGameId(), body.rawMove())
                        )
                );
                if (game instanceof DotsAndBoxesGame dbGame) {
                    scheduleTurnTimer(game.getGameId(), dbGame.getCurrentPlayerId());
                }
            }
            case MoveResult.Rejected(String reason) -> {
                logger.info(
                        "Rejected game move by game rules: messageId={}, gameId={}, playerUserId={}, reason={}",
                        message.messageId(),
                        game.getGameId(),
                        playerId,
                        reason
                );
                sendError(message, connection, ErrorCodeType.INVALID_MOVE, reason);
            }
            case MoveResult.GameOver(UUID winnerId) -> {
                cancelTurnTimer(game.getGameId());
                User p1 = userStore.findById(game.getPlayer1Id()).orElseThrow();
                User p2 = userStore.findById(game.getPlayer2Id()).orElseThrow();
                User winner = winnerId == null ? null : (winnerId.equals(p1.getUserId()) ? p1 : p2);

                logger.info(
                        "Game over reached after move: messageId={}, gameId={}, playerUserId={}, winnerUserId={}",
                        message.messageId(),
                        game.getGameId(),
                        playerId,
                        winner
                );
                connection.sendMessage(messageBuilder.ok(message.messageId(), message.actionType()));
                UUID opponentId = game.getPlayer1Id().equals(playerId)
                        ? game.getPlayer2Id() : game.getPlayer1Id();
                sessionManager.getSessionByUserId(opponentId).ifPresent(s ->
                        s.getConnection().sendMessage(
                                messageBuilder.gameMovePush(game.getGameId(), body.rawMove())
                        )
                );

                double playTimeSecs = (System.currentTimeMillis() - game.getStartTimeMillis()) / 1000.0;
                boolean p1Won = winner != null && winner.getUserId().equals(p1.getUserId());
                boolean p2Won = winner != null && winner.getUserId().equals(p2.getUserId());

                p1.setStats(p1.getStats().withMatch(p1Won, playTimeSecs, p2.getUserId(), p2.getUsername()));
                p2.setStats(p2.getStats().withMatch(p2Won, playTimeSecs, p1.getUserId(), p1.getUsername()));

                pushGameOver(game, winner);
                gameManager.endGame(game.getGameId());
                logger.info("Closed active game: gameId={}, winnerUserId={}", game.getGameId(), winner);
            }
            case MoveResult.Draw ignored -> {
                cancelTurnTimer(game.getGameId());
                logger.info(
                        "Game over (draw) reached after move: messageId={}, gameId={}, playerUserId={}",
                        message.messageId(),
                        game.getGameId(),
                        playerId
                );
                connection.sendMessage(messageBuilder.ok(message.messageId(), message.actionType()));
                UUID opponentId = game.getPlayer1Id().equals(playerId)
                        ? game.getPlayer2Id() : game.getPlayer1Id();
                sessionManager.getSessionByUserId(opponentId).ifPresent(s ->
                        s.getConnection().sendMessage(
                                messageBuilder.gameMovePush(game.getGameId(), body.rawMove())
                        )
                );

                double playTimeSecs = (System.currentTimeMillis() - game.getStartTimeMillis()) / 1000.0;

                userStore.findById(game.getPlayer1Id()).ifPresent(p -> {
                    p.setStats(p.getStats().withMatch(false, playTimeSecs, game.getPlayer2Id(), null));
                });
                userStore.findById(game.getPlayer2Id()).ifPresent(p -> {
                    p.setStats(p.getStats().withMatch(false, playTimeSecs, game.getPlayer1Id(), null));
                });

                pushGameDraw(game);
                gameManager.endGame(game.getGameId());
                logger.info("Closed active game: gameId={}, result=draw", game.getGameId());
            }
        }
    }

    /**
     * Handles surrender requests for active games.
     * @param message the surrender request message
     * @param connection the client connection
     */
    public void surrender(Message message, Connection connection) {
        if (!requireConfiguredGame(message, connection)) return;
        Optional<Session> sessionOpt = requireSession(message, connection);
        if (sessionOpt.isEmpty()) return;
        Session session = sessionOpt.get();

        UUID playerId = session.getUserId();
        MessageBody.Surrender body = (MessageBody.Surrender) message.body();

        logger.debug(
                "Processing surrender request: messageId={}, gameId={}, playerUserId={}",
                message.messageId(),
                body.gameId(),
                playerId
        );

        Optional<Game> gameOpt = gameManager.getGame(body.gameId());
        if (gameOpt.isEmpty()) {
            logger.warn(
                    "Rejected surrender: game not found: messageId={}, gameId={}, playerUserId={}",
                    message.messageId(),
                    body.gameId(),
                    playerId
            );
            sendError(message, connection, ErrorCodeType.GAME_NOT_FOUND, "Game not found");
            return;
        }

        Game game = gameOpt.get();

        if (!playerId.equals(game.getPlayer1Id())
                && !playerId.equals(game.getPlayer2Id())) {
            logger.warn(
                    "Rejected surrender: player is not part of game: messageId={}, gameId={}, playerUserId={}",
                    message.messageId(),
                    game.getGameId(),
                    playerId
            );
            sendError(message, connection, ErrorCodeType.UNEXPECTED_MESSAGE_ACTION, "You are not a player in this game");
            return;
        }

        UUID winnerId = game.getPlayer1Id().equals(playerId) ? game.getPlayer2Id() : game.getPlayer1Id();

        User p1 = userStore.findById(game.getPlayer1Id()).orElseThrow();
        User p2 = userStore.findById(game.getPlayer2Id()).orElseThrow();
        User winner = winnerId.equals(p1.getUserId()) ? p1 : p2;

        logger.info(
                "Game over reached by surrender: messageId={}, gameId={}, surrenderingPlayerId={}, winnerUserId={}",
                message.messageId(),
                game.getGameId(),
                playerId,
                winner.getUserId()
        );

        // Acknowledge surrender
        connection.sendMessage(messageBuilder.ok(message.messageId(), message.actionType()));

        double playTimeSecs = (System.currentTimeMillis() - game.getStartTimeMillis()) / 1000.0;
        boolean p1Won = winner.getUserId().equals(p1.getUserId());
        boolean p2Won = winner.getUserId().equals(p2.getUserId());

        p1.setStats(p1.getStats().withMatch(p1Won, playTimeSecs, p2.getUserId(), p2.getUsername()));
        p2.setStats(p2.getStats().withMatch(p2Won, playTimeSecs, p1.getUserId(), p1.getUsername()));

        pushGameOverSurrender(game, winner);
        gameManager.endGame(game.getGameId());
        cancelTurnTimer(game.getGameId());
        logger.info("Closed active game: gameId={}, winnerUserId={} (reason: SURRENDER)", game.getGameId(), winner.getUserId());
    }

    private void pushGameOverSurrender(Game game, User winner) {
        logger.info(
                "Broadcasting game over event (surrender): gameId={}, winnerUserId={}, player1UserId={}, player2UserId={}",
                game.getGameId(),
                winner,
                game.getPlayer1Id(),
                game.getPlayer2Id()
        );
        sessionManager.getSessionByUserId(game.getPlayer1Id())
                .ifPresent(s -> s.getConnection().sendMessage(messageBuilder.gameOverPush(game.getGameId(), winner, "SURRENDER", s.getUser())));
        sessionManager.getSessionByUserId(game.getPlayer2Id())
                .ifPresent(s -> s.getConnection().sendMessage(messageBuilder.gameOverPush(game.getGameId(), winner, "SURRENDER", s.getUser())));
    }

    /**
     * Rejects client-originated game-over requests.
     * @param message the game over message
     * @param connection the client connection
     */
    public void gameOver(Message message, Connection connection) {
        // GAME_OVER is server-initiated (PUSH only), client should never send this
        logger.warn(
                "Rejected client-originated GAME_OVER request: messageId={}, sessionTokenPresent={}",
                message.messageId(),
                message.sessionToken() != null
        );
        sendError(message, connection, ErrorCodeType.UNEXPECTED_MESSAGE_ACTION, "GAME_OVER is server-initiated only");
    }

    /**
     * Broadcasts a game draw push notification to all players in the match.
     *
     * @param game the completed game match state
     */
    public void pushGameDraw(Game game) {
        logger.info(
                "Broadcasting game over (draw) event: gameId={}, player1UserId={}, player2UserId={}",
                game.getGameId(),
                game.getPlayer1Id(),
                game.getPlayer2Id()
        );
        sessionManager.getSessionByUserId(game.getPlayer1Id())
                .ifPresent(s -> s.getConnection().sendMessage(messageBuilder.gameOverDrawPush(game.getGameId(), s.getUser())));
        sessionManager.getSessionByUserId(game.getPlayer2Id())
                .ifPresent(s -> s.getConnection().sendMessage(messageBuilder.gameOverDrawPush(game.getGameId(), s.getUser())));
    }

    private void pushGameOver(Game game, User winner) {
        logger.info(
                "Broadcasting game over event: gameId={}, winnerUserId={}, player1UserId={}, player2UserId={}",
                game.getGameId(),
                winner,
                game.getPlayer1Id(),
                game.getPlayer2Id()
        );
        sessionManager.getSessionByUserId(game.getPlayer1Id())
                .ifPresent(s -> s.getConnection().sendMessage(messageBuilder.gameOverPush(game.getGameId(), winner, s.getUser())));
        sessionManager.getSessionByUserId(game.getPlayer2Id())
                .ifPresent(s -> s.getConnection().sendMessage(messageBuilder.gameOverPush(game.getGameId(), winner, s.getUser())));
    }

    private boolean requireConfiguredGame(Message message, Connection connection) {
        if (gameManager.hasFactory()) {
            return true;
        } else {
            logger.error(
                    "Rejected game action: no game factory configured: messageId={}, actionType={}",
                    message.messageId(),
                    message.actionType()
            );
            sendError(message, connection, ErrorCodeType.INTERNAL_ERROR, "No game is configured on this server");
            return false;
        }
    }

    private void scheduleTurnTimer(UUID gameId, UUID currentPlayerId) {
        cancelTurnTimer(gameId);
        int timeoutSecs = ServerConfiguration.TURN_TIMEOUT_SECONDS;
        ScheduledFuture<?> future = timerScheduler.schedule(() -> {
            handleTurnTimeout(gameId, currentPlayerId);
        }, timeoutSecs, TimeUnit.SECONDS);
        activeTimers.put(gameId, future);
        logger.info("Scheduled turn timer for gameId={}, player={}, limit={}s", gameId, currentPlayerId, timeoutSecs);
    }

    private void cancelTurnTimer(UUID gameId) {
        ScheduledFuture<?> future = activeTimers.remove(gameId);
        if (future != null) {
            future.cancel(false);
            logger.info("Cancelled turn timer for gameId={}", gameId);
        }
    }

    private void handleTurnTimeout(UUID gameId, UUID timedOutPlayerId) {
        synchronized (gameManager) {
            Optional<Game> gameOpt = gameManager.getGame(gameId);
            if (gameOpt.isEmpty()) {
                return;
            }
            Game game = gameOpt.get();
            if (game instanceof DotsAndBoxesGame dbGame) {
                if (dbGame.isGameOver() || !dbGame.getCurrentPlayerId().equals(timedOutPlayerId)) {
                    return; // Already completed or not their turn anymore
                }
                UUID winnerId = dbGame.getPlayer1Id().equals(timedOutPlayerId) ? dbGame.getPlayer2Id() : dbGame.getPlayer1Id();
                dbGame.forceGameOver(winnerId);

                User p1 = userStore.findById(dbGame.getPlayer1Id()).orElseThrow();
                User p2 = userStore.findById(dbGame.getPlayer2Id()).orElseThrow();
                User winner = winnerId.equals(p1.getUserId()) ? p1 : p2;

                logger.info("Forfeited game due to turn timeout: gameId={}, timedOutPlayerId={}, winnerId={}",
                        gameId, timedOutPlayerId, winnerId);

                double playTimeSecs = (System.currentTimeMillis() - dbGame.getStartTimeMillis()) / 1000.0;
                boolean p1Won = winner.getUserId().equals(p1.getUserId());
                boolean p2Won = winner.getUserId().equals(p2.getUserId());

                p1.setStats(p1.getStats().withMatch(p1Won, playTimeSecs, p2.getUserId(), p2.getUsername()));
                p2.setStats(p2.getStats().withMatch(p2Won, playTimeSecs, p1.getUserId(), p1.getUsername()));

                pushGameOverTimeout(dbGame, winner);
                gameManager.endGame(gameId);
                activeTimers.remove(gameId);
                persistenceManager.save();
            }
        }
    }

    private void pushGameOverTimeout(Game game, User winner) {
        logger.info(
                "Broadcasting game over event (timeout): gameId={}, winnerUserId={}, player1UserId={}, player2UserId={}",
                game.getGameId(),
                winner.getUserId(),
                game.getPlayer1Id(),
                game.getPlayer2Id()
        );
        sessionManager.getSessionByUserId(game.getPlayer1Id())
                .ifPresent(s -> s.getConnection().sendMessage(messageBuilder.gameOverPush(game.getGameId(), winner, "TIMEOUT", s.getUser())));
        sessionManager.getSessionByUserId(game.getPlayer2Id())
                .ifPresent(s -> s.getConnection().sendMessage(messageBuilder.gameOverPush(game.getGameId(), winner, "TIMEOUT", s.getUser())));
    }
}
