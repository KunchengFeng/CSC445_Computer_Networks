// This will be running on the school server to act as middle man for connecting us. Firewalls do not like UDP packets.

import ServerStuffMkII.CustomObjects.*;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;

public class MiddleMan {
    static final int SCHOOL_PORT = 27050;

    public static void main(String[] args) {
        try {
            HostsManager hostsManager = new HostsManager();
            DatagramSocket socket = new DatagramSocket(SCHOOL_PORT);
            byte[] buffer = new byte[Packet.MAX_SIZE];
            DatagramPacket udpPacket = new DatagramPacket(buffer, buffer.length);

            System.out.println("Using school port: 27050");

            while (true) {
                try {
                    socket.receive(udpPacket);
                    hostsManager.handlePacket(socket, udpPacket, buffer);
                    socket.setSoTimeout(5000);
                }
                catch (SocketTimeoutException e) {
                    ArrayList<Host> timeouts = hostsManager.getUnresponsive();
                    for (Host host : timeouts) {
                        System.out.println(host.NAME + " disconnected");
                    }
                    socket.setSoTimeout(0);
                }
            }


        } catch (IOException e) {
            System.out.println("Error, something went wrong...");
            e.printStackTrace();
            System.exit(-1);
        }
    }
}
