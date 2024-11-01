package de.rsh.math;

import java.util.Optional;
import de.rsh.graph.V2;

/*
 * V2 has to be a type which accesses a 2 dimensional vector of doubles
 */
public class LineSegment<V2Impl, V2Arena extends V2<V2Impl>> {
   public record LineIntersection<V2Impl>(V2Impl pos, double t ) {}
   private V2Impl start; 
   private V2Impl end;
   private V2Arena v2;
   public LineSegment(V2Arena v2, V2Impl start, V2Impl end) {
    this.v2 = v2;
    this.start = start;
    this.end = end;
   }
    public Optional<LineIntersection<V2Impl>> lineSegmentIntersection(LineSegment<V2Impl, V2Arena> ls1, LineSegment<V2Impl, V2Arena> ls2) {
        //const [A, B, C, D] = [ls1[0], ls1[1], ls2[0], ls2[1]];
        var A = ls1.start;
        var B = ls1.end;
        var C = ls2.start;
        var D = ls2.end;
        var tTop = (v2.x(D) - v2.x(C)) * (v2.y(A) - v2.y(C)) - (v2.y(D) - v2.y(C)) * (v2.x(A) - v2.x(C));
        var uTop = (v2.y(C) - v2.y(A)) * (v2.x(A) - v2.x(B)) - (v2.x(C) - v2.x(A)) * (v2.y(A) - v2.y(B));
        var bottom = (v2.y(D) - v2.y(C)) * (v2.x(B) - v2.x(A)) - (v2.x(D) - v2.x(C)) * (v2.y(B) - v2.y(A));

        if (bottom != 0) {
            var t = tTop / bottom;
            var u = uTop / bottom;
            if (t >= 0 && t <= 1 && u >= 0 && u <= 1) {
                var pos_x = lerp(v2.x(A), v2.x(B), t);
                var pos_y = lerp(v2.y(A), v2.y(B), t);
                var pos = v2.c(pos_x, pos_y);
                return Optional.of(new LineIntersection<V2Impl>( pos, t));
            }
        }
        return Optional.empty();
    }

    public boolean checkPlygonIntersection(LineSegment<V2Impl, V2Arena>[] p1, LineSegment<V2Impl, V2Arena>[] p2) {
        for (int  i = 0; i < p1.length; i++) {
            for (int j = 0; j < p2.length; j++) {
                var touch = lineSegmentIntersection(p1[i], p2[j]);
                if (touch.isPresent()) {
                    return true;
                }
            }
        }
        return false;
    }

    private double lerp(double A, double B, double t) {
        return A + (B - A) * t;
    }
}
