package ServerStuffMkII.CustomObjects;

import java.util.Arrays;
import java.util.Objects;

public class Packet {
    public static final byte
            ACKNOWLEDGEMENT = -2,
            WARMUP = -1,
            REGISTRATION = 0,
            ACCEPTANCE = 1,
            TEXT = 2,
            HEALTH_CHK = 3,
            HEALTH_ACK = 4,
            VOTE_REQUEST = 5,
            VOTE_REPLY = 6,
            CANVAS_DATA = 7;
    public static final int MAX_SIZE = 2 + 8 + 8 + 60000;
    public static final int MAX_CONTENT = 60000;

    public final byte TYPE;
    public final ID SOURCE, TARGET;
    public final byte[] CONTENT;

    public Packet(byte type, ID source, ID target, byte[] content) {
        TYPE = type;
        SOURCE = Objects.requireNonNullElseGet(source, () -> new ID(new byte[] {0, 0, 0, 0, 0, 0, 0, 0}));
        TARGET = Objects.requireNonNullElseGet(target, () -> new ID(new byte[] {0, 0, 0, 0, 0, 0, 0, 0}));
        CONTENT = Objects.requireNonNullElseGet(content, () -> new byte[0]);
    }

    public void printInfo() {
        System.out.println("Packet information: " +
                "\nType: " + TYPE +
                "\nSource: " + SOURCE.toString() +
                "\nTarget: " + TARGET.toString() +
                "\nContent: " + Arrays.toString(CONTENT));
    }

    /*
        Generally a packet looks like this :-
            2 Bytes     8 Bytes     8 Bytes     0 ~ 32768 Bytes
        +-------------+-----------+-----------+---------+
        | Packet Type | Source ID | Target ID | CONTENT |
        +-------------+-----------+-----------+---------+
     */
    public byte[] toBytes() {
        byte[] a = {0, TYPE};
        byte[] b = SOURCE.toBytes();
        byte[] c = TARGET.toBytes();
        byte[][] everything = {a, b, c, CONTENT};
        return ByteHelper.combine(everything);
    }

    public static Packet parse(byte[] data) {
        byte type = data[1];
        ID source = new ID(Arrays.copyOfRange(data, 2, 10));
        ID target = new ID(Arrays.copyOfRange(data, 10, 18));
        byte[] content = Arrays.copyOfRange(data, 18, data.length);
        return new Packet(type, source, target, content);
    }

    public boolean hasTarget() {
        return !Arrays.equals(TARGET.toBytes(), new byte[] {0, 0, 0, 0, 0, 0, 0, 0});
    }

    public boolean hasSource() {
        return !Arrays.equals(SOURCE.toBytes(), new byte[] {0, 0, 0, 0, 0, 0, 0, 0});
    }
}
