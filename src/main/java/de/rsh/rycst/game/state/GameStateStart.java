package de.rsh.rycst.game.state;

import java.util.Optional;

import de.rsh.rycst.utils.Vec2d;

public class GameStateStart extends GameState{

    private static volatile GameStateStart instance = null;
    private GameStateStart() {}
    public static GameState getInstance() {
        if(instance == null) {
            synchronized(GameStateStart.class) {
                if(instance == null) {
                    instance = new GameStateStart();
                    instance.getStateRecord().setPos(Vec2d.zero()).setDir(new Vec2d(0, 1).normalized());
                    instance.getStateRecord().getPlayer()
                                                .setPos(Vec2d.zero())
                                                .setDir(Vec2d.up().normalized());
                                                //.setDir(Vec2d.up().turnByAngleOf(Math.PI/4).normalized());
                }
            }
        }
        return instance;
    }


    @Override
    public GameState mouseMove(Optional<Vec2d> p) { 
        p.ifPresent(p2 -> {
            stateRecord.getPos()
                .ifPresent(p1 -> {
                    stateRecord.setDir(p2.sub(p1).normalized());
            });
        });
      return this;
    }

    @Override
    public GameState moveAhead() {
        stateRecord.getPlayer().moveAhead();
        return  this;
    }
    @Override
    public GameState moveBack() {
        stateRecord.getPlayer().moveBack();
        return  this;
    }
    @Override
    public GameState turnLeft() {
        stateRecord.getPlayer().turnLeft();
        return  this;
    }
    @Override
    public GameState turnRight() {
        stateRecord.getPlayer().turnRight();
        return  this;
    }
    public GameState retard() {
        stateRecord.getPlayer().retard();
        return this;
    }
    @Override
    public GameState stopTurning() {
        stateRecord.getPlayer().stopTurning();
        return this;
    }
}
