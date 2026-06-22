package iecd.a51597.server.network;

import iecd.a51597.server.Server;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * Accept loop thread that creates and starts per-client {@link Connection} workers.
 */
public class ListenerThread extends Thread {

    private volatile boolean running = true;
    private final int port;
    private final Server server;
    private static final Logger logger = LogManager.getLogger(ListenerThread.class);
    private volatile ServerSocket serverSocket;

    /**
     * Creates a listener bound to a configured port.
     *
     * @param port listening port
     * @param server owning server instance
     */
    public ListenerThread(int port, Server server) {
        this.port = port;
        this.server = server;
    }

    /**
     * Accept loop. Stops when requested or when server socket closes.
     */
    @Override
    public void run() {
        try {
            ServerSocket ss = new ServerSocket(port);
            try (ss) {
                this.serverSocket = ss;
                while (running) {
                    Connection conn = new Connection(serverSocket.accept(), server, server.getMessageHandler());
                    server.addConnection(conn);
                    new Thread(conn).start();

                    logger.info("New connection established from IP: {}", conn.getClientSocket().getInetAddress().getHostAddress());
                    logger.info("Total Connections: {}", server.getConnections().size());
                }
            }
        } catch (IOException e) {
            if (running) {
                logger.error("Error in ListenerThread", e);
            }
        } finally {
            running = false;
        }
    }

    /**
     * @return listening port
     */
    public int getPort() {
        return port;
    }

    /**
     * Requests listener shutdown and closes the server socket.
     */
    public void stopListener() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); }
        catch (IOException e) { logger.error("Error closing socket", e); }
        logger.info("Stopping ListenerThread");
    }

    /**
     * @return {@code true} while accept loop is active
     */
    public boolean isRunning() {
        return running;
    }
}
