package pt.ulisboa.tecnico.hdsledger.utilities;

import java.util.Map.Entry;

public class Pair<T,K> {
    private T data1;
    private K data2;

    public Pair(T d1, K d2) {
        data1 = d1;
        data2 = d2;
    }

    public Pair(Entry<T, K> entry) {
        this(entry.getKey(), entry.getValue());
    }

    public T getFirst() {
        return data1;
    }

    public K getSecond() {
        return data2;
    }
}
