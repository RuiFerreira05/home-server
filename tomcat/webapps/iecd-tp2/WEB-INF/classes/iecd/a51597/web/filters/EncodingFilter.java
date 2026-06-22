package iecd.a51597.web.filters;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import java.io.IOException;

/**
 * Ensures all HTTP requests and responses use UTF-8 character encoding.
 */
@WebFilter("/*")
public class EncodingFilter implements Filter {

    /**
     * Initializes the encoding filter.
     *
     * @param filterConfig the filter configuration object
     * @throws ServletException if a servlet exception occurs during initialization
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}

    /**
     * Sets the request and response character encoding to UTF-8 and passes the request
     * down the filter chain.
     *
     * @param request the servlet request
     * @param response the servlet response
     * @param chain the filter chain
     * @throws IOException if an I/O error occurs
     * @throws ServletException if a servlet error occurs
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        chain.doFilter(request, response);
    }

    /**
     * Cleans up filter resources. Called when the filter is being destroyed.
     */
    @Override
    public void destroy() {}
}
