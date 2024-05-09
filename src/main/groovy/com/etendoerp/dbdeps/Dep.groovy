package com.etendoerp.dbdeps

class Dep {
    String group
    String artifact
    String version
    String depType
    // Constructor
    Dep(String group, String artifact, String version, String depType) {
        this.group = group
        this.artifact = artifact
        this.version = version
        this.depType = depType
    }

    // toString
    @Override
    String toString() {
        return "${group}:${artifact}:${version}"
    }
}
