import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

// This class handles the sending and receiving of the packets.
public class TFTP {
    /*
    Project Background {
        TFTP :- Trivial File Transfer Protocol
            -> Implemented on top of UDP
            -> Read & write files from/to a remote server
        Octet :- An 8 bit byte.

        TFTP Packet:
            Opcode  Operation
            01      Read request (RRQ)
            02      Write request (WRQ)
            03      Data (DATA)
            04      Acknowledgement (ACK)
            05      ERROR (ERROR)
            06      Option acknowledgement (OACK)

        Error Codes:
            Value   Meaning
            00      Not defined, see error message
            01      File not found
            02      Access violation
            03      Disk full or allocation exceeded
            04      Illegal TFTP operation
            05      Unknown transfer ID
            06      File already exists
            07      No such user
            08      Option termination

        Packet Format:
            RRQ/WRQ:
                    2      String    1  String  1
                +-------+----------+---+------+---+
                | 01/02 | Filename | 0 | Mode | 0 |
                +-------+----------+---+------+---+
                !Note: only "octet" mode is supported for this project!

            DATA:
                  2    2          n
                +----+---------+------+
                | 03 | Block # | data |
                +----+---------+------+
                !Note: data packet is usually 516 bytes long, with 512 bytes of data, shorter length means last data block.
                !Note: block number = first byte * 127 + second byte. Because I'm running out of block numbers.

            ACK:
                  2    2
                +----+---------+
                | 04 | Block # |
                +----+---------+

            ERROR:
                  2    2           String   1
                +----+-----------+--------+---+
                | 05 | ErrorCode | ErrMsg | 0 |
                +----+-----------+--------+---+

            (Option packets)
            RRQ/WRQ:
                +--------+----------+---+------+---+------+---+--------+---+------+---+--------+---+
                | Opcode | Filename | 0 | Mode | 0 | Opt1 | 0 | Value1 | 0 | OptN | 0 | ValueN | 0 |
                +--------+----------+---+------+---+------+---+--------+---+------+---+--------+---+

            OACK:
                +--------+------+---+--------+---+------+---+--------+---+
                | Opcode | Opt1 | 0 | Value1 | 0 | OptN | 0 | ValueN | 0 |
                +--------+------+---+--------+---+------+---+--------+---+

        !(For this assignment)
            RRQ/WRQ:
                +-------+----------+---+------------+---+----------+---+--------+---+-------+---+
                | 01/02 | Filename | 0 | <Packages> | 0 | <Window> | 0 | <Drop> | 0 | <Key> | 0 |
                +-------+----------+---+------------+---+----------+---+--------+---+-------+---+
            -> !Note the variables are separated by 0s, so avoid them been in variables.
            -> Only image files are going to be transferred, where else am I going to find bytes?
            -> Key option is going to be the last.
            -> Drop option :- 1 = true, 2 = false.
            -> Amount of packages are known ahead of time, makes things much easier.
    }
     */

    static final int PORT = 27050;
    static final int PACKET_SIZE = 516;
    static final byte OP_RRQ = 1;
    static final byte OP_WRQ = 2;
    static final byte OP_DATA = 3;
    static final byte OP_ACK = 4;
    static final byte OP_ERROR = 5;
    static final byte OP_OACK = 6;
    static final byte OP_TERM = 7;          // Added in for this assignment
    static final byte OP_EXIT = 8;          // Shutdown server remotely


