import com.battleship.ui.MainWindow;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;

public final class GuiCapture {
    static MainWindow window;
    static JButton find(Container root,String prefix){
        for(Component c:root.getComponents()){
            if(c instanceof JButton b && b.getText()!=null&&b.getText().startsWith(prefix))return b;
            if(c instanceof Container child){JButton found=find(child,prefix);if(found!=null)return found;}
        }return null;
    }
    static void click(String prefix)throws Exception{SwingUtilities.invokeAndWait(()->{
        JButton b=find(window.getContentPane(),prefix);
        if(b==null)throw new IllegalStateException("Button not found: "+prefix);
        if(!b.isEnabled())throw new IllegalStateException("Button disabled: "+prefix);
        b.doClick();
    });Thread.sleep(250);}
    static void screenshot(String name)throws Exception{
        Thread.sleep(450);BufferedImage img=new Robot().createScreenCapture(new Rectangle(0,0,1280,850));ImageIO.write(img,"png",new File(name));
    }
    public static void main(String[] args)throws Exception{
        SwingUtilities.invokeAndWait(()->{window=new MainWindow();window.setVisible(true);});
        click("NEW CAMPAIGN");screenshot("/mnt/data/BattleShip-Reborn/setup-preview.png");
        click("DEPLOY YOUR FLEET");screenshot("/mnt/data/BattleShip-Reborn/placement-preview.png");
        click("AUTO");click("CONFIRM DEPLOYMENT");
        Thread.sleep(900);screenshot("/mnt/data/BattleShip-Reborn/battle-preview.png");
        SwingUtilities.invokeAndWait(window::dispose);
        System.exit(0);
    }
}
