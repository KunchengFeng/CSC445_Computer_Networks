package ServerStuffMkII.CustomObjects;

import java.net.InetAddress;
import java.util.Arrays;

// This object will be primarily used by the school server to keep track of connected users.
public class Host {
    public final String NAME;
    public final int RAFT_NUM, CANVAS_NUM, TEXT_NUM;
    public final ID ID;
    public final InetAddress ADDRESS;
    private long lastResponded;

    public Host(String name, ID id, InetAddress address, int raft, int canvas, int text) {
        NAME = name;
        ID = id;
        ADDRESS = address;
        RAFT_NUM = raft;
        CANVAS_NUM = canvas;
        TEXT_NUM = text;
        this.lastResponded = System.currentTimeMillis();
    }

    public void printInfo() {
        System.out.println("Name: " + NAME
                + "\nID: " + ID.toString()
                + "\nAddress: " + ADDRESS.getHostAddress()
                + "\nRaft port: " + RAFT_NUM
                + "\nCanvas port: " + CANVAS_NUM
                + "\nText port: " + TEXT_NUM);
    }

    /*
        Host objects represented in byte array form :-
            ... | CONTENT | :
            8       2                   2                   2                   X
            +----+------------------+--------------------+------------------+------+
            | ID | Raft Port Number | Canvas Port Number | Text Port Number | Name |
            +----+------------------+--------------------+------------------+------+
     */
    public byte[] toBytes() {
        byte[] a = ID.toBytes();
        byte[] b = ByteHelper.intToByte(RAFT_NUM);
        byte[] c = ByteHelper.intToByte(CANVAS_NUM);
        byte[] d = ByteHelper.intToByte(TEXT_NUM);
        byte[] e = NAME.getBytes();
        byte[][] everything = {a, b, c, d, e};
        return ByteHelper.combine(everything);
    }

    // I can't really know my public address if I'm the one sending stuff.
    public static Host parse(byte[] data, InetAddress address) {
        ID id = new ID(Arrays.copyOfRange(data, 0, 8));
        int raft = ByteHelper.byteToInt(Arrays.copyOfRange(data, 8, 10));
        int canvas = ByteHelper.byteToInt(Arrays.copyOfRange(data, 10, 12));
        int text = ByteHelper.byteToInt(Arrays.copyOfRange(data, 12, 14));
        String name = new String(Arrays.copyOfRange(data, 14, data.length));
        return new Host(name, id, address, raft, canvas, text);
    }

    public void timeStamp() {lastResponded = System.currentTimeMillis();}

    // Time out means if I have not responded in 2 seconds;
    public boolean hasTimeout(long currentTime) {
        return currentTime - lastResponded > 2000;
    }
}
