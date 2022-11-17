import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;

// This class organize all the necessary information involving TFTP packets.
public class TFTP_Packets {
    String fileName;
    int windowSize;
    int packetSize;
    int expectedPackets;
    boolean drop;
    byte[] key;

    HashMap<Integer, byte[]> dataPackets;
    HashMap<Integer, Integer> acknowledgements;

    // If data is empty, it needs to be inserted later.
    public TFTP_Packets(String fileName, byte[] fileByteArray, int windowSize, int packetSize, boolean drop) {
        this.fileName = fileName;
        this.drop = drop;
        this.windowSize = windowSize;
        this.packetSize = packetSize;
        this.key = createKey((int) ((Math.random() * (32 - 8)) + 8));
        this.dataPackets = new HashMap<>();
        this.acknowledgements = new HashMap<>();

        if (fileByteArray != null) {
            int blockSize = packetSize - 4;
            int packet = 0;
            // Insert data into normal packets
            while ((packet + 1) * blockSize < fileByteArray.length) {
                byte[] data = Arrays.copyOfRange(fileByteArray, packet * blockSize, (packet + 1) * blockSize);
                dataPackets.put(packet, data);
                packet++;
            }
            // Insert last data to last packet
            if (packet * blockSize < fileByteArray.length) {
                byte[] data = Arrays.copyOfRange(fileByteArray, packet * blockSize, fileByteArray.length);
                dataPackets.put(packet, data);
            }

            this.expectedPackets = dataPackets.size();
        } else {
            this.expectedPackets = 1;
        }
    }

    // Have this instance created based on the request byte array
    public TFTP_Packets(byte[] requestArray, int packetSize) {
        int start = 2;
        int end = getNextZero(requestArray, start);
        this.fileName = new String(Arrays.copyOfRange(requestArray, start, end));
        System.out.println("Parsed filename: " + fileName);

        start = end + 1;
        end = getNextZero(requestArray, start);
        this.expectedPackets = Integer.parseInt(new String(Arrays.copyOfRange(requestArray, start, end)));
        System.out.println("Parsed expected packets: " + expectedPackets);

        start = end + 1;
        this.windowSize = requestArray[start];
        end = start + 1;
        System.out.println("Parsed window size: " + windowSize);

        start = end + 1;
        this.drop = requestArray[start] == 1;
        end = start + 1;
        System.out.println("Parsed drop: " + drop);

        start = end + 1;
        end = getNextZero(requestArray, start);
        this.key = Arrays.copyOfRange(requestArray, start, end);
        System.out.println("Parsed key: " + Arrays.toString(key));


        this.packetSize = packetSize;
        this.dataPackets = new HashMap<>();
        this.acknowledgements = new HashMap<>();
    }

    // -------------------------------------------- Primary Functions ------------------------------------------------//

    public int countPackets() {return dataPackets.size();}

    public int countBytes() {
        int last = dataPackets.get(dataPackets.size() - 1).length;
        return (dataPackets.size() - 1) * packetSize + last;
    }

    public byte[] createRequest(byte opcode) {
        byte[] block = new byte[0];
        try {
            ByteArrayOutputStream request = new ByteArrayOutputStream();
            request.write(0);
            request.write(opcode);
            request.write(fileName.getBytes());
            request.write(0);
            request.write(Integer.toString(expectedPackets).getBytes());
            request.write(0);
            request.write(windowSize);
            request.write(0);
            request.write(tfToByte(drop));
            request.write(0);
            request.write(key);
            request.write(0);

            block = request.toByteArray();
            request.close();
        } catch (IOException exception) {
            System.out.println("Error, something went wrong while creating request.");
            exception.printStackTrace();
            System.exit(1);
        }
        return block;
    }

    public byte[] createResponds(byte opcode) {
        byte[] packet = new byte[0];
        try {
            ByteArrayOutputStream request = new ByteArrayOutputStream();
            request.write(0);
            request.write(opcode);
            request.write(fileName.getBytes());
            request.write(0);
            request.write(Integer.toString(expectedPackets).getBytes());
            request.write(0);
            request.write(windowSize);
            request.write(0);
            request.write(tfToByte(drop));
            request.write(0);
            request.write(key);
            request.write(0);

            packet = request.toByteArray();
            request.close();
        } catch (IOException exception) {
            System.out.println("Error, something went wrong while creating responds.");
            exception.printStackTrace();
            System.exit(1);
        }
        return packet;
    }

