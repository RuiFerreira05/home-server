<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="iecd.a51597.common.store.UserDTO" %>
<%@ page import="iecd.a51597.common.store.PlayerStats" %>
<%@ page import="iecd.a51597.web.ActiveGameSession" %>
<%
    // Standard request scope setting using plain Java scriptlets
    request.setAttribute("pageStyle", "dashboard.css");
    request.setAttribute("pageScript", "search.js");
    request.setAttribute("pageActive", "dashboard");

    UserDTO userObj = (UserDTO) session.getAttribute("user");

    String username = "";
    int played = 0;
    int won = 0;
    int lost = 0;
    float winRate = 0.0f;
    double playtimeSecs = 0.0;

    if (userObj != null) {
        username = userObj.username();
        PlayerStats stats = userObj.stats();
        played = stats != null ? stats.gamesPlayed() : 0;
        won = stats != null ? stats.gamesWon() : 0;
        lost = stats != null ? stats.gamesLost() : 0;
        winRate = stats != null ? stats.winRate() : 0.0f;
        playtimeSecs = stats != null ? stats.totalPlayTimeSecs() : 0.0;
    }

    String winRateStr = String.format("%.1f%%", winRate * 100.0);
    int playtimeMin = (int) (playtimeSecs / 60.0);

    String contextPath = request.getContextPath();
%>
<script>
    window.isUserAuthenticated = <%= userObj != null %>;
    window.contextPath = "<%= contextPath %>";
</script>

<jsp:include page="common/header.jsp"/>

