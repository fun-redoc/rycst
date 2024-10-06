package de.rsh.utils;
import java.util.function.*;

public record Tupl3<T,S,R>(T t, S s, R r) {
    public T fst() { return t;}
    public S snd() { return s;}
    public R thr() { return r;}
    public <U,V,W> Tupl3<U, V, W> map(Function<Tupl3<T,S,R>, Tupl3<U,V,W>> cb) { return cb.apply(this); }
    @Override public boolean equals(Object o) {
        return switch (o) {
            case null -> false;
            case Tupl3<?,?,?> t -> this.t.equals(t.t) && this.s.equals(t.s) && this.s.equals(t.r);
            default -> false;
        };
    }
@Override
public int hashCode() {
    final int prime = 2147483647;
    int result = 1;
    result = prime * result + ((s == null) ? 0 : s.hashCode());
    result = prime * result + ((t == null) ? 0 : t.hashCode());
    result = prime * result + ((r == null) ? 0 : r.hashCode());
    return result;
}
}
