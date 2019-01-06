package net.phonex.util.guava;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by miroc on 7.12.14.
 */
public class Lists {
    public static <E> ArrayList<E> newArrayList(E... elements) {
        ArrayList<E> list = new ArrayList<E>(elements.length);
        for(E el : elements){
            list.add(el);
        }
        return list;
    }

    public static <E> ArrayList<E> newArrayList() {
        ArrayList<E> list = new ArrayList<E>();
        return list;
    }

    public static <E> ArrayList<E> newArrayList(Collection<E> collection){
        ArrayList <E> list = new ArrayList<>(collection);
        return list;
    }
}
