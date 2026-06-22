<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
    // Standard request scope setting using plain Java scriptlets
    request.setAttribute("pageStyle", "login.css");
    
    String error = (String) request.getAttribute("error");
    String contextPath = request.getContextPath();
%>
<jsp:include page="common/header.jsp"/>

<div class="auth-wrapper">
    <div class="auth-card">
        <div class="auth-header">
            <h1 class="auth-title">Get Started</h1>
            <p class="auth-subtitle">Register a new player profile and start competing today</p>
        </div>

        <!-- Error Notification -->
        <% if (error != null) { %>
            <div class="alert alert-danger">
                <i class="fa-solid fa-circle-exclamation alert-icon"></i>
                <div class="alert-content">
                    <span class="alert-title">Registration Failed</span>
                    <span class="alert-text"><%= error %></span>
                </div>
            </div>
        <% } %>

        <form action="<%= contextPath %>/auth/register" method="POST" class="auth-form" onsubmit="return validatePasswords()">
            <div class="form-group">
                <label for="username" class="form-label">Username</label>
                <div class="input-wrapper">
                    <i class="fa-solid fa-user input-icon"></i>
                    <input type="text" id="username" name="username" class="form-control" placeholder="Choose a username" required minlength="3" autocomplete="username">
                </div>
            </div>

            <div class="form-group">
                <label for="password" class="form-label">Password</label>
                <div class="input-wrapper">
                    <i class="fa-solid fa-lock input-icon"></i>
                    <input type="password" id="password" name="password" class="form-control" placeholder="Create a strong password" required minlength="4" autocomplete="new-password">
                </div>
            </div>

            <div class="form-group">
                <label for="confirmPassword" class="form-label">Confirm Password</label>
                <div class="input-wrapper">
                    <i class="fa-solid fa-check-double input-icon"></i>
                    <input type="password" id="confirmPassword" name="confirmPassword" class="form-control" placeholder="Confirm your password" required autocomplete="new-password">
                </div>
            </div>
            
            <div id="js-error" class="alert alert-danger" style="display: none; margin-bottom: 1.5rem;">
                <i class="fa-solid fa-circle-exclamation alert-icon"></i>
                <div class="alert-content">
                    <span class="alert-text" id="js-error-text"></span>
                </div>
            </div>

            <button type="submit" class="btn btn-primary btn-block">
                <span>Sign Up</span> <i class="fa-solid fa-user-plus"></i>
            </button>
        </form>

        <div class="auth-footer">
            <p class="footer-text">Already registered?</p>
            <a href="<%= contextPath %>/auth/login" class="auth-link">Sign In Instead</a>
        </div>
    </div>
</div>

<script>
    function validatePasswords() {
        var password = document.getElementById("password").value;
        var confirm = document.getElementById("confirmPassword").value;
        var errorDiv = document.getElementById("js-error");
        var errorText = document.getElementById("js-error-text");

        if (password !== confirm) {
            errorText.textContent = "Passwords do not match.";
            errorDiv.style.display = "flex";
            return false;
        }
        errorDiv.style.display = "none";
        return true;
    }
</script>

<jsp:include page="common/footer.jsp"/>
