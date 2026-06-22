<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
    // Standard request scope setting using plain Java scriptlets
    request.setAttribute("pageStyle", "game.css");
    request.setAttribute("pageScript", "game.js");
    request.setAttribute("pageActive", "game");

    iecd.a51597.common.store.UserDTO userObj = (iecd.a51597.common.store.UserDTO) session.getAttribute("user");
    
    if (userObj == null) {
        response.sendRedirect(request.getContextPath() + "/auth/login");
        return;
    }
    
    String userIdStr = userObj.userId().toString();
    String username = userObj.username();
    
    String gameId = request.getParameter("gameId");
    String role = request.getParameter("role");
    String opponentName = request.getParameter("opponent");
    if (opponentName == null || opponentName.isBlank()) {
        opponentName = "Opponent";
    }
    
    String contextPath = request.getContextPath();

    // Prepare JSON arrays for active game state reconstruction
    java.util.Map<java.util.UUID, iecd.a51597.web.ActiveGameSession> activeGames = 
        (java.util.Map<java.util.UUID, iecd.a51597.web.ActiveGameSession>) session.getAttribute("activeGames");
    iecd.a51597.web.ActiveGameSession activeGame = null;
    if (activeGames != null && gameId != null) {
        activeGame = activeGames.get(java.util.UUID.fromString(gameId));
    }
    StringBuilder linesBuilder = new StringBuilder();
    if (activeGame != null) {
        for (String line : activeGame.getDrawnLines()) {
            if (linesBuilder.length() > 0) {
                linesBuilder.append(",");
            }
            linesBuilder.append("\"").append(line).append("\"");
        }
    }

    StringBuilder boxesBuilder = new StringBuilder();
    if (activeGame != null) {
        for (java.util.Map.Entry<String, String> entry : activeGame.getCapturedBoxes().entrySet()) {
            if (boxesBuilder.length() > 0) {
                boxesBuilder.append(",");
            }
            boxesBuilder.append("{\"key\":\"").append(entry.getKey()).append("\",\"owner\":\"").append(entry.getValue()).append("\"}");
        }
    }
%>

<!-- Bootstrap game parameters for client-side JS -->
<script>
    window.gameBootstrap = {
        gameId: '<%= gameId %>',
        role: '<%= role %>',
        userId: '<%= userIdStr %>',
        username: '<%= username %>',
        opponentName: '<%= opponentName %>',
        initialLines: [<%= linesBuilder.toString() %>],
        initialBoxes: [<%= boxesBuilder.toString() %>],
        initialP1Score: <%= activeGame != null ? activeGame.getPlayer1Score() : 0 %>,
        initialP2Score: <%= activeGame != null ? activeGame.getPlayer2Score() : 0 %>,
        initialIsMyTurn: <%= activeGame != null ? activeGame.isMyTurn() : "inviter".equalsIgnoreCase(role) %>
    };
</script>

<jsp:include page="common/header.jsp"/>

<% if (userObj != null && userObj.favoriteColor() != null && !userObj.favoriteColor().isBlank()) { %>
<style>
    .canvas-wrapper, #game-board-canvas {
        background-color: <%= userObj.favoriteColor() %> !important;
    }
</style>
<% } %>

