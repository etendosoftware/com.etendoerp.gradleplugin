package com.etendoerp.legacy.dependencies.container

enum DependencyType {
    MAVEN           ("MAVEN"),
    ETENDOJARMODULE ("ETENDOJARMODULE"),
    ETENDOZIPMODULE ("ETENDOZIPMODULE"),
    ETENDOCORE      ("ETENDOCORE")

    private final String type

    DependencyType(String type) {
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
