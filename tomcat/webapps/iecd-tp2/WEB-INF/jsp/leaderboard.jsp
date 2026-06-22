<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.util.List" %>
<%@ page import="iecd.a51597.common.store.UserDTO" %>
<%@ page import="iecd.a51597.common.store.PlayerStats" %>
<%
    // Standard request scope setting using plain Java scriptlets
    request.setAttribute("pageStyle", "leaderboard.css");
    request.setAttribute("pageActive", "leaderboard");

    List<UserDTO> leaderboard = (List<UserDTO>) request.getAttribute("leaderboard");
    String contextPath = request.getContextPath();
%>
<jsp:include page="common/header.jsp"/>

<div class="leaderboard-wrapper">
    <!-- Title and Header section -->
    <div class="leaderboard-header">
        <div>
            <h1 class="leaderboard-title"><i class="fa-solid fa-trophy"></i> Global Leaderboard</h1>
            <p class="leaderboard-subtitle">Real-time player rankings ordered by wins and speed</p>
        </div>
    </div>

    <% if (leaderboard == null || leaderboard.isEmpty()) { %>
        <div class="card">
            <div class="no-data-fallback">
                <i class="fa-solid fa-ranking-star fallback-icon"></i>
                <p>No players found on the server.</p>
                <span>Complete registered profiles to populate the board!</span>
            </div>
        </div>
    <% } else { %>
        <div class="leaderboard-grid">
            
            <!-- Podium / Top 3 Row -->
            <div class="podium-container">
                <% 
                    // Render Silver (2nd) first for standard visual podium order: 2nd, 1st, 3rd
                    int[] podiumOrder = { 1, 0, 2 }; // Index: 1 = Silver, 0 = Gold, 2 = Bronze
                    for (int pIdx : podiumOrder) {
                        if (pIdx < leaderboard.size()) {
                            UserDTO user = leaderboard.get(pIdx);
                            PlayerStats stats = user.stats();
                            int wins = stats != null ? stats.gamesWon() : 0;
                            int losses = stats != null ? stats.gamesLost() : 0;
                            double playtime = stats != null ? stats.totalPlayTimeSecs() : 0.0;
                            int playtimeMin = (int) (playtime / 60.0);
                            String styleClass = (pIdx == 0) ? "podium-gold" : (pIdx == 1) ? "podium-silver" : "podium-bronze";
                            String medal = (pIdx == 0) ? "🥇 Gold" : (pIdx == 1) ? "🥈 Silver" : "🥉 Bronze";
                %>
                    <div class="podium-card <%= styleClass %>">
                        <div class="podium-rank">#<%= pIdx + 1 %></div>
                        <a href="<%= contextPath %>/profile?username=<%= user.username() %>" class="podium-player-link">
                            <div class="podium-avatar-wrapper">
                                <% if (user.photo() != null && !user.photo().isEmpty()) { %>
                                    <img src="<%= contextPath %>/photo/<%= user.photo() %>" alt="Avatar" class="podium-avatar">
                                <% } else { %>
                                    <div class="podium-avatar avatar-placeholder">
                                        <i class="fa-solid fa-user"></i>
                                    </div>
                                <% } %>
                                <span class="podium-badge"><%= medal %></span>
                            </div>
                            <div class="podium-username"><%= user.username() %></div>
                        </a>
                        <div class="podium-nationality" style="display: flex; align-items: center; justify-content: center; gap: 6px;">
                            <% if (user.nationality() != null && !user.nationality().isEmpty()) { %>
                                <img src="https://flagcdn.com/16x12/<%= user.nationality().toLowerCase() %>.png" alt="<%= user.nationality() %> flag" class="podium-flag" style="border-radius: 2px; box-shadow: 0 1px 3px rgba(0,0,0,0.3);">
                                <span><%= user.nationality() %></span>
                            <% } else { %>
                                <i class="fa-solid fa-earth-americas"></i> Global
                            <% } %>
                        </div>
                        <div class="podium-stats">
                            <div class="podium-stat-item">
                                <span class="podium-stat-val"><%= wins %></span>
                                <span class="podium-stat-label">Wins</span>
                            </div>
                            <div class="podium-stat-item">
                                <span class="podium-stat-val"><%= wins + losses %></span>
                                <span class="podium-stat-label">Played</span>
                            </div>
                            <div class="podium-stat-item">
                                <span class="podium-stat-val"><%= playtimeMin %>m</span>
                                <span class="podium-stat-label">Time</span>
                            </div>
                        </div>
                    </div>
                <% 
                        }
                    } 
                %>
            </div>

            <!-- Full Leaderboard Table Card -->
            <div class="card leaderboard-table-card">
                <div class="card-header">
                    <h2 class="card-title"><i class="fa-solid fa-list-ol"></i> Top Players Ranking</h2>
                </div>
                <div class="card-body">
                    <div class="table-responsive">
                        <table class="leaderboard-table">
                            <thead>
                                <tr>
                                    <th class="rank-col">Rank</th>
                                    <th>Player</th>
                                    <th>Nationality</th>
                                    <th>Played</th>
                                    <th>Won</th>
                                    <th>Lost</th>
                                    <th>Win Rate</th>
                                    <th>Playtime</th>
                                </tr>
                            </thead>
                            <tbody>
                                <% 
                                    for (int i = 0; i < leaderboard.size(); i++) {
                                        UserDTO user = leaderboard.get(i);
                                        PlayerStats stats = user.stats();
                                        int played = stats != null ? stats.gamesPlayed() : 0;
                                        int won = stats != null ? stats.gamesWon() : 0;
                                        int lost = stats != null ? stats.gamesLost() : 0;
                                        float winRate = stats != null ? stats.winRate() : 0.0f;
                                        double playtime = stats != null ? stats.totalPlayTimeSecs() : 0.0;
                                        int playtimeMin = (int) (playtime / 60.0);
                                        int playtimeSec = (int) (playtime % 60.0);
                                        String winRatePct = String.format("%.1f%%", winRate * 100.0);
                                        String rankClass = (i == 0) ? "rank-1" : (i == 1) ? "rank-2" : (i == 2) ? "rank-3" : "";
                                %>
                                    <tr class="<%= rankClass %>">
                                        <td class="rank-col">
                                            <span class="rank-number"><%= i + 1 %></span>
                                        </td>
                                        <td>
                                            <a href="<%= contextPath %>/profile?username=<%= user.username() %>" class="leaderboard-player-link">
                                                <div class="player-col">
                                                    <% if (user.photo() != null && !user.photo().isEmpty()) { %>
                                                        <img src="<%= contextPath %>/photo/<%= user.photo() %>" alt="Avatar" class="player-avatar">
                                                    <% } else { %>
                                                        <div class="player-avatar avatar-placeholder">
                                                            <i class="fa-solid fa-user"></i>
                                                        </div>
                                                    <% } %>
                                                    <span class="player-name-label"><%= user.username() %></span>
                                                </div>
                                            </a>
                                        </td>
                                        <td class="nat-col">
                                            <% if (user.nationality() != null && !user.nationality().isEmpty()) { %>
                                                <span class="flag-badge" style="display: inline-flex; align-items: center; gap: 6px; padding: 0.3rem 0.6rem;">
                                                    <img src="https://flagcdn.com/16x12/<%= user.nationality().toLowerCase() %>.png" alt="<%= user.nationality() %> flag" style="border-radius: 2px; box-shadow: 0 1px 2px rgba(0,0,0,0.2);">
                                                    <span><%= user.nationality() %></span>
                                                </span>
                                            <% } else { %>
                                                <span class="flag-badge" style="opacity: 0.5;">-</span>
                                            <% } %>
                                        </td>
                                        <td class="stats-number"><%= played %></td>
                                        <td class="stats-number text-success" style="color: var(--text-success); font-weight: 600;"><%= won %></td>
                                        <td class="stats-number text-danger" style="color: var(--text-danger); font-weight: 600;"><%= lost %></td>
                                        <td>
                                            <div class="win-rate-container">
                                                <span class="stats-number"><%= winRatePct %></span>
                                                <div class="win-rate-bar-container">
                                                    <div class="win-rate-bar" style="width: <%= (int)(winRate * 100) %>%;"></div>
                                                </div>
                                            </div>
                                        </td>
                                        <td class="stats-number">
                                            <%= playtimeMin %>m <%= playtimeSec %>s
                                        </td>
                                    </tr>
                                <% } %>
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>

        </div>
    <% } %>
</div>

<jsp:include page="common/footer.jsp"/>