    public static void main(String[] args) {
        String input;
	    Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("Is this a server? [y/n] ");
            input = scanner.nextLine();
            if (input.equalsIgnoreCase("y")) {
                serverTFTP();
                break;
            } else if (input.equalsIgnoreCase("n")) {
                clientTFTP(scanner);
                break;
            } else {
                System.out.println("Unknown input.");
            }
        }
    }



    // ----------------------------------------------- Client Operations ---------------------------------------------//

    private static void clientTFTP(Scanner scanner) {
        System.out.print("Please enter the host's name: ");
        String hostName = scanner.nextLine();

        try {
            // Connect to host
            DatagramSocket socket = new DatagramSocket();
            InetAddress host = InetAddress.getByName(hostName);
            socket.connect(host, PORT);

            String operation = "";
            while (true) {
                operation = askOperation(scanner);
                String fileName = askFileName(scanner);
                byte windowSize = askWindowSize(scanner);
                boolean drop = askDrop(scanner);

                if (operation.equalsIgnoreCase("upload")) {
                    clientUpload(socket, fileName, windowSize, drop);
                } else if (operation.equalsIgnoreCase("download")) {
                    clientDownload(socket, fileName, windowSize, drop);
                } else if (operation.equalsIgnoreCase("exit")){
                    // Tell the server to shut down as well.
                    byte[] last = {0, 8};
                    DatagramPacket packet = new DatagramPacket(last, 2);
                    socket.send(packet);
                    break;
                }
            }
        } catch (Exception exception) {
            System.err.println("Something went wrong...");
            exception.printStackTrace();
            System.exit(1);
        }
    }

    private static void clientUpload(DatagramSocket socket, String fileName, byte windowSize, boolean drop) throws IOException {
        byte[] data = readFile(fileName);
        if (data.length == 0) {return;}

        TFTP_Packets packets = new TFTP_Packets(fileName, data, windowSize, PACKET_SIZE, drop);
        packets.printInfo();

        byte[] request = packets.createRequest(OP_WRQ);

        // First packet, unencrypted.
        DatagramPacket datagramPacket = new DatagramPacket(request, request.length);
        socket.send(datagramPacket);

        socket.receive(datagramPacket);
        byte[] reply = datagramPacket.getData();
        reply = xor(reply, packets.getKey());

        if (reply[1] == OP_OACK) {
            // Packets are sent here.
            long start = System.nanoTime();
            sendPackets(socket, packets, null, 0);
            long end = System.nanoTime();
            calculateThroughput(start, end, packets);

        } else if (reply[1] == OP_ERROR) {
            System.out.println("Error: " + new String(Arrays.copyOfRange(reply, 4, reply.length-1)));
        } else {
            System.out.println("Unexpected exchange...");
            System.exit(1);
        }
    }

    private static void clientDownload(DatagramSocket socket, String fileName, byte windowSize, boolean drop) throws IOException {
        TFTP_Packets packets = new TFTP_Packets(fileName, null, windowSize, PACKET_SIZE, drop);
        System.out.println("Generated key: " + Arrays.toString(packets.getKey()));
        byte[] request = packets.createRequest(OP_RRQ);
        DatagramPacket packet = new DatagramPacket(request, request.length);
        socket.send(packet);

        // Wait for the server for more information about the packets
        byte[] response = new byte[100];
        DatagramPacket responsePacket = new DatagramPacket(response, response.length);
        socket.receive(responsePacket);
        packets = new TFTP_Packets(responsePacket.getData(), PACKET_SIZE);
        byte[] ackBlock = {0, OP_ACK, 0, 0};
        DatagramPacket ackPacket = new DatagramPacket(ackBlock, ackBlock.length);
        socket.send(ackPacket);

        // Packets are sent here
        long start = System.nanoTime();
        receivePackets(socket, packets);
        long end = System.nanoTime();
        calculateThroughput(start, end, packets);

        FileOutputStream writer = new FileOutputStream(packets.getFileName());
        writer.write(packets.packetsToData());
        System.out.println("Image created.");
    }



    // ---------------------------------------------- Server Operations ----------------------------------------------//

    private static void serverTFTP() {
        try {
            System.out.println("Waiting for connection...");

            DatagramSocket socket = new DatagramSocket(PORT);

            while (true) {
                byte[] buffer = new byte[100];
                DatagramPacket requestPacket = new DatagramPacket(buffer, buffer.length);
                socket.receive(requestPacket);
                buffer = requestPacket.getData();

                if (buffer[1] == OP_RRQ) {
                    TFTP_Packets packets = new TFTP_Packets(buffer, PACKET_SIZE);
                    FileInputStream reader = new FileInputStream(packets.getFileName());
                    byte[] fileData = reader.readAllBytes();
                    reader.close();
                    packets.insertFileData(fileData);

                    // Inform the client of the upcoming packets
                    buffer = packets.createRequest(OP_OACK);
                    requestPacket.setData(buffer);
                    socket.send(requestPacket);
                    // Wait for a response from the client
                    byte[] ackBlock = new byte[4];
                    DatagramPacket ackPacket = new DatagramPacket(ackBlock, ackBlock.length);
                    socket.receive(ackPacket);

                    sendPackets(socket, packets, requestPacket.getAddress(), requestPacket.getPort());

                } else if (buffer[1] == OP_WRQ) {
                    TFTP_Packets packets = new TFTP_Packets(buffer, PACKET_SIZE);
                    packets.printInfo();

                    byte[] response = packets.createResponds(OP_OACK);

                    response = xor(response, packets.getKey());
                    requestPacket.setData(response);
                    socket.send(requestPacket);

                    receivePackets(socket, packets);

                    FileOutputStream writer = new FileOutputStream(packets.getFileName());
                    writer.write(packets.packetsToData());
                    System.out.println("Image saved...");

                } else if (buffer[1] == 8) {
                    break;
                }
            }
            socket.close();
        } catch (IOException exception) {
            System.err.println("Something went wrong...");
            exception.printStackTrace();
            System.exit(1);
        }
    }


    // ---------------------------------------------- Send and Receive -----------------------------------------------//

    private static void sendPackets(DatagramSocket socket, TFTP_Packets packets, InetAddress address, int port) throws IOException {
        byte[] dataBlock = new byte[PACKET_SIZE];
        byte[] ackBlock = new byte[4];
        DatagramPacket dataPacket = new DatagramPacket(dataBlock, dataBlock.length);
        DatagramPacket ackPacket = new DatagramPacket(ackBlock, ackBlock.length);
        if (address != null) {
            dataPacket.setAddress(address);
            ackPacket.setAddress(address);
        }
        if (port != 0) {
            dataPacket.setPort(port);
            ackPacket.setPort(port);
        }

        socket.setSoTimeout(100);
        int block = 1;
        int windowCounter = 0;

        while (!packets.isAllSent()) {
            try {
                dataBlock = packets.getDataBlock(block);
                if (packets.isLastPacket(block)) {
                    dataBlock[1] = OP_TERM;
                }
                dataBlock = xor(dataBlock, packets.getKey());
                dataPacket.setData(dataBlock);
                socket.send(dataPacket);
                System.out.println("Packet " + block + " sent.");
                windowCounter++;

                if (windowCounter == packets.getWindowSize() || packets.isLastPacket(block)) {
                    while (windowCounter > 0) {
                        socket.receive(ackPacket);
                        ackBlock = ackPacket.getData();
                        ackBlock = xor(ackBlock, packets.getKey());
                        packets.storeAckBlock(ackBlock);
                        windowCounter--;
                    }
                    if (packets.isLastPacket(block)) {break;}
                }

                block++;

            } catch (SocketTimeoutException exception) {
                block -= packets.getWindowSize();
                windowCounter = 0;
                if (block < 1) {block = 1;}
            }
        }

        socket.setSoTimeout(0);
        System.out.println("All packages send...");
    }

    private static void receivePackets(DatagramSocket socket, TFTP_Packets packets) throws IOException {
        byte[] received = new byte[PACKET_SIZE];
        byte[] response = new byte[4];
        DatagramPacket dataPacket = new DatagramPacket(received, received.length);
        DatagramPacket ackPacket = new DatagramPacket(response, response.length);

        do {
            // Receive packet
            socket.receive(dataPacket);
            // simulates a drop by not sending a response packet
            if (packets.drop && ((int)(Math.random() * 100)) == 1) {
                System.out.println("Dropped a packet...");
                continue;
            }

            received = dataPacket.getData();
            received = xor(received, packets.getKey());

            // Process received data block.
            if (received[1] == OP_TERM) {
                byte[] actualBlock = TFTP_Packets.getActualBlock(received);
                packets.storeDataBlock(actualBlock);
                System.out.println("Packet received, block num: " + TFTP_Packets.byteToBlock(Arrays.copyOfRange(actualBlock, 2, 4)) + ", last block length: " + actualBlock.length);
                packets.printInfo();
            } else {
                packets.storeDataBlock(received);
                System.out.println("Packet received, block num: " + TFTP_Packets.byteToBlock(Arrays.copyOfRange(received, 2, 4)) + ", block length: " + received.length);
            }

            // send back a 4 byte response
            response = Arrays.copyOfRange(received, 0, 4);
            response = xor(response, packets.getKey());
            ackPacket.setData(response);
            ackPacket.setAddress(dataPacket.getAddress());
            ackPacket.setPort(dataPacket.getPort());
            socket.send(ackPacket);

        } while (!packets.isAllReceived());

        System.out.println("All packets received...");
    }


    // ---------------------------------------------- Rest of the functions ------------------------------------------//

    // Have the user indicate a file to read, then return the file data as byte array.
    private static byte[] readFile(String fileName) {
        byte[] data = new byte[0];
        try {
            FileInputStream reader = new FileInputStream(fileName);
            data = reader.readAllBytes();
            reader.close();
        } catch (FileNotFoundException e){
            System.out.println("File not found...");
        } catch (IOException e) {
            System.out.println("Can't read file...");
        }
        return data;
    }

    private static boolean askDrop(Scanner scanner) {
        System.out.print("Do you wish 1 percent of the packets to be dropped? [y/n] ");
        String input = scanner.nextLine();
        if (input.equalsIgnoreCase("y")) {
            return true;
        } else if (input.equalsIgnoreCase("n")) {
            return false;
        } else {
            System.out.println("Assumed no.");
            return false;
        }
    }

    private static String askFileName(Scanner scanner) {
        System.out.print("Please enter the file's name: ");
        return scanner.nextLine();
    }

    private static String askOperation(Scanner scanner) {
        System.out.print("What operation do you want to perform? [upload/download/exit] ");
        return scanner.nextLine();
    }

    private static byte askWindowSize(Scanner scanner) {
        System.out.print("Please enter the sliding windows' size: [ <= 127] ");
        return Byte.parseByte(scanner.nextLine());
    }

    // Throughput is in Megabits per second
    private static void calculateThroughput(long start, long end, TFTP_Packets packets) {
        long dataBits = packets.countBytes() * 8L;
        long ackBits = packets.countPackets() * 4 * 8L;
        long bits = dataBits + ackBits;
        double megabits = bits / Math.pow(10, 6);
        double seconds = (end - start) / Math.pow(10, 9);
        double mbps = megabits / seconds;
        System.out.println("Throughput: " + mbps + " megabits per second.");
    }

    private static byte[] xor(byte[] data, byte[] key) {
        byte[] encoded = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            encoded[i] = (byte) (data[i] ^ key[i % key.length]);
        }
        return encoded;
    }
}
