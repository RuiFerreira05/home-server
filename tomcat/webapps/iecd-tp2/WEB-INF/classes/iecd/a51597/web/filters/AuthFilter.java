package iecd.a51597.web.filters;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Protects application routes from unauthenticated access.
 * Allows login, registration, and static resources to bypass check.
 */
@WebFilter("/*")
public class AuthFilter implements Filter {

    /**
     * Initializes the filter with the specified configuration.
     *
     * @param filterConfig the filter configuration object
     * @throws ServletException if a servlet exception occurs during initialization
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}

    /**
     * Intercepts incoming requests to verify authentication before allowing access
     * to protected routes. Unauthenticated requests are redirected to the login page.
     *
     * @param request the servlet request
     * @param response the servlet response
     * @param chain the filter chain to forward the request
     * @throws IOException if an I/O error occurs
     * @throws ServletException if a servlet-specific error occurs
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String path = httpRequest.getRequestURI().substring(httpRequest.getContextPath().length());
        
        // Normalize path (remove trailing slashes for routing checks)
        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }

        // 1. Check if it's a static resource, an API endpoint, or an auth route
        boolean isStaticAsset = path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/photo/");
        boolean isAuthRoute = path.startsWith("/auth");
        boolean isRootPath = path.equals("") || path.equals("/");
        boolean isProfileView = path.startsWith("/profile") && !path.startsWith("/profile/edit");
        boolean isLeaderboardView = "/leaderboard".equals(path);
        boolean isDashboardView = "/dashboard".equals(path);
        boolean isApiSearch = "/api/search".equals(path);

        if (isStaticAsset || isAuthRoute || isRootPath || isProfileView || isLeaderboardView || isDashboardView || isApiSearch) {
            // Let request through normally
            chain.doFilter(request, response);
            return;
        }

        // 2. Validate current session and logged-in user
        HttpSession session = httpRequest.getSession(false);
        boolean loggedIn = false;
        
        if (session != null) {
            Object user = session.getAttribute("user");
            Object bridge = session.getAttribute("server_bridge");
            if (user != null && bridge != null) {
                loggedIn = true;
            }
        }

        // 3. Act based on auth state
        if (loggedIn) {
            chain.doFilter(request, response);
        } else {
            // Unauthenticated: redirect to login
            httpResponse.sendRedirect(httpRequest.getContextPath() + "/auth/login");
        }
    }

    /**
     * Cleans up filter resources. Called when the filter is being destroyed.
     */
    @Override
    public void destroy() {}
}
