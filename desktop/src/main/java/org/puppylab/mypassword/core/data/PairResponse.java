package org.puppylab.mypassword.core.data;

import org.puppylab.mypassword.rpc.BaseResponse;

public class PairResponse extends BaseResponse {

    public static class PairResponseData {
        public long   id;
        public String seed;
    }

    public PairResponseData data;

}
