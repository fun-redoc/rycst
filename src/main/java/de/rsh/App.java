package de.rsh;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.function.Function;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import de.rsh.rycst.game.WorldMap;
import de.rsh.rycst.utils.Pair;
import de.rsh.rycst.utils.RayCaster;
import de.rsh.rycst.utils.Vec2d;
import de.rsh.rycst.utils.RayCaster.Side;

class Loop {
    // do i still have to wrap funtions in classes?
    public static  Function<App, Thread> loop(long frameDurationNanos) {   return (app) -> {
            return Thread.ofVirtual().unstarted(() -> {
                    boolean running = true;
                    try {
                        var last = System.nanoTime();
                        Thread.sleep(frameDurationNanos/1000);
                        while(running) {
                            var dt = System.nanoTime()-last;
                            var t = System.nanoTime();       
                            last = t;
                            app.update(t, dt);
                            app.render();
                            var duration = System.nanoTime() - t;
                            if(duration < frameDurationNanos) {
                                Thread.sleep((frameDurationNanos - duration)/1000); // sleep thre remainig milli secotds
                            }
                        }
                    } catch(InterruptedException e) {
                        running = false;
                        System.out.println("game loop terminated.");
                    }
                }
            );
        };
    }
}

enum GameEventTag { MOVE_AHEAD, MOVE_BACK, TURN_RIGHT, TURN_LEFT, MOUSE_MOVE, RETARD, STOP_TURNING };
final class GameEvent {
        private GameEventTag evt;
        private Optional<Vec2d> p;
        private long timeStamp;
        public GameEvent(GameEventTag evt, Optional<Vec2d> p) {
            this.timeStamp = System.nanoTime();
            this.p = p;
            this.evt = evt;
        }
        public GameEvent(GameEventTag evt) {
            this(evt, Optional.empty());
        }
        public long getTimestamp() {
            return this.timeStamp;
        }
        public GameEventTag getEvent() {
            return this.evt;
        }
        public Optional<Vec2d> getPoint() {
            return p;
        }
    }

class RayCastState {
    public static enum State { RESTING,  MOVING, MOVING_AND_TURNING, TURNING, RETARDING, RETARDING_AND_TURNING  };
    public static enum Event { MOVE, RETARD, STOP_MOVE, TURN, STOP_TURN  };
    public static Map<Pair<State, Event>, State> transition = Map.ofEntries(
        Map.entry(new Pair<State,Event>(State.RESTING, Event.TURN), State.TURNING),
        Map.entry(new Pair<State,Event>(State.RESTING, Event.MOVE), State.MOVING),
        Map.entry(new Pair<State,Event>(State.MOVING, Event.RETARD), State.RETARDING),
        Map.entry(new Pair<State,Event>(State.MOVING, Event.TURN), State.MOVING_AND_TURNING),
        Map.entry(new Pair<State,Event>(State.MOVING, Event.STOP_MOVE), State.RESTING),
        Map.entry(new Pair<State,Event>(State.TURNING, Event.STOP_TURN), State.RESTING),
        Map.entry(new Pair<State,Event>(State.TURNING, Event.MOVE), State.MOVING_AND_TURNING),
        Map.entry(new Pair<State,Event>(State.MOVING_AND_TURNING, Event.RETARD), State.RETARDING_AND_TURNING),
        Map.entry(new Pair<State,Event>(State.MOVING_AND_TURNING, Event.STOP_TURN), State.MOVING),
        Map.entry(new Pair<State,Event>(State.MOVING_AND_TURNING, Event.STOP_MOVE), State.TURNING),
        Map.entry(new Pair<State,Event>(State.RETARDING_AND_TURNING, Event.STOP_TURN), State.RETARDING),
        Map.entry(new Pair<State,Event>(State.RETARDING_AND_TURNING, Event.STOP_MOVE), State.TURNING),
        Map.entry(new Pair<State,Event>(State.RETARDING_AND_TURNING, Event.MOVE), State.MOVING_AND_TURNING),
        Map.entry(new Pair<State,Event>(State.RETARDING, Event.STOP_MOVE), State.RESTING),
        Map.entry(new Pair<State,Event>(State.RETARDING, Event.MOVE), State.MOVING),
        Map.entry(new Pair<State,Event>(State.RETARDING, Event.TURN), State.RETARDING_AND_TURNING)
    );
    State state = State.RESTING;
    final static double ACC_MAX = 0.05;
    final static double V_MAX = 1; // squares/second
    final static double RETARD_FACTOR = 0.05; // squares/second
    final static double AHEAD = 1.0;
    final static double REVERSE = -1.0;
    final static double STOPPED = 0.0;
    final static double TURN_RADINAS = Math.PI/2/1000000000D; // turn per nano second

