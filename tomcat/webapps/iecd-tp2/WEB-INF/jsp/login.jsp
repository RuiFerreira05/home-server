<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
    // Standard request scope setting using plain Java scriptlets
    request.setAttribute("pageStyle", "login.css");
    
    String error = (String) request.getAttribute("error");
    boolean isRegistered = request.getParameter("registered") != null;
    
    String contextPath = request.getContextPath();
%>
<jsp:include page="common/header.jsp"/>

<div class="auth-wrapper">
    <div class="auth-card">
        <div class="auth-header">
            <h1 class="auth-title">Welcome Back</h1>
            <p class="auth-subtitle">Login to connect with active players and join match lobbies</p>
        </div>

        <!-- Success Notification (e.g. from registration) -->
        <% if (isRegistered) { %>
            <div class="alert alert-success">
                <i class="fa-solid fa-circle-check alert-icon"></i>
                <div class="alert-content">
                    <span class="alert-title">Account Created!</span>
                    <span class="alert-text">You can now sign in using your username and password.</span>
                </div>
            </div>
        <% } %>

        <!-- Error Notification -->
        <% if (error != null) { %>
            <div class="alert alert-danger">
                <i class="fa-solid fa-circle-exclamation alert-icon"></i>
                <div class="alert-content">
                    <span class="alert-title">Authentication Error</span>
                    <span class="alert-text"><%= error %></span>
                </div>
            </div>
        <% } %>

        <form action="<%= contextPath %>/auth/login" method="POST" class="auth-form">
            <div class="form-group">
                <label for="username" class="form-label">Username</label>
                <div class="input-wrapper">
                    <i class="fa-solid fa-user input-icon"></i>
                    <input type="text" id="username" name="username" class="form-control" placeholder="Enter your username" required autocomplete="username">
                </div>
            </div>

            <div class="form-group">
                <label for="password" class="form-label">Password</label>
                <div class="input-wrapper">
                    <i class="fa-solid fa-lock input-icon"></i>
                    <input type="password" id="password" name="password" class="form-control" placeholder="Enter your password" required autocomplete="current-password">
                </div>
            </div>

            <button type="submit" class="btn btn-primary btn-block">
                <span>Sign In</span> <i class="fa-solid fa-arrow-right-to-bracket"></i>
            </button>
        </form>

        <div class="auth-footer">
            <p class="footer-text">New to Dots &amp; Boxes?</p>
            <a href="<%= contextPath %>/auth/register" class="auth-link">Create an Account</a>
        </div>
    </div>
</div>

<jsp:include page="common/footer.jsp"/>