<div class="game-wrapper">
    <!-- Top Match Header Panel -->
    <div class="match-header">
        <h1 class="match-title"><i class="fa-solid fa-chess-board"></i> Dots &amp; Boxes Match</h1>
        <div class="lobby-badge">
            <span class="pulse-icon"><i class="fa-solid fa-gamepad"></i></span>
            <span id="match-status-text">Active Game Session</span>
        </div>
    </div>

    <!-- Active Match Dashboard Grid -->
    <div class="game-layout">
        
        <!-- Scoreboard Column -->
        <div class="scoreboard-sidebar">
            <div class="card scorecard-card">
                <div class="card-header">
                    <h2 class="card-title"><i class="fa-solid fa-square-poll-vertical"></i> Scoreboard</h2>
                </div>
                <div class="card-body">
                    <!-- Player 1 Profile Info -->
                    <div class="player-score-row" id="player1-score-row">
                        <div class="player-score-meta">
                            <span class="player-name" id="player1-name">Player 1</span>
                            <span class="player-dot-color p1-color"><i class="fa-solid fa-circle"></i> Red Lines</span>
                        </div>
                        <span class="score-number" id="player1-score-val">0</span>
                    </div>

                    <div class="scoreboard-vs"><i class="fa-solid fa-bolt"></i> VS</div>

                    <!-- Player 2 Profile Info -->
                    <div class="player-score-row" id="player2-score-row">
                        <div class="player-score-meta">
                            <span class="player-name" id="player2-name">Player 2</span>
                            <span class="player-dot-color p2-color"><i class="fa-solid fa-circle"></i> Blue Lines</span>
                        </div>
                        <span class="score-number" id="player2-score-val">0</span>
                    </div>

                    <!-- Current Turn Notification Widget -->
                    <div class="turn-indicator-widget" id="turn-widget">
                        <span class="turn-pulse-icon" id="turn-pulse-icon-element"><i class="fa-solid fa-hourglass-start fa-spin"></i></span>
                        <span class="turn-text" id="turn-text-element">Loading turn...</span>
                    </div>
                    
                    <!-- Move Timeout Countdown Visual Widget -->
                    <div class="turn-timer-widget" id="turn-timer-container" style="margin-top: 1rem; padding: 0.8rem; background-color: rgba(255, 255, 255, 0.03); border: 1px solid var(--border-color); border-radius: 4px; text-align: center;">
                        <span style="font-size: 0.85rem; opacity: 0.7; display: block; margin-bottom: 0.2rem;"><i class="fa-regular fa-clock"></i> Move Timer Limit</span>
                        <span id="timer-countdown-val" style="font-size: 1.6rem; font-weight: bold; color: var(--accent);">30</span><span style="font-size: 0.9rem; font-weight: bold; opacity: 0.7;"> seconds left</span>
                    </div>
                </div>
            </div>

            <!-- Surrender and Lobby Actions -->
            <div class="game-lobby-actions">
                <button type="button" id="surrender-match-btn" class="btn btn-outline-danger btn-block btn-lg">
                    <i class="fa-solid fa-flag"></i> Surrender Match
                </button>
            </div>
        </div>

        <!-- Interactive Board Column -->
        <div class="board-container-side">
            <div class="card board-card">
                <div class="card-body board-body">
                    <!-- Interactive HTML5 Canvas Board Element -->
                    <div class="canvas-wrapper">
                        <canvas id="game-board-canvas" width="480" height="480"></canvas>
                    </div>
                </div>
            </div>
        </div>

    </div>
</div>

<!-- Game Over Fullscreen Overlay Modal -->
<div class="modal-overlay" id="game-over-modal" style="display: none;">
    <div class="modal-card">
        <div class="modal-icon" id="game-over-icon-wrapper">
            <!-- Icon sets by result: Win = Medal, Loss = Skull, Draw = Scale -->
            <i class="fa-solid fa-trophy" id="game-over-icon"></i>
        </div>
        <h3 class="modal-title" id="game-over-title">Game Over</h3>
        <p class="modal-text" id="game-over-summary">Match is finished. The scores were calculated.</p>
        
        <div class="modal-scorecard">
            <div class="modal-score-item">
                <span class="m-player" id="m-p1-name">P1</span>
                <span class="m-score" id="m-p1-score">0</span>
            </div>
            <div class="modal-vs"><i class="fa-solid fa-bolt"></i></div>
            <div class="modal-score-item">
                <span class="m-player" id="m-p2-name">P2</span>
                <span class="m-score" id="m-p2-score">0</span>
            </div>
        </div>

        <div class="modal-actions">
            <button type="button" class="btn btn-primary" onclick="window.location.href='<%= contextPath %>/dashboard'">
                <i class="fa-solid fa-chart-line"></i> Return to Dashboard
            </button>
        </div>
    </div>
</div>

<jsp:include page="common/footer.jsp"/>
