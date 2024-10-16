package de.rsh.graph;

import java.util.function.*;
import java.lang.foreign.*;
import java.lang.invoke.VarHandle;

import de.rsh.utils.Pair;

public class Vec2Arena {
    private long size;
    VarHandle xHandle;
    VarHandle yHandle;
    VarHandle isNormalizedHandle;
    MemorySegment segment;
    long last = 0;
    public Vec2Arena(long size) {
        this.size = size;
        MemoryLayout vec2Layout = MemoryLayout.structLayout(ValueLayout.JAVA_DOUBLE.withName("x"),
                                                            ValueLayout.JAVA_DOUBLE.withName("y"),
                                                            ValueLayout.JAVA_BOOLEAN.withName("isNormalized"),
                                                            MemoryLayout.paddingLayout(7) // fills the alignment for the bool
                                                            );
        System.out.printf("size:%d, alignment:%d", vec2Layout.byteSize(), vec2Layout.byteAlignment());
        SequenceLayout arenaLayout = MemoryLayout.sequenceLayout(size, vec2Layout);
        this.xHandle = arenaLayout.varHandle(MemoryLayout.PathElement.sequenceElement(),
                                                  MemoryLayout.PathElement.groupElement("x"));
        this.yHandle = arenaLayout.varHandle(MemoryLayout.PathElement.sequenceElement(),
                                                  MemoryLayout.PathElement.groupElement("y"));
        this.isNormalizedHandle = arenaLayout.varHandle(MemoryLayout.PathElement.sequenceElement(),
                                                  MemoryLayout.PathElement.groupElement("isNormalized"));
        Arena arena = Arena.ofAuto();
        this.segment = arena.allocate(arenaLayout);

    }
    public long clearPoint() {
        return last;
    }
    public void clearFrom(long clearPoint) {
        last = clearPoint;
    }
    public void clear() {
        last = 0;
    }
    /**
     * creates new vector and stores the values in the arena
     * @param x
     * @param y
     * @return integer handle to the vector, dont mess araound with it, java urgently needs type aliases!!!
     */
    public long c(double x, double y) {
        return c(x,y,false);
    }
    private long c(double x, double y, boolean isNormalized) {
        xHandle.set(segment, last, x);
        yHandle.set(segment, last, y);
        isNormalizedHandle.set(segment, last, isNormalized);
        return last++;
    }
    public double x(long handle) {
        return (double) xHandle.get(segment, handle);
    }
    public double y(long handle) {
        return (double) yHandle.get(segment, handle);
    }
    private boolean isNormalized(long handle) {
        return (boolean) isNormalizedHandle.get(segment, handle);
    }
    public long zero() {return c(0,0);}
    public  <T extends Number> long fromPair(Pair<T,T> pair) { // this is strage, T should have the constraint to be integral, but there is non in java or is it?
        return c(pair.fst().doubleValue(), pair.snd().doubleValue());
    }
    public Pair<Integer,Integer> toInt(long handle) {
        var x_ = (int)x(handle);
        var y_ = (int)y(handle);
        return new Pair<>(x_, y_);
    }
    public <T> T map(long handle, BiFunction<Double, Double, T> f) { return 
        f.apply(x(handle), y(handle));
    }
    public long abs(long handle) {return c(Math.abs(x(handle)), Math.abs(handle));}
    public long add(long handle, long otherHandle) {return c(x(handle) + x(otherHandle), y(handle) + y(otherHandle));}
    public long sub(long handle, long otherHandle) {return c(x(handle) - x(otherHandle), y(handle) - y(otherHandle));}
    public long mul(long handle, long otherHandle) {return c(x(handle) * x(otherHandle), y(handle) * y(otherHandle));}
    public long div(long handle, long otherHandle) { 
        // beware division by 0 will crash
        return c(x(handle) / x(otherHandle), y(handle) / y(otherHandle));
    }
    public double norm(long handle) {
        var x = x(handle);
        var y = y(handle);
        return x*x + y*y;
    }
    public double len(long handle) {
        return  isNormalized(handle) ? 1 : Math.sqrt(norm(handle));
    }
    public long flippedHorizontally(long handle) {return c(x(handle), -y(handle), isNormalized(handle));}
    public long flippedVertically(long handle) {return c(-x(handle), y(handle), isNormalized(handle));}
    public long rotateOrthogonallyCounterClockwize(long handle) {return c(-y(handle), x(handle), isNormalized(handle));}
    public long rotateOrthogonallyClockwize(long handle) {return c(y(handle), -x(handle), isNormalized(handle));}
    public long movedBy(long handle, long otherHandle) { return c(x(handle) + x(otherHandle), y(handle) + y(otherHandle)); }
    public long movedByXY(long handle, double dx, double dy) { return c(x(handle) + dx, y(handle) + dy); }
    public long neg(long handle) { return c(-x(handle), -y(handle)); }
    public long scaled(long handle, double s) { return c(x(handle) * s, y(handle) * s); }
    public long normalized(long handle) {
        return isNormalized(handle) ? handle :  this.scaled(handle, 1/this.len(handle));
    }
    public long componentMap(long handle, Function<Double,Double> f) {
        return c(f.apply(x(handle)), f.apply(y(handle)));
    }
    public long floor(long handle) {
        return componentMap(handle, Math::floor);
    }
    public long turnByAngleOf(long handle, double rad) { 
        var sin_rad = Math.sin(rad);
        var cos_rad = Math.cos(rad);
        var x = x(handle);
        var y = y(handle);
        return c(x*cos_rad-y*sin_rad, x*sin_rad+y*cos_rad, isNormalized(handle));
    }
    public double distTo(long handle, long otherHandle){
        var dx = x(handle) - x(otherHandle);
        var dy = y(handle) - y(otherHandle);
        //    return Math.sqrt(dx * dx + dy * dy);
        return Math.hypot(dx, dy);
    }
    //public boolean isNear(Vec2 p, double r){ return distTo(p) <= r; }
    public boolean isNear(long handle, int otherHandle, double r){
        var dx = x(handle) - x(otherHandle);
        var dy = y(handle) - y(otherHandle);
        return dx*dx+dy*dy <= r*r; 
    }

    public double dot(long handle, long otherHandle) { return x(otherHandle)*x(handle)+y(otherHandle)*y(handle); }

    /**
     * perpedicular distance of the point represented by handle to the line represented by the handleB and handleM (line exuqtion y = Mx+B)
     * @param handle
     * @param handleB
     * @param handleM
     * @return
     */
    public double perpDistToLine(long handle, long handleB , long handleM ) {
        // d = (p-this)*n/len(n) where * is dot product and n is the normal to dir
        var n = rotateOrthogonallyCounterClockwize(handleM);
        var delta = sub(handleB, handle);
        var dp = dot(delta, n);
        var d = dp/len(n);
        return Math.abs(d);
    }

    public long clone(long handle) { return c(x(handle), y(handle)); }
    public boolean equals(long handle, long handleOther) {
        return x(handle) == x(handleOther) && y(handle) == y(handleOther);
    }
    public String toString(long handle) {
        return String.format("Vec2:[%.4f, %.4f]", x(handle), y(handle));
    }

}