<div class="dashboard-wrapper">
    <%
        java.util.Map<java.util.UUID, ActiveGameSession> activeGames = (java.util.Map<java.util.UUID, ActiveGameSession>) session.getAttribute("activeGames");
        if (activeGames != null && !activeGames.isEmpty()) {
            for (ActiveGameSession match : activeGames.values()) {
                String turnLabel = match.isMyTurn() ? "<span style='color: #22c55e; font-weight: bold;'>YOUR TURN!</span>" : "<span style='opacity: 0.7;'>Opponent's turn</span>";
    %>
    <!-- Dynamic Active Match Quick Return Alert -->
    <div class="active-match-alert" style="margin-bottom: 1rem;">
        <div class="ama-content">
            <span class="ama-icon"><i class="fa-solid fa-gamepad fa-beat"></i></span>
            <span class="ama-text">
                    Active match against <strong><%= match.getOpponentName() %></strong>! — <%= turnLabel %>
                </span>
        </div>
        <a href="<%= contextPath %>/game/play?gameId=<%= match.getGameId() %>&role=<%= match.getRole() %>&opponent=<%= java.net.URLEncoder.encode(match.getOpponentName(), "UTF-8") %>"
           class="btn btn-outline btn-sm ama-btn">
            <i class="fa-solid fa-play"></i> Resume Match
        </a>
    </div>
    <%
        }
    } else {
        ActiveGameSession activeGame = (ActiveGameSession) request.getAttribute("activeGame");
        if (activeGame != null) {
    %>
    <!-- Dynamic Active Match Quick Return Alert -->
    <div class="active-match-alert" style="margin-bottom: 1rem;">
        <div class="ama-content">
            <span class="ama-icon"><i class="fa-solid fa-gamepad fa-beat"></i></span>
            <span class="ama-text">
                    You have an active match in progress against <strong><%= activeGame.getOpponentName() %></strong>!
                </span>
        </div>
        <a href="<%= contextPath %>/game/play?gameId=<%= activeGame.getGameId() %>&role=<%= activeGame.getRole() %>&opponent=<%= java.net.URLEncoder.encode(activeGame.getOpponentName(), "UTF-8") %>"
           class="btn btn-outline btn-sm ama-btn">
            <i class="fa-solid fa-play"></i> Go Back to Game
        </a>
    </div>
    <%
            }
        }
    %>

    <% if (userObj == null) { %>
    <!-- Guest Welcome Banner -->
    <div class="welcome-banner guest-banner">
        <div class="welcome-text">
            <h1>Welcome to <span class="username">Dots &amp; Boxes</span>!</h1>
            <p>Join the classic competitive board game arena. Login or create a free account to challenge players and
                climb the ranks!</p>
        </div>
        <div class="navbar-auth-buttons" style="display: flex; gap: 0.8rem;">
            <a href="<%= contextPath %>/auth/login" class="btn btn-outline">Login</a>
            <a href="<%= contextPath %>/auth/register" class="btn btn-primary">Register</a>
        </div>
    </div>
    <% } else { %>
    <!-- Top Welcome Banner -->
    <div class="welcome-banner">
        <div class="welcome-text">
            <h1>Hello, <span class="username"><%= username %></span>!</h1>
            <p>Ready for a match? Challenge online players or manage your stats below.</p>
        </div>
        <div class="status-indicator">
            <span class="pulse-icon"><i class="fa-solid fa-circle"></i></span>
            <span class="status-text">Connected to Server</span>
        </div>
    </div>
    <% } %>

    <!-- Main Grid Dashboard layout -->
    <div class="dashboard-grid <%= userObj == null ? "guest-dashboard-grid" : "" %>">

        <!-- Left Column: Stats & Invites (Authenticated) or Search & Promo (Guest) -->
        <div class="grid-left">
            <% if (userObj != null) { %>
            <!-- Statistics Card -->
            <div class="card stats-card">
                <div class="card-header">
                    <h2 class="card-title"><i class="fa-solid fa-trophy"></i> Your Statistics</h2>
                </div>
                <div class="card-body">
                    <div class="stats-grid">
                        <div class="stat-item">
                            <span class="stat-value"><%= played %></span>
                            <span class="stat-label">Played</span>
                        </div>
                        <div class="stat-item highlight-win">
                            <span class="stat-value"><%= won %></span>
                            <span class="stat-label">Wins</span>
                        </div>
                        <div class="stat-item highlight-loss">
                            <span class="stat-value"><%= lost %></span>
                            <span class="stat-label">Losses</span>
                        </div>
                        <div class="stat-item">
                            <span class="stat-value"><%= winRateStr %></span>
                            <span class="stat-label">Win Rate</span>
                        </div>
                    </div>
                    <div class="playtime-summary">
                        <i class="fa-regular fa-clock"></i>
                        <span>Total Playtime:
                                <strong>
                                    <%= playtimeMin %> min
                                </strong>
                            </span>
                    </div>
                </div>
            </div>

            <!-- Invites List Card -->
            <div class="card invites-card" id="invites-card-element">
                <div class="card-header">
                    <h2 class="card-title"><i class="fa-solid fa-envelope-open-text"></i> Pending Game Invites</h2>
                </div>
                <div class="card-body">
                    <div id="no-invites-fallback" class="no-data-fallback">
                        <i class="fa-solid fa-inbox fallback-icon"></i>
                        <p>No pending invitations at the moment.</p>
                        <span>Active challenges will appear here in real-time.</span>
                    </div>
                    <div id="active-invites-list" class="invites-list" style="display: none;">
                        <!-- JS injects invite list items here -->
                    </div>
                </div>
            </div>
            <% } else { %>
            <!-- Real-time Autocomplete Search Card for Guests -->
            <div class="card search-card">
                <div class="card-header">
                    <h2 class="card-title"><i class="fa-solid fa-user-plus"></i> Search Players</h2>
                    <p class="card-subtitle">Search for active players to view their stats, records, and profiles</p>
                </div>
                <div class="card-body">
                    <div class="search-box">
                        <div class="search-input-wrapper">
                            <i class="fa-solid fa-magnifying-glass search-icon"></i>
                            <input type="text" id="player-search-input" class="form-control"
                                   placeholder="Type a player name..." autocomplete="off">
                            <button type="button" id="clear-search-btn" class="clear-btn" style="display: none;">
                                <i class="fa-solid fa-circle-xmark"></i>
                            </button>
                        </div>

                        <!-- Autocomplete Dropdown list results -->
                        <div id="search-autocomplete-dropdown" class="autocomplete-dropdown" style="display: none;">
                            <div class="dropdown-loader" id="dropdown-loader" style="display: none;">
                                <i class="fa-solid fa-circle-notch fa-spin"></i> Searching...
                            </div>
                            <div id="autocomplete-results-container" class="results-container">
                                <!-- Search results injected here -->
                            </div>
                        </div>
                    </div>

                    <div class="search-tip">
                        <i class="fa-regular fa-lightbulb"></i>
                        <span>Start typing above to search case-insensitively across registered game players.</span>
                    </div>
                </div>
            </div>

            <!-- Guest Promo Card -->
            <div class="card guest-promo-card">
                <div class="card-header">
                    <h2 class="card-title"><i class="fa-solid fa-gamepad"></i> Join the Competition</h2>
                </div>
                <div class="card-body">
                    <p class="promo-text">Dots and Boxes is a classic strategy game where players take turns drawing
                        lines on a grid. Complete a square to score a point!</p>
                    <div class="promo-features"
                         style="display: flex; flex-direction: column; gap: 0.8rem; margin: 1rem 0;">
                        <div class="feature-item" style="display: flex; align-items: center; gap: 0.5rem;"><i
                                class="fa-solid fa-bolt text-success"></i> <span>Live TCP matchmaking</span></div>
                        <div class="feature-item" style="display: flex; align-items: center; gap: 0.5rem;"><i
                                class="fa-solid fa-chart-line text-info"></i>
                            <span>Detailed match history & statistics</span></div>
                        <div class="feature-item" style="display: flex; align-items: center; gap: 0.5rem;"><i
                                class="fa-solid fa-medal text-warning"></i> <span>Global player leaderboard ranks</span>
                        </div>
                    </div>
                    <div style="margin-top: 1.5rem; text-align: center;">
                        <a href="<%= contextPath %>/auth/register" class="btn btn-primary"
                           style="display: block; width: 100%;">Create Your Free Account</a>
                    </div>
                </div>
            </div>
            <% } %>
        </div>

        <!-- Right Column: Search & Challenge (Authenticated) or Leaderboard (Both) -->
        <div class="grid-right">
            <% if (userObj != null) { %>
            <!-- Real-time Autocomplete Search Card -->
            <div class="card search-card">
                <div class="card-header">
                    <h2 class="card-title"><i class="fa-solid fa-user-plus"></i> Challenge Players</h2>
                    <p class="card-subtitle">Search for active players to send an instant match invitation or view their
                        profiles</p>
                </div>
                <div class="card-body">
                    <div class="search-box">
                        <div class="search-input-wrapper">
                            <i class="fa-solid fa-magnifying-glass search-icon"></i>
                            <input type="text" id="player-search-input" class="form-control"
                                   placeholder="Type a player name to challenge..." autocomplete="off">
                            <button type="button" id="clear-search-btn" class="clear-btn" style="display: none;">
                                <i class="fa-solid fa-circle-xmark"></i>
                            </button>
                        </div>

                        <!-- Autocomplete Dropdown list results -->
                        <div id="search-autocomplete-dropdown" class="autocomplete-dropdown" style="display: none;">
                            <div class="dropdown-loader" id="dropdown-loader" style="display: none;">
                                <i class="fa-solid fa-circle-notch fa-spin"></i> Searching...
                            </div>
                            <div id="autocomplete-results-container" class="results-container">
                                <!-- Search results injected here -->
                            </div>
                        </div>
                    </div>

                    <div class="search-tip">
                        <i class="fa-regular fa-lightbulb"></i>
                        <span>Start typing above to search case-insensitively across registered game players.</span>
                    </div>
                </div>
            </div>

            <!-- Outgoing Pending Invite Card -->
            <div class="card sent-invite-card" id="sent-invite-card-element" style="display: none;">
                <div class="card-header">
                    <h2 class="card-title"><i class="fa-solid fa-hourglass-half"></i> Invitation Sent</h2>
                </div>
                <div class="card-body">
                    <div class="sent-invite-status">
                        <div class="spinner-circle">
                            <div class="double-bounce1"></div>
                            <div class="double-bounce2"></div>
                        </div>
                        <p>Waiting for <strong id="sent-invite-opponent-name">Player</strong> to accept...</p>
                        <button type="button" id="cancel-sent-invite-btn"
                                class="btn btn-outline-danger btn-sm btn-block">
                            <i class="fa-solid fa-ban"></i> Cancel Invitation
                        </button>
                    </div>
                </div>
            </div>
            <% } %>

            <!-- Global Rankings Leaderboard Card (Visible to BOTH guest and authenticated!) -->
            <%
                List<UserDTO> leaderboard = (List<UserDTO>) request.getAttribute("leaderboard");
                if (leaderboard != null && !leaderboard.isEmpty()) {
            %>
            <div class="card dashboard-leaderboard-card">
                <div class="card-header"
                     style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 0.5rem; border-bottom: 1px solid var(--border-color); padding-bottom: 1rem; margin-bottom: 1rem;">
                    <h2 class="card-title"><i class="fa-solid fa-list-ol"></i> Global Rankings</h2>
                    <a href="<%= contextPath %>/leaderboard" class="btn btn-outline btn-xs"
                       style="padding: 4px 8px; font-size: 0.75rem; border-radius: 8px; text-decoration: none;">View
                        Full Board</a>
                </div>
                <div class="card-body">
                    <div class="table-responsive">
                        <table class="leaderboard-table dashboard-comp-table"
                               style="font-size: 0.85rem; width: 100%; border-collapse: collapse;">
                            <thead>
                            <tr style="border-bottom: 1px solid rgba(255, 255, 255, 0.08); text-align: left;">
                                <th class="rank-col" style="padding-bottom: 0.5rem;">Rank</th>
                                <th style="padding-bottom: 0.5rem;">Player</th>
                                <th style="padding-bottom: 0.5rem;">Nationality</th>
                                <th style="text-align: right; padding-bottom: 0.5rem;">Wins</th>
                                <th style="text-align: right; padding-bottom: 0.5rem;">Win Rate</th>
                            </tr>
                            </thead>
                            <tbody>
                            <%
                                int count = Math.min(leaderboard.size(), 5); // Show top 5 on dashboard
                                for (int i = 0; i < count; i++) {
                                    UserDTO u = leaderboard.get(i);
                                    PlayerStats s = u.stats();
                                    int leaderboardWins = s != null ? s.gamesWon() : 0;
                                    float wr = s != null ? s.winRate() : 0.0f;
                                    String wrPct = String.format("%.1f%%", wr * 100.0);
                                    String rankClass = (i == 0) ? "rank-1" : (i == 1) ? "rank-2" : (i == 2) ? "rank-3" : "";
                            %>
                            <tr class="<%= rankClass %>" style="border-bottom: 1px solid rgba(255, 255, 255, 0.03);">
                                <td class="rank-col" style="padding: 0.7rem 0.2rem;">
                                    <span class="rank-number"
                                          style="font-size: 0.75rem; width: 22px; height: 22px; display: inline-flex; align-items: center; justify-content: center; border-radius: 50%; font-weight: bold; background-color: rgba(255, 255, 255, 0.05);"><%= i + 1 %></span>
                                </td>
                                <td style="padding: 0.7rem 0.2rem;">
                                    <a href="<%= contextPath %>/profile?username=<%= u.username() %>"
                                       class="leaderboard-player-link"
                                       style="display: flex; align-items: center; gap: 0.5rem; font-size: 0.85rem; text-decoration: none; color: inherit;">
                                        <% if (u.photo() != null && !u.photo().isEmpty()) { %>
                                        <img src="<%= contextPath %>/photo/<%= u.photo() %>" alt="Avatar"
                                             class="player-avatar"
                                             style="width: 24px; height: 24px; border-radius: 50%; object-fit: cover;">
                                        <% } else { %>
                                        <div class="player-avatar avatar-placeholder"
                                             style="width: 24px; height: 24px; border-radius: 50%; background-color: rgba(255, 255, 255, 0.05); display: flex; align-items: center; justify-content: center; color: var(--text-muted); font-size: 0.7rem;">
                                            <i class="fa-solid fa-user"></i>
                                        </div>
                                        <% } %>
                                        <span class="player-name-label"
                                              style="font-weight: 600;"><%= u.username() %></span>
                                    </a>
                                </td>
                                <td style="padding: 0.7rem 0.2rem;">
                                    <% if (u.nationality() != null && !u.nationality().isEmpty()) { %>
                                    <span class="flag-badge"
                                          style="display: inline-flex; align-items: center; gap: 4px; padding: 2px 6px; font-size: 0.7rem; background-color: rgba(255, 255, 255, 0.05); border: 1px solid rgba(255, 255, 255, 0.08); border-radius: 8px;">
                                                        <img src="https://flagcdn.com/16x12/<%= u.nationality().toLowerCase() %>.png"
                                                             alt="<%= u.nationality() %> flag"
                                                             style="border-radius: 1px; box-shadow: 0 1px 2px rgba(0,0,0,0.2);">
                                                        <span><%= u.nationality() %></span>
                                                    </span>
                                    <% } else { %>
                                    <span style="opacity: 0.3;">-</span>
                                    <% } %>
                                </td>
                                <td style="text-align: right; font-weight: 600; color: var(--text-success); padding: 0.7rem 0.2rem;"><%= leaderboardWins %>
                                </td>
                                <td style="text-align: right; font-weight: 600; padding: 0.7rem 0.2rem;"><%= wrPct %>
                                </td>
                            </tr>
                            <% } %>
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
            <% } %>
        </div>
    </div>
</div>

<!-- Modal Dialog overlay for Game Launch / Accept -->
<div class="modal-overlay" id="game-lobby-modal" style="display: none;">
    <div class="modal-card">
        <div class="modal-icon accent">
            <i class="fa-solid fa-gamepad fa-bounce"></i>
        </div>
        <h3 class="modal-title">Challenge Accepted!</h3>
        <p class="modal-text">Get ready! You are entering the match against <strong
                id="lobby-opponent-name">Opponent</strong>.</p>
        <div class="modal-actions">
            <span class="loading-lobby"><i class="fa-solid fa-circle-notch fa-spin"></i> Setting up game board...</span>
        </div>
    </div>
</div>

<jsp:include page="common/footer.jsp"/>
