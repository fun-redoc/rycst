package de.rsh.graph;
import java.lang.Math;
import java.util.Optional;
import java.util.function.Function;

import de.rsh.utils.Pair;

public final class Vec2d implements Cloneable {
    private static final Vec2d zero0 = new Vec2d(0.0,0.0);
    private static final Vec2d up = new Vec2d(0.0,1.0);
    private static final Vec2d down = new Vec2d(0.0,-1.0);
    private static final Vec2d left = new Vec2d(-1.0,0);
    private static final Vec2d right = new Vec2d(1.0,0);

    private boolean alreadyNormalized = false;
    private double x;
    private double y;

    public static Vec2d zero() {return Vec2d.zero0;}
    public static Vec2d up() {return Vec2d.up;}
    public static Vec2d down() {return Vec2d.down;}
    public static Vec2d left() {return Vec2d.left;}
    public static Vec2d right() {return Vec2d.right;}
    public static <T extends Number> Vec2d fromPair(Pair<T,T> pair) { // this is strage, T should have the constraint to be integral, but there is non in java or is it?
        return new Vec2d(pair.fst().doubleValue(), pair.snd().doubleValue());
    }

    /**
     * public constructor for 2 dim vectors
     * @param x
     * @param y
     * @return instance of Vec2d
     */
    public static Vec2d c(double x, double y) {
        return new Vec2d(x, y);
    }

    private Vec2d(double x, double y) {
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
    private Vec2d(double x, double y, boolean alreadyNormalized) {
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
    public <T> T map(Function<Vec2d, T> f) { return f.apply(this);}
    public Vec2d abs() {return new Vec2d(Math.abs(x), Math.abs(y));}
    public Vec2d add(Vec2d v) {return new Vec2d(x + v.x, y + v.y);}
    public Vec2d sub(Vec2d v) {return new Vec2d(x - v.x, y - v.y);}
    public Vec2d mul(Vec2d v) {return new Vec2d(x * v.x, y * v.y);}
    public Optional<Vec2d> div(Vec2d v) { if(v.x == 0.0 || v.y == 0) return Optional.empty(); 
                                          else return Optional.of(new Vec2d(x / v.x, y / v.y));}
    public double norm() {return x*x + y*y;}
    public double len() {
        return  alreadyNormalized ? 1 : Math.sqrt(norm());
    }
    public Vec2d flippedHorizontally() {return new Vec2d(x, -y, alreadyNormalized);}
    public Vec2d flippedVertically() {return new Vec2d(-x, y, alreadyNormalized);}
    public Vec2d rotateOrthogonallyCounterClockwize() {return new Vec2d(-y, x, alreadyNormalized);}
    public Vec2d rotateOrthogonallyClockwize() {return new Vec2d(y, -x, alreadyNormalized);}
    public Vec2d movedBy(Vec2d v) { return new Vec2d(x + v.x, y + v.y); }
    public Vec2d movedByXY(double dx, double dy) { return new Vec2d(x + dx, y + dy); }
    public Vec2d neg() { return new Vec2d(-x, -y); }
    public Vec2d scaled(double s) { return new Vec2d(x * s, y * s); }
    public Vec2d normalized() {
        return alreadyNormalized ? this :  this.scaled(1/this.len());
    }
    public Vec2d componentMap(Function<Double,Double> f) {
        return new Vec2d(f.apply(x), f.apply(y));
    }
    public Vec2d floor() {
        return componentMap(Math::floor);
    }
    public Vec2d turnByAngleOf(double rad) { 
        var sin_rad = Math.sin(rad);
        var cos_rad = Math.cos(rad);
        return new Vec2d(x*cos_rad-y*sin_rad, x*sin_rad+y*cos_rad, alreadyNormalized);
    }
    public double distTo(Vec2d p){
        var dx = x - p.x;
        var dy = y - p.y;
        //    return Math.sqrt(dx * dx + dy * dy);
        return Math.hypot(dx, dy);
    }
    //public boolean isNear(Vec2d p, double r){ return distTo(p) <= r; }
    public boolean isNear(Vec2d p, double r){
        var dx = x - p.x;
        var dy = y - p.y;
        return dx*dx+dy*dy <= r*r; 
    }

    public double dot(Vec2d other) { return other.x()*this.x() + other.y()*this.y(); }

    public double perpDistToLine(Vec2d p, Vec2d dir) {
        // d = (p-this)*n/len(n) where * is dot product and n is the normal to dir
        var n = dir.rotateOrthogonallyCounterClockwize();
        var delta = p.sub(this);
        var dp = delta.dot(n);
        var d = dp/n.len();
        return Math.abs(d);
    }

    @Override
    public Vec2d clone() { return new Vec2d(x, y); }
    @Override
    public boolean equals(Object o) {
        if(o == null || !(o instanceof Vec2d)) return false;
        var v = (Vec2d)o;
        return x == v.x && y == v.y;
    }
    @Override
    public String toString() {
        return String.format("Vec2d:[%.4f, %.4f]", x(), y());
    }
}

