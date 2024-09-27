package de.rsh.rycst.utils;
import java.util.function.*;

public record Pair<T,S>(T t, S s) {
    public T fst() { return t;}
    public S snd() { return s;}
    public <U,V> Pair<U, V> map(Function<Pair<T,S>, Pair<U,V>> cb) { return cb.apply(this); }
    @Override public boolean equals(Object o) {
        return switch (o) {
            case null -> false;
            case Pair<?,?> t -> this.t.equals(t.t) && this.s.equals(t.s);
            default -> false;
        };
//        if(o == null || !(o instanceof Tuple<?,?>)) return false;
//        var tpl = (Tuple<?,?>)o;
//        return this.t.equals(tpl.t) && this.s.equals(tpl.s);
    }
    @Override
    public int hashCode() {
        final int prime = 2147483647;
        int result = 1;
        result = prime * result + ((s == null) ? 0 : s.hashCode());
        result = prime * result + ((t == null) ? 0 : t.hashCode());
        return result;
    }
    @Override
    public String toString() {
        return String.format("Pair:[%s,%s]", t,s );
    }
}
