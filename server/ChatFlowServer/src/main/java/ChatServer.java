import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import com.google.gson.Gson;
import java.net.InetSocketAddress;

public class ChatServer extends WebSocketServer {

    private final Gson gson = new Gson();

    public ChatServer(int port) {
        super(new InetSocketAddress(port));
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        // Get the path
        String path = handshake.getResourceDescriptor();

        // Check if it is a valid path
        if (path == null || !path.startsWith("/chat/")) {
            System.out.println("Rejected connection: Invalid path " + path);
            conn.close(1002, "Invalid Endpoint. Use /chat/{roomId}");
            return;
        }

        // Extract Room ID
        String roomId = path.substring(6);
        System.out.println("New connection to Room " + roomId + " from " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("Closed connection to " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        // This runs when a message arrives
        System.out.println("Received: " + message);

        try {
            // Convert jason to java object
            ChatMessage msg = gson.fromJson(message, ChatMessage.class);

            // Validate the message
            if (msg.username == null || msg.username.length() < 3) {
                conn.send("Error: Username too short");
                return;
            }

            // Echo the message back to the client
            // send the same object back as a jason
            conn.send(gson.toJson(msg));
            System.out.println("Sent back: " + gson.toJson(msg));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("Server started successfully!");
    }

    public static void main(String[] args) {
        int port = 8080;
        ChatServer server = new ChatServer(port);
        server.start();
        System.out.println("ChatServer is running on port: " + port);
    }
}