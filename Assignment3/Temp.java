import ServerStuffMkII.CustomObjects.*;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;

public class Temp {

    public static void main(String[] args) throws IOException {

    }

    private boolean majority(int prop, DatagramSocket[] hosts) {
        int votesNeeded = hosts.length / 2 + 1;
        byte[] data = {(byte) prop};
        DatagramPacket packet = new DatagramPacket(data, data.length);

        try {
            for (DatagramSocket host : hosts) {
                host.send(packet);
            }

            int votes = 0;
            for (DatagramSocket host : hosts) {
                host.receive(packet);
                if (data[0] >= 0) {
                    votes++;
                }
            }

            return votes >= votesNeeded;

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static byte[] createKey(int length) {
        byte[] key = new byte[length];
        int i = 0;
        while(i < length) {
            byte random = (byte) (Math.random() * (127 + 128) - 128);
            if (random != 0) {          // Cannot have a zero in the key due to parsing process.
                key[i] = random;
                i++;
            }
        }
        return key;
    }

    private static byte[] xor(byte[] data, byte[] key) {
        byte[] encoded = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            encoded[i] = (byte) (data[i] ^ key[i % key.length]);
        }
        return encoded;
    }
}
