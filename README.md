# CS6650-Assigment1

# Chat Server (WebSocket)

Building a WebSocket Chat Server and Client

## Prerequisites
* Java 11
* Maven 3.6+

## Running
### AWS EC2 
1.  Launch an AWS EC2 instance (t2.micro).

2.  SSH into the server and run it:
    ssh -i ~/Desktop/chatflow-oregon.pem ec2-user@PUBLIC_EC2_IP_ADDRESS
    java -jar ChatFlowServer-1.0-SNAPSHOT.jar

## Endpoints
* **WebSocket URL:** `ws://[IP]:8080/chat/{roomID}`
