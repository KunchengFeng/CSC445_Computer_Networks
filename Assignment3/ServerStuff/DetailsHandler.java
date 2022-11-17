package ServerStuff;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;


// This file is intended to support "Server.java" and "Client.java" by handling their communication details.

/*
        For this Assignment:
            Packets are like this:
                    2 bytes         2 bytes         max 32768 bytes
                   +-------------+-------------+-------------------+
                -> | Packet type | Rest length | Rest............. |
                   +-------------+-------------+-------------------+

     */

public class DetailsHandler {
    public static final byte
            REGISTRATION = 0,
            ACCEPTANCE = 1,
            COMMUNICATION = 2,
            HEALTH_CHK = 3,
            HEALTH_ACK = 4,
            VOTE_REQUEST = 5,
            VOTE_REPLY = 6,
            CANVAS_ASK = 7,
            CANVAS_MOD = 8,
            CANVAS_ALL = 9;



    // ------------------------------------------------- Regarding Registration ------------------------------------- //
    /*
        Rest...... :- A byte array representation of an ClientInfo class.

                4 Bytes         2 Bytes             2 Bytes         2 Bytes     8 Bytes     2 Bytes       X Bytes
            +---------------+-------------------+-----------------+-----------+-----------+---------------+------+
            | Address Bytes | Canvas Port Bytes | Raft Port Bytes | Text Port | Key Bytes | Election Term | Name |
            +---------------+-------------------+-----------------+-----------+-----------+---------------+------+
        !Note:
            -> This datagram packet is to inform the leader node of a new node.
     */
    static DatagramPacket createRegistration(ClientInfo client) {
        byte[] combined = clientInfoToBytes(client);
        if (combined == null) {
            return null;
        } else {
            return wrapUp(REGISTRATION, combined);
        }
    }

    static ClientInfo parseRegistration(DatagramPacket registrationPacket) {
        byte[] infoArray = unwrap(registrationPacket);
        return bytesToClientInfo(infoArray);
    }
    // -------------------------------------------------------------------------------------------------------------- //


    // -------------------------------------------------- Regarding Acceptance -------------------------------------- //
    /*
        Rest...... :- A reply from the leader

                X Bytes
            +--------------+
            | Leader's Key |
            +--------------+
        !Note:
            -> The follower needs to know who the leader is before he/she starts.
     */
    static DatagramPacket createAcceptance(ClientInfo self) {
        byte[] data = clientInfoToBytes(self);
        return wrapUp(ACCEPTANCE, data);
    }

    static ClientInfo parseAcceptance(DatagramPacket acceptancePacket) {
        byte[] data = unwrap(acceptancePacket);
        return bytesToClientInfo(data);
    }
    // -------------------------------------------------------------------------------------------------------------- //


    // ----------------------------------------------- Regarding Communication -------------------------------------- //
    /*
        Rest...... :- A message from someone

               8 Bytes      X Bytes
            +------------+----------+
            | Sender Key | <String> |
            +------------+----------+
        !Note:
            -> This function is not a requirement of the assignment.
            -> This function is created to test out the communication between hosts.
     */
    static DatagramPacket createCommunication(byte[] senderKey, String message) {
        byte[][] everything = { senderKey, message.getBytes() };
        byte[] data = combine(everything);
        assert data != null;
        return wrapUp(COMMUNICATION, data);
    }

    static void parseCommunication(HashMap<String, ClientInfo> nodes, DatagramPacket packet) {
        byte[] data = unwrap(packet);
        byte[] senderKey = Arrays.copyOfRange(data, 0, 8);
        ClientInfo sender = nodes.get(Arrays.toString(senderKey));

        if (sender == null) {
            System.out.println("Message from unknown sender...");

        } else {
            String message = new String(Arrays.copyOfRange(data, 8, data.length));
            System.out.println(sender.NAME + ": " + message);
        }
    }

    // -------------------------------------------------------------------------------------------------------------- //


