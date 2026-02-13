public class ChatMessage {
    public String uID;
    public String username;
    public String message;
    public String messageType;
    public String timestamp;

    public ChatMessage(String uID, String username, String message, String messageType) {
        this.uID = uID;
        this.username = username;
        this.message = message;
        this.messageType = messageType;
        this.timestamp = String.valueOf(System.currentTimeMillis());
    }

    public ChatMessage() {}
}