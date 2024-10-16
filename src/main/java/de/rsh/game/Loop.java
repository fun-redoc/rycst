package de.rsh.game;

import java.util.function.Function;

import de.rsh.rycst.App;

public class Loop {
    // do i still have to wrap funtions in classes?
    public static  Function<App, Thread> loop(long frameDurationNanos) {   return (app) -> {
            return Thread.ofVirtual().unstarted(() -> {
                    boolean running = true;
                    try {
                        var last = System.nanoTime();
                        Thread.sleep(frameDurationNanos/1000_000);
                        while(running) {
                            var dt = System.nanoTime()-last;
                            var t = System.nanoTime();       
                            last = t;
                            app.update(t, dt);
                            app.render();
                            var duration = System.nanoTime() - t;
                            if(duration < frameDurationNanos) {
                                var timeLeftToSleep = (frameDurationNanos - duration);
                                Thread.sleep(timeLeftToSleep/1000_000); // sleep thre remainig milli secotds
                            } else {
                                System.err.println("frame to long");
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