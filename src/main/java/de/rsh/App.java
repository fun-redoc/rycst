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
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.function.Function;
import java.util.function.DoubleSupplier;

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
import de.rsh.rycst.utils.MathUtils;
import de.rsh.rycst.utils.Pair;
import de.rsh.rycst.utils.RayCaster;
import de.rsh.rycst.utils.Texture;
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
    private final int  textureHeight = 64;
    private final int  textureWidth = 64;
    public final long FPS=60; // frame per second
    public final long FRAME_DURATION_NANOS=1000*1000/FPS; // nano secds
    private Thread loop;
    private RayCastState rayCastState;

    private BufferedImage texRedX = new BufferedImage(textureWidth, textureHeight, BufferedImage.TYPE_INT_RGB);
    {
        var texture = Texture.RED_WITH_BLACK_CROSS.get(textureWidth,textureHeight);
        texRedX.setRGB(0, 0, texture.width(), texture.height(), texture.arr(), 0, texture.width());
    }
    private BufferedImage texRedBrick = new BufferedImage(textureWidth, textureHeight, BufferedImage.TYPE_INT_RGB);
    {
        var texture = Texture.RED_BRICKS.get(textureWidth,textureHeight);
        texRedBrick.setRGB(0, 0, texture.width(), texture.height(), texture.arr(), 0, texture.width());
    }
    private BufferedImage texVertYello = new BufferedImage(textureWidth, textureHeight, BufferedImage.TYPE_INT_RGB);
    {
        var texture = Texture.VERT_YELLO.get(textureWidth,textureHeight);
        texVertYello.setRGB(0, 0, texture.width(), texture.height(), texture.arr(), 0, texture.width());
    }
    private BufferedImage texHrzBlu = new BufferedImage(textureWidth, textureHeight, BufferedImage.TYPE_INT_RGB);
    {
        var texture = Texture.HORIZ_BLUE.get(textureWidth,textureHeight);
        texHrzBlu.setRGB(0, 0, texture.width(), texture.height(), texture.arr(), 0, texture.width());
    }
    private BufferedImage texTest1 = new BufferedImage(textureWidth, textureHeight, BufferedImage.TYPE_INT_RGB);
    {
        var texture = Texture.TEST1.get(textureWidth,textureHeight);
        texTest1.setRGB(0, 0, texture.width(), texture.height(), texture.arr(), 0, texture.width());
    }

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
    private void drawForeground(Graphics2D g) {
        g.setColor(new Color(0xff,0xff,0xff,255));
        var trace = Optional.of((List<Pair<Double,Double>>)new ArrayList<Pair<Double,Double>>());
        //drawGameField3D(g, Optional.empty());
        drawGameField3D_rsh(g, trace);
        drawGameFieldMiniMap(g, new Color(0xbb,0xbb,0xbb,0x55), 1, 1, 0.5, trace);
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

    @SuppressWarnings("unused") // I leave the lodev implementation as a reference and best practices
    private void drawGameField3D(Graphics2D g, Optional<List<Pair<Double,Double>>> trace) {
        RayCaster
        .drawGameField3D_lodev(getWidth(), getHeight(),
                               rayCastState.posX, rayCastState.posY,
                               rayCastState.dirX, rayCastState.dirY,
                               rayCastState.ncpX, rayCastState.ncpY,
                               (mapX, mapY) -> (rayCastState.map[mapY][mapX] != WorldMap.SPACE),
                               (side,x,y1,y2,mapX, mapY) -> {
                                    //choose wall color
                                    Color color = worldFieldToColor(rayCastState.map[mapY][mapX]);

                                    //give x and y sides different brightness
                                    if(side == 1) {color = color.darker();}

                                    //draw the pixels of the stripe as a vertical line
                                    g.setColor(color);
                                    g.drawLine(x, y1, x, y2);
                               },
                               trace);
        
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
                final var fieldUpperX = (int)(posX + x*fieldWidth);
                final var fieldUpperY = (int)(posY + y*fieldHeight);
                switch (field) {
                    case WorldMap.SPACE:
                        // leave transparent
                        break;
                    case WorldMap.OUTERWALL:
                    {
                        g.drawImage(texHrzBlu, fieldUpperX,fieldUpperY, (int)fieldWidth, (int)fieldHeight, null);
                    }
                    break;
                    case WorldMap.PILAR:
                    {

                        g.drawImage(texVertYello, fieldUpperX,fieldUpperY, (int)fieldWidth, (int)fieldHeight, null);
                    }
                    break;
                    case WorldMap.HOUSEWALL:
                    {

                        g.drawImage(texRedBrick, fieldUpperX,fieldUpperY, (int)fieldWidth, (int)fieldHeight, null);
                    }
                    break;
                    case WorldMap.COTTAGEWALL:
                    {

                        g.drawImage(texRedX, fieldUpperX,fieldUpperY, (int)fieldWidth, (int)fieldHeight, null);
                    }
                    break;
                
                    default:
                        final Color color = worldFieldToColor(field);
                        g.setColor(color);
                        g.fillRect(fieldUpperX, fieldUpperY, (int)fieldWidth, (int)fieldHeight);
                        break;
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


        var ncpTraverser = ncpFrom;
        // TODO: having a texture with the width of tw only tw stripes have to be drawn
        for(int x=0; x<w; x++) {
            var rayDir = ncpTraverser.sub(pos).normalized();
            //var maybeCastRes = rc.rayCastToGrid(pos, rayDir, (c) -> (map[c.snd()][c.fst()] != WorldMap.SPACE));
            var maybeCastRes = RayCaster.rayCastUntilHit(pos, rayDir,
                                                         WorldMap.mapWidth, WorldMap.mapHeight,
                                                         (c) -> (map[c.snd()][c.fst()] != WorldMap.SPACE));
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
                int drawStartClip = 0;
                int drawEndClip = 0;
                double factorStart = 0;
                double factorEnd = 1;
                int drawStart = -lineHeight / 2 + h / 2;
                int drawEnd = lineHeight / 2 + h / 2;
                if(drawStart < 0) {
                    drawStartClip = -drawStart;
                    factorStart = drawStartClip/(double)lineHeight;
                    drawStart = 0;
                }
                if(drawEnd >= h) {
                    drawEndClip = drawEnd - (h-1);
                    factorEnd = 1.0 - drawEndClip/(double)lineHeight;
                    drawEnd = h-1;
                }

                //choose wall color
                var cell = map[castCell.snd()][castCell.fst()];

                Vec2d _castCell = Vec2d.fromPair(castCell);
                double castCellXRaw = castPos
                                                .sub(_castCell)
                                                .map(v ->  Math.abs(Side.Hor == castSide ? v.x() : v.y()));
                double castCellX = castCellXRaw * textureWidth;
                double castCellStripeWidth = 1;
                double texStart = MathUtils.lerp(0, textureHeight, factorStart);
                double texEnd = MathUtils.lerp(0, textureHeight, factorEnd);

                switch (cell) {
                    case WorldMap.SPACE:
                        //transparent
                        break;
                    case WorldMap.PILAR:
                    {
                        g.drawImage(texVertYello, x, drawStart, x+1, drawEnd,
                                    (int)castCellX, 0, (int)(castCellX + castCellStripeWidth), textureHeight,
                                    null);
                    }
                    break;
                    case WorldMap.OUTERWALL:
                    {
                        g.drawImage(texHrzBlu, x, drawStart, x+1, drawEnd,
                                    (int)castCellX, 0, (int)(castCellX + castCellStripeWidth), textureHeight,
                                    null);
                    }
                    break;
                    case WorldMap.HOUSEWALL:
                    {
                        g.drawImage(texRedBrick, x, drawStart, x+1, drawEnd,
                                    (int)castCellX, (int)(texStart), (int)(castCellX + castCellStripeWidth), (int)(texEnd),
                                    null);
                    }
                    break;
                    case WorldMap.COTTAGEWALL:
                    {
                        //g.drawImage(texRedX, x, drawStart, x+1, drawEnd,
                        g.drawImage(texTest1, x, drawStart, x+1, drawEnd,
                                    (int)castCellX, (int)(texStart), (int)(castCellX + castCellStripeWidth), (int)(texEnd),
                                    null);
                    }
                    break;
                
                    default:
                        Color color = worldFieldToColor(map[castCell.snd()][castCell.fst()]);

                        //give x and y sides different brightness
                        if(castSide == Side.Ver) {color = color.darker();}

                        //draw the pixels of the stripe as a vertical line
                        g.setColor(color);
                        g.drawLine(x, drawStart, x, drawEnd);
                        break;
                }
            };

            ncpTraverser = ncpTraverser.add(ncpStride);
        }

    }
}