package GUI;

import ServerStuffMkII.CustomObjects.Packet;

import java.awt.*;
import javax.swing.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class Painter extends JFrame {
    static final int WIDTH = 800;
    static final int HEIGHT = 600;
    static final int MAX_PEN_SIZE = 25;

    private int x = 0;
    private int y = 0;
    static JColorChooser colorChooser;
    static JPanel canvas;
    static JSpinner penSizeBox;
    private ConcurrentHashMap<Point, Color> pixelMap;
    private HashMap<Point, Color> outgoingPixels;
    private final PainterHelper painterHelper;

    public Painter(String title, PainterHelper helper) {
        painterHelper = helper;


        javax.swing.SwingUtilities.invokeLater(this::createAndShowGUI);

        setTitle(title);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        canvas = new Canvas(WIDTH, HEIGHT);
        canvas.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

        canvas.addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent event) {
                x = event.getX();
                y = event.getY();
                int penSize = (int) penSizeBox.getValue();

                for(int i = x; i < x+penSize; i++){
                    for (int j = y; j < y+penSize; j++){
                        pixelMap.put(new Point(i,j), colorChooser.getColor());
                        outgoingPixels.put(new Point(i,j), colorChooser.getColor());
                    }
                }
                repaint();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                repaint();
            }
        });
        canvas.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {

            }

            @Override
            public void mousePressed(MouseEvent event) {
                x = event.getX();
                y = event.getY();
                int penSize = (int) penSizeBox.getValue();

                for(int i = x; i < x+penSize; i++){
                    for (int j = y; j < y+penSize; j++){
                        pixelMap.put(new Point(i,j), colorChooser.getColor());
                        outgoingPixels.put(new Point(i,j), colorChooser.getColor());
                    }
                }

                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                System.out.println("Sending Bytes");
                try {
                    sendBytes(false);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {

            }

            @Override
            public void mouseExited(MouseEvent e) {

            }
        });

        setVisible(true);

    }

    public void createAndShowGUI(){
        pixelMap = new ConcurrentHashMap<>();
        outgoingPixels = new HashMap<>();

        setLayout(new BorderLayout());

        JPanel canvasPanel = new JPanel();
        canvasPanel.setLayout(new BorderLayout());

        JPanel toolPanel = new JPanel();
        toolPanel.setLayout(new BorderLayout());

        add(canvasPanel, BorderLayout.NORTH);
        add(toolPanel, BorderLayout.SOUTH);

        colorChooser = new JColorChooser(Color.BLACK);
        SpinnerModel model = new SpinnerNumberModel(5, 1, MAX_PEN_SIZE, 1);
        penSizeBox = new JSpinner(model);

        toolPanel.add(penSizeBox, BorderLayout.WEST);
        penSizeBox.setVisible(true);

        toolPanel.add(colorChooser, BorderLayout.EAST);
        colorChooser.setVisible(true);

        canvasPanel.add(canvas, BorderLayout.CENTER);

        pack();

    }

    public void paint(Graphics g) {

        for(Point p:pixelMap.keySet()){
            g.setColor(pixelMap.get(p));
            g.fillRect(p.x, p.y, 1, 1);
        }

//        int penSize = (int) penSizeBox.getValue();
//
//        if(x < WIDTH && y < HEIGHT && x > 0 && y > 0) {
//            g.setColor(colorChooser.getColor());
//            int offsetX = x + 7;
//            int offsetY = y + 30;
//
//            int paintX = penSize;
//            int paintY = penSize;
//
//            if(x + paintX > WIDTH) paintX = WIDTH - x;
//            if(y + paintY > HEIGHT) paintY = HEIGHT - y;
//
//            g.fillRect(offsetX, offsetY, paintX, paintY);
//
//            for(int i = offsetX; i < paintX + offsetX; i++){
//                for(int j = offsetY; j < paintY + offsetY; j++){
//                    Point point = new Point(i, j);
//                    pixelMap.put(point, g.getColor());
//                    outgoingPixels.put(point, g.getColor());
//
//                }
//            }
//        }
    }

    private void sendBytes(boolean sendAll) throws InterruptedException {

        //if a new client joins they need all the pixels already present
        if(sendAll) outgoingPixels = new HashMap<>(pixelMap);

        while(!outgoingPixels.isEmpty()) {
            int payloadSize = Math.min(outgoingPixels.size() * 12, Packet.MAX_CONTENT);
            ByteBuffer payload = ByteBuffer.allocate(payloadSize);
            ArrayList<Point> sentPixels = new ArrayList<>();

            for (Point p : outgoingPixels.keySet()) {
                payload.putInt(p.x);
                payload.putInt(p.y);
                payload.putInt(outgoingPixels.get(p).getRGB());
                sentPixels.add(p);
                if (payload.position() == payload.capacity()) break;
            }

            for(Point p: sentPixels){
                outgoingPixels.remove(p);
            }

            payload.flip();
            painterHelper.sendBytes(payload.array());
            Thread.sleep(10);
            //clear the contents since they've all been sent
        }
        outgoingPixels = new HashMap<>();
    }

    public void receiveBytes(){
        byte[] bytes;
        if((bytes = painterHelper.receiveBytes()) != null) {

            ByteBuffer buff = ByteBuffer.wrap(bytes);
            //Color lastColor = colorChooser.getColor();
            //int lastPenSize = (int) penSizeBox.getValue();
            while (buff.hasRemaining()) {
                x = buff.getInt();
                y = buff.getInt();
                Point point = new Point(x, y);
                Color color = new Color(buff.getInt());
                //colorChooser.setColor(color);
                pixelMap.put(point, color);
                //repaint();
            }
            //colorChooser.setColor(lastColor);
            //penSizeBox.setValue(lastPenSize);
        }
    }

}
