package com.etendoerp.operatingsystem

import com.etendoerp.operatingsystem.os.IOS
import com.etendoerp.operatingsystem.os.Linux
import com.etendoerp.operatingsystem.os.Windows

class OSystemMap {

    static HashMap<OSystem, OSystemMetadata> map;

    static {
        map.put(OSystem.LINUX, new Linux())
        map.put(OSystem.WINDOWS, new Windows())
        map.put(OSystem.IOS, new IOS())
    }

}
