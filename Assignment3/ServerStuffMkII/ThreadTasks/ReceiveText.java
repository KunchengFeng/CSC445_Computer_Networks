package ServerStuffMkII.ThreadTasks;

import ServerStuffMkII.CustomObjects.Host;
import ServerStuffMkII.CustomObjects.HostsManager;
import ServerStuffMkII.CustomObjects.Packet;
import ServerStuffMkII.CustomObjects.Text;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public class ReceiveText implements Runnable {
    DatagramSocket socket;
    AtomicBoolean keepGoing;
    Host mySelf;

    public ReceiveText(Host mySelf, DatagramSocket socket, AtomicBoolean keepGoing) {
        this.socket = socket;
        this.mySelf = mySelf;
        this.keepGoing = keepGoing;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[Packet.MAX_SIZE];
        DatagramPacket udpPacket = new DatagramPacket(buffer, buffer.length);

        while (keepGoing.get()) {
            try {
                socket.setSoTimeout(3000);
                socket.receive(udpPacket);
                Packet packet = Packet.parse(Arrays.copyOfRange(udpPacket.getData(), 0, udpPacket.getLength()));
                Text text = Text.parse(packet.CONTENT);
                text.printInfo();

                if (!packet.SOURCE.equals(mySelf.ID)) {
                    Packet ack = new Packet(Packet.ACKNOWLEDGEMENT, mySelf.ID, null, null);
                    byte[] ackBytes = ack.toBytes();
                    DatagramPacket ackUDP = new DatagramPacket(ackBytes, ackBytes.length);
                    socket.send(ackUDP);
                }
            }
            catch (SocketTimeoutException ignored) {}
            catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
