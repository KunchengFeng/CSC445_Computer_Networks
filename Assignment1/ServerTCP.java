import java.io.*;
import java.net.*;

public class ServerTCP {
    static final int PORT = 27050;
    static final int TRIALS = 100;
    static final byte[] KEY = "Keyboard".getBytes();

    public static void main(String[] args) {
        try {
            // Connect client
            System.out.println("Waiting for an connection...");
            ServerSocket serverSocket = new ServerSocket(PORT);
            Socket clientSocket = serverSocket.accept();
            System.out.println("Connected a client...");

            DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
            DataInputStream in = new DataInputStream(clientSocket.getInputStream());

            while (true) {
                byte[] cmd = new byte[4];
                in.readFully(cmd, 0, 4);
                String s = new String(xor(cmd));

                if (s.equalsIgnoreCase("exit")) {
                    break;

                } else if (s.equalsIgnoreCase("late")) {
                    System.out.println("Latency Test Commenced...");
                    latencyTest(8, out, in);
                    latencyTest(64, out, in);
                    latencyTest(256, out, in);
                    latencyTest(1024, out, in);
                    System.out.println("Latency Test Concluded.");

                } else if (s.equalsIgnoreCase("thro")) {
                    System.out.println("Throughput Test Commenced...");
                    throughputTest(1024, 1024, out, in);
                    throughputTest(2048, 512, out, in);
                    throughputTest(4096, 256, out, in);
                    System.out.println("Throughput Test Concluded.");

                } else {
                    System.out.println("Unknown command received..." + s);
                }
            }

            System.out.println("Disconnected...");
            in.close();
            out.close();
            clientSocket.close();
            serverSocket.close();

        } catch(IOException exception) {
            exception.printStackTrace();
            System.exit(-1);
        }
    }

    private static void latencyTest(int size, DataOutputStream out, DataInputStream in) throws IOException {
        byte[] data = new byte[size];

        for (int i = 0; i < TRIALS; i++) {
            in.readFully(data);
//            String s = new String(xor(data));
//            System.out.println("Received: " + s);
            out.write(data, 0, size);
        }
    }

    private static void throughputTest(int packets, int size, DataOutputStream out, DataInputStream in) throws IOException {
        byte[] data = new byte[size];

        for (int i = 0; i < packets; i++) {
            in.readFully(data);
        }

        byte[] reply = "Received".getBytes();

        out.write(xor(reply), 0, 8);
    }

    private static byte[] xor(byte[] data) {
        byte[] encoded = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            encoded[i] = (byte) (data[i] ^ KEY[i % 8]);
        }
        return encoded;
    }
}
