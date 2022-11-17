// We will use this class with our personal computers
import GUI.PainterHelper;
import ServerStuffMkII.CustomObjects.Host;
import ServerStuffMkII.CustomObjects.ID;
import ServerStuffMkII.CustomObjects.Packet;
import ServerStuffMkII.ThreadTasks.*;

import GUI.Painter;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    static final int SCHOOL_PORT = 27050;

    public static void main(String[] args) {
        try {
            // ---------------------------------------------- Initialize stuff -------------------------------------- //
            Scanner scanner = new Scanner(System.in);
            System.out.println("Default server: pi.cs.oswego.edu" +
                    "\nDefault server port: 27050");
//            String server = scanner.nextLine();
            String server = "pi.cs.oswego.edu";
            InetAddress address = InetAddress.getByName(server);
            DatagramSocket raftSocket = getAvailableSocket();
            DatagramSocket canvasSocket = getAvailableSocket();
            DatagramSocket textSocket = getAvailableSocket();
            raftSocket.connect(address, SCHOOL_PORT);
            canvasSocket.connect(address, SCHOOL_PORT);
            textSocket.connect(address, SCHOOL_PORT);

            System.out.print("Please enter your name: ");
            String name = scanner.nextLine();
            ID id = new ID();
            Host mySelf = new Host(name, id, InetAddress.getLocalHost(), raftSocket.getLocalPort(), canvasSocket.getLocalPort(), textSocket.getLocalPort());
            mySelf.printInfo();
//            // ------------------------------------------------------------------------------------------------------ //
//
//
//            // ------------------------------------------ Register to school server --------------------------------- //
            canvasSocket.setSoTimeout(2500);
            raftSocket.setSoTimeout(2500);
            textSocket.setSoTimeout(2500);

            // Warm up the ports otherwise the server can't reach back
            Packet warmUp = new Packet(Packet.WARMUP, null, null, null);
            byte[] a = warmUp.toBytes();
            DatagramPacket aPacket = new DatagramPacket(a, a.length);

            textSocket.send(aPacket);
            raftSocket.send(aPacket);
            canvasSocket.send(aPacket);

            textSocket.receive(aPacket);
            raftSocket.receive(aPacket);
            canvasSocket.receive(aPacket);

            // True registration
            Packet registration = new Packet(Packet.REGISTRATION, mySelf.ID, null, mySelf.toBytes());
            byte[] registrationBytes = registration.toBytes();
            DatagramPacket registrationPacket = new DatagramPacket(registrationBytes, registrationBytes.length);
            raftSocket.send(registrationPacket);
            raftSocket.receive(registrationPacket);
            Packet response = Packet.parse(registrationPacket.getData());
            if (response.TYPE == Packet.ACCEPTANCE) {
                System.out.println("I have connected with a server.");
                canvasSocket.setSoTimeout(0);
                raftSocket.setSoTimeout(0);
                textSocket.setSoTimeout(0);
            }
//            // ------------------------------------------------------------------------------------------------------ //
//
//
//            // ---------------------------- Give tasks to other threads since some of them blocks ------------------- //
            AtomicInteger state = new AtomicInteger(1);
            AtomicInteger term = new AtomicInteger(0);
            AtomicBoolean keepGoing = new AtomicBoolean(true);
            HashMap<ID, Long> responseTracker = new HashMap<>();

            new Thread(new SendRaft(mySelf, raftSocket, state, term, keepGoing, responseTracker)).start();
            new Thread(new ReceiveRaft(mySelf, raftSocket, state, term, keepGoing, responseTracker)).start();
            new Thread(new SendText(mySelf, textSocket, scanner, keepGoing)).start();
            new Thread(new ReceiveText(mySelf, textSocket, keepGoing)).start();

            // GUI stuff

            new Thread(
                    new ReceiveCanvas(mySelf, canvasSocket, keepGoing,
                    new Painter("Collaborative Painter",
                            new PainterHelper(mySelf, canvasSocket)))).start();
//            PainterHelper painterHelper = new PainterHelper(mySelf, canvasSocket);
//            Painter painter = new Painter("Painter", painterHelper);
//            painter.start(painterHelper);
            // ------------------------------------------------------------------------------------------------------ //

        }
        catch (SocketTimeoutException e) {
            System.out.println("Error, server did not respond.");
            e.printStackTrace();
            System.out.println("\nTip: you need a MiddleMan.java class running on the school server in order to redirect all the packets.");
        }
        catch (Exception e) {
            System.err.println("Error, something went wrong at Main.main().");
            e.printStackTrace();
        }
    }

    private static DatagramSocket getAvailableSocket() {
        while (true) {
            try {
                int port = (int) ((Math.random() * (65535 - 1024)) + 1024);
                return new DatagramSocket(port);
            } catch (Exception ignored) {}
        }
    }
}