    public byte[] createError(byte errorCode, String message) {
        byte[] packet = new byte[0];
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] head = {0, 5, 0, errorCode};
            byte[] body = message.getBytes();
            buffer.write(head);
            buffer.write(body);
            packet = buffer.toByteArray();
        } catch (Exception exception) {
            System.out.println("Error, something went wrong...");
            exception.printStackTrace();
            System.exit(1);
        }
        return packet;
    }

    // Note packet number start from 1.
    public byte[] getDataBlock(int packetNumber) {
        byte[] head = {0, 3};
        byte[] blockNum = blockToByte(packetNumber);
        byte[] packet = new byte[0];
        try{
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            buffer.write(head);
            buffer.write(blockNum);
            buffer.write(dataPackets.get(packetNumber-1));

            // Manually fill in zeros if is last packet.
            if (packetNumber == dataPackets.size()) {
                int fillerSize = packetSize - 4 - dataPackets.get(packetNumber - 1).length;
                byte[] filler = new byte[fillerSize];
                buffer.write(filler);
            }
            packet = buffer.toByteArray();
        } catch (IOException exception) {
            System.err.println("Error, problem with getting packets.");
            exception.printStackTrace();
            System.exit(-1);
        }
        return packet;
    }

    public void insertFileData(byte[] fileByteArray) {
        int blockSize = packetSize - 4;
        int packet = 0;
        // Insert data into normal packets
        while ((packet + 1) * blockSize < fileByteArray.length) {
            byte[] data = Arrays.copyOfRange(fileByteArray, packet * blockSize, (packet + 1) * blockSize);
            dataPackets.put(packet, data);
            packet++;
        }
        // Insert last data to last packet
        if (packet * blockSize < fileByteArray.length) {
            byte[] data = Arrays.copyOfRange(fileByteArray, packet * blockSize, fileByteArray.length);
            dataPackets.put(packet, data);
        }
        expectedPackets = dataPackets.size();
    }

    public boolean isLastPacket(int packetNumber) {return packetNumber == dataPackets.size();}

    // All this check is if there are any missing packets before the last packet
    public boolean isAllReceived() {
        return expectedPackets == dataPackets.size();
    }

    public boolean isAllSent() {
        return acknowledgements.size() == dataPackets.size();
    }

    // Turn all the stored data packets into data array.
    public byte[] packetsToData() {
        byte[] data = new byte[0];
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            for (int i = 0; i < dataPackets.size(); i++) {
                buffer.write(dataPackets.get(i));
            }
            data = buffer.toByteArray();
            buffer.close();

        } catch (IOException exception) {
            System.out.println("Error, something went wrong...");
            exception.printStackTrace();
            System.exit(1);
        }

        return data;
    }

    public void storeDataBlock(byte[] dataBlock) {
        if (dataBlock.length == 4) {return;}
        int block = byteToBlock(Arrays.copyOfRange(dataBlock, 2, 4));
        dataPackets.put(block-1, Arrays.copyOfRange(dataBlock, 4, dataBlock.length));
    }

    public void storeAckBlock(byte[] ackBlock) {
        int block = byteToBlock(Arrays.copyOfRange(ackBlock, 2, 4));
        acknowledgements.put(block-1, 1);
    }

    public byte[] getKey() {return key;}
    public String getFileName() {return fileName;}
    public int getWindowSize() {return windowSize;}

    public void printInfo() {
        System.out.println("FileName: " + fileName);
        System.out.println("Expected Packages: " + expectedPackets);
        System.out.println("Current Packages: " + dataPackets.size());
        System.out.println("WindowSize: " + windowSize);
        System.out.println("PacketSize: " + packetSize);
        System.out.println("Drop: " + drop);
        System.out.println("Key: " + Arrays.toString(key));
    }


    // ----------------------------------------------- Secondary Functions -------------------------------------------//


    // block number = first byte * 127 + second byte. Because I'm running out of block numbers.
    public static byte[] blockToByte(int number) {
        return new byte[]{(byte) (number / 127), (byte) (number % 127)};
    }

    public static int byteToBlock(byte[] blockBytes) {
        return blockBytes[0] * 127 + blockBytes[1];
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

    // true -> 1; false -> 2
    private static byte tfToByte(boolean b) {
        if (b) {
            return 1;
        } else {
            return 2;
        }
    }

    // 1 -> true; 2 -> false
    private static boolean byteToTF(byte b) {
        return b == 1;
    }

    public static byte[] getActualBlock(byte[] dataBlock) {
        int end = 0;
        for (int i = dataBlock.length-1; i >= 0; i--) {
            if (dataBlock[i] != 0) {
                end = i+1;
                break;
            }
        }
        return Arrays.copyOfRange(dataBlock, 0, end);
    }

    // start is inclusive
    private static int getNextZero(byte[] array, int start) {
        for (int i = start; i < array.length; i++) {
            if (array[i] == 0) {
                return i;
            }
        }
        return 0;
    }
}
