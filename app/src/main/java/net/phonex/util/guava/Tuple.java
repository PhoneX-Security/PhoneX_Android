package net.phonex.util.guava;

/**
 * Created by miroc on 7.12.14.
 */
public class Tuple<A, B>{
    private final A first;
    private final B second;

    public static <A,B> Tuple<A, B> of(A first, B second){
        return new Tuple<A, B>(first, second);
    }

    private Tuple(A first, B second) {
        this.first = first;
        this.second = second;
    }

    public A getFirst() {
        return first;
    }

    public B getSecond() {
        return second;
    }

    @Override
    public String toString() {
        return "Tuple{" +
                "first=" + first +
                ", second=" + second +
                '}';
    }
}
