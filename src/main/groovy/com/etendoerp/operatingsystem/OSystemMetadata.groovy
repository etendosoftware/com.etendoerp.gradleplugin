package com.etendoerp.operatingsystem

class OSystemMetadata {

    OSystem system
    String name
    String pathSeparator

    OSystemMetadata() {}

    OSystemMetadata(OSystem system, String name, String pathSeparator) {
        this.system = system;
        this.name = name
        this.pathSeparator = pathSeparator
    }

}
