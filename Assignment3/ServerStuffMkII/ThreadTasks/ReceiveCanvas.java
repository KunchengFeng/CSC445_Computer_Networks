package ServerStuffMkII.ThreadTasks;

import GUI.Painter;
import ServerStuffMkII.CustomObjects.Host;

import java.net.DatagramSocket;
import java.util.concurrent.atomic.AtomicBoolean;

public class ReceiveCanvas implements Runnable {

    DatagramSocket socket;
    Host mySelf;
    AtomicBoolean keepGoing;
    Painter painter;

    public ReceiveCanvas(Host mySelf, DatagramSocket socket, AtomicBoolean keepGoing, Painter painter) {
        this.socket = socket;
        this.mySelf = mySelf;
        this.keepGoing = keepGoing;
        this.painter = painter;
    }

    @Override
    public void run() {
        System.out.println("Listening for canvas bytes....");
        while(keepGoing.get()){

            painter.receiveBytes();
//            try {
//                Thread.sleep(10);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }

        }
    }
}
