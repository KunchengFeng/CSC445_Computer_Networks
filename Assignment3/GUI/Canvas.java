package GUI;

import javax.swing.*;
import java.awt.*;

public class Canvas extends JPanel {

    public Canvas(int width, int height){
        Dimension dimension = new Dimension(width, height);
        setPreferredSize(dimension);
        setMaximumSize(dimension);
        setMinimumSize(dimension);
        setBorder(BorderFactory.createLineBorder(Color.black, 5));
        setOpaque(true);
        setVisible(true);
    }
}


