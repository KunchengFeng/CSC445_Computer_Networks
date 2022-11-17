package ServerStuff;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

public class ClientInfo {
    public final String NAME;
    public final byte[] ADDRESS;
    public final int CANVAS_PORT, RAFT_PORT, TEXT_PORT;
    public final byte[] KEY;
    public int term;

    public ClientInfo(String name, byte[] address, int canvasPort, int raftPort, int textPort) {
        this.NAME = name;
        this.ADDRESS = address;
        this.CANVAS_PORT = canvasPort;
        this.RAFT_PORT = raftPort;
        this.TEXT_PORT = textPort;
        this.KEY = generateKey();
        this.term = 0;
    }

    public ClientInfo(String name, byte[] address, int canvasPort, int raftPort, int textPort, int term, byte[] key) {
        this.NAME = name;
        this.ADDRESS = address;
        this.CANVAS_PORT = canvasPort;
        this.RAFT_PORT = raftPort;
        this.TEXT_PORT = textPort;
        this.KEY = key;
        this.term = term;
    }

    public void printInfo() {
        System.out.println("Name: " + NAME
                + "\nAddress: " + getAddressString()
                + "\nCanvas Port: " + CANVAS_PORT
                + "\nRaft Port: " + RAFT_PORT
                + "\nText Port: " + TEXT_PORT
                + "\nKey: " + Arrays.toString(KEY)
                + "\nCurrent Term: " + term);
    }

    private byte[] generateKey() {
        byte[] key = new byte[8];
        for (int i = 0; i < 8; i++) {
            key[i] = (byte) (Math.random() * (127 + 128) - 128);
        }
        return key;
    }

    String getAddressString() {
        try {
            InetAddress a = InetAddress.getByAddress(ADDRESS);
            return a.getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return null;
        }
    }
}
