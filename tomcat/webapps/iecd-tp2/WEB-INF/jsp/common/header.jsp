<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
    // Retrieve attributes using plain Java
    String pageStyle = (String) request.getAttribute("pageStyle");
    String pageActive = (String) request.getAttribute("pageActive");
    iecd.a51597.common.store.UserDTO userObj = null;
    
    HttpSession httpSession = request.getSession(false);
    if (httpSession != null) {
        userObj = (iecd.a51597.common.store.UserDTO) httpSession.getAttribute("user");
    }
    
    String contextPath = request.getContextPath();
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Dots &amp; Boxes | Interactive Gaming</title>
    
    <!-- Design Typography: Outfit (Heading) and Inter (Body) -->
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&family=Outfit:wght@400;500;600;700;800&display=swap" rel="stylesheet">
    
    <!-- FontAwesome Icons for modern aesthetic -->
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    
    <!-- Core Style System & Page Stylesheets -->
    <link rel="stylesheet" href="<%= contextPath %>/css/common.css">
    <% if (pageStyle != null && !pageStyle.isEmpty()) { %>
        <link rel="stylesheet" href="<%= contextPath %>/css/<%= pageStyle %>">
    <% } %>
</head>
<body>

    <!-- Premium Dynamic Header Bar -->
    <header class="app-navbar">
        <div class="navbar-container">
            <a href="<%= contextPath %>/dashboard" class="navbar-brand">
                <i class="fa-solid fa-gamepad brand-icon"></i>
                <span class="brand-text">Dots <span class="accent-text">&amp;</span> Boxes</span>
            </a>
            
            <% if (userObj != null) { %>
                <!-- Logged in navigation options -->
                <nav class="navbar-links">
                    <a href="<%= contextPath %>/dashboard" class="nav-link <%= "dashboard".equals(pageActive) ? "active" : "" %>">
                        <i class="fa-solid fa-chart-line"></i> Dashboard
                    </a>
                    <a href="<%= contextPath %>/profile" class="nav-link <%= "profile".equals(pageActive) ? "active" : "" %>">
                        <i class="fa-solid fa-user"></i> My Profile
                    </a>
                    <a href="<%= contextPath %>/leaderboard" class="nav-link <%= "leaderboard".equals(pageActive) ? "active" : "" %>">
                        <i class="fa-solid fa-trophy"></i> Leaderboard
                    </a>
                </nav>
                
                <!-- Logged in User widget and dropdown info -->
                <div class="navbar-user">
                    <div class="user-info">
                        <span class="username"><%= userObj.username() %></span>
                    </div>
                    
                    <% if (userObj.photo() != null && !userObj.photo().isEmpty()) { %>
                        <img src="<%= contextPath %>/photo/<%= userObj.photo() %>" alt="Profile Avatar" class="user-avatar">
                    <% } else { %>
                        <div class="user-avatar avatar-placeholder">
                            <i class="fa-solid fa-user"></i>
                        </div>
                    <% } %>

                    <!-- Logout quick link -->
                    <form action="<%= contextPath %>/auth/logout" method="POST" style="margin: 0; display: inline;">
                        <button type="submit" class="logout-btn" title="Logout">
                            <i class="fa-solid fa-power-off"></i>
                        </button>
                    </form>
                </div>
            <% } else { %>
                <!-- Public navigation options for guest users -->
                <nav class="navbar-links">
                    <a href="<%= contextPath %>/leaderboard" class="nav-link <%= "leaderboard".equals(pageActive) ? "active" : "" %>">
                        <i class="fa-solid fa-trophy"></i> Leaderboard
                    </a>
                </nav>
                <!-- Logged out layout buttons -->
                <div class="navbar-auth-buttons">
                    <a href="<%= contextPath %>/auth/login" class="btn btn-outline btn-sm">Login</a>
                    <a href="<%= contextPath %>/auth/register" class="btn btn-primary btn-sm">Register</a>
                </div>
            <% } %>
        </div>
    </header>

    <!-- Global Toast Alerts container for real-time invite triggers -->
    <div id="toast-container" class="toast-container"></div>
    
    <!-- Main page wrapper -->
    <main class="main-content">
