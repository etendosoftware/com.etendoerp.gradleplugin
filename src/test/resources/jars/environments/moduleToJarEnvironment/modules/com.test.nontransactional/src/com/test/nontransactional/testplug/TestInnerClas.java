package com.test.nontransactional.testplug;

import javax.sql.DataSource;
import java.io.Serializable;

public class TestInnerClas {

//    public static class Inner {
//
//    }

    static class PoolInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        public String name = null;
        public DataSource ds = null;
        public String rdbms = null;
        public String dbSession = null;

        public PoolInfo(String name, DataSource ds, String rdbms, String dbSession) {
            this.name = name;
            this.ds = ds;
            this.rdbms = rdbms;
            this.dbSession = dbSession;
        }

        public static String test() {
            return "test";
        }
    }

    private static class ClusterServiceThread implements Runnable {

        @Override
        public void run() {

        }
    }

}



class PoolInfo_1 implements Serializable {
    private static final long serialVersionUID = 1L;
    public String name = null;
    public DataSource ds = null;
    public String rdbms = null;
    public String dbSession = null;

    public PoolInfo_1() {}

    public PoolInfo_1(String name, DataSource ds, String rdbms, String dbSession) {
        this.name = name;
        this.ds = ds;
        this.rdbms = rdbms;
        this.dbSession = dbSession;
    }
}
