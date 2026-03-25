module org.puppylab.mypassword.cli {
    requires java.sql;

    requires org.jline;
    requires com.fasterxml.jackson.databind;

    requires org.puppylab.mypassword.rpc;
    requires java.net.http;
    requires rawhttp.core;

    opens org.puppylab.mypassword.cli to info.picocli;
}
