package com.etendoerp.publication.configuration

class EntryProjects<String, V> extends AbstractMap.SimpleEntry<String, V> {

    EntryProjects(String key, V value) {
        super(key, value)
    }

    @Override
    boolean equals(Object o) {
        if (!(o instanceof Map.Entry)) {
            return false
        }
        Map.Entry<?, ?> e = (Map.Entry)o

        return eq(this.key.toString().toLowerCase(), e.getKey().toString().toLowerCase())
    }

    static boolean eq(Object o1, Object o2) {
        return o1 == null ? o2 == null : o1 == o2
    }
}