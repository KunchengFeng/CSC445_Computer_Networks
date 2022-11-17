package ServerStuffMkII.CustomObjects;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class HostsManager {
    private final int RAFT = 1, CANVAS = 2, TEXT = 3;
    private final HashMap<ID, Host> hosts;
    private final boolean
            showReceived = false,
            showReply = false,
            showRedirected = false,
            showRaft = false,
            showText = false;

    public HostsManager() {hosts= new HashMap<>();}

    // ----------------------------------------------- Main functions here ------------------------------------------ //
    public void handlePacket(DatagramSocket socket, DatagramPacket udpPacket, byte[] buffer) {
        byte[] data = Arrays.copyOfRange(buffer, 0, udpPacket.getLength());
        Packet packet = Packet.parse(data);

        if (showReceived) {
            System.out.println("Received:");
            packet.printInfo();
        }

        try {
            if (packet.TYPE == Packet.ACKNOWLEDGEMENT) {
                // Do nothing
            }
            else if (packet.TYPE == Packet.WARMUP) {
                Packet reply = new Packet(Packet.WARMUP, null, null, null);
                byte[] replyBytes = reply.toBytes();
                DatagramPacket udpReply = new DatagramPacket(replyBytes, replyBytes.length);
                udpReply.setAddress(udpPacket.getAddress());
                udpReply.setPort(udpPacket.getPort());
                socket.send(udpReply);
            }
            else if (packet.TYPE == Packet.REGISTRATION) {
                // Register the new host
                Host newHost = Host.parse(packet.CONTENT, udpPacket.getAddress());
                hosts.put(newHost.ID, newHost);
                welcomeMessage(socket, newHost);
                System.out.println(newHost.NAME + " joined");
//                newHost.printInfo();

                // Reply
                Packet reply = new Packet(Packet.ACCEPTANCE, null, null, null);
                byte[] replyBytes = reply.toBytes();
                DatagramPacket udpReply = new DatagramPacket(replyBytes, replyBytes.length);
                udpReply.setAddress(newHost.ADDRESS);
                udpReply.setPort(newHost.RAFT_NUM);

                socket.send(udpReply);
                if (showReply) {
                    System.out.println("Replied:");
                    reply.printInfo();
                }
            }
            else if (packet.TYPE == Packet.TEXT) {
                redirectPacket(socket, packet, TEXT);
            }
            else if (packet.TYPE == Packet.HEALTH_CHK || packet.TYPE == Packet.HEALTH_ACK) {
                hosts.get(packet.SOURCE).timeStamp();
                ArrayList<Host> timeouts = getUnresponsive();
                goodbyeMessage(socket, timeouts);
                redirectPacket(socket, packet, RAFT);
            }
            else if (packet.TYPE == Packet.VOTE_REQUEST || packet.TYPE == Packet.VOTE_REPLY) {
                redirectPacket(socket, packet, RAFT);
            }
            else if (packet.TYPE == Packet.CANVAS_DATA) {
                redirectPacket(socket, packet, CANVAS);
            }
            else {
                System.out.println("Error, packet type has yet been implemented.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // -------------------------------------------------------------------------------------------------------------- //


    // ------------------------------------------------ Secondary functions here ------------------------------------ //
    private void redirectPacket(DatagramSocket socket, Packet packet, int type) throws IOException {
        byte[] a = packet.toBytes();
        DatagramPacket udpPacket = new DatagramPacket(a, a.length);
        Host target = null;
        if (packet.hasTarget()) {
            target = hosts.get(packet.TARGET);
        }

        // Redirect to all the known hosts, this solves the 1 hosts no response issue.
        for (Host host : hosts.values()) {
            udpPacket.setAddress(host.ADDRESS);
            if (type == RAFT) {
                udpPacket.setPort(host.RAFT_NUM);
            }
            else if (type == TEXT) {
                udpPacket.setPort(host.TEXT_NUM);
            }
            else {
                udpPacket.setPort(host.CANVAS_NUM);
            }

            if (target != null) {
                // Send to that specific target only.
                if (host.ID.equals(target.ID)) {
                    socket.send(udpPacket);
                }
            } else {
                // Send to everyone.
                socket.send(udpPacket);
            }
        }

        if (showRedirected) {
            System.out.println("Redirected:");
            packet.printInfo();
        }
    }

    private void welcomeMessage(DatagramSocket socket, Host newHost) throws IOException {
        String message = newHost.NAME + " has joined us.";
        Text text = new Text("Server", message);
        Packet packet = new Packet(Packet.TEXT, null, null, text.toBytes());    // Since text port is the one receiving id do not matter.
        byte[] data = packet.toBytes();
        DatagramPacket udpPacket = new DatagramPacket(data, data.length);

        for (Host host : hosts.values()) {
            udpPacket.setAddress(host.ADDRESS);
            udpPacket.setPort(host.TEXT_NUM);
            socket.send(udpPacket);
        }

        if (showText) {
            System.out.println("Welcome message send:");
            packet.printInfo();
        }
    }

    private void goodbyeMessage(DatagramSocket socket, ArrayList<Host> timeouts) throws IOException {
        for (Host timeout : timeouts) {
            String message = timeout.NAME + " has disconnected.";
            Text text = new Text("Server", message);
            Packet packet = new Packet(Packet.TEXT, null, null, text.toBytes());
            byte[] a = packet.toBytes();
            DatagramPacket udpPacket = new DatagramPacket(a, a.length);

            for (Host remain : hosts.values()) {
                udpPacket.setAddress(remain.ADDRESS);
                udpPacket.setPort(remain.TEXT_NUM);
                socket.send(udpPacket);
            }

            if (showText) {
                System.out.println("Goodbye message send:");
                packet.printInfo();
            }
        }
    }
    // -------------------------------------------------------------------------------------------------------------- //


    // ------------------------------------------------ Tertiary functions here ------------------------------------- //
    public Host getHost(ID id) {return hosts.get(id);}

    public ArrayList<Host> getUnresponsive() {
        ArrayList<Host> notResponsive = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        for (Host host : hosts.values()) {
            if (host.hasTimeout(currentTime)) {
                notResponsive.add(host);
            }
        }

        for (Host host : notResponsive) {
            System.out.println(host.NAME + " has timed out.");
            hosts.remove(host.ID);
        }
        return notResponsive;
    }
    // -------------------------------------------------------------------------------------------------------------- //
}
