package de.rsh.rycst.game;

import java.util.Map;
import java.util.function.Function;
import java.util.Optional;

import de.rsh.utils.Pair;

public class GameState {
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

    public GameState(double posX, double posY, int[][] map) {
        this.posX = posX;
        this.posY = posY;
        this.map = map;
    }

    public Optional<Double> getFPS() {
        if(dt==0) return Optional.empty();
        return Optional.of(1/nanoToSecond(dt));
    }

    public double posX() {
        return posX;
    }

    public double posY() {
        return posY;
    }

    public double dirX() {
        return dirX;
    }

    public double dirY() {
        return dirY;
    }

    public double ncpX() {
        return ncpX;
    }

    public double ncpY() {
        return ncpY;
    }
    public int map(int x, int y) {
        return map[y][x];
    }
    public int rowWidth(int y) {
        int res = 0;
        if(0 <= y && y < mapHeight()) {
            res = map[y].length;
        }
        return res;
    }
    public int mapHeight() {
        return map.length;
    }
    public int mapWidth() {
        int res = 0;
        if(map.length > 0) {
            res = map[0].length;
        }
        return res;
    }
    public boolean isSpace(int x, int y) {
        return (map(x,y) != WorldMap.SPACE);
    }


    public GameState map(Function<GameState,GameState> fn) {
        return fn.apply(this);
    }
    public GameState update(long t, long dt) {
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
        return (double)ns/1000_000_000D; //(10^-9)
    }
    private State transitionOnEvent(Event e) {
        var nextState = transition.get(new Pair<State,Event>(state, e));
        //System.out.printf("(%s,%s)=>%s\n", state, e, nextState);
        return nextState != null ? nextState : state;
    }
    private GameState move(long t, long dt) {
        var dts = nanoToSecond(dt);
        v += a*dts;
        v = Math.clamp(v, 0, V_MAX);
        //if(map[(int)(posX + dirX * d * v)][(int)posY] == WorldMap.SPACE) posX += dirX * d * v; else stop();
        //if(map[(int)posX][(int)(posY + dirY  * d * v)] == WorldMap.SPACE) posY += dirY * d * v; else stop();
        if(map[(int)posY][(int)(posX + dirX * d * v)] == WorldMap.SPACE) posX += dirX * d * v; else stop();
        if(map[(int)(posY + dirY  * d * v)][(int)posX] == WorldMap.SPACE) posY += dirY * d * v; else stop();
        return this;
    }
    private GameState turn(long t, long dt) {
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
    private GameState retard(long t, long dt) {
        move(t,dt);
        if(v == 0.0) {
            stop();
        }
        return this;
    }
    public GameState goAhead() {
        a = ACC_MAX; 
        d = AHEAD;
        state = transitionOnEvent(Event.MOVE);
        return this;
    }
    public GameState reverse() {
        a = ACC_MAX; 
        d = REVERSE;
        state = transitionOnEvent(Event.MOVE);
        return this;
    }
    public GameState retard() {
        a = -RETARD_FACTOR;
        state = transitionOnEvent(Event.RETARD);
        return this;
    }
    public GameState turnLeft() {
        alpha = -TURN_RADINAS;
        state = transitionOnEvent(Event.TURN);
        return this;
    }
    public GameState turnRight() {
        alpha = TURN_RADINAS;
        state = transitionOnEvent(Event.TURN);
        return this;
    }
    public GameState stopTurning() {
        alpha = 0;
        state = transitionOnEvent(Event.STOP_TURN);
        return this;
    }

    /**
     * @category internal state manipulation function
     * @return
     */
    GameState stop() {
        a = 0;
        v = 0;
        d = 0;
        state = transitionOnEvent(Event.STOP_MOVE);
        return this;
    }
}