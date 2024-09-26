package de.rsh.rycst.game.state;
import java.lang.RuntimeException;
import java.util.*;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import de.rsh.rycst.player.Player;
import de.rsh.rycst.utils.Vec2d;
import de.rsh.rycst.game.WorldMap;


/**
 * @apiNote implements the base class for the automaton and provides the noop behaviour for not relevat events in respective states
 */
public class GameState {
    public static enum States { MOVE_AHEAD, MOVE_BACK, TURN_RIGHT, TURN_LEFT, MOUSE_MOVE, RETARD, STOP_TURNING };
    public static final class GameEvent {
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


    public static final class StateRecord {
        private WorldMap map = new WorldMap();
        private Player player = new Player(Vec2d.zero(), Vec2d.zero());
        private Optional<Vec2d> pos = Optional.empty();
        private Optional<Vec2d> dir = Optional.empty();
        
        public WorldMap getWorldMap() { return map;}
        public Player getPlayer() {return player;}
        public Optional<Vec2d> getPos() { return pos; }
        public StateRecord setPos(Vec2d pos) { this.pos = Optional.of(pos); return this; }
        public Optional<Vec2d> getDir() { return dir; }
        public StateRecord setDir(Vec2d dir) { this.dir = Optional.of(dir); return this; }

        @Override
        public String toString() {
            return String.format("pos: %s; dir: %s, player: %s", pos, dir, player);
        }

    }

    protected static StateRecord stateRecord = new StateRecord();
    //protected static StateRecord stateRecord = new StateRecord().setPos(new Vec2d(0.099, -0.031));
    //protected static StateRecord stateRecord = new StateRecord(new Vec2d(0, 0), new Vec2d(0, 0));
    //protected static StateRecord stateRecord = new StateRecord(new Vec2d(0.25, -0.031), new Vec2d(0, 0));
    //protected static StateRecord stateRecord = new StateRecord(new Vec2d(0.2556, -0.25), new Vec2d(0, 0));

    public StateRecord getStateRecord() {
        return stateRecord;
    }
    public GameState eventActionAndTransition(GameEvent evt) {
        switch(evt.getEvent()) {
        case MOVE_AHEAD:
            return moveAhead();
        case MOVE_BACK:
            return moveBack();
        case TURN_LEFT:
            return turnLeft();
        case TURN_RIGHT:
            return turnRight();
        case MOUSE_MOVE:
            return mouseMove(evt.getPoint());
        case RETARD:
            return retard();
        case STOP_TURNING:
            return stopTurning();
        default:
            throw new RuntimeException("unexpeted event.");
        }
    }

    //public GameState update(long t, long dt) { return this;}
    public GameState update(long t, long dt) { getStateRecord().getPlayer().update(t, dt); return this;}
    public GameState moveAhead(){ return this;}
    public GameState moveBack(){ return this;}
    public GameState turnLeft(){ return this;}
    public GameState turnRight(){ return this;}
    public GameState retard(){ return this;}
    public GameState stopTurning() {return this;}

    public GameState mouseMove(Optional<Vec2d> p) { return this;}
}
