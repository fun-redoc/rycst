package de.rsh.rycst.utils;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.swing.JComponent;

import de.rsh.rycst.swingrendering.graphics.CoordintateTransformer;

import java.util.Optional;

public class RayCaster {
    private final double EPSILON = 1e-6;
    private int gridWidth;
    private int gridHeight;
    private CoordintateTransformer coordintateTransformer;
    public RayCaster(int gridWidth, int gridHeight, CoordintateTransformer coordintateTransformer) {
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.coordintateTransformer = coordintateTransformer;
    }
    private final Vec2d worldMapSize() {
        final double h = (double)gridWidth;
        final double w = (double)gridHeight;
        final var s = new Vec2d(w, h);
        return s;
    }
    /**
     * find the nearest point in direction dir which snaps to the grid of the worldMap
     * main idea taken from: https://lodev.org/cgtutor/raycasting.html
     * TODO: this algi intetrnally knows the grid coords and if x (NorthSouth) or y(EastWest) side of grid was hit
     *       thiose information should be passed to the outside...and used on 
     * @param from
     * @param dir
     * @return neares snap point
     */
    private Vec2d rayStep(Vec2d from, Vec2d dir) {
        final double eps = 1e-3;
        var d = dir.mul(worldMapSize());
        var v = from.mul(worldMapSize());
        if(v.x() == 0 || v.y() == 0 || v.x()/Math.floor(v.x()) == 1.0 || v.y()/Math.floor(v.y()) == 1.0) {
            // for those above some nudging is needed, otherwise the ray would stay in place
            v = v.add(dir.scaled(eps)); // nudge it into right direction
        }
        double xSnappedToGrid;
        double ySnappedToGrid;
        double x,y;

        if(dir.x() > 0) {
            xSnappedToGrid = Math.ceil(v.x());
        } else {
            xSnappedToGrid = Math.floor(v.x());
        }
        if(dir.y() > 0) {
            ySnappedToGrid = Math.ceil(v.y());
        } else {
            ySnappedToGrid = Math.floor(v.y());
        }
        // m = dy/dx
        // y1 = m*x1 + b => b = y1 - m*x1
        // y2 = m*x2 + b => b = y2 - m*x2
        // y1 = m*x1 + y2 - m*x2 => y1 - y2 = m*(x1 - x2) => m = y1 - y2 / x1 - x2 = dy/dx
        if(d.x() == 0) {
            y= v.y();
        } else {
            var m = d.y()/d.x();
            var b = v.y() - m*v.x();
            y= b + xSnappedToGrid*m;
        }
        var raySnap1 =  new Vec2d(xSnappedToGrid, y).div(worldMapSize()).orElseThrow();
        // y1 = m*x1 + b => y1 - b = m*x1 => 
        // x1 = (y1 - b)/m
        if(d.y() == 0) {
            x= v.x();
        } else {
            var m = d.y()/d.x();
            var b = v.y() - m*v.x();
            x = (ySnappedToGrid - b) / m;
        }
        var raySnap2 =  new Vec2d(x, ySnappedToGrid).div(worldMapSize()).orElseThrow();
        if( raySnap1.distTo(from) <= raySnap2.distTo(from)) {
            return raySnap1;
        } else {

            return raySnap2;
        }
//        System.out.printf("(xsn,ysn)=(%.4f,%.4f);v=%s; raySnap=%s\n",xSnappedToGrid, ySnappedToGrid, v, raySnap);
    }


    /**
     * casts a ray from from in direction dir, calls callback on grid hit points
     * @param from
     * @param dir
     * @param callback takes snap point in normalized coordinate system and computes something else, e.g. the grid index within the world grid
     */
    public <T> Optional<Tuple<Vec2d, T>> rayCast(Vec2d from, Vec2d dir, Function<Vec2d, Tuple<Boolean, Optional<T>>> callback ) {
        var rs = rayStep(from, dir);
        var goonAndIntermediateResult = new Tuple<Boolean, Optional<T>>(true, Optional.empty());
        while(goonAndIntermediateResult.fst() && Math.abs(rs.x()) <= 0.5 && Math.abs(rs.y()) <= 0.5) {
            goonAndIntermediateResult = callback.apply(rs);
            if(goonAndIntermediateResult.fst()) { rs = rayStep(rs, dir); }
        }
        if(goonAndIntermediateResult.snd().isPresent()) {
            return Optional.of(new Tuple<Vec2d,T>(rs, goonAndIntermediateResult.snd().get()));
        } else {
            return Optional.empty();
        }
    }

    /**
     * determins the most outer snap point found and his grid index. grid index may be empty e.g. if there is nothing
     * if the grid index is provided the snap point corresponds to its normalized coordinates
     * @param pos
     * @param dir
     * @return
     */
    public Optional<Tuple<Vec2d, Tuple<Integer,Integer>>> rayCastToGrid(Vec2d pos, Vec2d dir,
                                                                        Predicate<Tuple<Integer,Integer>> hit) {
        var res = this.<Tuple<Integer,Integer>>rayCast(pos, dir, (rs) -> {
            var reOvershoot = rs.add(dir.scaled(EPSILON));  // due to epsiol step can overshoot out of space
            var gridCoord = coordintateTransformer.normalizedToGrid(reOvershoot, gridWidth, gridHeight).toInt();  // due to epsiol step can overshoot out of space
            if(gridCoord.fst() < 0 || gridCoord.fst() >= gridWidth ||
               gridCoord.snd() < 0 || gridCoord.snd() >= gridHeight) {
                System.err.printf("rs:%s -> reOver:%s -> gridCoord:%s\n", rs, reOvershoot, gridCoord);
            }
            else {
                if(hit.test(gridCoord)) {
                    return new Tuple<>(false,Optional.of(gridCoord));
                }
//                if(world.map[gridCoord.snd()][gridCoord.fst()] != WorldMap.SPACE) {
//                    return new Tuple<>(false,Optional.of(gridCoord));
//                }
            }
            return new Tuple<>(true, Optional.empty());
        });
        return res;
    }
}
