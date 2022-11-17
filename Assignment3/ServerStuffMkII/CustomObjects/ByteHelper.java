package ServerStuffMkII.CustomObjects;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ByteHelper {

    // Integer to byte[2], max size 65,535.
    public static byte[] intToByte(int i) {
        byte[] b = new byte[2];
        b[0] = (byte) (i / 256 - 128);
        b[1] = (byte) (i % 256 - 128);
        return b;
    }

    // Opposite of above method.
    public static int byteToInt(byte[] bytes) {
        int i = 0;
        i += (bytes[0] + 128) * 256;
        i += (bytes[1] + 128);
        return i;
    }

    // Concatenating byte arrays
    public static byte[] combine(byte[][] byteArrays) {
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            for (byte[] byteArray : byteArrays) {
                stream.write(byteArray);
            }
            byte[] combined = stream.toByteArray();
            stream.close();
            return combined;

        } catch (IOException e) {
            System.out.println("Error while concatenating byte arrays.");
            e.printStackTrace();
            System.exit(-1);
            return new byte[0];     // This will never return, but now we don't have assert byte[]==null thing.
        }
    }

    public static int getNextZero(byte[] array, int start) {
        for (int i = start; i < array.length; i++) {
            if (array[i] == 0) {
                return i;
            }
        }
        return 0;
    }
}
