package de.rsh.rycst.game;



public interface GamePanel{

    // beeing nice to other processes, restrict the frame rate to FPS fps
    public final long FPS=60; // frame per second
    public final long FRAME_DURATION_NANOS=1000*1000/FPS; // nano secds

    public void start();
    public void terminate();

    // update game state
    void update(long t, long dt);

    // render to graphics system
    void render();
}