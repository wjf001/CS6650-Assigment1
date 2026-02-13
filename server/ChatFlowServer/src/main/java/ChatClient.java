import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ChatClient {

    // Configuration
    private static final String TARGET_SERVER = "ws://54.200.227.63:8080";
    private static final int MSG_COUNT = 500000;
    private static final int THREAD_POOL_SIZE = 32;

    // Global counter for received messages
    public static AtomicInteger receivedCount = new AtomicInteger(0);

    public static void main(String[] args) throws Exception {

        BlockingQueue<ChatMessage> queue = new LinkedBlockingQueue<>();
        prepareMessageData(queue);

        CountDownLatch senderLatch = new CountDownLatch(THREAD_POOL_SIZE);
        ExecutorService workerPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        Map<Integer, List<ChatClientTask>> roomTasks = new HashMap<>();

        long startTimestamp = System.currentTimeMillis();
        System.out.println("Launching " + THREAD_POOL_SIZE + " worker threads...");

        Random rng = new Random();
        for (int i = 0; i < THREAD_POOL_SIZE; i++) {
            int roomNum = rng.nextInt(20) + 1;
            String fullUrl = TARGET_SERVER + "/chat/" + roomNum;
            ChatClientTask worker = new ChatClientTask(fullUrl, queue, senderLatch);
            roomTasks.computeIfAbsent(roomNum, k -> new ArrayList<>()).add(worker);
            workerPool.submit(worker);
        }

        // Wait for sending to finish
        senderLatch.await();
        System.out.println("All messages sent. Waiting for responses...");

        // 2. Wait check to see if we get stuck
        int lastCount = 0;
        int stuckCounter = 0;

        while (receivedCount.get() < MSG_COUNT) {
            int current = receivedCount.get();
            System.out.print("\rReceived: " + current + " / " + MSG_COUNT);

            if (current == lastCount) {
                stuckCounter++;
                // If the number hasn't changed for 10 seconds, give up and print
                if (stuckCounter >= 10) {
                    System.out.println("\n\nERROR: Stop the chat");
                    break;
                }
            } else {
                // If numbers are moving, reset the stuck counter
                stuckCounter = 0;
                lastCount = current;
            }
            Thread.sleep(1000); // Check every 1 second
        }

        workerPool.shutdownNow(); // Kill threads

        long endTimestamp = System.currentTimeMillis();
        long totalDuration = endTimestamp - startTimestamp;

        // Generate Report
        generateReport(roomTasks, totalDuration);
    }

    private static void prepareMessageData(BlockingQueue<ChatMessage> queue) {
        System.out.println("Initializing message with " + MSG_COUNT + " records...");
        Random rand = new Random();
        String[] events = {"TEXT", "JOIN", "LEAVE"};
        for (int i = 0; i < MSG_COUNT; i++) {
            String uid = String.valueOf(rand.nextInt(100000) + 1);
            ChatMessage msg = new ChatMessage(uid, "User" + uid, "Msg #" + i, events[rand.nextInt(3)]);
            queue.add(msg);
        }
        System.out.println("Message ready.");
    }

    private static void generateReport(Map<Integer, List<ChatClientTask>> roomTasks, long totalTime) throws Exception {
        System.out.println("Statistical Analysis");

        List<Long> allLatencies = new ArrayList<>();
        Map<String, Integer> typeCounts = new HashMap<>();
        typeCounts.put("TEXT", 0);
        typeCounts.put("JOIN", 0);
        typeCounts.put("LEAVE", 0);

        PrintWriter fileWriter = new PrintWriter(new FileWriter("results.csv"));
        fileWriter.println("StartTime,Latency,Type");

        // Gather data from all tasks
        for (List<ChatClientTask> tasks : roomTasks.values()) {
            for (ChatClientTask task : tasks) {
                for (String[] record : task.results) {
                    fileWriter.println(String.join(",", record));

                    allLatencies.add(Long.parseLong(record[1]));
                    String type = record[2];
                    typeCounts.put(type, typeCounts.getOrDefault(type, 0) + 1);
                }
            }
        }
        fileWriter.close();

        if (allLatencies.isEmpty()) {
            System.out.println("No responses received. Server might be down or unreachable.");
            return;
        }

        // Report help caculator
        Collections.sort(allLatencies);
        double mean = allLatencies.stream().mapToLong(val -> val).average().orElse(0.0);
        long median = allLatencies.get(allLatencies.size() / 2);
        long p95 = allLatencies.get((int) (allLatencies.size() * 0.95)); // Added 95th
        long p99 = allLatencies.get((int) (allLatencies.size() * 0.99));
        long min = allLatencies.get(0);
        long max = allLatencies.get(allLatencies.size() - 1);

        // Throughput
        double totalThroughput = (double) allLatencies.size() / (totalTime / 1000.0);

        // print report
        System.out.println("===========================================");
        System.out.println("Statistical Analysis Report");
        System.out.println("===========================================");
        System.out.println("Total Duration:     " + totalTime + " ms");
        System.out.println("Total Throughput:   " + String.format("%.2f", totalThroughput) + " msg/sec");
        System.out.println("Mean Latency:       " + String.format("%.2f", mean) + " ms");
        System.out.println("Median Latency:     " + median + " ms");
        System.out.println("95th % Latency:     " + p95 + " ms");
        System.out.println("99th % Latency:     " + p99 + " ms");
        System.out.println("Min Latency:        " + min + " ms");
        System.out.println("Max Latency:        " + max + " ms");
        System.out.println("-------------------------------------------");
        System.out.println("Message Distribution:");
        System.out.println("  TEXT:  " + typeCounts.get("TEXT"));
        System.out.println("  JOIN:  " + typeCounts.get("JOIN"));
        System.out.println("  LEAVE: " + typeCounts.get("LEAVE"));
        System.out.println("-------------------------------------------");
        System.out.println("Throughput Per Room:");

        List<Integer> sortedRooms = new ArrayList<>(roomTasks.keySet());
        Collections.sort(sortedRooms);

        for (Integer roomId : sortedRooms) {
            int roomMsgCount = 0;
            for (ChatClientTask t : roomTasks.get(roomId)) {
                roomMsgCount += t.results.size();
            }
            double roomThroughput = (double) roomMsgCount / (totalTime / 1000.0);
            System.out.println("  Room " + roomId + ": " + String.format("%.2f", roomThroughput) + " msg/sec");
        }
        System.out.println("===========================================");
    }
}