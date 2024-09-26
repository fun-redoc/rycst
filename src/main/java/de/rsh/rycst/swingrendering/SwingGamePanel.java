package de.rsh.rycst.swingrendering;
import java.awt.Point;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import de.rsh.rycst.game.*;
import de.rsh.rycst.swingrendering.graphics.CoordintateTransformer;
import de.rsh.rycst.swingrendering.graphics.Drawing2D;
import de.rsh.rycst.utils.RayCaster;
import de.rsh.rycst.utils.Tuple;
import de.rsh.rycst.utils.Vec2d;

import java.util.function.*;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.*;
import java.lang.Math;

enum States { MOVE_AHEAD, MOVE_BACK, TURN_RIGHT, TURN_LEFT, MOUSE_MOVE, RETARD, STOP_TURNING };
final class GameEvent {
        private States evt;
        private Optional<Vec2d> p;
        private long timeStamp;
        public GameEvent(States evt, Optional<Vec2d> p) {
            this.timeStamp = System.nanoTime();
            this.p = p;
            this.evt = evt;
        }
        public GameEvent(States evt) {
            this(evt, Optional.empty());
        }
        public long getTimestamp() {
            return this.timeStamp;
        }
        public States getEvent() {
            return this.evt;
        }
        public Optional<Vec2d> getPoint() {
            return p;
        }
    }

class RayCastState {
  double posX, posY;  //x and y  position in grid
  double dirX = 0, dirY = -1; //initially look upward (one grid)
  double ncpX = 0, ncpY = 0.66; // direction vecor of the near clipping plane, corresponds to rougly FOV of 66° (Formula d*tan(alpha/2) where d is the distance to the player/camera ici 1 grid)

  double v = 0.0; // velocity - moving speed of the player/camera in grids per second
  double r =  0.0; // rotation - rotation speed in rad per second

  long curTime = 0; //time of current frame
  long dt = 0; //time passed till previous frame

  int[][] map;

    public RayCastState(double posX, double posY, double dirX, double dirY, int[][] map) {
        this.posX = posX;
        this.posY = posY;
        this.dirX = dirX;
        this.dirY = dirY;
        this.map = map;
    }

    public RayCastState map(Function<RayCastState,RayCastState> fn) {
        return fn.apply(this);
    }
}

public class SwingGamePanel extends JComponent implements GamePanel {
    private final boolean RENDER_VIA_BUFFER = false; // trial, what is better, passive oder active rendering....?j
    private Graphics2D graphics;
    private Thread loop;
    private RayCastState rayCastState;

    private static final int INITIAL_QUEUE_CAPACITY = 11;
    public PriorityBlockingQueue<GameState.GameEvent> eventQueue =
        new PriorityBlockingQueue<GameState.GameEvent>(SwingGamePanel.INITIAL_QUEUE_CAPACITY,
                                             new Comparator<GameState.GameEvent>() {
                                                @Override public int compare(GameEvent a, GameEvent b) {
                                                    return  Long.compare(a.getTimestamp(),b.getTimestamp());
                                                } 
                                             });

    public SwingGamePanel() {
        rayCastState = new RayCastState(WorldMap.mapWidth/2, WorldMap.mapHeight/2, 0, -1, WorldMap.map.clone());
        this.loop = Loop.loop.apply(this);
    }

    public SwingGamePanel(Function<GamePanel, Thread> loop) {
        // maybe a different loop implementation, e.g without fixed FPS
        this.loop = loop.apply(this);
    }

    
    @Override
    public void start() {
//        System.out.printf("Dims: %d,%d\n", getWidth(), getHeight());

        // initialize privates
        //panelMidPoint = Optional.of(new Vec2d(getWidth()/2.0, getHeight()/2.0)); <- in this place sometimes not correct values for getW.. and getH..

        // initialize listeners
        addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent arg0) { /* ignore */ }

            @Override
            public void mouseEntered(MouseEvent e) { /* ignore */ }

            @Override
            public void mouseExited(MouseEvent e) { /* ignore */}

