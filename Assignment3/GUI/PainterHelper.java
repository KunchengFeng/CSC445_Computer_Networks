package GUI;

import ServerStuffMkII.CustomObjects.Host;
import ServerStuffMkII.CustomObjects.Packet;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.util.Arrays;

public class PainterHelper {
    Host mySelf;
    DatagramSocket socket;

    public PainterHelper(Host mySelf, DatagramSocket socket) {
        this.mySelf = mySelf;
        this.socket = socket;
    }

    public void sendBytes(byte[] data) {
        Packet packet = new Packet(Packet.CANVAS_DATA, mySelf.ID, null, data);
        byte[] packetBytes = packet.toBytes();
        DatagramPacket udpPacket = new DatagramPacket(packetBytes, packetBytes.length);
        try {
            System.out.println("Byte array length: " + packetBytes.length);
            socket.send(udpPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public byte[] receiveBytes() {
        byte[] buffer = new byte[Packet.MAX_SIZE];
        DatagramPacket udpPacket = new DatagramPacket(buffer, buffer.length);

        while(true) {
            try {
                socket.setSoTimeout(100);
                socket.receive(udpPacket);
                byte[] data = Arrays.copyOfRange(buffer, 0, udpPacket.getLength());
                Packet packet = Packet.parse(data);

                if (!packet.SOURCE.equals(mySelf.ID)) {
                    Packet ack = new Packet(Packet.ACKNOWLEDGEMENT, mySelf.ID, null, null);
                    byte[] ackBytes = ack.toBytes();
                    DatagramPacket ackUDP = new DatagramPacket(ackBytes, ackBytes.length);
                    socket.send(ackUDP);

                    return packet.CONTENT;
                }
            }
            catch (SocketTimeoutException e) {
                return null;
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
