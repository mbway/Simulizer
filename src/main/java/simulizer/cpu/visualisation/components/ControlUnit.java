package simulizer.cpu.visualisation.components;

import javafx.scene.shape.Rectangle;

import java.util.Observable;
import java.util.Observer;

public class ControlUnit extends ComponentStackPane implements Observer {

    public ControlUnit(int x, int y, int width, int height, String label){
        super(x, y, width, height, label);
        this.shape = new Rectangle(x, y, width, height);
        setAttributes();
    }

    public void update(Observable obs, Object obj){

    }

}