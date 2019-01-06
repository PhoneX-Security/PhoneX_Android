package net.phonex.util.guava;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Guava Joiner replacement
 * Created by miroc on 7.12.14.
 */
public class Joiner {
    private String separator;

    private Joiner(String separator) {
        this.separator = separator;
    }

    public static Joiner on(String separator){
        return new Joiner(separator);
    }

    public String join(Iterable<?> parts){
        StringBuilder sb = new StringBuilder();

        Iterator<?> iterator = parts.iterator();
        while (iterator.hasNext()){
            sb.append(iterator.next().toString());
            if (iterator.hasNext()){
                sb.append(separator);
            }
        }

        return sb.toString();
    }

    public String join(Object[] parts){
        List<Object> list = new ArrayList<Object>();
        for (Object part : parts){
            list.add(part);
        }
        return join(list);
    }
}
