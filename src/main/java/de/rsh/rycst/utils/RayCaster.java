package de.rsh.rycst.utils;
import java.util.function.*;

import java.util.Optional;
import java.util.List;

public class RayCaster {
    // WTH: I only want a function, when will java have real functions?
    /**
     * function callback used in drawGamefield3D_lodev function, to decouple ray cast math from drawing method
     * in the case i want to use it with SDL, Terminal, or others,.. instead of pure java.
     */
    @FunctionalInterface
    public interface RayDrawingCallback   {
        public void apply(int side, int x, int y1, int y2, int mapX, int map);
    }

    /**
     * find the nearest point in direction dir which snaps to the grid of the worldMap
     * Credits: main idea - jumping from one grid snap poit to the next -  taken from: https://lodev.org/cgtutor/raycasting.html
     *          finally the I chose to do it my way, lodev's coding is not used, but it is very sophisticated and probably faster than mine
     * @param from: Position within the grid coordingate system
     * @param dir
     * @return neares snap point and snap Side
     */
    public static enum Side {Hor, Ver};
    private static Pair<Vec2d, Side> rayStep(Vec2d from, Vec2d dir) {
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
     * determins the most outer snap point found and his grid index. grid index may be empty e.g. if there is nothing
     * if the grid index is provided the snap point corresponds to its normalized coordinates
     * it stops on hit or when leaving the grid
     * @param pos
     * @param dir
     * @return snap coords on hit grid cell, the hit side, the index of grid cell
     */
    public static Optional<Tupl3<Vec2d, Side, Pair<Integer,Integer>>>
           rayCastUntilHit(Vec2d pos, Vec2d dir, int gridWidth, int gridHeight, Predicate<Pair<Integer,Integer>> hit) {

        final double EPSILON = 1e-3;
        Optional<Tupl3<Vec2d, Side, Pair<Integer, Integer>>> result = Optional.empty();
        var rs = rayStep(pos, dir);
        var goon = true;
        while(goon && rs.fst().x() >= 0.0 && rs.fst().x() < gridWidth 
                   && rs.fst().y() >= 0.0 && rs.fst().y() < gridHeight) {

                var rsOvershoot = rs.fst().add(dir.scaled(EPSILON));  // due to epsiol step can overshoot out of space
                if(rsOvershoot.x() < 0 || rsOvershoot.x() >= gridWidth ||
                   rsOvershoot.y() < 0 || rsOvershoot.y() >= gridHeight) {
                    System.err.printf("rs:%s -> rsOverhoot:%s\n", rs, rsOvershoot);
                } else {
                    var _gridIndex = rsOvershoot.toInt();
                    if(hit.test(_gridIndex)) {
                        goon =  false;
                        result = Optional.of(new Tupl3<>(rs.fst(), rs.snd(), _gridIndex));
                    }
                }
            if(goon) { rs = rayStep(rs.fst(), dir); }
        }
        return result;
    }

    /**
     * drawing raycasting according to https://lodev.org/cgtutor/raycasting.html
     * Copyright (c) 2004-2021, Lode Vandevenne
     * algorithm directly translated from c to java, movement, keyboard handling has to happen elsewhere,
     * drawing and hit via callbacks
     */
    public static void drawGameField3D_lodev(
          int width, int height,
          double posX, double posY,
          double dirX, double dirY,
          double planeX, double planeY,
          BiPredicate<Integer, Integer> hitCallback,
          RayDrawingCallback rayDrawingCallback,
          Optional<List<Pair<Double,Double>>> trace) {
        /*
        Copyright (c) 2004-2021, Lode Vandevenne

        All rights reserved.

        Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

            * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
            * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.

        THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
        "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
        LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
        A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
        CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
        EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
        PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
        PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
        LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
        NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
        SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
        */
//        var map = rayCastState.map;

//        double posX = rayCastState.posX, posY = rayCastState.posY;  //x and y start position
//        double dirX = rayCastState.dirX, dirY = rayCastState.dirY; //idirection vector
//        double planeX = rayCastState.ncpX, planeY = rayCastState.ncpY; //the 2d raycaster version of camera plane


        //double time = 0; //time of current frame
        //double oldTime = 0; //time of previous frame
        var w = width;
        var h = height;

        for(int x = 0; x < w; x++) {

            //calculate ray position and direction
            var cameraX = (2.0 * (double)x / (double)w) - 1.0; //x-coordinate in camera space

            var rayDirX = dirX + planeX * cameraX;
            var rayDirY = dirY + planeY * cameraX;

            //which box of the map we're in
            var mapX = (int)posX;
            var mapY = (int)posY;

            //length of ray from current position to next x or y-side
            double sideDistX;
            double sideDistY;

            //length of ray from one x or y-side to next x or y-side
            //these are derived as:
            //deltaDistX = sqrt(1 + (rayDirY * rayDirY) / (rayDirX * rayDirX))
            //deltaDistY = sqrt(1 + (rayDirX * rayDirX) / (rayDirY * rayDirY))
            //which can be simplified to abs(|rayDir| / rayDirX) and abs(|rayDir| / rayDirY)
            //where |rayDir| is the length of the vector (rayDirX, rayDirY). Its length,
            //unlike (dirX, dirY) is not 1, however this does not matter, only the
            //ratio between deltaDistX and deltaDistY matters, due to the way the DDA
            //stepping further below works. So the values can be computed as below.
            // Division through zero is prevented, even though technically that's not
            // needed in C++ with IEEE 754 floating point values.
            var deltaDistX = (rayDirX == 0) ? 1e30 : Math.abs(1.0 / rayDirX);
            var deltaDistY = (rayDirY == 0) ? 1e30 : Math.abs(1.0 / rayDirY);

            double perpWallDist;

            //what direction to step in x or y-direction (either +1 or -1)
            int stepX;
            int stepY;

            boolean hit = false; //was there a wall hit?
            int side = 0; //was a  0==North/South or a 1==Eeast/West wall hit?
            //calculate step and initial sideDist
            if(rayDirX < 0) {
                stepX = -1;
                sideDistX = (posX - mapX) * deltaDistX;
            } else {
                stepX = 1;
                sideDistX = (mapX + 1.0 - posX) * deltaDistX;
            }
            if(rayDirY < 0) {
                stepY = -1;
                sideDistY = (posY - mapY) * deltaDistY;
            } else {
                stepY = 1;
                sideDistY = (mapY + 1.0 - posY) * deltaDistY;
            }

            //perform DDA
            while(!hit) {
                //jump to next map square, either in x-direction, or in y-direction
                if(sideDistX < sideDistY) {
                    sideDistX += deltaDistX;
                    mapX += stepX;
                    side = 0;
                } else {
                    sideDistY += deltaDistY;
                    mapY += stepY;
                    side = 1;
                }
                //Check if ray has hit a wall
                //hit = (map[mapX][mapY] != WorldMap.SPACE);
                //hit = (map[mapY][mapX] != WorldMap.SPACE);
                hit = hitCallback.test(mapX,mapY);
            }

            //Calculate distance projected on camera direction. This is the shortest distance from the point where the wall is
            //hit to the camera plane. Euclidean to center camera point would give fisheye effect!
            //This can be computed as (mapX - posX + (1 - stepX) / 2) / rayDirX for side == 0, or same formula with Y
            //for size == 1, but can be simplified to the code below thanks to how sideDist and deltaDist are computed:
            //because they were left scaled to |rayDir|. sideDist is the entire length of the ray above after the multiple
            //steps, but we subtract deltaDist once because one step more into the wall was taken above.
        if(side == 0) perpWallDist = (sideDistX - deltaDistX);
        else          perpWallDist = (sideDistY - deltaDistY);
            if(trace.isPresent()) {
                if(side == 0)
                    trace.get().add(new Pair<Double,Double>((double)(mapX), (double)(mapY)));
                else
                    trace.get().add(new Pair<Double,Double>((double)(mapX), (double)(mapY)));
            }

            //Calculate height of line to draw on screen
            int lineHeight = (int)(h / perpWallDist);

            //calculate lowest and highest pixel to fill in current stripe
            int drawStart = -lineHeight / 2 + h / 2;
            if(drawStart < 0) drawStart = 0;
            int drawEnd = lineHeight / 2 + h / 2;
            if(drawEnd >= h) drawEnd = h - 1;

            rayDrawingCallback.apply(side, x,drawStart, drawEnd, mapX, mapY);

        }
    }
}

