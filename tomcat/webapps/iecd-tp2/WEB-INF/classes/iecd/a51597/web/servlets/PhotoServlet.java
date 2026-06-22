package iecd.a51597.web.servlets;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Mapped to /photo/*.
 * Streams saved user profile photos directly from the disk storage directory 'data/photos/' via HTTP.
 * This bridges the game server's photo reference ID to browser-renderable image tags.
 */
@WebServlet("/photo/*")
public class PhotoServlet extends HttpServlet {

    private static final Logger logger = LogManager.getLogger(PhotoServlet.class);
    
    /**
     * Resolves the absolute path of the requested photo by trying multiple dynamic
     * strategies (web.xml parameter, parent workspace directory traversal, CWD).
     * Tests for the presence of the actual image file to bypass empty copy shadowing.
     */
    /**
     * Resolves the absolute path of the requested photo by trying multiple dynamic
     * strategies (classpath traversal, real path traversal, CWD, and user home).
     * Prints all checked paths to System.out to provide clear debug traces in the Eclipse Console.
     */
    private File resolvePhotoFile(String filename) {
        logger.debug("Resolving photo file: {}", filename);
        
        // 1. Try to read a context parameter from web.xml if configured
        String configPath = getServletContext().getInitParameter("photoStorePath");
        if (configPath != null && !configPath.trim().isEmpty()) {
            File dir = new File(configPath.trim());
            File target = new File(dir, filename);
            logger.debug("Checking web.xml parameter path: {} [Exists: {}]", target.getAbsolutePath(), target.exists());
            if (target.exists() && target.isFile()) {
                return target;
            }
        }

        // 2. Classpath resource traversal (Guaranteed to be non-null inside Tomcat)
        java.net.URL resourceUrl = PhotoServlet.class.getResource("/log4j2.xml");
        if (resourceUrl != null && "file".equals(resourceUrl.getProtocol())) {
            try {
                File current = new File(resourceUrl.toURI());
                while (current != null) {
                    File dir1 = new File(current, "data/photos");
                    File target1 = new File(dir1, filename);
                    logger.debug("Checking Classpath Traversal 1: {} [Exists: {}]", target1.getAbsolutePath(), target1.exists());
                    if (target1.exists() && target1.isFile()) {
                        return target1;
                    }
                    
                    File dir2 = new File(current, "iecd-tp2/data/photos");
                    File target2 = new File(dir2, filename);
                    logger.debug("Checking Classpath Traversal 2: {} [Exists: {}]", target2.getAbsolutePath(), target2.exists());
                    if (target2.exists() && target2.isFile()) {
                        return target2;
                    }
                    current = current.getParentFile();
                }
            } catch (Exception e) {
                logger.warn("Error during classpath traversal: {}", e.getMessage());
            }
        }

        // 3. Parent folder traversal starting from getRealPath
        String realPath = getServletContext().getRealPath("/");
        if (realPath != null) {
            File current = new File(realPath);
            while (current != null) {
                File dir1 = new File(current, "data/photos");
                File target1 = new File(dir1, filename);
                logger.debug("Checking RealPath Traversal 1: {} [Exists: {}]", target1.getAbsolutePath(), target1.exists());
                if (target1.exists() && target1.isFile()) {
                    return target1;
                }
                
                File dir2 = new File(current, "iecd-tp2/data/photos");
                File target2 = new File(dir2, filename);
                logger.debug("Checking RealPath Traversal 2: {} [Exists: {}]", target2.getAbsolutePath(), target2.exists());
                if (target2.exists() && target2.isFile()) {
                    return target2;
                }
                current = current.getParentFile();
            }
        }

        // 4. Fallback relative to System CWD
        File cwdFallback = new File("data/photos", filename);
        logger.debug("Checking CWD Fallback: {} [Exists: {}]", cwdFallback.getAbsolutePath(), cwdFallback.exists());
        if (cwdFallback.exists() && cwdFallback.isFile()) {
            return cwdFallback;
        }

        // Default fallback
        File absoluteFallback = new File("C:/Users/rui/local-projects/FACULDADE/IECD/eclipse/iecd-tp2/data/photos/", filename);
        logger.debug("Checking absolute backup path: {} [Exists: {}]", absoluteFallback.getAbsolutePath(), absoluteFallback.exists());
        if (absoluteFallback.exists() && absoluteFallback.isFile()) {
            return absoluteFallback;
        }

        logger.warn("Photo not found anywhere! Defaulting to: {}", cwdFallback.getAbsolutePath());
        return cwdFallback;
    }

    /**
     * Handles HTTP GET requests to stream a saved user profile photo from disk.
     * Sanitizes the filename to prevent directory traversal and sets caching headers.
     *
     * @param request the HttpServletRequest object
     * @param response the HttpServletResponse object
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "File name missing");
            return;
        }

        // Get sanitized filename (prevent directory traversal e.g. ../..)
        String filename = new File(pathInfo).getName();
        File file = resolvePhotoFile(filename);

        if (!file.exists() || !file.isFile()) {
            // If the photo file does not exist, send a 404
            logger.warn("Requested profile photo does not exist at any candidate path: {}", filename);
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Photo not found");
            return;
        }

        // Detect Content-Type by file extension
        String mimeType = getServletContext().getMimeType(file.getName());
        if (mimeType == null) {
            String lowerName = file.getName().toLowerCase();
            if (lowerName.endsWith(".png")) {
                mimeType = "image/png";
            } else if (lowerName.endsWith(".webp")) {
                mimeType = "image/webp";
            } else {
                mimeType = "image/jpeg"; // Default fallback
            }
        }
        
        response.setContentType(mimeType);
        response.setContentLengthLong(file.length());
        
        // Cache profile images for 1 day to maximize load performance
        response.setHeader("Cache-Control", "private, max-age=86400");

        // Stream file bytes directly to client
        try (FileInputStream fis = new FileInputStream(file);
             OutputStream os = response.getOutputStream()) {
            
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.flush();
        } catch (IOException e) {
            logger.error("Error streaming profile photo file: {}", file.getName(), e);
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
    }
}
