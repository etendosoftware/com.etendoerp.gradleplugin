package com.etendoerp.operatingsystem

enum OSystem {

    IOS     ("ios"),
    LINUX   ("linux"),
    WINDOWS ("windows")

    private final String sys

    OSystem(String sys) {
        this.sys = sys
    }

    String getSystem() {
        return sys
    }

    static boolean containsSystem(String sys) {
        values()*.sys.contains(sys)
    }

    String toString() {
        sys
    }

}