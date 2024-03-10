package com.etendoerp.core

enum CoreType {
    SOURCES   ('SOURCES'),
    JAR       ('JAR'),
    UNDEFINED ('UNDEFINED')

    private final String type

    CoreType(String type) {
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

}