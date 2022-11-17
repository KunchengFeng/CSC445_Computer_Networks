package ServerStuffMkII.CustomObjects;

import java.util.Arrays;

public record Text(String ownerName, String message) {

    /*
        Generally a text looks like this :-
        ... | CONTENT | :
        X               1   X
        +------------+---+---------+
        | Owner Name | 0 | Message |
        +------------+---+---------+
     */
    public byte[] toBytes() {
        byte[] a = ownerName.getBytes();
        byte[] b = {0};
        byte[] c = message.getBytes();
        byte[][] everything = {a, b, c};
        return ByteHelper.combine(everything);
    }

    public static Text parse(byte[] data) {
        int i = ByteHelper.getNextZero(data, 0);
        String ownerName = new String(Arrays.copyOfRange(data, 0, i));
        String message = new String(Arrays.copyOfRange(data, i + 1, data.length));
        return new Text(ownerName, message);
    }

    public void printInfo() {System.out.println(ownerName() + ": " + message());}
}
