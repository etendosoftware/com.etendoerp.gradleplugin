package com.etendoerp.consistency

enum VersionStatus {
    MAJOR     ("MAJOR"),
    MINOR     ("MINOR"),
    EQUAL     ("EQUAL"),
    UNDEFINED ("UNDEFINED")

    private final String type

    VersionStatus(String type) {
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