    // -------------------------------------------------- Regarding Health Checks ----------------------------------- //
    /*
        Rest...... :- Repeating elements of ClientInfo class

                2 Bytes          X Bytes            Repeating
            +---------------+-------------------+----
            | Object Length | Object Byte Array | ....
            +---------------+-------------------+----
        !Note:
            -> This datagram packet will be sent through raft port.
            -> This datagram packet is used as regular node connection check.
            -> This datagram packet will also update the followers of what the leader knows in terms of connections.
     */
    static DatagramPacket createHealthCheck(HashMap<String, ClientInfo> nodes) {
        // Put all client information from the hashMap into a byte array list.
        ArrayList<byte[]> list = new ArrayList<>();
        for (ClientInfo client : nodes.values()) {
            list.add(clientInfoToBytes(client));
        }

        // Convert the byte array list into a single array.
        byte[][] everything = new byte[list.size()][];
        for (int i = 0; i < list.size(); i++) {
            byte[] object = list.get(i);
            byte[] objectLength = intToByte(object.length);
            byte[][] thisObject = {objectLength, object};
            everything[i] = combine(thisObject);
        }
        byte[] data = combine(everything);

        // Convert the data into a package for sending.
        if (data == null) {
            return null;
        } else {
            return wrapUp(HEALTH_CHK, data);
        }
    }

    static HashMap<String, ClientInfo> parseHealthCheck(DatagramPacket healthCheckPacket) {
        byte[] infoArray = unwrap(healthCheckPacket);
        HashMap<String, ClientInfo> nodes = new HashMap<>();
        int index = 0;

        while (index < infoArray.length) {
            int objectLength = byteToInt(Arrays.copyOfRange(infoArray, index, index + 2));
            index += 2;
            ClientInfo client = bytesToClientInfo(Arrays.copyOfRange(infoArray, index, index + objectLength));
            index += objectLength;
            nodes.put(Arrays.toString(client.KEY), client);
        }

        return nodes;
    }
    // -------------------------------------------------------------------------------------------------------------- //


    // ------------------------------------------ Regarding Health Acknowledgements --------------------------------- //
    /*
        Rest...... :- key of the sender.

            8 Bytes
            +-----+
            | KEY |
            +-----+
        !Note:
            -> The leader will identify the sender node with this.
     */
    static DatagramPacket createHealthAck(ClientInfo client) {
        return wrapUp(HEALTH_ACK, client.KEY);
    }

    static byte[] parseHealthAck(DatagramPacket healthAckPacket) {
        return unwrap(healthAckPacket);
    }
    // -------------------------------------------------------------------------------------------------------------- //


    // -------------------------------------------------- Regarding Vote Request ------------------------------------ //
    /*
        Rest...... :- An request from the candidate
                8 Bytes   2 Bytes
            +------------+------+
            | Leader Key | Term |
            +------------+------+
        !Note:
            -> All a node need to know is actually just the term number.
            -> But I thought it would be nice to know who wants your vote.
     */
    static DatagramPacket createVoteRequest(byte[] key, int term) {
        byte[] a = intToByte(term);
        byte[][] b = {key, a};
        byte[] c = combine(b);
        assert c != null;
        return wrapUp(VOTE_REQUEST, c);
    }

    static int parseVoteRequest(DatagramPacket packet, HashMap<String, ClientInfo> nodes) {
        byte[] infoArray = unwrap(packet);
        String senderKey = Arrays.toString(Arrays.copyOfRange(infoArray, 0, 8));
        int term = byteToInt(Arrays.copyOfRange(infoArray, 8, 10));

        ClientInfo sender = nodes.get(senderKey);
        if (sender == null) {
            System.out.println("Vote request from unknown sender...");

        } else {
            System.out.println("Vote request from " + sender.NAME);
        }

        return term;
    }
    // -------------------------------------------------------------------------------------------------------------- //


    // ------------------------------------------------- Regarding Vote Reply --------------------------------------- //
    /*
        Rest...... :- An reply from a constituent
                8 bytes  2 Bytes
            +-----------+-----+
            | Voter Key | Y/N |
            +-----------+-----+
        !Note:
            -> It would be nice to know who voted for me.
            -> It would be nice to know who refused to vote for me.
     */
    static DatagramPacket createVoteReply(byte[] myKey, boolean vote) {
        byte[] a = {0, 0};
        if (vote) {a[1] = 1;}
        byte[][] b = {myKey, a};
        byte[] c = combine(b);
        assert c != null;
        return wrapUp(VOTE_REPLY, c);
    }

