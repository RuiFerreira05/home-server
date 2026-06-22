<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
    String pageScript = (String) request.getAttribute("pageScript");
    boolean isLoggedIn = false;
    
    HttpSession httpSession = request.getSession(false);
    if (httpSession != null && httpSession.getAttribute("user") != null) {
        isLoggedIn = true;
    }
    
    String contextPath = request.getContextPath();
%>
    </main>

    <!-- Premium Styling Footer -->
    <footer class="app-footer">
        <div class="footer-container">
            <p class="copyright">&copy; 2026 Dots &amp; Boxes Interactive. All rights reserved.</p>
            <div class="footer-links">
                <span>Faculty Project</span>
                <span class="divider">|</span>
                <span>Course: IECD</span>
            </div>
        </div>
    </footer>

    <!-- Bootstrap context path global variable for JS references -->
    <script>
        window.contextPath = '<%= contextPath %>';
    </script>

    <!-- Core polling script (only loaded when user session exists) -->
    <% if (isLoggedIn) { %>
        <script src="<%= contextPath %>/js/push.js"></script>
    <% } %>

    <!-- Page Specific Script Import -->
    <% if (pageScript != null && !pageScript.isEmpty()) { %>
        <script src="<%= contextPath %>/js/<%= pageScript %>"></script>
    <% } %>

</body>
</html>
