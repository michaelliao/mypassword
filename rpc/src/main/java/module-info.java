module org.puppylab.mypassword.rpc {
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.databind;

    exports org.puppylab.mypassword.rpc;
    exports org.puppylab.mypassword.rpc.util;
    exports org.puppylab.mypassword.rpc.data;
    exports org.puppylab.mypassword.rpc.request;
    exports org.puppylab.mypassword.rpc.response;

    opens org.puppylab.mypassword.rpc.request to com.fasterxml.jackson.databind;
    opens org.puppylab.mypassword.rpc.response to com.fasterxml.jackson.databind;
}
