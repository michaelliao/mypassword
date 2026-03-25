package org.puppylab.mypassword.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.puppylab.mypassword.core.entity.VaultConfig;
import org.puppylab.mypassword.core.entity.VaultVersion;
import org.puppylab.mypassword.core.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

public class DbManager implements AutoCloseable {

    final Logger                 logger      = LoggerFactory.getLogger(getClass());
    final Map<Class<?>, Mapping> ormMappings = new HashMap<>();

    Connection connection;

    public DbManager(Path dbFile) {
        String jdbcUrl = "jdbc:sqlite:" + dbFile.toUri().getPath();
        logger.info("set jdbc url: {}", jdbcUrl);
        boolean shouldInitDb = !Files.isRegularFile(dbFile);
        try {
            this.connection = DriverManager.getConnection(jdbcUrl);
            this.connection.setAutoCommit(true);
        } catch (SQLException e) {
            throw new DataAccessException(e);
        }
        // 优化 SQLite 性能
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL;");
            stmt.execute("PRAGMA synchronous=NORMAL;");
        } catch (SQLException e) {
            throw new DataAccessException(e);
        }
        if (shouldInitDb) {
            logger.info("init db...");
            String sqlText;
            try (var reader = new BufferedReader(
                    new InputStreamReader(getClass().getResourceAsStream("/init/1.sql"), StandardCharsets.UTF_8))) {
                sqlText = reader.lines().map(line -> line.replaceAll("--.*", "")).collect(Collectors.joining(" "));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            final String fullSql = sqlText.replaceAll("\s+", " ");
            logger.info("loaded sql: {}", fullSql);

            this.tx(() -> {
                for (String sql : fullSql.split(";")) {
                    if (!sql.trim().isEmpty()) {
                        execute(sql);
                    }
                }
            });
        }
    }

    public VaultVersion queryVaultVersion() {
        return queryUnique(VaultVersion.class, "WHERE id = ?", 1);
    }

    public VaultConfig queryVaultConfig() {
        return queryFirst(VaultConfig.class, "WHERE id = ?", 1);
    }

    public <T> T queryFirst(Class<T> clazz, String where, Object... args) {
        return queryForObject(true, clazz, where, args);
    }

    public <T> T queryUnique(Class<T> clazz, String where, Object... args) {
        T obj = queryForObject(false, clazz, where, args);
        if (obj == null) {
            throw new IllegalStateException("No result set.");
        }
        return obj;
    }

