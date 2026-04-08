package org.puppylab.mypassword.rpc.response;

import org.puppylab.mypassword.rpc.BaseResponse;

public class InfoResponse extends BaseResponse {

    public static class InfoData {

        // database location:
        public String database;

        // is vault initialized?
        public boolean initialized;

        // is vault locked?
        public boolean locked;

        public int appVersion;

        public int dataVersion;

        // vault settings: public Map<String,String> settings;
    }

    public InfoData data;
}
