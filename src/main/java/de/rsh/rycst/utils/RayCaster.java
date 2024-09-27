package de.rsh.rycst.utils;
import java.util.function.Function;
import java.util.function.Predicate;

import java.util.Optional;

public class RayCaster {
    private final double EPSILON = 1e-3;
    private double gridWidth;
    private double gridHeight;
    public RayCaster(int gridWidth, int gridHeight) {
        this.gridWidth  = (double)gridWidth;
        this.gridHeight = (double)gridHeight;
    }

    /**
     * find the nearest point in direction dir which snaps to the grid of the worldMap
     * Credits: main idea taken from: https://lodev.org/cgtutor/raycasting.html
     * lodev's coding is not used
     * TODO: this algi intetrnally knows the grid coords and if x (NorthSouth) or y(EastWest) side of grid was hit
     *       thiose information should be passed to the outside...and used on 
     * @param from: Position within the grid coordingate system
     * @param dir
     * @return neares snap point and snap Side
     */
    public static enum Side {Hor, Ver};
    private Pair<Vec2d, Side> rayStep(Vec2d from, Vec2d dir) {
        final double eps = 1e-3;
        var d = dir;
        var v = from;
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
        var raySnap1 =  new Vec2d(xSnappedToGrid, y);  // snapped vertically
        // y1 = m*x1 + b => y1 - b = m*x1 => 
        // x1 = (y1 - b)/m
        if(d.y() == 0) {
            x= v.x();
        } else {
            var m = d.y()/d.x();
            var b = v.y() - m*v.x();
            x = (ySnappedToGrid - b) / m;
        }
        var raySnap2 =  new Vec2d(x, ySnappedToGrid); // snapped horizontally
        if( raySnap1.distTo(from) <= raySnap2.distTo(from)) {
            return new Pair<Vec2d, Side>(raySnap1, Side.Ver);
        } else {

            return new Pair<Vec2d, Side>(raySnap2, Side.Hor);
        }
//        System.out.printf("(xsn,ysn)=(%.4f,%.4f);v=%s; raySnap=%s\n",xSnappedToGrid, ySnappedToGrid, v, raySnap);
    }


    /**
     * casts a ray from from in direction dir, calls callback on grid hit points
     * @param from
     * @param dir
     * @param callback takes snap point in normalized coordinate system and computes something else, e.g. the grid index within the world grid
     */
    public <T> Optional<Tupl3<Vec2d, Side, T>> rayCast(Vec2d from, Vec2d dir, Function<Pair<Vec2d, Side>, Pair<Boolean, Optional<T>>> callback ) {
        var rs = rayStep(from, dir);
        var goonAndIntermediateResult = new Pair<Boolean, Optional<T>>(true, Optional.empty());
        while(goonAndIntermediateResult.fst() && rs.fst().x() >= 0.0 && rs.fst().x() < gridWidth 
                                              && rs.fst().y() >= 0.0 && rs.fst().y() < gridHeight

             ) {
            goonAndIntermediateResult = callback.apply(rs);
            if(goonAndIntermediateResult.fst()) { rs = rayStep(rs.fst(), dir); }
        }
        if(goonAndIntermediateResult.snd().isPresent()) {
            return Optional.of(new Tupl3<Vec2d, Side,T>(rs.fst(), rs.snd(), goonAndIntermediateResult.snd().get()));
        } else {
            return Optional.empty();
        }
    }

    /**
     * determins the most outer snap point found and his grid index. grid index may be empty e.g. if there is nothing
     * if the grid index is provided the snap point corresponds to its normalized coordinates
     * @param pos
     * @param dir
     * @return snap coords on hit grid cell, the hit side, the index of grid cell
     */
    public Optional<Tupl3<Vec2d, Side, Pair<Integer,Integer>>>
           rayCastToGrid(Vec2d pos, Vec2d dir, Predicate<Pair<Integer,Integer>> hit) {
        var res = this.<Pair<Integer,Integer>>rayCast(pos, dir, (rs) -> {
            var rsOvershoot = rs.fst().add(dir.scaled(EPSILON));  // due to epsiol step can overshoot out of space
            var gridCoord = rsOvershoot;  // due to epsiol step can overshoot out of space
            if(gridCoord.x() < 0 || gridCoord.x() >= gridWidth ||
               gridCoord.y() < 0 || gridCoord.y() >= gridHeight) {
                System.err.printf("rs:%s -> rsOver:%s -> gridCoord:%s\n", rs, rsOvershoot, gridCoord);
            }
            else {
                if(hit.test(gridCoord.toInt())) {
                    return new Pair<>(false,Optional.of(gridCoord.toInt()));
                }
//                if(world.map[gridCoord.snd()][gridCoord.fst()] != WorldMap.SPACE) {
//                    return new Tuple<>(false,Optional.of(gridCoord));
//                }
            }
            return new Pair<>(true, Optional.empty());
        });
        return res;
    }
}