    <T> T queryForObject(boolean allowMultipleResults, Class<T> clazz, String where, Object... args) {
        String sql = "SELECT * FROM " + clazz.getSimpleName() + " " + where;
        logger.info("sql: {}", sql);
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                T obj = createObject(clazz.getConstructor(), rs);
                if (rs.next()) {
                    if (!allowMultipleResults) {
                        throw new IllegalStateException("Non unique result set.");
                    }
                }
                return obj;
            }
        } catch (SQLException e) {
            throw new DataAccessException(e);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> List<T> queryForList(Class<T> clazz, String where, Object... args) {
        String sql = "SELECT * FROM " + clazz.getSimpleName() + " " + where;
        logger.info("sql: {}", sql);
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<T> list = new ArrayList<>();
                Constructor<T> constructor = clazz.getConstructor();
                while (rs.next()) {
                    T obj = createObject(constructor, rs);
                    list.add(obj);
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException(e);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> void insert(T obj) {
        Class<?> clazz = obj.getClass();
        Mapping mapping = getMapping(clazz);
        String sql = mapping.insertSql;
        logger.info("insert: {}", sql);
        try (PreparedStatement ps = connection.prepareStatement(sql,
                mapping.idAutoIncrement ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS)) {
            for (int i = 0; i < mapping.insertFields.length; i++) {
                String fieldName = mapping.insertFields[i];
                Field field = mapping.fields.get(fieldName);
                Object value = field.get(obj);
                ps.setObject(i + 1, value);
            }
            ps.executeUpdate();
            if (mapping.idAutoIncrement) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        long key = rs.getLong(1);
                        mapping.idField.set(obj, key);
                    } else {
                        throw new RuntimeException("No generated keys.");
                    }
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException(e);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public void update(Object obj, String... fields) {
        Class<?> clazz = obj.getClass();
        Mapping mapping = getMapping(clazz);
        StringBuilder sb = new StringBuilder(128);
        sb.append("UPDATE ").append(clazz.getSimpleName()).append(" SET ");
        Object[] values = new Object[fields.length];
        try {
            for (int i = 0; i < fields.length; i++) {
                String field = fields[i];
                sb.append(field).append(" = ?,");
                values[i] = mapping.fields.get(field).get(obj);
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        String sql = sb.toString();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                ps.setObject(i + 1, values[i]);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException(e);
        }
    }

    public void tx(Runnable r) {
        try {
            this.connection.setAutoCommit(false);
            r.run();
            this.connection.commit();
        } catch (DataAccessException e) {
            try {
                this.connection.rollback();
            } catch (SQLException er) {
                logger.error("rollback failed.", er);
            }
            throw e;
        } catch (SQLException e) {
            try {
                this.connection.rollback();
            } catch (SQLException er) {
                logger.error("rollback failed.", er);
            }
            throw new DataAccessException(e);
        } finally {
            try {
                this.connection.setAutoCommit(true);
            } catch (SQLException e) {
                logger.error("set auto commit = true failed.", e);
            }
        }
    }

    public void execute(String sql) {
        logger.info("executing SQL: {}", sql);
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            throw new DataAccessException(e);
        }
    }

    public void execute(String sql, Object... args) {
        logger.info("executing SQL: {}", sql);
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            ps.executeUpdate(sql);
        } catch (SQLException e) {
            throw new DataAccessException(e);
        }
    }

    @Override
    public void close() {
        try {
            this.connection.close();
        } catch (SQLException e) {
            logger.error("failed close db.", e);
        }
    }

    <T> T createObject(Constructor<T> constructor, ResultSet rs) throws ReflectiveOperationException, SQLException {
        T obj = constructor.newInstance();
        Mapping mapping = getMapping(constructor.getDeclaringClass());
        for (String name : mapping.fields.keySet()) {
            Field f = mapping.fields.get(name);
            Class<?> t = f.getType();
            if (t == long.class || t == Long.class) {
                f.set(obj, rs.getLong(name));
            } else if (t == int.class || t == Integer.class) {
                f.set(obj, rs.getInt(name));
            } else if (t == boolean.class || t == Boolean.class) {
                f.set(obj, rs.getBoolean(name));
            } else if (t == String.class) {
                f.set(obj, rs.getString(name));
            } else {
                throw new IllegalArgumentException("Unsupported type: " + t);
            }
        }
        return obj;
    }

    Mapping getMapping(Class<?> clazz) {
        Mapping mapping = this.ormMappings.get(clazz);
        if (mapping == null) {
            mapping = new Mapping(clazz);
            this.ormMappings.put(clazz, mapping);
        }
        return mapping;
    }
}

class Mapping {
    final boolean            idAutoIncrement;
    final String             idName;
    final Field              idField;
    final String[]           insertFields;   // may exclude id
    final Map<String, Field> fields;         // include id

    final String querySql;
    final String queryById;
    final String insertSql;

    Mapping(Class<?> clazz) {
        Map<String, Field> fields = new HashMap<>();
        Field idField = null;
        boolean idAutoIncrement = false;
        for (Field f : clazz.getFields()) {
            int mod = f.getModifiers();
            if (Modifier.isFinal(mod) || Modifier.isStatic(mod)) {
                continue;
            }
            fields.put(f.getName(), f);
            if (f.isAnnotationPresent(Id.class)) {
                if (idField != null) {
                    throw new RuntimeException("Duplicate @Id defined in " + clazz);
                }
                idField = f;
            }
        }
        if (idField == null) {
            throw new RuntimeException("No @Id defined in " + clazz);
        }
        this.idAutoIncrement = idField.isAnnotationPresent(GeneratedValue.class);
        this.idName = idField.getName();
        this.idField = idField;
        this.fields = fields;
        Set<String> fieldNames = new HashSet<>(fields.keySet());
        if (idAutoIncrement) {
            fieldNames.remove(idName);
        }
        this.insertFields = fieldNames.toArray(String[]::new);

        this.querySql = "SELECT * FROM " + clazz.getSimpleName();
        this.queryById = this.querySql + " WHERE " + idName + " = ?";

        this.insertSql = "INSERT INTO " + clazz.getSimpleName() + " (" + String.join(", ", this.insertFields)
                + ") VALUES (" + "?, ".repeat(this.insertFields.length - 1) + "?)";
    }
}
