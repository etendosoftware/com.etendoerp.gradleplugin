package com.etendoerp.operatingsystem.os

import com.etendoerp.operatingsystem.OSystem
import com.etendoerp.operatingsystem.OSystemMetadata

class Linux extends OSystemMetadata {

    Linux() {
        super(OSystem.LINUX, OSystem.LINUX.getSystem(), File.separator)
    }

}