    double posX, posY;  //x and y  position in grid
    double dirX = 0.0, dirY = -1.0; //initially look upward (one grid)
    double ncpX = 0.66, ncpY = 0.0; // direction vecor of the near clipping plane, corresponds to rougly FOV of 66° (Formula d*tan(alpha/2) where d is the distance to the player/camera ici 1 grid)

    double a = 0.0; // acceleration
    double v = 0.0; // velocity - moving speed of the player/camera in grids per second
    double d = 1.0; // direction 1:ahead, -1:reverse    
    double alpha =  0.0; // rotation - rotation speed in rad per nano-second

    long curTime = 0; //time of current frame
    long dt = 0; //time passed till previous frame

    int[][] map;

    public RayCastState(double posX, double posY, int[][] map) {
        this.posX = posX;
        this.posY = posY;
        this.map = map;
    }

    public RayCastState map(Function<RayCastState,RayCastState> fn) {
        return fn.apply(this);
    }
    public RayCastState update(long t, long dt) {
        this.curTime = t;
        this.dt = dt;
        switch (state) {
            case MOVING:
                move(t,dt);
                break;
            case TURNING:
                turn(t,dt);
                break;
            case MOVING_AND_TURNING:
                move(t,dt);
                turn(t,dt);
                break;
            case RETARDING_AND_TURNING:
                retard(t, dt);
                turn(t,dt);
                break;
            case RETARDING:
                retard(t,dt);
            break;
            case RESTING:
                break;
        }

        return this;
    }
    private double nanoToSecond(long ns) {
        return (double)ns/1000000000D; //(10^-9)
    }
    private State transitionOnEvent(Event e) {
        var nextState = transition.get(new Pair<State,Event>(state, e));
        //System.out.printf("(%s,%s)=>%s\n", state, e, nextState);
        return nextState != null ? nextState : state;
    }
    private RayCastState move(long t, long dt) {
        var dts = nanoToSecond(dt);
        v += a*dts;
        v = Math.clamp(v, 0, V_MAX);
        //if(map[(int)(posX + dirX * d * v)][(int)posY] == WorldMap.SPACE) posX += dirX * d * v; else stop();
        //if(map[(int)posX][(int)(posY + dirY  * d * v)] == WorldMap.SPACE) posY += dirY * d * v; else stop();
        if(map[(int)posY][(int)(posX + dirX * d * v)] == WorldMap.SPACE) posX += dirX * d * v; else stop();
        if(map[(int)(posY + dirY  * d * v)][(int)posX] == WorldMap.SPACE) posY += dirY * d * v; else stop();
        return this;
    }
    private RayCastState turn(long t, long dt) {
        //both camera direction and camera plane must be rotated
        var r = alpha*dt;
        double oldDirX = dirX;
        dirX = dirX * Math.cos(r) - dirY * Math.sin(r);
        dirY = oldDirX * Math.sin(r) + dirY * Math.cos(r);
        double oldncpX = ncpX;
        ncpX = ncpX * Math.cos(r) - ncpY * Math.sin(r);
        ncpY = oldncpX * Math.sin(r) + ncpY * Math.cos(r);
        return this;
    }
    private RayCastState retard(long t, long dt) {
        move(t,dt);
        if(v == 0.0) {
            stop();
        }
        return this;
    }
    public RayCastState goAhead() {
        a = ACC_MAX; 
        d = AHEAD;
        state = transitionOnEvent(Event.MOVE);
        return this;
    }
    public RayCastState reverse() {
        a = ACC_MAX; 
        d = REVERSE;
        state = transitionOnEvent(Event.MOVE);
        return this;
    }
    public RayCastState retard() {
        a = -RETARD_FACTOR;
        state = transitionOnEvent(Event.RETARD);
        return this;
    }
    public RayCastState turnLeft() {
        alpha = -TURN_RADINAS;
        state = transitionOnEvent(Event.TURN);
        return this;
    }
    public RayCastState turnRight() {
        alpha = TURN_RADINAS;
        state = transitionOnEvent(Event.TURN);
        return this;
    }
    public RayCastState stopTurning() {
        alpha = 0;
        state = transitionOnEvent(Event.STOP_TURN);
        return this;
    }

