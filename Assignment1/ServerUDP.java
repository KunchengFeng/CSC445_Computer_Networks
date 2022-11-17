import java.io.*;
import java.net.*;

public class ServerUDP {
    static final int PORT = 27050;
    static final int TRIALS = 100;
    static final byte[] KEY = "Keyboard".getBytes();

    public static void main(String[] args) {
        try {
            // Connect client
            DatagramSocket socket = new DatagramSocket(PORT);
            System.out.println("Waiting for packets...");

            while (true) {
                DatagramPacket packet = new DatagramPacket(new byte[4], 4);
                socket.receive(packet);
                String s = new String(xor(packet.getData()));

                if (s.equalsIgnoreCase("exit")) {
                    break;

                } else if (s.equalsIgnoreCase("late")) {
                    System.out.println("Latency Test Commenced...");
                    latencyTest(8, socket);
                    latencyTest(64, socket);
                    latencyTest(256, socket);
                    latencyTest(1024, socket);
                    System.out.println("Latency Test Concluded.");

                } else if (s.equalsIgnoreCase("thro")) {
                    System.out.println("Throughput Test Commenced...");
                    throughputTest(1024, 1024, socket);
                    throughputTest(2048, 512, socket);
                    throughputTest(4096, 256, socket);
                    System.out.println("Throughput Test Concluded.");

                } else {
                    System.out.println("Unknown command received: " + s);
                }
            }

            System.out.println("Disconnected...");
            socket.close();

        } catch(Exception exception) {
            exception.printStackTrace();
            System.exit(-1);
        }
    }

    private static void latencyTest(int size, DatagramSocket socket) throws IOException {
        DatagramPacket packet = new DatagramPacket(new byte[size], size);

        for (int i = 0; i < TRIALS; i++) {
            socket.receive(packet);
            String s = new String(xor(packet.getData()));
//            System.out.println("Received: " + s);
            socket.send(packet);
        }
    }

    private static void throughputTest(int packets, int size, DatagramSocket socket) throws IOException {
        DatagramPacket packet = new DatagramPacket(new byte[size], size);

        socket.setSoTimeout(10000);
        while (true) {
            try {
                for (int i = 0; i < packets; i++) {
                    // Receive packet
                    socket.receive(packet);
//                  System.out.println((i + 1) + " packets received...");
                }

                // Send back acknowledgement
                packet.setData(xor("Received".getBytes()));
                socket.send(packet);
                break;

            } catch (SocketTimeoutException exception) {
                System.out.println("Socket timeout, retrying...");

            }
        }

        socket.setSoTimeout(0);
    }

    private static byte[] xor(byte[] data) {
        byte[] encoded = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            encoded[i] = (byte) (data[i] ^ KEY[i % 8]);
        }
        return encoded;
    }
}
