module org.puppylab.mypassword.core {
    requires java.sql;

    requires com.fasterxml.jackson.databind;
    requires jakarta.persistence;
    requires org.slf4j;
    requires rawhttp.core;

    requires org.puppylab.mypassword.rpc;
}
