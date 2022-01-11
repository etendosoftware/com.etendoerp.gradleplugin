package com.etendoerp.core

enum CoreStatus {
    RESOLVED ("RESOLVED"),
    UNRESOLVED ("UNRESOLVED"),
    TOBERESOLVED ("TOBERESOLVED")

    private final String status

    CoreStatus(String status) {
        this.status = status
    }

    String getStatus() {
        return status
    }

    static boolean containsStatus(String status) {
        values()*.status.contains(status)
    }

    String toString() {
        status
    }

}