            @Override
            public void mousePressed(MouseEvent e) {
//                GameState.States evt = MouseEvent.BUTTON1 == e.getButton() ?  GameState.States.MOUSE_LEFT_DOWN : GameState.States.MOUSE_RIGHT_DOWN;
//                eventQueue.add(new GameState.GameEvent(evt, Optional.of(eventWorldCoords(e))));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
//                GameState.States evt = MouseEvent.BUTTON1 == e.getButton() ?  GameState.States.MOUSE_RIGHT_UP : GameState.States.MOUSE_RIGHT_UP;
//                eventQueue.add(new GameState.GameEvent(evt, Optional.of(eventWorldCoords(e))));
            }
        });
        addMouseMotionListener(new MouseMotionListener() {

            @Override
            public void mouseDragged(MouseEvent e) {
//                eventQueue.add(new GameState.GameEvent(GameState.States.MOUSE_MOVE, Optional.of(eventWorldCoords(e))));
            }


            @Override
            public void mouseMoved(MouseEvent e) {
                eventQueue.add(new GameState.GameEvent(GameState.States.MOUSE_MOVE, Optional.of( new Vec2d(e.getX(), e.getY()))));
            }
        });

        // TODO unfortunatelly the key listener is ignored on this level, but why?
        //      key listener moved to App.java
        //addKeyListener(new KeyListener() {
        //    @Override
        //    public void keyPressed(KeyEvent e) {
        //        System.out.println("key pressed");
        //        switch (e.getKeyCode()) {
        //            case KeyEvent.VK_J:
        //                eventQueue.add(new GameState.GameEvent(GameState.States.MOVE_AHEAD));
        //                break;
        //            case KeyEvent.VK_K:
        //                eventQueue.add(new GameState.GameEvent(GameState.States.MOVE_BACK));
        //                break;
        //            case KeyEvent.VK_H:
        //                eventQueue.add(new GameState.GameEvent(GameState.States.TURN_LEFT));
        //                break;
        //            case KeyEvent.VK_L:
        //                eventQueue.add(new GameState.GameEvent(GameState.States.TURN_RIGHT));
        //                break;
        //        
        //            default:
        //                break;
        //        }
        //    }

        //    @Override
        //    public void keyReleased(KeyEvent e) {
        //        // TODO Auto-generated method stub
        //        System.out.println("key released");
        //    }

        //    @Override
        //    public void keyTyped(KeyEvent e) {
        //        // TODO Auto-generated method stub
        //        System.out.println("key typed");
        //    }

        //});

        // start loop Thread
        loop.start();
    }

    @Override
    public void terminate() {
        finish();
    }

    public void finish() {
        loop.interrupt();
        // TODO remove listeners
    }

    // update game state
    @Override
    public  void update(long t, long dt){ 
        rayCastState.map((s) -> {s.curTime = t; s.dt = dt;
                                 s.v = dt* 5.0; 
                                 s.r = dt * 3.0; 
                                 return s;});
        GameEvent gameEvent;
        while ((gameEvent = eventQueue.poll()) != null) {
            switch (gameEvent) {
                case :
                    
                    break;
            
                default:
                    break;
            }
    if(keyDown(SDLK_UP))
    {
      if(worldMap[int(posX + dirX * moveSpeed)][int(posY)] == false) posX += dirX * moveSpeed;
      if(worldMap[int(posX)][int(posY + dirY * moveSpeed)] == false) posY += dirY * moveSpeed;
    }
    //move backwards if no wall behind you
    if(keyDown(SDLK_DOWN))
    {
      if(worldMap[int(posX - dirX * moveSpeed)][int(posY)] == false) posX -= dirX * moveSpeed;
      if(worldMap[int(posX)][int(posY - dirY * moveSpeed)] == false) posY -= dirY * moveSpeed;
    }
    //rotate to the right
    if(keyDown(SDLK_RIGHT))
    {
      //both camera direction and camera plane must be rotated
      double oldDirX = dirX;
      dirX = dirX * cos(-rotSpeed) - dirY * sin(-rotSpeed);
      dirY = oldDirX * sin(-rotSpeed) + dirY * cos(-rotSpeed);
      double oldPlaneX = planeX;
      planeX = planeX * cos(-rotSpeed) - planeY * sin(-rotSpeed);
      planeY = oldPlaneX * sin(-rotSpeed) + planeY * cos(-rotSpeed);
    }
    //rotate to the left
    if(keyDown(SDLK_LEFT))
    {
      //both camera direction and camera plane must be rotated
      double oldDirX = dirX;
      dirX = dirX * cos(rotSpeed) - dirY * sin(rotSpeed);
      dirY = oldDirX * sin(rotSpeed) + dirY * cos(rotSpeed);
      double oldPlaneX = planeX;
      planeX = planeX * cos(rotSpeed) - planeY * sin(rotSpeed);
      planeY = oldPlaneX * sin(rotSpeed) + planeY * cos(rotSpeed);
    }
  }
        }
    }

    public double getZoom() {
       return zoom;
    }

    public Vec2d getPan() {
        return panOffset.scaled(this.getZoom());
    }



    @Override
    public Dimension getPreferredSize()
    {
        return isPreferredSizeSet() ?
            super.getPreferredSize() : new Dimension(getWidth(), getHeight());
    }

    // render to graphics system
    @Override
    public  void render(){
        if(RENDER_VIA_BUFFER) {
            // don't block this thread witch rendering
            SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        draw(graphics);
                        repaint();
                    }
                });
        } else {
            repaint();
        } 
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent(g);

        //  Custom code to support painting from the BufferedImage

        if(RENDER_VIA_BUFFER) {
            g.drawImage(image, 0, 0, null);
        } else {
            draw((Graphics2D)g);
        }

    }

    public  void draw(Graphics2D g){
        drawBackground(g, drawing2d.posTopLeftNormalized, drawing2d.posBottomRightNormalized);
        drawForeground(g);
    }

    Tuple<Vec2d,Vec2d> nearClippingPlane(Vec2d pos, Vec2d dir, double dist, double fov) {
        var dirNormalized = dir.normalized();
        var playerRay = dirNormalized.scaled(dist).movedBy(pos);
        var nearClipPaneLen = Math.tan(fov/2.0)*dist;
        var nearClipCw = dirNormalized.rotateOrthogonallyClockwize().scaled(nearClipPaneLen);
        var nearClipCCw = dirNormalized.rotateOrthogonallyCounterClockwize().scaled(nearClipPaneLen);
        return new Tuple<Vec2d,Vec2d>(playerRay.add(nearClipCw), playerRay.add(nearClipCCw));
    }
    private void drawGameField3D_X(Graphics2D g) {

        double NEAR_CLIPPING_PANE_DIST = 0.125/2*Math.sqrt(2);
        double FIELD_OF_VIEW_RAD = Math.toRadians(66);
        var player = state.getStateRecord().getPlayer();
        var world = state.getStateRecord().getWorldMap();
        var pos = player.getPos();
        var dir = player.getDir().normalized();
        var ncp = nearClippingPlane(pos, dir, NEAR_CLIPPING_PANE_DIST, FIELD_OF_VIEW_RAD);
        var ncpPos = ncp.fst();
        var ncpDir = ncp.snd().normalized();
        var ncpStep = 1.0/ncp.snd().len();
        var posOnNcp = ncpPos;

        for(int x=0; x<getWidth()-1; x++) {
            var rayDir = posOnNcp.sub(player.getPos()).normalized();
            var rayDestinataion = rayCaster.rayCastToGrid(player.getPos(), rayDir, xy -> world.map[xy.snd()][xy.fst()] != WorldMap.SPACE);

            posOnNcp = posOnNcp.add(ncpDir.scaled(ncpStep));
        }


    }

    private void drawForeground(Graphics2D g) {
        g.setColor(new Color(0xff,0xff,0xff,255));
        drawGameField3D(g);
        var minimapPos = new Vec2d(0.2,-0.2);
        double minimapSize = 0.3D;
        //var minimapPos = new Vec2d(-0.5,0.5);
        //double minimapSize = 1.0D;
        drawing2d.draw(g, minimapPos, minimapPos.movedByXY(minimapSize, -minimapSize));
    }

    private void drawBackground(Graphics2D g, Vec2d from, Vec2d to ) {
        var p1 = coordintateTransformer.normalizedToPanelCoords(from).toInt();
        var p2 = coordintateTransformer.normalizedToPanelCoords(to).toInt();
        var size_ = new Tuple<>(p2.fst()-p1.fst(), p2.snd()-p1.snd());
        g.setColor(new Color(0xbb,0xbb,0xbb,255));
        g.fillRect(p1.fst(), p1.snd(), size_.fst(), size_.snd());
    }
    
