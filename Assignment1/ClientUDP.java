import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ClientUDP {
    static final int PORT = 27050;
    static final int TRIALS = 100;
    static final byte[] KEY = "Keyboard".getBytes();

    public static void main(String[] args) {
        System.out.print("Please enter the host's name: ");
        Scanner scanner = new Scanner(System.in);
        String hostName = scanner.nextLine();

        try {
            DatagramSocket socket = new DatagramSocket();
            InetAddress host = InetAddress.getByName(hostName);
            socket.connect(host, PORT);

            System.out.println("Type an 4 byte command... [ late | thro | exit ]");
            while (true) {
                String input = scanner.nextLine();
                if (input.length() < 4) {
                    System.out.println("Command too short.");
                    continue;
                } else {
                    DatagramPacket cmd = new DatagramPacket(xor(input.substring(0, 4).getBytes()), 4);
                    socket.send(cmd);
                }

                if (input.equalsIgnoreCase("exit")) {
                    break;

                } else if (input.equalsIgnoreCase("late")) {
                    System.out.println("Conducting Latency Test...");
                    latencyTest(8, socket);
                    latencyTest(64, socket);
                    latencyTest(256, socket);
                    latencyTest(1024, socket);
                    System.out.println("Latency Test Concluded.");

                } else if (input.equalsIgnoreCase("thro")) {
                    System.out.println("Conducting Throughput Test...");
                    throughputTest(1024, 1024, socket);
                    throughputTest(2048, 512, socket);
                    throughputTest(4096, 256, socket);
                    System.out.println("Throughput Test Concluded.");

                } else {
                    System.out.println("Available commands: [ late | thro | exit ]");
                }
            }

            socket.close();

        } catch (Exception exception) {
            System.err.println("Error, something went wrong...");
            exception.printStackTrace();
            System.exit(1);

        }
    }

    private static void latencyTest(int size, DatagramSocket socket) throws IOException {
        long average = 0;
        DatagramPacket packet = new DatagramPacket(new byte[size], size);

        for (int i = 0; i < TRIALS; i++) {
            byte[] send = randomBytes(size);
            byte[] received;
            packet.setData(xor(send));

            // Send
            long start = System.nanoTime();
            socket.send(packet);

            // Receive
            socket.receive(packet);
            long end = System.nanoTime();

            received = packet.getData();
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

        System.out.println("Message size: " + size + " bytes, average round time trip: " + (average / Math.pow(10,6))  + " ms.");
    }

    // Flood the server with packets, if received a reply calculate throughput, else try again.
    private static void throughputTest(int packets, int size, DatagramSocket socket) throws IOException, InterruptedException {
        DatagramPacket packet = new DatagramPacket(new byte[size], size);
        DatagramPacket reply = new DatagramPacket(new byte[8], 8);

        int waitTime = 1;
        socket.setSoTimeout(10000);
        while (true) {
            try {
                long start = System.nanoTime();

                for (int i = 0; i < packets; i++) {
                    packet.setData(xor(randomBytes(size)));
                    socket.send(packet);
                    Thread.sleep(waitTime);
                }
                socket.receive(reply);
                long end = System.nanoTime();
                long realTime = end - start;

                // Calculate throughput
                long bits = ((long) packets * size + 8) * 8;
                double megabits = bits / Math.pow(10, 6);
                long tripTime = (long) (realTime - packets * waitTime * Math.pow(10, 6));
                double throughput = megabits / (tripTime / Math.pow(10, 9));

                System.out.println(packets + " x " + size + " Byte message send, throughput: "
                                    + throughput + " megabits per second.");
                //        String s = new String(xor(reply.getData()));
                //        System.out.println("Reply: " + s);
                break;

            } catch (SocketTimeoutException exception) {
                System.out.println("Socket timeout, retrying " + packets + " x " + size + " byte message.");
                waitTime++;

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

    private static byte[] randomBytes(int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) Math.floor(Math.random() * (122 - 66) + 65);
        }
        return data;
    }
}