    /**
     * @category internal state manipulation function
     * @return
     */
    RayCastState stop() {
        a = 0;
        v = 0;
        d = 0;
        state = transitionOnEvent(Event.STOP_MOVE);
        return this;
    }
}

public class App extends JFrame {
    public final long FPS=60; // frame per second
    public final long FRAME_DURATION_NANOS=1000*1000/FPS; // nano secds
    private Thread loop;
    private RayCastState rayCastState;

    private static final int INITIAL_QUEUE_CAPACITY = 11;
    public PriorityBlockingQueue<GameEvent> eventQueue =
        new PriorityBlockingQueue<GameEvent>(App.INITIAL_QUEUE_CAPACITY,
                                             new Comparator<GameEvent>() {
                                                @Override public int compare(GameEvent a, GameEvent b) {
                                                    return  Long.compare(a.getTimestamp(),b.getTimestamp());
                                                } 
                                             });
    private JComponent canvas;

    private App() {
        initSwing();
        rayCastState = new RayCastState(WorldMap.mapWidth/2.0, WorldMap.mapHeight/2.0, WorldMap.map.clone());
        this.loop = Loop.loop(FRAME_DURATION_NANOS).apply(this);
    }
    private void initSwing() {
        // Menu
        JMenuBar menuBar;
        JMenu menu ;
        JMenuItem menuItem;

        //Create the menu bar.
        menuBar = new JMenuBar();

        //Build the first menu.
        menu = new JMenu("File");
        menu.setMnemonic(KeyEvent.VK_F);
        menu.getAccessibleContext().setAccessibleDescription("File");
        menuBar.add(menu);
        //a group of JMenuItems
        menuItem = new JMenuItem("Exit", KeyEvent.VK_Q);
        menuItem.setAccelerator(KeyStroke.getKeyStroke(
                KeyEvent.VK_Q, ActionEvent.ALT_MASK));
        menuItem.getAccessibleContext().setAccessibleDescription(
                "Exits the program.");
                menuItem.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        // TODO ask if unsaved changes
                        // TODO make a better (not so brute) way to quit look: https://github.com/tips4java/tips4java/blob/main/source/ExitAction.java
                        System.exit(0);
                    }
                    
                });
        menu.add(menuItem);

        //a group of radio button menu items
        menu.addSeparator();

        menuItem = new JMenuItem("Save",
                                new ImageIcon("images/save.gif"));
        menuItem.setMnemonic(KeyEvent.VK_S);
        menuItem.setAccelerator(KeyStroke.getKeyStroke(
                KeyEvent.VK_S, ActionEvent.ALT_MASK));
        menuItem.getAccessibleContext().setAccessibleDescription(
                "Save Graph to File.");
        menu.add(menuItem);

        menuItem = new JMenuItem("Open", new ImageIcon("images/load.gif"));
        menuItem.setMnemonic(KeyEvent.VK_O);
        menuItem.setAccelerator(KeyStroke.getKeyStroke(
                KeyEvent.VK_O, ActionEvent.ALT_MASK));
        menuItem.getAccessibleContext().setAccessibleDescription(
                "Load Graph from File.");
        menu.add(menuItem);

        this.setJMenuBar(menuBar);

        // GAme Panel
        setTitle("SDCS");
        setSize(1024, 800);
        setLocationRelativeTo(null);
        setResizable(true);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        canvas = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                draw((Graphics2D)g);
            }
        };
        add(canvas);
        var that = this;
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent evt) {
                that.start();
            }
            @Override
            public void windowClosing(WindowEvent evt) {
                that.terminate();
            }
        });
        
        // TODO why does swing igmnore the listener of the game panel, and I have to put it on the level of App.java (JFrame())
        addKeyListener(new KeyListener() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_J:
                        eventQueue.add(new GameEvent(GameEventTag.MOVE_AHEAD));
                        break;
                    case KeyEvent.VK_K:
                        eventQueue.add(new GameEvent(GameEventTag.MOVE_BACK));
                        break;
                
                    case KeyEvent.VK_H:
                        eventQueue.add(new GameEvent(GameEventTag.TURN_LEFT));
                        break;
                    case KeyEvent.VK_L:
                        eventQueue.add(new GameEvent(GameEventTag.TURN_RIGHT));
                        break;

                    default:
                        break;
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_J:
                        eventQueue.add(new GameEvent(GameEventTag.RETARD));
                        break;
                    case KeyEvent.VK_K:
                        eventQueue.add(new GameEvent(GameEventTag.RETARD));
                        break;

                    case KeyEvent.VK_H:
                        eventQueue.add(new GameEvent(GameEventTag.STOP_TURNING));
                        break;
                    case KeyEvent.VK_L:
                        eventQueue.add(new GameEvent(GameEventTag.STOP_TURNING));
                        break;
                
                    default:
                        break;
                }
            }

            @Override
            public void keyTyped(KeyEvent e) {
            }

        });
    }

    public static void main(String[] argv) {
       // create ui on ui thread
       SwingUtilities.invokeLater(new Runnable() {
			public void run() {
                new App().setVisible(true); 
			}
		});
    }
     
    public void start() {
        // start loop Thread
        loop.start();
    }

    public void terminate() {
        finish();
    }

    public void finish() {
        loop.interrupt();
        // TODO remove listeners
    }

    @Override
    public Dimension getPreferredSize()
    {
        return isPreferredSizeSet() ?
            super.getPreferredSize() : new Dimension(getWidth(), getHeight());
    }

    // render to graphics system
    public  void render(){
        repaint();
    }

    // update game state
    public  void update(long t, long dt){ 
        rayCastState.update(t,dt);
        GameEvent gameEvent;
        while ((gameEvent = eventQueue.poll()) != null) {
            switch (gameEvent.getEvent()) {
                case MOVE_AHEAD:
                    rayCastState.goAhead();
                    break;
                case MOVE_BACK:
                    rayCastState.reverse();
                    break;
                case TURN_LEFT:
                    rayCastState.turnLeft(); 
                    break;
                case TURN_RIGHT:
                    rayCastState.turnRight(); 
                    break;
                case RETARD:
                    rayCastState.retard();
                    break;
                case STOP_TURNING:
                    rayCastState.stopTurning();
                    break;
                default:
                    System.err.println("unknown event " + gameEvent.getEvent());
                    break;
            }
        }
    }

    public  void draw(Graphics2D g){
//        drawBackground(g);
        drawForeground(g);
    }
    private void drawBackground(Graphics2D g) {
        g.setColor(new Color(0xbb,0xbb,0xbb,255));
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
    }
    private void drawForeground(Graphics2D g) {
        g.setColor(new Color(0xff,0xff,0xff,255));
        var trace = Optional.of((List<Pair<Double,Double>>)new ArrayList<Pair<Double,Double>>());
        //drawGameField3D(g, Optional.empty());
        drawGameField3D_rsh(g, trace);
        drawGameFieldMiniMap(g, new Color(0xbb,0xbb,0xbb,0x55), 1, 1, 0.3, trace);
    }

    private Color worldFieldToColor(int field) {

            switch(field)
            {
                case 1:  return  Color.RED;    
                case 2:  return  Color.GREEN;  
                case 3:  return  Color.BLUE;   
                case 4:  return  Color.WHITE;  
                default: return  Color.YELLOW; 
            }
    }

    /**
     * drawing raycasting according to https://lodev.org/cgtutor/raycasting.html
     * Copyright (c) 2004-2021, Lode Vandevenne
     * @param g
     * @param trace hit coordinates are returned
     */
    private void drawGameField3D(Graphics2D g, Optional<List<Pair<Double,Double>>> trace) {
        /*
        Copyright (c) 2004-2021, Lode Vandevenne

        All rights reserved.

        Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

            * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
            * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.

        THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
        "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
        LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
        A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
        CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
        EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
        PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
        PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
        LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
        NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
        SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
        */
        var map = rayCastState.map;

        double posX = rayCastState.posX, posY = rayCastState.posY;  //x and y start position
        double dirX = rayCastState.dirX, dirY = rayCastState.dirY; //idirection vector
        double planeX = rayCastState.ncpX, planeY = rayCastState.ncpY; //the 2d raycaster version of camera plane


        //double time = 0; //time of current frame
        //double oldTime = 0; //time of previous frame
        var w = getWidth();
        var h = getHeight();

        for(int x = 0; x < w; x++) {

            //calculate ray position and direction
            var cameraX = (2.0 * (double)x / (double)w) - 1.0; //x-coordinate in camera space

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
            var deltaDistX = (rayDirX == 0) ? 1e30 : Math.abs(1.0 / rayDirX);
            var deltaDistY = (rayDirY == 0) ? 1e30 : Math.abs(1.0 / rayDirY);

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
                //hit = (map[mapX][mapY] != WorldMap.SPACE);
                hit = (map[mapY][mapX] != WorldMap.SPACE);
            }

            //Calculate distance projected on camera direction. This is the shortest distance from the point where the wall is
            //hit to the camera plane. Euclidean to center camera point would give fisheye effect!
            //This can be computed as (mapX - posX + (1 - stepX) / 2) / rayDirX for side == 0, or same formula with Y
            //for size == 1, but can be simplified to the code below thanks to how sideDist and deltaDist are computed:
            //because they were left scaled to |rayDir|. sideDist is the entire length of the ray above after the multiple
            //steps, but we subtract deltaDist once because one step more into the wall was taken above.
        if(side == 0) perpWallDist = (sideDistX - deltaDistX);
        else          perpWallDist = (sideDistY - deltaDistY);
            if(trace.isPresent()) {
                if(side == 0)
                    trace.get().add(new Pair<Double,Double>((double)(mapX), (double)(mapY)));
                else
                    trace.get().add(new Pair<Double,Double>((double)(mapX), (double)(mapY)));
            }

            //Calculate height of line to draw on screen
            int lineHeight = (int)(h / perpWallDist);

            //calculate lowest and highest pixel to fill in current stripe
            int drawStart = -lineHeight / 2 + h / 2;
            if(drawStart < 0) drawStart = 0;
            int drawEnd = lineHeight / 2 + h / 2;
            if(drawEnd >= h) drawEnd = h - 1;

            //choose wall color
            //Color color = worldFieldToColor(map[mapX][mapY]);
            Color color = worldFieldToColor(map[mapY][mapX]);

            //give x and y sides different brightness
            if(side == 1) {color = color.darker();}

            //draw the pixels of the stripe as a vertical line
            g.setColor(color);
            g.drawLine(x, drawStart, x, drawEnd);
        }
    }
    private void drawGameFieldMiniMap(Graphics2D g, final Color backColor, final int posX, final int posY, final double scale, Optional<List<Pair<Double,Double>>> trace) {
        final var saveColor = g.getColor();
        final var map = rayCastState.map;
        final var w = scale*getWidth();
        final var h = scale*getHeight();
        final var fieldHeight = h/map.length;
        final var fieldWidth = w/map[0].length;
        g.setColor(backColor);

        // Background
        g.fillRect(posX, posY, (int)(posX+w), (int)(posY + h));

        // world
        for(int y=0; y<map.length; y++) {
            for(int x= 0; x<map[y].length; x++) {
                final var field = map[y][x];
                if(field != WorldMap.SPACE) {
                    final Color color = worldFieldToColor(field);
                    g.setColor(color);
                    final var fieldUpperX = (int)(posX + x*fieldWidth);
                    final var fieldUpperY = (int)(posY + y*fieldHeight);
                    g.fillRect(fieldUpperX, fieldUpperY, (int)fieldWidth, (int)fieldHeight);
                }
            }
        }

        // player / camera
        g.setColor(Color.BLACK);
        var cameraR = (Math.min(fieldHeight, fieldWidth)*0.7);
        var cameraX = (posX + fieldWidth*rayCastState.posX - cameraR/2.0);
        var cameraY = (posY + fieldHeight*rayCastState.posY - cameraR/2.0);
        g.fillOval((int)cameraX, (int)cameraY, (int)cameraR, (int)cameraR);

        // ray
        var rayFromX = (posX + fieldWidth*rayCastState.posX );
        var rayFromY = (posY + fieldHeight*rayCastState.posY);
        var rayToX = (rayFromX + fieldWidth*rayCastState.dirX);
        var rayToY = (rayFromY + fieldWidth*rayCastState.dirY);
        g.drawLine((int)rayFromX, (int)rayFromY, (int)rayToX, (int)rayToY);

        // frustrum
        var frustrumFromX = rayToX - fieldWidth*rayCastState.ncpX;
        var frustrumFromY = rayToY - fieldWidth*rayCastState.ncpY;
        var frustrumToX = rayToX + fieldWidth*rayCastState.ncpX;
        var frustrumToY = rayToY + fieldWidth*rayCastState.ncpY;
        g.drawLine((int)frustrumFromX, (int)frustrumFromY, (int)frustrumToX, (int)frustrumToY);
        g.drawLine((int)rayFromX, (int)rayFromY, (int)frustrumFromX, (int)frustrumFromY);
        g.drawLine((int)rayFromX, (int)rayFromY, (int)frustrumToX, (int)frustrumToY);

        // trace
        g.setColor(Color.LIGHT_GRAY);
        trace.ifPresent(tr -> tr.stream().forEach(xy -> {
            var toX = fieldWidth*xy.fst().doubleValue();
            var toY = fieldHeight*xy.snd().doubleValue();
            g.drawLine((int)rayFromX, (int)rayFromY,(int)toX, (int)toY);
        }));

        // reset color
        g.setColor(saveColor);
    }
    private void drawGameField3D_rsh(Graphics2D g, Optional<List<Pair<Double,Double>>> trace) {
        var map = rayCastState.map;

        double posX   = rayCastState.posX, posY   = rayCastState.posY;  //x and y start position
        double dirX   = rayCastState.dirX, dirY   = rayCastState.dirY; //idirection vector
        double planeX = rayCastState.ncpX, planeY = rayCastState.ncpY; //the 2d raycaster version of camera plane

        //double time = 0; //time of current frame
        //double oldTime = 0; //time of previous frame
        var w = getWidth();
        var h = getHeight();

        var pos = new Vec2d(posX, posY);
        var dir = new Vec2d(dirX, dirY);
        var ncp = new Vec2d(planeX, planeY);
        var ncpFrom = pos.add(dir).sub(ncp);
        var ncpTo = pos.add(dir).add(ncp);
        var ncpDir = ncpTo.sub(ncpFrom);
        var ncpLen = ncpDir.len();
        var ncpStrideDist = ncpLen / (double)w;
        var ncpStride = ncpDir.scaled(ncpStrideDist);


        var rc = new RayCaster(WorldMap.mapWidth, WorldMap.mapHeight);
        
        var ncpTraverser = ncpFrom;
        for(int x=0; x<w; x++) {
            var rayDir = ncpTraverser.sub(pos).normalized();
            var maybeCastRes = rc.rayCastToGrid(pos, rayDir, (c) -> (map[c.snd()][c.fst()] != WorldMap.SPACE));
            if(maybeCastRes.isPresent()) {
                var castRes  = maybeCastRes.get();
                var castPos  = castRes.fst();
                var castSide = castRes.snd();
                var castCell = castRes.thr();

                if(trace.isPresent()) {
                    trace.get().add(new Pair<Double, Double>(castPos.x(), castPos.y()));
                }

                // now I need the perpedicular distance between castPos and the camera plane 
                var perpDist = castPos.perpDistToLine(pos, ncp);
                //var perpDist = castPos.sub(pos).len(); <- gives fisheye effect

                //Calculate height of line to draw on screen
                int lineHeight = (int)(h / perpDist);

                //calculate lowest and highest pixel to fill in current stripe
                int drawStart = -lineHeight / 2 + h / 2;
                if(drawStart < 0) drawStart = 0;
                int drawEnd = lineHeight / 2 + h / 2;
                if(drawEnd >= h) drawEnd = h - 1;

                //choose wall color
                //Color color = worldFieldToColor(map[mapX][mapY]);
                Color color = worldFieldToColor(map[castCell.snd()][castCell.fst()]);

                //give x and y sides different brightness
                if(castSide == Side.Ver) {color = color.darker();}

                //draw the pixels of the stripe as a vertical line
                g.setColor(color);
                g.drawLine(x, drawStart, x, drawEnd);
            };

            ncpTraverser = ncpTraverser.add(ncpStride);
        }

    }
}