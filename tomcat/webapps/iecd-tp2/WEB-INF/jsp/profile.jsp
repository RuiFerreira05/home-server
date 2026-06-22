<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
    // Standard request scope setting using plain Java scriptlets
    request.setAttribute("pageStyle", "profile.css");
    request.setAttribute("pageActive", "profile");

    iecd.a51597.common.store.UserDTO loggedInUser = null;
    HttpSession httpSession = request.getSession(false);
    if (httpSession != null) {
        loggedInUser = (iecd.a51597.common.store.UserDTO) httpSession.getAttribute("user");
    }

    iecd.a51597.common.store.UserDTO userObj = (iecd.a51597.common.store.UserDTO) request.getAttribute("user");
    String error = (String) request.getAttribute("error");

    String username = "";
    String photo = "";
    String nationality = "";
    java.time.LocalDate dob = null;
    java.util.List<iecd.a51597.common.store.PlayerStats.MatchRecord> matches = null;

    if (userObj != null) {
        username = userObj.username();
        photo = userObj.photo();
        nationality = userObj.nationality();
        dob = userObj.dob();
        if (userObj.stats() != null) {
            matches = userObj.stats().matches();
        }
    }
    
    boolean isOwnProfile = loggedInUser != null && userObj != null && loggedInUser.userId().equals(userObj.userId());
    String contextPath = request.getContextPath();
%>
<jsp:include page="common/header.jsp"/>

<div class="profile-wrapper">
    
    <!-- Top Action Headers -->
    <div class="profile-header-actions">
        <% if (loggedInUser != null) { %>
            <a href="<%= contextPath %>/dashboard" class="btn btn-outline btn-sm">
                <i class="fa-solid fa-arrow-left"></i> Back to Dashboard
            </a>
        <% } else { %>
            <a href="<%= contextPath %>/leaderboard" class="btn btn-outline btn-sm">
                <i class="fa-solid fa-trophy"></i> View Leaderboard
            </a>
            <a href="<%= contextPath %>/auth/login" class="btn btn-outline btn-sm">
                <i class="fa-solid fa-right-to-bracket"></i> Login
            </a>
        <% } %>
        
        <% if (isOwnProfile) { %>
            <a href="<%= contextPath %>/profile/edit" class="btn btn-primary btn-sm">
                <i class="fa-solid fa-user-gear"></i> Edit Profile
            </a>
        <% } %>
    </div>

    <% if (error != null) { %>
        <div class="card" style="margin-top: 2rem;">
            <div class="no-data-fallback">
                <i class="fa-solid fa-circle-exclamation fallback-icon" style="color: var(--text-danger);"></i>
                <p>Lookup Failed</p>
                <span><%= error %></span>
            </div>
        </div>
    <% } else { %>
        <div class="profile-layout-grid">
            
            <!-- Left: Profile Info Card -->
            <div class="profile-sidebar">
                <div class="card profile-user-card">
                    <div class="user-card-top">
                        <% if (photo != null && !photo.isEmpty()) { %>
                            <img src="<%= contextPath %>/photo/<%= photo %>" alt="Avatar" class="avatar-large">
                        <% } else { %>
                            <div class="avatar-large placeholder-icon">
                                <i class="fa-solid fa-user"></i>
                            </div>
                        <% } %>
                        <h1 class="username-title"><%= username %></h1>
                    </div>
                    
                    <hr class="card-divider">
                    
                    <div class="user-details-list">
                        <div class="detail-item">
                            <span class="detail-label"><i class="fa-solid fa-earth-americas"></i> Nationality</span>
                            <span class="detail-value">
                                <% if (nationality != null && !nationality.isEmpty()) { %>
                                    <span class="flag-icon-span" style="display: inline-flex; align-items: center; gap: 6px;">
                                        <img src="https://flagcdn.com/16x12/<%= nationality.toLowerCase() %>.png" alt="<%= nationality %> flag" style="border-radius: 2px; box-shadow: 0 1px 2px rgba(0,0,0,0.2);">
                                        <span><%= nationality %></span>
                                    </span>
                                <% } else { %>
                                    Not specified
                                <% } %>
                            </span>
                        </div>
                        
                        <div class="detail-item">
                            <span class="detail-label"><i class="fa-solid fa-cake-candles"></i> Birthday</span>
                            <span class="detail-value">
                                <% if (dob != null) { %>
                                    <span><%= dob %></span> <span class="age-text">(<%= userObj.getAge() %> yrs old)</span>
                                <% } else { %>
                                    Not specified
                                <% } %>
                            </span>
                        </div>

                        <div class="detail-item">
                            <span class="detail-label"><i class="fa-solid fa-palette"></i> Game Background</span>
                            <span class="detail-value" style="display: flex; align-items: center; gap: 8px;">
                                <% if (userObj.favoriteColor() != null && !userObj.favoriteColor().isEmpty()) { %>
                                    <span style="display: inline-block; width: 16px; height: 16px; border-radius: 4px; border: 1px solid var(--border-color); background-color: <%= userObj.favoriteColor() %>;"></span>
                                    <span><%= userObj.favoriteColor() %></span>
                                <% } else { %>
                                    Default (#0b0c13)
                                <% } %>
                            </span>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Right: Match History Table -->
            <div class="profile-main">
                <div class="card history-card">
                    <div class="card-header">
                        <h2 class="card-title"><i class="fa-solid fa-clock-rotate-left"></i> Match History</h2>
                        <p class="card-subtitle">Summary of all registered game results played on this server</p>
                    </div>
                    <div class="card-body">
                        <% if (matches == null || matches.isEmpty()) { %>
                            <div class="no-data-fallback">
                                <i class="fa-solid fa-scroll fallback-icon"></i>
                                <p>No match history available.</p>
                                <span>Complete your first challenge to see match stats here!</span>
                            </div>
                        <% } else { %>
                            <div class="table-responsive">
                                <table class="history-table">
                                    <thead>
                                        <tr>
                                            <th>Result</th>
                                            <th>Opponent</th>
                                            <th>Playtime</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        <% for (iecd.a51597.common.store.PlayerStats.MatchRecord match : matches) { 
                                            int min = (int) (match.playtimeSecs() / 60.0);
                                            int sec = (int) (match.playtimeSecs() % 60.0);
                                        %>
                                            <tr>
                                                <td>
                                                    <% if (match.won()) { %>
                                                        <span class="match-badge match-win"><i class="fa-solid fa-medal animate-badge"></i> WIN</span>
                                                    <% } else { %>
                                                        <span class="match-badge match-loss"><i class="fa-solid fa-skull"></i> LOSS</span>
                                                    <% } %>
                                                </td>
                                                <td>
                                                    <span class="opponent-name">
                                                        <% if (match.opponentUsername() != null && !match.opponentUsername().isEmpty()) { %>
                                                            <a href="<%= contextPath %>/profile?username=<%= match.opponentUsername() %>" class="player-name-link"><%= match.opponentUsername() %></a>
                                                        <% } else { %>
                                                            Unknown Player
                                                        <% } %>
                                                    </span>
                                                </td>
                                                <td>
                                                    <span class="match-playtime">
                                                        <%= min %>m <%= sec %>s
                                                    </span>
                                                </td>
                                            </tr>
                                        <% } %>
                                    </tbody>
                                </table>
                            </div>
                        <% } %>
                    </div>
                </div>
            </div>

        </div>
    <% } %>
</div>

<jsp:include page="common/footer.jsp"/>
