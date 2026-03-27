package org.puppylab.mypassword.rpc.response;

import java.util.List;

import org.puppylab.mypassword.rpc.BaseResponse;
import org.puppylab.mypassword.rpc.data.LoginItemData;

@Deprecated
public class LoginItemsDataResponse extends BaseResponse {

    public List<LoginItemData> data;
}
