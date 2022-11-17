package ServerStuffMkII.ThreadTasks;

import ServerStuffMkII.CustomObjects.HealthCheck;
import ServerStuffMkII.CustomObjects.Host;
import ServerStuffMkII.CustomObjects.ID;
import ServerStuffMkII.CustomObjects.Packet;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

// This class is for actively sending health checks
public class SendRaft implements Runnable {
    public static final int LEADER = 3;
    DatagramSocket socket;
    Host mySelf;
    AtomicInteger state, term;
    AtomicBoolean keepGoing;
    volatile HashMap<ID, Long> responseTracker;

    public SendRaft(Host mySelf, DatagramSocket socket, AtomicInteger state, AtomicInteger term, AtomicBoolean keepGoing, HashMap<ID, Long> responseTracker) {
        this.mySelf = mySelf;
        this.socket = socket;
        this.state = state;
        this.term = term;
        this.keepGoing = keepGoing;
        this.responseTracker = responseTracker;
    }

    @Override
    public void run() {
        while (keepGoing.get()) {
            int state = this.state.get();

            if (state == LEADER) {
                HealthCheck healthCheck = new HealthCheck(term.get(), responseTracker.size());
                Packet packet = new Packet(Packet.HEALTH_CHK, mySelf.ID, null, healthCheck.toBytes());
                byte[] packetByte = packet.toBytes();
                DatagramPacket udpPacket = new DatagramPacket(packetByte, packetByte.length);
                udpPacket.setData(packet.toBytes());

                try {
                    socket.send(udpPacket);
                    Thread.sleep(200);
                }
                catch (InterruptedException ignored) {}
                catch (Exception e) {
                    e.printStackTrace();
                    keepGoing.set(false);
                    break;
                }
            }
        }
    }
}
