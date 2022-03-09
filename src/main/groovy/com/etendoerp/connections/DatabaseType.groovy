package com.etendoerp.connections

enum DatabaseType {
    POSTGRE    ("POSTGRE"),
    ORACLE     ("ORACLE"),
    UNDEFINED  ("UNDEFINED")

    private final String type

    DatabaseType(String type) {
        this.type = type
    }

    String getType() {
        return type
    }

    static boolean containsType(String type) {
        values()*.type.contains(type)
    }

    String toString() {
        type
    }

    static DatabaseType parseType(String type) {
        switch (type) {
            case POSTGRE.type:
                return POSTGRE
                break
            case ORACLE.type:
                return ORACLE
                break
            default:
                return UNDEFINED
        }
    }

}