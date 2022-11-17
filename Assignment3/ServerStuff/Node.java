package ServerStuff;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Node implements Runnable {
    private static final int
            FOLLOWER = 1,
            CANDIDATE = 2,
            LEADER = 3,
            SEND_MESSAGE = 0,
            RECEIVE_MESSAGE = 1,
            SEND_RAFT = 2,
            RECEIVE_RAFT = 3,
            SEND_CANVAS = 4,
            RECEIVE_CANVAS = 5,
            BUFFER_SIZE = 32768 + 4;

    public static void main(String[] args) {
        try {
            // ------------------------------------------------- Set up stuff --------------------------------------- //
            Scanner scanner = new Scanner(System.in);
            System.out.print("Please enter your name: ");
            String name = scanner.nextLine();

            DatagramSocket raftSocket = DetailsHandler.getAvailableSocket();
            DatagramSocket canvasSocket = DetailsHandler.getAvailableSocket();
            DatagramSocket textSocket = DetailsHandler.getAvailableSocket();

            InetAddress myIP = InetAddress.getLocalHost();

            ClientInfo self = new ClientInfo(
                    name,
                    myIP.getAddress(),
                    canvasSocket.getLocalPort(),
                    raftSocket.getLocalPort(),
                    textSocket.getLocalPort());
            self.printInfo();

            HashMap<String, ClientInfo> nodes = new HashMap<>();
            nodes.put(Arrays.toString(self.KEY), self);

            byte[] leaderKey = null;

            // ----------------------------------- Try to connect to host, or start as one -------------------------- //
            System.out.print("Please enter the leader's IP address (0 if no leader): ");
            String input = scanner.next();
            if (input.equals("0")) {
                leaderKey = self.KEY;

            } else {
                System.out.print("Please enter the leader's raft port number: ");
                DatagramPacket packet = DetailsHandler.createRegistration(self);
                assert packet != null;

                int port = Integer.parseInt(scanner.next());
                InetAddress address = InetAddress.getByName(input);
                packet.setPort(port);
                packet.setAddress(address);
                raftSocket.send(packet);

                raftSocket.setSoTimeout(500);
                try {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
                    raftSocket.receive(reply);
                    if (DetailsHandler.getPacketType(reply) == DetailsHandler.ACCEPTANCE) {
                        ClientInfo leader = DetailsHandler.parseAcceptance(reply);
                        leaderKey = leader.KEY;
                        nodes.put(Arrays.toString(leaderKey), leader);
                        System.out.println("I have connected with " + leader.NAME);

                    } else {
                        System.err.println("Error, unknown response...");
                        System.exit(-1);
                    }

                } catch (SocketTimeoutException exception) {
                    System.err.println("Error, the host did not respond...");
                    System.exit(-1);

                } finally {
                    raftSocket.setSoTimeout(0);
                }
            }

            // -------------------------- Have multiple threads do the work, since some function blocks ------------- //
            AtomicInteger state = new AtomicInteger(1);
            AtomicBoolean keepGoing = new AtomicBoolean(true);
            AtomicBoolean voted = new AtomicBoolean(false);
            new Thread(new Node(textSocket, SEND_MESSAGE, self.KEY, leaderKey, nodes, state, keepGoing, voted)).start();
            new Thread(new Node(textSocket, RECEIVE_MESSAGE, self.KEY, leaderKey, nodes, state, keepGoing, voted)).start();
            new Thread(new Node(raftSocket, SEND_RAFT, self.KEY, leaderKey, nodes, state, keepGoing, voted)).start();
            new Thread(new Node(raftSocket, RECEIVE_RAFT, self.KEY, leaderKey, nodes, state, keepGoing, voted)).start();

        } catch (Exception exception) {
            exception.printStackTrace();
            System.exit(1);
        }
    }

    int task;
    DatagramSocket socket;
    byte[] key, leaderKey;
    // 1 == follower, 2 == candidate, 3 == leader
    AtomicInteger state;
    AtomicBoolean keepGoing;
    AtomicBoolean voted;
    volatile HashMap<String, ClientInfo> nodes;

    Node(DatagramSocket socket, int task, byte[] selfKey, byte[] leaderKey,
                        HashMap<String, ClientInfo> nodes,
                        AtomicInteger state, AtomicBoolean keepGoing, AtomicBoolean voted) {
        this.socket = socket;
        this.task = task;
        this.key = selfKey;
        this.leaderKey = leaderKey;
        this.nodes = nodes;
        this.state = state;
        this.keepGoing = keepGoing;
        this.voted = voted;
    }

    public void run() {
        if (this.task == SEND_MESSAGE) {sendMessages(socket);}
        else if (this.task == RECEIVE_MESSAGE) {receiveMessages(socket);}
        else if (this.task == SEND_RAFT) {sendRaft(socket);}
        else if (this.task == RECEIVE_RAFT) {receiveRaft(socket);}
//        else if (this.task == SEND_CANVAS) {exchangeCanvas(socket);}
//        else if (this.task == RECEIVE_CANVAS) {receiveCanvas(socket);}
    }


    // ---------------------------------------------- Thread Tasks -------------------------------------------------- //
    private void sendMessages(DatagramSocket socket) {
        Scanner scanner = new Scanner(System.in);

        while (keepGoing.get()) {
            String message = scanner.nextLine();
            if (message.equalsIgnoreCase("exit")) {
                keepGoing.set(false);
                break;
            }

            for (String key : nodes.keySet()) {
                try {
                    ClientInfo client = nodes.get(key);
                    int port = client.TEXT_PORT;
                    InetAddress address = InetAddress.getByAddress(client.ADDRESS);
                    DatagramPacket packet = DetailsHandler.createCommunication(this.key, message);
                    packet.setPort(port);
                    packet.setAddress(address);
                    socket.send(packet);

                } catch (UnknownHostException exception) {
                    System.out.println("Watch-out! an unknown host amongst the nodes...");

                } catch (IOException exception) {
                    System.out.println("Watch-out! Something went wrong at sendMessages()...");
                    exception.printStackTrace();
                }
            }
        }
    }

    private void receiveMessages(DatagramSocket socket) {
        byte[] buffer = new byte[BUFFER_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (keepGoing.get()) {
            try {
                socket.setSoTimeout(1000);      // So this thread won't block after "exit" was entered.
                socket.receive(packet);
                if (DetailsHandler.getPacketType(packet) == DetailsHandler.COMMUNICATION) {
                    DetailsHandler.parseCommunication(nodes, packet);

                } else {
                    System.out.println("Watch-out! Wrong package type received at text socket port...");
                }

            } catch (SocketTimeoutException a) {
                if (!keepGoing.get()) {break;}

            } catch (IOException b) {
                System.out.println("Watch-out! Something went wrong at receiveMessages()...");
                b.printStackTrace();
            }

        }
    }

    // This is used to actively send out health checks while been a leader, because socket.receive() blocks.
    private void sendRaft(DatagramSocket socket) {
        while (keepGoing.get()) {
            int state = this.state.get();

            if (state == LEADER) {
                DatagramPacket packet = DetailsHandler.createHealthCheck(nodes);
                assert packet != null;
                for (String key : nodes.keySet()) {
                    if (!key.equals(Arrays.toString(this.key))) {
                        try {
                            ClientInfo client = nodes.get(key);
                            int port = client.RAFT_PORT;
                            InetAddress address = InetAddress.getByAddress(client.ADDRESS);
                            packet.setPort(port);
                            packet.setAddress(address);
                            socket.send(packet);
//                            System.out.println("Health check sent to " + client.NAME);

                        } catch (UnknownHostException a) {
                            System.out.println("Watch-out! an unknown host amongst the nodes...");

                        } catch (IOException b) {
                            System.out.println("Watch-out! Something went wrong at sendRaft()...");
                            b.printStackTrace();
                        }
                    }
                }
            }

            try {Thread.sleep(50);}
            catch (InterruptedException ignored) {}
        }
    }

    private void receiveRaft(DatagramSocket socket) {
        // Keep track of the last time someone responded to me.
        HashMap<String, Long> respondTime = new HashMap<>();
        int votes = 0;

        while (keepGoing.get()) {
            try {
                // Each timeout session needs to vary otherwise we will have endless elections.
                int milliTime = (int) (Math.random() * 150) + 150;
                socket.setSoTimeout(milliTime);

                // Receives datagram packet.
                byte[] buffer = new byte[BUFFER_SIZE];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                InetAddress address = packet.getAddress();
                int port = packet.getPort();

                // Act based on the packet received.
                byte packetType = DetailsHandler.getPacketType(packet);
                switch (packetType) {
                    case DetailsHandler.REGISTRATION -> {
                        // Parse the registration packet.
                        ClientInfo newNode = DetailsHandler.parseRegistration(packet);
                        this.nodes.put(Arrays.toString(newNode.KEY), newNode);
                        System.out.println(newNode.NAME + " just connected with me.");

                        // Respond with acceptance so the node can continue.
                        DatagramPacket response = DetailsHandler.createAcceptance(nodes.get(Arrays.toString(this.key)));
                        response.setAddress(address);
                        response.setPort(port);
                        socket.send(response);
                    }

                    case DetailsHandler.HEALTH_CHK -> {
                        // Parse the new health check, then update a lot of information accordingly.
                        HashMap<String, ClientInfo> newNodes = DetailsHandler.parseHealthCheck(packet);

                        // Since "this.nodes = newNodes" won't overwrite the old one, I have to loop through them.
                        if (this.nodes.size() > newNodes.size()) {
                            // Someone left, two loops because can't remove the elements that I'm looping over.
                            ArrayList<String> disconnected = new ArrayList<>();
                            for (String key : this.nodes.keySet()) {
                                if (!newNodes.containsKey(key)) {disconnected.add(key);}
                            }
                            for (String key : disconnected) {
                                ClientInfo client = this.nodes.remove(key);
                                String nodeName = client.NAME;
                                System.out.println(nodeName + " has left us.");
                            }

                        } else if (this.nodes.size() < newNodes.size()) {
                            // Someone joined us.
                            for (String key : newNodes.keySet()) {
                                if (!this.nodes.containsKey(key)) {
                                    ClientInfo newNode = newNodes.get(key);
                                    String nodeName = newNode.NAME;
                                    System.out.println(nodeName + " has joined us.");
                                    this.nodes.put(key, newNode);
                                }
                            }
                        }

                        // If I received a health check while been a candidate, that means I have lost the election.
                        if (this.state.compareAndSet(CANDIDATE, FOLLOWER)) {
                            System.out.println("I have stepped down as a candidate.");
                        }

                        // Figure out if someone new has become the leader, this could happen even if I didn't enter the election.
                        byte[] senderAddress = address.getAddress();
                        for (ClientInfo node : nodes.values()) {
                            if (Arrays.equals(senderAddress, node.ADDRESS)
                                    && port == node.RAFT_PORT
                                    && !Arrays.equals(this.leaderKey, node.KEY)) {

                                this.leaderKey = node.KEY;
                                System.out.println(node.NAME + " has become the new leader for term " + node.term);
                                nodes.get(Arrays.toString(this.key)).term = node.term;
                                break;
                            }
                        }

                        // Make a health check acknowledgement.
                        DatagramPacket response = DetailsHandler.createHealthAck(this.nodes.get(Arrays.toString(this.key)));
                        response.setAddress(address);
                        response.setPort(port);
                        socket.send(response);
                    }

                    case DetailsHandler.HEALTH_ACK -> {
                        // Mark that such node has responded.
                        String responderKey = Arrays.toString(DetailsHandler.parseHealthAck(packet));
                        long currentTime = System.currentTimeMillis();
                        respondTime.put(responderKey, currentTime);

                        // Anyone who have not responded for 2 seconds will be removed from the group
//                        System.out.println("Health Ack from " + nodes.get(responderKey).NAME + ", time: " + System.currentTimeMillis());
                        ArrayList<String> timeouts = new ArrayList<>();
                        for (String key : respondTime.keySet()) {
                            if (currentTime - respondTime.get(key) > 2000) {
                                timeouts.add(key);
                                ClientInfo node = this.nodes.remove(key);
                                String nodeName = node.NAME;
                                System.out.println(nodeName + " disconnected.");
                            }
                        }
                        for (String key : timeouts) {
                            respondTime.remove(key);
                        }
                    }

                    case DetailsHandler.VOTE_REQUEST -> {
                        // When a node enters a new term, it should already have voted.
                        int term = DetailsHandler.parseVoteRequest(packet, this.nodes);
                        ClientInfo self = nodes.get(Arrays.toString(this.key));

                        DatagramPacket reply;
                        if (voted.compareAndSet(false, true)) {
                            reply = DetailsHandler.createVoteReply(this.key, true);
                        } else {
                            reply = DetailsHandler.createVoteReply(this.key, false);
                        }
                        if (term > self.term) {self.term = term;}

                        reply.setAddress(address);
                        reply.setPort(port);
                        socket.send(reply);
                    }

                    case DetailsHandler.VOTE_REPLY -> {
                        int votesNeeded = (nodes.size() + 1) / 2;
                        if (DetailsHandler.parseVoteReply(packet, nodes)) {
                            votes++;
                        }
                        if (votes >= votesNeeded) {
                            if (this.state.compareAndSet(CANDIDATE, LEADER)) {
                                ClientInfo self = nodes.get(Arrays.toString(this.key));
                                System.out.println("I have become the leader for term " + self.term);
                                System.out.println("My IP address is: " + self.getAddressString());
                                System.out.println("My raft port is: " + self.RAFT_PORT);
                            }
                        }
                    }

                    default -> System.out.println("Unexpected packet received at raft port...");
                }

            } catch (SocketTimeoutException exception) {
                if (!keepGoing.get()) {break;}

                switch (this.state.get()) {
                    case FOLLOWER -> {
                        // Become a candidate myself
                        int term = ++this.nodes.get(Arrays.toString(this.key)).term;
                        this.state.set(CANDIDATE);
                        this.voted.set(false);
                        if (this.leaderKey != this.key) {
                            ClientInfo leader = this.nodes.remove(Arrays.toString(leaderKey));
                            System.out.println("leader " + leader.NAME + " has been removed from registration list.");
                        }
                        System.out.println("I have become a candidate for term " + term);

                        for (ClientInfo node : nodes.values()) {
                            try {
                                int port = node.RAFT_PORT;
                                InetAddress address = InetAddress.getByAddress(node.ADDRESS);
                                DatagramPacket packet = DetailsHandler.createVoteRequest(this.key, term);
                                packet.setPort(port);
                                packet.setAddress(address);
                                socket.send(packet);
                                System.out.println("Vote request send out to " + node.NAME);

                            } catch (UnknownHostException a) {
                                System.out.println("Watch-out! an unknown host amongst the nodes...");

                            } catch (IOException b) {
                                System.out.println("Watch-out! Something went wrong at sendRaft()...");
                                b.printStackTrace();
                            }
                        }
                    }

                    case CANDIDATE -> {
                        // I can't imagine a candidate timeout scenario, switch back to follower to wait and see.
                        int term = this.nodes.get(Arrays.toString(this.key)).term;
                        System.out.println("Election timeout for term " + term);
                        this.state.set(FOLLOWER);
                    }

                    case LEADER -> {
                        // Well I'm either alone to begin with or people all left me.
                        if (nodes.size() > 1) {
                            // Why the two loops? because I can't modify a set while looping through it.
                            ArrayList<String> disconnected = new ArrayList<>();
                            for (String key : nodes.keySet()) {
                                if (!key.equals(Arrays.toString(this.key))) {
                                    disconnected.add(key);
                                }
                            }
                            for (String key : disconnected) {
                                ClientInfo client = nodes.remove(key);
                                System.out.println(client.NAME + " disconnected.");
                            }
                        }
                    }

                    default -> {
                        System.out.println("What state am I even in? " +
                                "This has to be a mistake (at receiveRaft()'s timeout session.");
                        this.state.set(FOLLOWER);
                    }
                }

            } catch (IOException exception) {
                System.out.println("Watch-out! Something went wrong at receiveRaft()...");
                exception.printStackTrace();
            }
        }
    }
}
