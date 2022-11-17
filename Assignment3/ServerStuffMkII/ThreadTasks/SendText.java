package ServerStuffMkII.ThreadTasks;

import ServerStuffMkII.CustomObjects.Host;
import ServerStuffMkII.CustomObjects.Packet;
import ServerStuffMkII.CustomObjects.Text;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class SendText implements Runnable {
    Scanner scanner;
    DatagramSocket socket;
    Host mySelf;
    AtomicBoolean keepGoing;

    public SendText(Host mySelf, DatagramSocket socket, Scanner scanner, AtomicBoolean keepGoing) {
        this.socket = socket;
        this.mySelf = mySelf;
        this.scanner = scanner;
        this.keepGoing = keepGoing;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[Packet.MAX_SIZE];
        DatagramPacket udpPacket = new DatagramPacket(buffer, buffer.length);

        while (keepGoing.get()) {
            try {
                String input = scanner.nextLine();
                if (input.equalsIgnoreCase("exit")) {
                    keepGoing.set(false);
                    System.exit(0);
                }

                Text text = new Text(mySelf.NAME, input);
                Packet packet = new Packet(Packet.TEXT, mySelf.ID, null, text.toBytes());
                udpPacket.setData(packet.toBytes());

                try {socket.send(udpPacket);}
                catch (IOException e) {
                    System.err.println("Error at SendText().");
                    e.printStackTrace();
                }
            }
            catch (NoSuchElementException ignore) {}
        }
    }
}
