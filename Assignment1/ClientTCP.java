import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ClientTCP {
    static final int PORT = 27050;
    static final int TRIALS = 100;
    static final byte[] KEY = "Keyboard".getBytes();

    public static void main(String[] args) {
        System.out.print("Please enter the host's name: ");
        Scanner scanner = new Scanner(System.in);
        String hostName = scanner.nextLine();

        Socket echoSocket;
        DataOutputStream out;
        DataInputStream in;

        try {
            echoSocket = new Socket(hostName, PORT);
            out = new DataOutputStream(echoSocket.getOutputStream());
            in = new DataInputStream(echoSocket.getInputStream());

            System.out.println("Type an 4 byte command... [ late | thro | exit ]");

            while (true) {
                String input = scanner.nextLine();
                out.write(xor(input.getBytes()), 0, 4);                     // Inform server of current action.

                if (input.equalsIgnoreCase("exit")) {
                    break;

                } else if (input.equalsIgnoreCase("late")) {
                    System.out.println("Conducting Latency Test...");
                    latencyTest(8, out, in);
                    latencyTest(64, out, in);
                    latencyTest(256, out, in);
                    latencyTest(1024, out, in);
                    System.out.println("Latency Test Concluded.");

                } else if (input.equalsIgnoreCase("thro")) {
                    System.out.println("Conducting Throughput Test...");
                    throughputTest(1024, 1024, out, in);
                    throughputTest(2048, 512, out, in);
                    throughputTest(4096, 256, out, in);
                    System.out.println("Throughput Test Concluded.");

                } else {
                    System.out.println("Available commands: [ late | thro | exit ]");
                }
            }

            // Close stuff...
            System.out.println("Have a nice day...");
            out.close();
            in.close();
            echoSocket.close();

        } catch (UnknownHostException exception) {
            System.err.println("Unknown host: " + hostName);
            exception.printStackTrace();
            System.exit(1);
        } catch (IOException exception) {
            System.err.println("I/O Exception: ");
            exception.printStackTrace();
            System.exit(1);
        }
    }

    // Note that XOR operation is included in the calculation.
    private static void latencyTest(int size, DataOutputStream out, DataInputStream in) throws IOException {
        long average = 0;
        for (int i = 0; i < TRIALS; i++) {
            byte[] send = randomBytes(size);
            byte[] received = new byte[size];

            // Send
            long start = System.nanoTime();
            out.write(xor(send), 0, size);

            // Receive
            in.readFully(received, 0, size);
            long end = System.nanoTime();

            String s = new String(xor(received));
//            System.out.println("Received: " + s);

            // Calculation
            long tripTime = end - start;
            if (average == 0) {
                average = tripTime;
            } else {
                average = (average + tripTime) / 2;
            }
        }
        System.out.println("Message size: " + size + " bytes, average round time trip: " + (average / Math.pow(10,6)) + " ms.");
    }

    // Bits / Second
    private static void throughputTest(int packets, int size, DataOutputStream out, DataInputStream in) throws IOException {
        // Send
        long start = System.nanoTime();
        for (int i = 0; i < packets; i++) {
            byte[] data = randomBytes(size);
            out.write(xor(data), 0, size);
        }

        // Receive
        byte[] reply = new byte[8];
        in.readFully(reply, 0, 8);
        long end = System.nanoTime();
        long tripTime = end - start;

        // Calculate throughput
        long bits = (((long) packets * size) + 8) * 8;
        long throughput = (long) (bits / (tripTime / Math.pow(10, 9)));
        // Convert to megabits
        double mbps = throughput / Math.pow(10, 6);

        System.out.println(packets + " x " + size + " Byte message send, throughput: " + mbps + " Megabits/second.");

        String s = new String(xor(reply));
        System.out.println("Reply: " + s);
    }

    private static byte[] xor(byte[] data) {
        byte[] encoded = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            encoded[i] = (byte) (data[i] ^ KEY[i % 8]);
        }
        return encoded;
    }

    private static byte[] randomBytes(int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) Math.floor(Math.random() * (122 - 66) + 65);
        }
        return data;
    }
}
