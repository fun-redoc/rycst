package de.rsh.rycst.game;
import java.util.function.*;

import de.rsh.rycst.game.Loop;

public class Loop {

    // do i still have to wrap funtions in classes?
    public static Function<GamePanel, Thread> loop  = (gamePanel) -> {
            return Thread.ofVirtual().unstarted(() -> {
                boolean running = true;
                    try {
                        var last = System.nanoTime();
                        Thread.sleep(GamePanel.FRAME_DURATION_NANOS/1000);
                        while(running) {
                            var dt = System.nanoTime()-last;
                            var t = System.nanoTime();       
                            last = t;
                            gamePanel.update(t, dt);
                            gamePanel.render();
                            var duration = System.nanoTime() - t;
                            if(duration < GamePanel.FRAME_DURATION_NANOS) {
                                Thread.sleep((GamePanel.FRAME_DURATION_NANOS - duration)/1000); // sleep thre remainig milli secotds
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