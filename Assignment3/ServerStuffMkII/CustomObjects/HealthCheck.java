package ServerStuffMkII.CustomObjects;

import java.util.Arrays;

public class HealthCheck {
    public final int TERM, HOST_COUNTS;

    public HealthCheck(int term, int hostCounts) {
        TERM = term;
        HOST_COUNTS = hostCounts;
    }

    /*
        Very simple looking object
        ...| CONTENT | =:
                2 bytes                 2 bytes
            +-----------------------+---------------------------+
            | leader's current term | leader's known host count |
            +-----------------------+---------------------------+
     */
    public byte[] toBytes() {
        byte[] a = ByteHelper.intToByte(TERM);
        byte[] b = ByteHelper.intToByte(HOST_COUNTS);
        byte[][] c = {a, b};
        return ByteHelper.combine(c);
    }

    public static HealthCheck parse(byte[] data) {
        int term = ByteHelper.byteToInt(Arrays.copyOfRange(data, 0, 2));
        int hostCounts = ByteHelper.byteToInt(Arrays.copyOfRange(data, 2, 4));
        return new HealthCheck(term, hostCounts);
    }
}
