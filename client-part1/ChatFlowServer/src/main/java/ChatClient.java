import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ChatClient {

    // Part 1 Configuration
    private static final String TARGET_SERVER = "ws://54.200.227.63:8080";
    private static final int MSG_COUNT = 32000; // 32 Threads * 1000 msg = 32,000
    private static final int THREAD_POOL_SIZE = 32;

    public static AtomicInteger receivedCount = new AtomicInteger(0);

    public static void main(String[] args) throws Exception {
        System.out.println("Starting Initial Phase");

        BlockingQueue<ChatMessage> queue = new LinkedBlockingQueue<>();
        prepareMessageData(queue);

        CountDownLatch senderLatch = new CountDownLatch(THREAD_POOL_SIZE);
        ExecutorService workerPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        long startTimestamp = System.currentTimeMillis();
        System.out.println("Launching " + THREAD_POOL_SIZE + " threads to send " + MSG_COUNT + " messages...");

        for (int i = 0; i < THREAD_POOL_SIZE; i++) {
            String fullUrl = TARGET_SERVER + "/chat/" + (new Random().nextInt(5) + 1);
            ChatClientTask worker = new ChatClientTask(fullUrl, queue, senderLatch);
            workerPool.submit(worker);
        }

        // Wait for sending to finish
        senderLatch.await();
        System.out.println("All messages sent. Waiting for responses...");

        while (receivedCount.get() < MSG_COUNT) {
            System.out.print("\rReceived: " + receivedCount.get() + " / " + MSG_COUNT);
            Thread.sleep(100);
            // Safety break if stuck for too long
            if (System.currentTimeMillis() - startTimestamp > 60000) break;
        }

        workerPool.shutdownNow();
        long endTimestamp = System.currentTimeMillis();
        long totalDuration = endTimestamp - startTimestamp;

        // Report
        double throughput = (double) receivedCount.get() / (totalDuration / 1000.0);

        System.out.println("\n\n===========================================");
        System.out.println("Initial Phase Report");
        System.out.println("===========================================");
        System.out.println("Total Messages: " + MSG_COUNT);
        System.out.println("Wall Time:      " + totalDuration + " ms");
        System.out.println("Throughput:     " + String.format("%.2f", throughput) + " msg/sec");
        System.out.println("===========================================");
    }

    private static void prepareMessageData(BlockingQueue<ChatMessage> queue) {
        Random rand = new Random();
        String[] events = {"TEXT", "JOIN", "LEAVE"};
        for (int i = 0; i < MSG_COUNT; i++) {
            String uid = String.valueOf(rand.nextInt(10000) + 1);
            ChatMessage msg = new ChatMessage(uid, "User" + uid, "Hello " + i, events[rand.nextInt(3)]);
            queue.add(msg);
        }
    }
}