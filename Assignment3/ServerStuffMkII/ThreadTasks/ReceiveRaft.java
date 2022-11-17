package ServerStuffMkII.ThreadTasks;

import ServerStuffMkII.CustomObjects.*;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

// This class receives packets and respond to them.
public class ReceiveRaft implements Runnable {
    public static final int FOLLWER = 1, CANDIDATE = 2, LEADER = 3;
    DatagramSocket socket;
    Host mySelf;
    AtomicInteger state, term;
    AtomicBoolean keepGoing;
    volatile HashMap<ID, Long> responseTracker;

    public ReceiveRaft(Host mySelf, DatagramSocket socket, AtomicInteger state, AtomicInteger term, AtomicBoolean keepGoing, HashMap<ID, Long> responseTracker) {
        this.mySelf = mySelf;
        this.socket = socket;
        this.state = state;
        this.term = term;
        this.keepGoing = keepGoing;
        this.responseTracker = responseTracker;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[Packet.MAX_SIZE];
        DatagramPacket udpPacket = new DatagramPacket(buffer, buffer.length);

        // For follower & candidate use only, I need to know how many votes I would need before entering an election.
        int hostCounts = 0;
        int votes = 0;
        boolean voted = false;

        while (keepGoing.get()) {
            try {
                int milliTime = (int) (Math.random() * 200) + 300;
                socket.setSoTimeout(milliTime);
                socket.receive(udpPacket);
                byte[] data = Arrays.copyOfRange(buffer, 0, udpPacket.getLength());
                Packet receivedPacket = Packet.parse(data);

                if (receivedPacket.hasSource()) {
                    responseTracker.put(receivedPacket.SOURCE, System.currentTimeMillis());
                }

                if (receivedPacket.TYPE == Packet.HEALTH_CHK) {
//                    System.out.println("Health check received.");
                    voted = false;

                    // I need to send a packet to myself so my socket don't time out and enter a new election.
                    if (receivedPacket.SOURCE.equals(mySelf.ID)) {
                        continue;
                    }

                    // If I happen to be a candidate and received a health check, that means I lost the election
                    if (state.compareAndSet(CANDIDATE, FOLLWER)) {
                        System.out.println("I have stepped down as a candidate.");
                    }

                    HealthCheck healthCheck = HealthCheck.parse(receivedPacket.CONTENT);
                    if (healthCheck.TERM > term.get()) {
                        term.set(healthCheck.TERM);
                    }
                    hostCounts = healthCheck.HOST_COUNTS;

                    Packet reply = new Packet(Packet.HEALTH_ACK, mySelf.ID, receivedPacket.SOURCE, null);
                    byte[] replyBytes = reply.toBytes();
                    DatagramPacket udpReply = new DatagramPacket(replyBytes, replyBytes.length);
                    socket.send(udpReply);
                }

                else if (receivedPacket.TYPE == Packet.HEALTH_ACK) {
                    // Only possible if myself is a leader.
                    // Smaller version of getUnresponsive() function from HostsManager.java, but it's dedicated to MiddleMan.java
                    ArrayList<ID> notResponsive = new ArrayList<>();
                    long currentTime = System.currentTimeMillis();

                    for (ID id : responseTracker.keySet()) {
                        if (currentTime - responseTracker.get(id) > 2000) {
                            notResponsive.add(id);
                        }
                    }

                    for (ID id : notResponsive) {
                        responseTracker.remove(id);
                    }
                }

                else if (receivedPacket.TYPE == Packet.VOTE_REQUEST) {
                    int term = ByteHelper.byteToInt(receivedPacket.CONTENT);
                    Packet reply;

                    if (!voted) {
                        voted = true;
                        this.term.set(term);
                        reply = new Packet(Packet.VOTE_REPLY, mySelf.ID, receivedPacket.SOURCE, new byte[] {1});
                        System.out.println("I voted yes for term: " + term);
                    }
                    else {
                        reply = new Packet(Packet.VOTE_REPLY, mySelf.ID, receivedPacket.SOURCE, new byte[] {0});
                        System.out.println("I voted no for term: " + term);
                    }
                    byte[] replyBytes = reply.toBytes();
                    DatagramPacket udpReply = new DatagramPacket(replyBytes, replyBytes.length);
                    socket.send(udpReply);
                }

                else if (receivedPacket.TYPE == Packet.VOTE_REPLY) {
                    if (Arrays.equals(receivedPacket.CONTENT, new byte[] {1})) {
                        votes++;
                        int votesNeed = hostCounts / 2 + 1;

                        System.out.println("I received a vote for term: " + term.get());
//                        System.out.println("Votes needed: " + votesNeed);

                        if (votes >= votesNeed && this.state.compareAndSet(CANDIDATE, LEADER)) {
                            System.out.println("I'm now the leader for term: " + term.get());
                        }
                    }
                }

                else {
                    receivedPacket.printInfo();
                    System.out.println("Error, packet type yet been implemented.");
                    System.exit(-1);
                }

            }
            catch (SocketTimeoutException e) {
                // Termination conditions
                if (!keepGoing.get()) {
                    break;
                }

                // Enter a new term and run for election
                if (state.compareAndSet(FOLLWER, CANDIDATE)) {
                    hostCounts--;
                    if (hostCounts < 0) {hostCounts = 0;}   // Initial case

                    System.out.println("I have become a candidate for term " + term.incrementAndGet());
                    Packet voteRequest = new Packet(Packet.VOTE_REQUEST, mySelf.ID, null, ByteHelper.intToByte(term.get()));
                    byte[] requestByte = voteRequest.toBytes();
                    DatagramPacket request = new DatagramPacket(requestByte, requestByte.length);

                    try {
                        socket.send(request);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        System.exit(-1);
                    }
                }
                else if (state.get() == CANDIDATE) {
                    System.out.println("Well this is not suppose to happen, I have timed out as a candidate");
                    System.exit(-1);
                }
                else {
                    System.out.println("Well this really should not happen, but I have timed out as a leader.");
                    System.exit(-1);
                }
            }
            catch (Exception e) {
                System.err.println("Error at ReceiveRaft.java...");
                e.printStackTrace();
                System.exit(-1);
            }
        }
    }
}