//    private static Vec2d fromPoint(Point p) {
//        return new Vec2d(p.getX(), p.getY());
//    }

    @SuppressWarnings("unused")
    private  double calcZoom() {
        return Math.pow(zoomMantisse, zoomExponent);
    }

    private void drawGameField3D(Graphics2D g) {
        final Vec2d planeBase = new Vec2d(0, 0.66);
        var world = state.getStateRecord().getWorldMap();
        var map = world.map;
        var player = state.getStateRecord().getPlayer();
        var pos =  player.getPos();
        var dir = player.getDir().normalized();
        var alpha = player.getAlphaRad();

        double posX = pos.x(), posY = pos.y();  //x and y start position
        double dirX = dir.x(), dirY = dir.y(); //initial direction vector
        var plane = planeBase.turnByAngleOf(alpha);
        double planeX = plane.x(), planeY = plane.y(); //the 2d raycaster version of camera plane


        //double time = 0; //time of current frame
        //double oldTime = 0; //time of previous frame
        var w = getWidth();
        var h = getHeight();

        for(int x = 0; x < w; x++) {

            //calculate ray position and direction
            var cameraX = 2 * x / (double)w - 1; //x-coordinate in camera space
            //var cameraX = x / (double)w - 0.5; //x-coordinate in camera space going from -0.5 to 0.5
            var rayDirX = dirX + planeX * cameraX;
            var rayDirY = dirY + planeY * cameraX;

            //which box of the map we're in
            var mapX = (int)posX;
            var mapY = (int)posY;

        //length of ray from current position to next x or y-side
        double sideDistX;
        double sideDistY;

        //length of ray from one x or y-side to next x or y-side
        //these are derived as:
        //deltaDistX = sqrt(1 + (rayDirY * rayDirY) / (rayDirX * rayDirX))
        //deltaDistY = sqrt(1 + (rayDirX * rayDirX) / (rayDirY * rayDirY))
        //which can be simplified to abs(|rayDir| / rayDirX) and abs(|rayDir| / rayDirY)
        //where |rayDir| is the length of the vector (rayDirX, rayDirY). Its length,
        //unlike (dirX, dirY) is not 1, however this does not matter, only the
        //ratio between deltaDistX and deltaDistY matters, due to the way the DDA
        //stepping further below works. So the values can be computed as below.
        // Division through zero is prevented, even though technically that's not
        // needed in C++ with IEEE 754 floating point values.
        var deltaDistX = (rayDirX == 0) ? 1e30 : Math.abs(1 / rayDirX);
        var deltaDistY = (rayDirY == 0) ? 1e30 : Math.abs(1 / rayDirY);

        double perpWallDist;

        //what direction to step in x or y-direction (either +1 or -1)
        int stepX;
        int stepY;

        boolean hit = false; //was there a wall hit?
        int side = 0; //was a  0==North/South or a 1==Eeast/West wall hit?
        //calculate step and initial sideDist
        if(rayDirX < 0) {
            stepX = -1;
            sideDistX = (posX - mapX) * deltaDistX;
        } else {
            stepX = 1;
            sideDistX = (mapX + 1.0 - posX) * deltaDistX;
        }
        if(rayDirY < 0) {
            stepY = -1;
            sideDistY = (posY - mapY) * deltaDistY;
        } else {
            stepY = 1;
            sideDistY = (mapY + 1.0 - posY) * deltaDistY;
        }

        //perform DDA
        while(!hit) {
            //jump to next map square, either in x-direction, or in y-direction
            if(sideDistX < sideDistY) {
                sideDistX += deltaDistX;
                mapX += stepX;
                side = 0;
            } else {
                sideDistY += deltaDistY;
                mapY += stepY;
                side = 1;
            }
            //Check if ray has hit a wall
            hit = (map[mapX][mapY] != WorldMap.SPACE);
        }

        //Calculate distance projected on camera direction. This is the shortest distance from the point where the wall is
        //hit to the camera plane. Euclidean to center camera point would give fisheye effect!
        //This can be computed as (mapX - posX + (1 - stepX) / 2) / rayDirX for side == 0, or same formula with Y
        //for size == 1, but can be simplified to the code below thanks to how sideDist and deltaDist are computed:
        //because they were left scaled to |rayDir|. sideDist is the entire length of the ray above after the multiple
        //steps, but we subtract deltaDist once because one step more into the wall was taken above.
        if(side == 0) perpWallDist = (sideDistX - deltaDistX);
        else          perpWallDist = (sideDistY - deltaDistY);

        //Calculate height of line to draw on screen
        int lineHeight = (int)(h / perpWallDist);

        //calculate lowest and highest pixel to fill in current stripe
        int drawStart = -lineHeight / 2 + h / 2;
        if(drawStart < 0) drawStart = 0;
        int drawEnd = lineHeight / 2 + h / 2;
        if(drawEnd >= h) drawEnd = h - 1;

        //choose wall color
        Color color;
        switch(map[mapX][mapY])
        {
            case 1:  color = Color.RED;    break; //red
            case 2:  color = Color.GREEN;  break; //green
            case 3:  color = Color.BLUE;   break; //blue
            case 4:  color = Color.WHITE;  break; //white
            default: color = Color.YELLOW; break; //yellow
        }

        //give x and y sides different brightness
        if(side == 1) {color = color.darker();}

        //draw the pixels of the stripe as a vertical line
        g.setColor(color);
        g.drawLine(x, drawStart, x, drawEnd);
    }
/* TODO show FPS
    //timing for input and FPS counter
    oldTime = time;
    time = getTicks();
    double frameTime = (time - oldTime) / 1000.0; //frameTime is the time this frame has taken, in seconds
    print(1.0 / frameTime); //FPS counter
    redraw();
    cls();

*/
/* TODO handle player movement
    //speed modifiers
    double moveSpeed = frameTime * 5.0; //the constant value is in squares/second
    double rotSpeed = frameTime * 3.0; //the constant value is in radians/second
    readKeys();
    //move forward if no wall in front of you
    if(keyDown(SDLK_UP))
    {
      if(worldMap[int(posX + dirX * moveSpeed)][int(posY)] == false) posX += dirX * moveSpeed;
      if(worldMap[int(posX)][int(posY + dirY * moveSpeed)] == false) posY += dirY * moveSpeed;
    }
    //move backwards if no wall behind you
    if(keyDown(SDLK_DOWN))
    {
      if(worldMap[int(posX - dirX * moveSpeed)][int(posY)] == false) posX -= dirX * moveSpeed;
      if(worldMap[int(posX)][int(posY - dirY * moveSpeed)] == false) posY -= dirY * moveSpeed;
    }
    //rotate to the right
    if(keyDown(SDLK_RIGHT))
    {
      //both camera direction and camera plane must be rotated
      double oldDirX = dirX;
      dirX = dirX * cos(-rotSpeed) - dirY * sin(-rotSpeed);
      dirY = oldDirX * sin(-rotSpeed) + dirY * cos(-rotSpeed);
      double oldPlaneX = planeX;
      planeX = planeX * cos(-rotSpeed) - planeY * sin(-rotSpeed);
      planeY = oldPlaneX * sin(-rotSpeed) + planeY * cos(-rotSpeed);
    }
    //rotate to the left
    if(keyDown(SDLK_LEFT))
    {
      //both camera direction and camera plane must be rotated
      double oldDirX = dirX;
      dirX = dirX * cos(rotSpeed) - dirY * sin(rotSpeed);
      dirY = oldDirX * sin(rotSpeed) + dirY * cos(rotSpeed);
      double oldPlaneX = planeX;
      planeX = planeX * cos(rotSpeed) - planeY * sin(rotSpeed);
      planeY = oldPlaneX * sin(rotSpeed) + planeY * cos(rotSpeed);
    }
    */
    }
}