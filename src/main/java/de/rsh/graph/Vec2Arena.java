package de.rsh.graph;

import java.util.function.*;

import de.rsh.utils.Pair;

/**
 * class allocates an arena of 2d vecs. can be used if you need lots of vec2d objects in a loop
 * this will make allocation and deallocation much faster and avoid gc slow downs
 * IMPORTANT: you shound be able to estimate the max number of vectors needed, because memory is
 *            allocated on creation and is not extended. there are also no bounds checks.
 * Use clear() and clearFrom() to cleanup on no cost.
 * 
 * Usage: 
 *       1. use constructor VEc2Arena to create an empty arena
 *       2. use the c(x,y) function to contruct a new vecotr
 *          c returns a handle which must be used to access the vetor in subsequent functions
 *       3. use the vector function and always provide the handle provided by c() to access the appropriate vecor
 *          e.g. 
 *          var arena = new Vec2Arena(100); // arena which can hold 100 vectors
 *          var v1 = c(10,10);
 *          var v2 = c(20,20);
 *          var vsum = arena.add(v1,v2);
 *          System.out.println(arena.toString(vsum)); // should print [30,30]
 */
public class Vec2Arena {
    private int size;
    private double[] x;
    private double[] y;
    private boolean[] isNormalized;
    int last = 0;
    /**
     * size: the maximal numbe of vecors to be used in this arena, wil not be extended, so beware
     */
    public Vec2Arena(int size) {
        this.size = size;
        this.x = new double[size];
        this.y = new double[size];
        this.isNormalized = new boolean[size];
    }
    /*
     * get the current pointer in the arena
     * cann be used to partly deallocate all entries down to this point
     */
    public int clearPoint() {
        return last;
    }
    /**
     * clear all entreies from this clearPoint on
     */
    public void clearFrom(int clearPoint) {
        last = clearPoint;
    }
    /**
     * clear the whole arena
     */
    public void clear() {
        last = 0;
    }
    /**
     * creates new vector and stores the values in the arena
     * @param x
     * @param y
     * @return integer handle to the vector, dont mess araound with it, java urgently needs type aliases!!!
     */
    public int c(double x, double y) {
        return c(x,y,false);
    }
    /**
     * @param x
     * @param y
     * @param isNormalized the normalized flag optimizes the behaviour when wee need the vetor of lenght == 1.0, 
     * @return integer handle to the vector, dont mess araound with it, java urgently needs type aliases!!!
     */
    private int c(double x, double y, boolean isNormalized) {
        this.x[last] =  x;
        this.y[last] = y;
        this.isNormalized[last] =  isNormalized;
        return last++;
    }
    public double x(int handle) {
        return x[handle];
    }
    public double y(int handle) {
        return y[handle];
    }
    private boolean isNormalized(int handle) {
        return isNormalized[handle];
    }

    /**
     * @return construcor for the zero vector
     */
    public long zero() {return c(0,0);}

    /**
     * constructs a vector from tuple
     */
    public  <T extends Number> long fromPair(Pair<T,T> pair) { // this is strage, T should have the constraint to be integral, but there is non in java or is it?
        return c(pair.fst().doubleValue(), pair.snd().doubleValue());
    }
    public Pair<Integer,Integer> toInt(int handle) {
        var x_ = (int)x(handle);
        var y_ = (int)y(handle);
        return new Pair<>(x_, y_);
    }
    public <T> T map(int handle, BiFunction<Double, Double, T> f) { return 
        f.apply(x(handle), y(handle));
    }
    public int abs(int handle) {return c(Math.abs(x(handle)), Math.abs(handle));}
    public int add(int handle, int otherHandle) {return c(x(handle) + x(otherHandle), y(handle) + y(otherHandle));}
    public int sub(int handle, int otherHandle) {return c(x(handle) - x(otherHandle), y(handle) - y(otherHandle));}
    public int mul(int handle, int otherHandle) {return c(x(handle) * x(otherHandle), y(handle) * y(otherHandle));}
    public int div(int handle, int otherHandle) { 
        // beware division by 0 will crash
        return c(x(handle) / x(otherHandle), y(handle) / y(otherHandle));
    }
    public double norm(int handle) {
        var x = x(handle);
        var y = y(handle);
        return x*x + y*y;
    }
    public double len(int handle) {
        return  isNormalized(handle) ? 1 : Math.sqrt(norm(handle));
    }
    public int flippedHorizontally(int handle) {return c(x(handle), -y(handle), isNormalized(handle));}
    public int flippedVertically(int handle) {return c(-x(handle), y(handle), isNormalized(handle));}
    public int rotateOrthogonallyCounterClockwize(int handle) {return c(-y(handle), x(handle), isNormalized(handle));}
    public int rotateOrthogonallyClockwize(int handle) {return c(y(handle), -x(handle), isNormalized(handle));}
    public int movedBy(int handle, int otherHandle) { return c(x(handle) + x(otherHandle), y(handle) + y(otherHandle)); }
    public int movedByXY(int handle, double dx, double dy) { return c(x(handle) + dx, y(handle) + dy); }
    public int neg(int handle) { return c(-x(handle), -y(handle)); }
    public int scaled(int handle, double s) { return c(x(handle) * s, y(handle) * s); }
    public int normalized(int handle) {
        return isNormalized(handle) ? handle :  this.scaled(handle, 1/this.len(handle));
    }
    public int componentMap(int handle, Function<Double,Double> f) {
        return c(f.apply(x(handle)), f.apply(y(handle)));
    }
    public int floor(int handle) {
        return componentMap(handle, Math::floor);
    }
    public int turnByAngleOf(int handle, double rad) { 
        var sin_rad = Math.sin(rad);
        var cos_rad = Math.cos(rad);
        var x = x(handle);
        var y = y(handle);
        return c(x*cos_rad-y*sin_rad, x*sin_rad+y*cos_rad, isNormalized(handle));
    }
    public double distTo(int handle, int otherHandle){
        var dx = x(handle) - x(otherHandle);
        var dy = y(handle) - y(otherHandle);
        //    return Math.sqrt(dx * dx + dy * dy);
        return Math.hypot(dx, dy);
    }
    //public boolean isNear(Vec2 p, double r){ return distTo(p) <= r; }
    public boolean isNear(int handle, int otherHandle, double r){
        var dx = x(handle) - x(otherHandle);
        var dy = y(handle) - y(otherHandle);
        return dx*dx+dy*dy <= r*r; 
    }

    public double dot(int handle, int otherHandle) { return x(otherHandle)*x(handle)+y(otherHandle)*y(handle); }

    /**
     * perpedicular distance of the point represented by handle to the line represented by the handleB and handleM (line exuqtion y = Mx+B)
     * @param handle
     * @param handleB
     * @param handleM
     * @return
     */
    public double perpDistToLine(int handle, int handleB , int handleM ) {
        // d = (p-this)*n/len(n) where * is dot product and n is the normal to dir
        var n = rotateOrthogonallyCounterClockwize(handleM);
        var delta = sub(handleB, handle);
        var dp = dot(delta, n);
        var d = dp/len(n);
        return Math.abs(d);
    }

    public long clone(int handle) { return c(x(handle), y(handle)); }
    public boolean equals(int handle, int handleOther) {
        return x(handle) == x(handleOther) && y(handle) == y(handleOther);
    }
    public String toString(int handle) {
        return String.format("Vec2:[%.4f, %.4f]", x(handle), y(handle));
    }

}
