package de.rsh.graph;
import java.lang.Math;
import java.util.Optional;
import java.util.function.Function;

import de.rsh.utils.Pair;

public final class Vec2 implements Cloneable {
    private static final Vec2 zero0 = Vec2.c(0.0,0.0);
    private static final Vec2 up = Vec2.c(0.0,1.0);
    private static final Vec2 down = Vec2.c(0.0,-1.0);
    private static final Vec2 left = Vec2.c(-1.0,0);
    private static final Vec2 right = Vec2.c(1.0,0);

    private boolean alreadyNormalized = false;
    private double x;
    private double y;

    public static Vec2 zero() {return Vec2.zero0;}
    public static Vec2 up() {return Vec2.up;}
    public static Vec2 down() {return Vec2.down;}
    public static Vec2 left() {return Vec2.left;}
    public static Vec2 right() {return Vec2.right;}
    public static <T extends Number> Vec2 fromPair(Pair<T,T> pair) { // this is strage, T should have the constraint to be integral, but there is non in java or is it?
        return Vec2.c(pair.fst().doubleValue(), pair.snd().doubleValue());
    }

    /**
     * public constructor for 2 dim vectors
     * @param x
     * @param y
     * @return instance of Vec2
     */
    public static Vec2 c(double x, double y) {
        return new Vec2(x, y);
    }
    private static Vec2 c(double x, double y, boolean alreadyNormalized) {
        return new Vec2(x, y, alreadyNormalized);
    }

    private Vec2(double x, double y) {
        this.x = x;
        this.y = y;
        this.alreadyNormalized = false;
    }
    /**
     * using this constructor you must make shure vector is already normalized (1 == sqrt(x^2 + y^2))
     * @param x
     * @param y
     * @param alreadyNormalized
     */
    private Vec2(double x, double y, boolean alreadyNormalized) {
        this.x = x;
        this.y = y;
        this.alreadyNormalized = alreadyNormalized;
    }
    public double x() {
        return x;
    }
    public double y() {
        return y;
    }
    public Pair<Integer,Integer> toInt() {
        //var x_ = (int)(Math.ceil(x()));
        //var y_ = (int)(Math.ceil(y()));
        //var x_ = (int)(Math.round(x()));
        //var y_ = (int)(Math.round(y()));
        //var x_ = (int)(Math.floor(x()));
        //var y_ = (int)(Math.floor(y()));
        var x_ = (int)x();
        var y_ = (int)y();
        return new Pair<>(x_, y_);
    }
    public <T> T map(Function<Vec2, T> f) { return f.apply(this);}
    public Vec2 abs() {return Vec2.c(Math.abs(x), Math.abs(y));}
    public Vec2 add(Vec2 v) {return Vec2.c(x + v.x, y + v.y);}
    public Vec2 sub(Vec2 v) {return Vec2.c(x - v.x, y - v.y);}
    public Vec2 mul(Vec2 v) {return Vec2.c(x * v.x, y * v.y);}
    public Optional<Vec2> div(Vec2 v) { if(v.x == 0.0 || v.y == 0) return Optional.empty(); 
                                          else return Optional.of(Vec2.c(x / v.x, y / v.y));}
    public double norm() {return x*x + y*y;}
    public double len() {
        return  alreadyNormalized ? 1 : Math.sqrt(norm());
    }
    public Vec2 flippedHorizontally() {return Vec2.c(x, -y, alreadyNormalized);}
    public Vec2 flippedVertically() {return Vec2.c(-x, y, alreadyNormalized);}
    public Vec2 rotateOrthogonallyCounterClockwize() {return Vec2.c(-y, x, alreadyNormalized);}
    public Vec2 rotateOrthogonallyClockwize() {return Vec2.c(y, -x, alreadyNormalized);}
    public Vec2 movedBy(Vec2 v) { return Vec2.c(x + v.x, y + v.y); }
    public Vec2 movedByXY(double dx, double dy) { return Vec2.c(x + dx, y + dy); }
    public Vec2 neg() { return Vec2.c(-x, -y); }
    public Vec2 scaled(double s) { return Vec2.c(x * s, y * s); }
    public Vec2 normalized() {
        return alreadyNormalized ? this :  this.scaled(1/this.len());
    }
    public Vec2 componentMap(Function<Double,Double> f) {
        return Vec2.c(f.apply(x), f.apply(y));
    }
    public Vec2 floor() {
        return componentMap(Math::floor);
    }
    public Vec2 turnByAngleOf(double rad) { 
        var sin_rad = Math.sin(rad);
        var cos_rad = Math.cos(rad);
        return Vec2.c(x*cos_rad-y*sin_rad, x*sin_rad+y*cos_rad, alreadyNormalized);
    }
    public double distTo(Vec2 p){
        var dx = x - p.x;
        var dy = y - p.y;
        //    return Math.sqrt(dx * dx + dy * dy);
        return Math.hypot(dx, dy);
    }
    //public boolean isNear(Vec2 p, double r){ return distTo(p) <= r; }
    public boolean isNear(Vec2 p, double r){
        var dx = x - p.x;
        var dy = y - p.y;
        return dx*dx+dy*dy <= r*r; 
    }

    public double dot(Vec2 other) { return other.x()*this.x() + other.y()*this.y(); }

    public double perpDistToLine(Vec2 p, Vec2 dir) {
        // d = (p-this)*n/len(n) where * is dot product and n is the normal to dir
        var n = dir.rotateOrthogonallyCounterClockwize();
        var delta = p.sub(this);
        var dp = delta.dot(n);
        var d = dp/n.len();
        return Math.abs(d);
    }

    @Override
    public Vec2 clone() { return Vec2.c(x, y); }
    @Override
    public boolean equals(Object o) {
        if(o == null || !(o instanceof Vec2)) return false;
        var v = (Vec2)o;
        return x == v.x && y == v.y;
    }
    @Override
    public String toString() {
        return String.format("Vec2:[%.4f, %.4f]", x(), y());
    }
}

