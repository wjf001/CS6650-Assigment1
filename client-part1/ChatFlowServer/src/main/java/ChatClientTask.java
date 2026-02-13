import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import com.google.gson.Gson;
import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.List;

public class ChatClientTask implements Runnable {

    private final String serverUri;
    private final BlockingQueue<ChatMessage> queue;
    private final CountDownLatch latch;
    private final Gson gson = new Gson();

    // Stats
    private final ConcurrentHashMap<String, Long> startTimes = new ConcurrentHashMap<>();

    public final List<String[]> results = new ArrayList<>();
    public int messagesSent = 0; // MUST be public
    public int messagesFailed = 0;

    public ChatClientTask(String serverUri, BlockingQueue<ChatMessage> queue, CountDownLatch latch) {
        this.serverUri = serverUri;
        this.queue = queue;
        this.latch = latch;
    }

    @Override
    public void run() {
        WebSocketClient client = null;
        try {
            client = new WebSocketClient(new URI(serverUri)) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {}

                @Override
                public void onMessage(String message) {
                    long endTime = System.currentTimeMillis();
                    ChatMessage response = gson.fromJson(message, ChatMessage.class);
                    Long startTime = startTimes.remove(response.uID);
                    if (startTime != null) {
                        long latency = endTime - startTime;
                        results.add(new String[]{
                                String.valueOf(startTime),
                                String.valueOf(latency),
                                response.messageType
                        });
                        // Count the response
                        ChatClient.receivedCount.incrementAndGet();
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {}
                @Override
                public void onError(Exception ex) {}
            };

            client.connectBlocking();

            while (true) {
                ChatMessage msg = queue.poll(1, java.util.concurrent.TimeUnit.SECONDS);
                if (msg == null) break;

                boolean sent = false;
                int retries = 0;
                long backoff = 100;

                while (!sent && retries < 5) {
                    try {
                        long start = System.currentTimeMillis();
                        if (retries == 0) startTimes.put(msg.uID, start);

                        client.send(gson.toJson(msg));
                        sent = true;
                        messagesSent++;

                        Thread.sleep(50);

                    } catch (Exception e) {
                        retries++;
                        try { Thread.sleep(backoff); } catch (Exception ignored) {}
                        backoff *= 2;
                    }
                }
                if (!sent) messagesFailed++;
            }

            // Wait a bit for remaining responses then close
            Thread.sleep(2000);
            client.close();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            latch.countDown();
        }
    }
}