    static boolean parseVoteReply(DatagramPacket packet, HashMap<String, ClientInfo> nodes) {
        byte[] data = unwrap(packet);
        String key = Arrays.toString(Arrays.copyOfRange(data, 0, 8));

        ClientInfo voter = nodes.get(key);
        if (voter == null) {
            System.out.println("Unknown voter...");
            return false;

        } else {
            if (data[9] == 1) {
                System.out.println(voter.NAME + " voted for me...");
                return true;

            } else {
                System.out.println(voter.NAME + " did not vote for me...");
                return false;
            }
        }
    }
    // -------------------------------------------------------------------------------------------------------------- //


    // --------------------------------------------------- General Methods ------------------------------------------ //
    static byte[] clientInfoToBytes(ClientInfo client) {
        byte[] address = client.ADDRESS;
        byte[] canvas = intToByte(client.CANVAS_PORT);
        byte[] raft = intToByte(client.RAFT_PORT);
        byte[] text = intToByte(client.TEXT_PORT);
        byte[] key = client.KEY;
        byte[] term = intToByte(client.term);
        byte[] name = client.NAME.getBytes();

        byte[][] everything = {address, canvas, raft, text, key, term, name};
        return combine(everything);
    }

    static ClientInfo bytesToClientInfo(byte[] infoArray) {
        byte[] address = Arrays.copyOfRange(infoArray, 0, 4);
        int canvasPort = byteToInt(Arrays.copyOfRange(infoArray, 4, 6));
        int raftPort = byteToInt(Arrays.copyOfRange(infoArray, 6, 8));
        int textPort = byteToInt(Arrays.copyOfRange(infoArray, 8, 10));
        byte[] key = Arrays.copyOfRange(infoArray, 10, 18);
        int term = byteToInt(Arrays.copyOfRange(infoArray, 18, 20));
        String name = new String(Arrays.copyOfRange(infoArray, 20, infoArray.length));
        return new ClientInfo(name, address, canvasPort, raftPort, textPort, term, key);
    }

    static byte getPacketType(DatagramPacket packet) {
        byte[] data = packet.getData();
        if (data.length < 2) {
            return (byte) -1;
        } else {
            return data[1];
        }
    }

    static byte[] combine(byte[][] byteArrays) {
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            for (byte[] byteArray : byteArrays) {
                stream.write(byteArray);
            }
            byte[] combined = stream.toByteArray();
            stream.close();
            return combined;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Takes a byte array of contents and packages it into a DatagramPacket.
    static DatagramPacket wrapUp(byte type, byte[] content) {
        byte[] a = {0, type};
        byte[] b = intToByte(content.length);
        byte[][] everything = {a, b, content};
        byte[] data = combine(everything);

        if (data == null) {
            return null;
        } else {
            return new DatagramPacket(data, data.length);
        }
    }

    // Takes a DatagramPacket and extract the byte array of contents out of it.
    static byte[] unwrap(DatagramPacket packet) {
        byte[] data = packet.getData();
        int contentLength = byteToInt(Arrays.copyOfRange(data, 2, 4));
        return Arrays.copyOfRange(data, 4, contentLength + 4);
    }

    // Return the position of the next zero in the byte array after the starting position.
    static int getNextZero(byte[] array, int start) {
        for (int i = start; i < array.length; i++) {
            if (array[i] == 0) {
                return i;
            }
        }
        return 0;
    }

    // Takes an integer and turns it into 2 bytes.
    static byte[] intToByte(int i) {
        byte[] b = new byte[2];
        b[0] = (byte) (i / 256 - 128);
        b[1] = (byte) (i % 256 - 128);
        return b;
    }

    // Opposite of above method.
    static int byteToInt(byte[] bytes) {
        int i = 0;
        i += (bytes[0] + 128) * 256;
        i += (bytes[1] + 128);
        return i;
    }

    static DatagramSocket getAvailableSocket() {
        while (true) {
            try {
                int port = (int) ((Math.random() * (65535 - 1024)) + 1024);
                return new DatagramSocket(port);
            } catch (Exception ignored) {}
        }
    }
    // -------------------------------------------------------------------------------------------------------------- //
}
