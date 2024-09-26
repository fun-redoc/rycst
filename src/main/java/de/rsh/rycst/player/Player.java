package de.rsh.rycst.player;

import java.util.*;

import de.rsh.rycst.utils.Tuple;
import de.rsh.rycst.utils.Vec2d;

public class Player {
    public static enum State { RESTING,  MOVING, MOVING_AND_TURNING, TURNING, RETARDING, RETARDING_AND_TURNING  };
    public static enum Event { MOVE, RETARD, STOP_MOVE, TURN, STOP_TURN  };
    public static Map<Tuple<State, Event>, State> transition = Map.ofEntries(
        Map.entry(new Tuple<State,Event>(State.RESTING, Event.TURN), State.TURNING),
        Map.entry(new Tuple<State,Event>(State.RESTING, Event.MOVE), State.MOVING),
        Map.entry(new Tuple<State,Event>(State.MOVING, Event.RETARD), State.RETARDING),
        Map.entry(new Tuple<State,Event>(State.MOVING, Event.TURN), State.MOVING_AND_TURNING),
        Map.entry(new Tuple<State,Event>(State.TURNING, Event.STOP_TURN), State.RESTING),
        Map.entry(new Tuple<State,Event>(State.TURNING, Event.MOVE), State.MOVING_AND_TURNING),
        Map.entry(new Tuple<State,Event>(State.MOVING_AND_TURNING, Event.RETARD), State.RETARDING_AND_TURNING),
        Map.entry(new Tuple<State,Event>(State.MOVING_AND_TURNING, Event.STOP_TURN), State.MOVING),
        Map.entry(new Tuple<State,Event>(State.RETARDING_AND_TURNING, Event.STOP_TURN), State.RETARDING),
        Map.entry(new Tuple<State,Event>(State.RETARDING_AND_TURNING, Event.STOP_MOVE), State.TURNING),
        Map.entry(new Tuple<State,Event>(State.RETARDING_AND_TURNING, Event.MOVE), State.MOVING_AND_TURNING),
        Map.entry(new Tuple<State,Event>(State.RETARDING, Event.STOP_MOVE), State.RESTING),
        Map.entry(new Tuple<State,Event>(State.RETARDING, Event.MOVE), State.MOVING),
        Map.entry(new Tuple<State,Event>(State.RETARDING, Event.TURN), State.RETARDING_AND_TURNING)
    );
    State state = State.RESTING;
    final static double ACC_MAX = 3;
    final static double V_MAX = 5; // squares/second
    final static double RETARD_FACTOR = 3; // squares/second
    final static double TURN_RADINAS = Math.PI/16/1000000000D; // turn per nano second
    Vec2d pos;
    Vec2d dir;
    double v;
    double a;
    double alpha;

    public Player(Vec2d pos, Vec2d dir) {
        this.pos = pos;
        this.dir = dir;
        this.v = 0; 
        this.a = 0;
        this.alpha = 0;

    }

    /**
     * @category internal helper funtion
     */
    double nanoToSecond(long ns) {
        return (double)ns/1000000000D; //(10^-9)
    }
    /**
     * @category internal helper funtion
     */
    State transitionOnEvent(Event e) {
        var nextState = transition.get(new Tuple<State,Event>(state, e));
        //System.out.printf("(%s,%s)=>%s\n", state, e, nextState);
        return nextState != null ? nextState : state;
    }

    /**
     * @category internal helper funtion
     */
    Player turnByAngleOf(double rad) {
        this.dir = dir.turnByAngleOf(rad);
        return this;
    }
    
    /**
     * @category api function
     * @param pos
     * @return
     */
    public Player setPos(Vec2d pos) { this.pos = pos; return this; }
    public Player setDir(Vec2d dir) { this.dir = dir; return this;}

    public Vec2d getDir() { return dir; }
    public Vec2d getPos() { return pos; }

    /**
     * @category state manipulation functions
     * @return
     */
    public Player moveAhead() {
        a = ACC_MAX;
        state = transitionOnEvent(Event.MOVE);
        return this;
    }
    public Player moveBack() {
        setDir(getDir().neg());
        a = ACC_MAX;
        state = transitionOnEvent(Event.MOVE);
        return this;
    }
    public Player retard() {

        a = -RETARD_FACTOR;
        state = transitionOnEvent(Event.RETARD);
        return this;
    }
    public Player turnLeft() {
        alpha = TURN_RADINAS;
        state = transitionOnEvent(Event.TURN);
        return this;
    }
    public Player turnRight() {
        alpha = -TURN_RADINAS;
        state = transitionOnEvent(Event.TURN);
        return this;
    }
    public Player stopTurning() {
        alpha = 0;
        state = transitionOnEvent(Event.STOP_TURN);
        return this;
    }

    /**
     * @category internal state manipulation function
     * @return
     */
    Player stop() {
        state = transitionOnEvent(Event.STOP_MOVE);
        return this;
    }

    @Override
    public String toString() {
        return "Player [pos=" + pos + ", dir=" + dir + ", a=" + a + ", v= " + v + ". alpha=" + alpha + "]";
    }


    /**
     * @category state update
     * @param t
     * @param dt
     * @return
     */
    Player move(long t, long dt) {
        var dts = nanoToSecond(dt);
        v += a*dts;
        v = Math.clamp(v, 0, V_MAX);
        setPos(getPos().add(getDir().normalized().scaled(v)));
        return this;
    }
    Player turn(long t, long dt) {
        turnByAngleOf(alpha*dt);
        return this;
    }
    Player retard(long t, long dt) {
        move(t,dt);
        if(v == 0) {
            a = 0;
            stop();
        }
        return this;
    }

    // state dependen methtods
    public Player update(long t, long dt) {
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
}
