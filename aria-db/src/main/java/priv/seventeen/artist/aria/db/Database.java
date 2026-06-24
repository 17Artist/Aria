/*
 * Copyright 2026 17Artist
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package priv.seventeen.artist.aria.db;

import priv.seventeen.artist.aria.callable.CallableManager;
import priv.seventeen.artist.aria.callable.InvocationData;
import priv.seventeen.artist.aria.db.dialect.H2Dialect;
import priv.seventeen.artist.aria.db.dialect.MySQLDialect;
import priv.seventeen.artist.aria.db.dialect.SQLDialect;
import priv.seventeen.artist.aria.db.dialect.SQLiteDialect;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.exception.AriaRuntimeException;
import priv.seventeen.artist.aria.value.*;

import java.sql.*;
import java.util.*;


public class Database {

    private Connection connection;
    private SQLDialect dialect;

    public Database() {}

    public Database(Connection connection, SQLDialect dialect) {
        this.connection = connection;
        this.dialect = dialect;
    }

    public static Database connect(String type, String path) throws SQLException {
        Database db = new Database();
        db.dialect = switch (type.toLowerCase()) {
            case "sqlite" -> new SQLiteDialect();
            case "h2" -> new H2Dialect();
            case "mysql" -> new MySQLDialect();
            default -> throw new SQLException("Unsupported database type: " + type);
        };
        String url = switch (type.toLowerCase()) {
            case "sqlite" -> "jdbc:sqlite:" + path;
            case "h2" -> "jdbc:h2:" + path;
            case "mysql" -> path.startsWith("jdbc:") ? path : "jdbc:mysql://" + path;
            default -> throw new SQLException("Unsupported: " + type);
        };
        db.connection = DriverManager.getConnection(url);
        return db;
    }


    public static Database connect(String type, String path, String user, String password) throws SQLException {
        Database db = new Database();
        db.dialect = switch (type.toLowerCase()) {
            case "sqlite" -> new SQLiteDialect();
            case "h2" -> new H2Dialect();
            case "mysql" -> new MySQLDialect();
            default -> throw new SQLException("Unsupported database type: " + type);
        };
        String url = switch (type.toLowerCase()) {
            case "sqlite" -> "jdbc:sqlite:" + path;
            case "h2" -> "jdbc:h2:" + path;
            case "mysql" -> path.startsWith("jdbc:") ? path : "jdbc:mysql://" + path;
            default -> throw new SQLException("Unsupported: " + type);
        };
        db.connection = DriverManager.getConnection(url, user, password);
        return db;
    }

    public Connection getConnection() { return connection; }
    public SQLDialect getDialect() { return dialect; }

    public void createTable(Table table) throws SQLException {
        String sql = table.createSQL(dialect);
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    public void dropTable(Table table) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(table.dropSQL(dialect));
        }
    }

    public List<Map<String, Object>> execute(Query query) throws SQLException {
        String sql = query.toSQL(dialect);
        List<Object> params = query.getParams();

        if (query.getType() == Query.Type.SELECT) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                setParams(ps, params);
                ResultSet rs = ps.executeQuery();
                return resultSetToList(rs);
            }
        } else {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                setParams(ps, params);
                ps.executeUpdate();
                return Collections.emptyList();
            }
        }
    }

    public Transaction beginTransaction() throws SQLException {
        return new Transaction(connection);
    }

    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }


    public static void register(CallableManager manager) {
        manager.registerStaticFunction("db", "connect", Database::slConnect);
        manager.registerStaticFunction("db", "table", Database::slTable);
        manager.registerStaticFunction("db", "createTable", Database::slCreateTable);
        manager.registerStaticFunction("db", "dropTable", Database::slDropTable);
        manager.registerStaticFunction("db", "insert", Database::slInsert);
        manager.registerStaticFunction("db", "select", Database::slSelect);
        manager.registerStaticFunction("db", "update", Database::slUpdate);
        manager.registerStaticFunction("db", "delete", Database::slDelete);
        manager.registerStaticFunction("db", "execute", Database::slExecute);
        manager.registerStaticFunction("db", "transaction", Database::slTransaction);
        manager.registerStaticFunction("db", "close", Database::slClose);
        manager.aliasNamespace("db","DataBase");
    }

    private static IValue<?> slConnect(InvocationData data) throws AriaException {
        try {
            String type = data.get(0).stringValue();
            String path = data.get(1).stringValue();
            Database db;
            if (data.argCount() >= 4) {
                // db.connect('mysql', 'localhost:3306/dbname', 'user', 'password')
                String user = data.get(2).stringValue();
                String password = data.get(3).stringValue();
                db = Database.connect(type, path, user, password);
            } else {
                db = Database.connect(type, path);
            }
            return new StoreOnlyValue<>(db);
        } catch (SQLException e) {
            throw new AriaRuntimeException("db.connect error: " + e.getMessage());
        }
    }

    // db.table(dbConn, tableName, columns) — columns 是 Map {'name': 'text', 'age': 'integer'}
    private static IValue<?> slTable(InvocationData data) throws AriaException {
        try {
            Database db = extractDb(data.get(0));
            String tableName = data.get(1).stringValue();
            Table table = new Table(tableName);
            if (data.argCount() > 2 && data.get(2) instanceof MapValue mv) {
                for (Map.Entry<IValue<?>, IValue<?>> entry : mv.jvmValue().entrySet()) {
                    String colName = entry.getKey().stringValue();
                    String colType = entry.getValue().stringValue().toUpperCase();
                    Column col = new Column(colName, colType);
                    if (colType.contains("PRIMARY KEY")) {
                        col.primaryKey();
                        col = new Column(colName, colType.replace("PRIMARY KEY", "").trim());
                        col.primaryKey();
                    }
                    table.getColumns().add(col);
                }
            }
            db.createTable(table);
            // 返回 [db, tableName] 的包装
            LinkedHashMap<IValue<?>, IValue<?>> tableRef = new LinkedHashMap<>();
            tableRef.put(new StringValue("__db__"), data.get(0));
            tableRef.put(new StringValue("__table__"), new StringValue(tableName));
            return new MapValue(tableRef);
        } catch (SQLException e) {
            throw new AriaRuntimeException("db.table error: " + e.getMessage());
        }
    }

    // db.createTable(dbConn, tableName, columns)
    private static IValue<?> slCreateTable(InvocationData data) throws AriaException {
        return slTable(data);
    }

    // db.dropTable(dbConn, tableName)
    private static IValue<?> slDropTable(InvocationData data) throws AriaException {
        try {
            Database db = extractDb(data.get(0));
            String tableName = data.get(1).stringValue();
            Table table = new Table(tableName);
            db.dropTable(table);
            return NoneValue.NONE;
        } catch (SQLException e) {
            throw new AriaRuntimeException("db.dropTable error: " + e.getMessage());
        }
    }

    // db.insert(dbConn, tableName, data) — data 是 Map {'name': 'Alice', 'age': 25}
    private static IValue<?> slInsert(InvocationData data) throws AriaException {
        try {
            Database db = extractDb(data.get(0));
            String tableName = data.get(1).stringValue();
            if (!(data.get(2) instanceof MapValue mv)) {
                throw new AriaRuntimeException("db.insert: data must be a map");
            }
            Map<IValue<?>, IValue<?>> values = mv.jvmValue();
            StringBuilder sql = new StringBuilder("INSERT INTO ").append(tableName).append(" (");
            StringBuilder placeholders = new StringBuilder();
            List<Object> params = new ArrayList<>();
            int i = 0;
            for (Map.Entry<IValue<?>, IValue<?>> entry : values.entrySet()) {
                if (i > 0) { sql.append(", "); placeholders.append(", "); }
                sql.append(entry.getKey().stringValue());
                placeholders.append("?");
                params.add(iValueToJava(entry.getValue()));
                i++;
            }
            sql.append(") VALUES (").append(placeholders).append(")");
            try (PreparedStatement ps = db.connection.prepareStatement(sql.toString())) {
                db.setParams(ps, params);
                ps.executeUpdate();
            }
            return NoneValue.NONE;
        } catch (SQLException e) {
            throw new AriaRuntimeException("db.insert error: " + e.getMessage());
        }
    }

    // db.select(dbConn, tableName) 或 db.select(dbConn, tableName, where)
    private static IValue<?> slSelect(InvocationData data) throws AriaException {
        try {
            Database db = extractDb(data.get(0));
            String tableName = data.get(1).stringValue();
            StringBuilder sql = new StringBuilder("SELECT * FROM ").append(tableName);
            List<Object> params = new ArrayList<>();
            if (data.argCount() > 2 && data.get(2) instanceof MapValue where) {
                sql.append(" WHERE ");
                int i = 0;
                for (Map.Entry<IValue<?>, IValue<?>> entry : where.jvmValue().entrySet()) {
                    if (i > 0) sql.append(" AND ");
                    sql.append(entry.getKey().stringValue()).append(" = ?");
                    params.add(iValueToJava(entry.getValue()));
                    i++;
                }
            }
            try (PreparedStatement ps = db.connection.prepareStatement(sql.toString())) {
                db.setParams(ps, params);
                ResultSet rs = ps.executeQuery();
                return resultToIValue(db.resultSetToList(rs));
            }
        } catch (SQLException e) {
            throw new AriaRuntimeException("db.select error: " + e.getMessage());
        }
    }

    // db.update(dbConn, tableName, data, where)
    private static IValue<?> slUpdate(InvocationData data) throws AriaException {
        try {
            Database db = extractDb(data.get(0));
            String tableName = data.get(1).stringValue();
            if (!(data.get(2) instanceof MapValue setMap)) throw new AriaRuntimeException("db.update: data must be a map");
            StringBuilder sql = new StringBuilder("UPDATE ").append(tableName).append(" SET ");
            List<Object> params = new ArrayList<>();
            int i = 0;
            for (Map.Entry<IValue<?>, IValue<?>> entry : setMap.jvmValue().entrySet()) {
                if (i > 0) sql.append(", ");
                sql.append(entry.getKey().stringValue()).append(" = ?");
                params.add(iValueToJava(entry.getValue()));
                i++;
            }
            if (data.argCount() > 3 && data.get(3) instanceof MapValue where) {
                sql.append(" WHERE ");
                i = 0;
                for (Map.Entry<IValue<?>, IValue<?>> entry : where.jvmValue().entrySet()) {
                    if (i > 0) sql.append(" AND ");
                    sql.append(entry.getKey().stringValue()).append(" = ?");
                    params.add(iValueToJava(entry.getValue()));
                    i++;
                }
            }
            try (PreparedStatement ps = db.connection.prepareStatement(sql.toString())) {
                db.setParams(ps, params);
                int affected = ps.executeUpdate();
                return new NumberValue(affected);
            }
        } catch (SQLException e) {
            throw new AriaRuntimeException("db.update error: " + e.getMessage());
        }
    }

    // db.delete(dbConn, tableName, where)
    private static IValue<?> slDelete(InvocationData data) throws AriaException {
        try {
            Database db = extractDb(data.get(0));
            String tableName = data.get(1).stringValue();
            StringBuilder sql = new StringBuilder("DELETE FROM ").append(tableName);
            List<Object> params = new ArrayList<>();
            if (data.argCount() > 2 && data.get(2) instanceof MapValue where) {
                sql.append(" WHERE ");
                int i = 0;
                for (Map.Entry<IValue<?>, IValue<?>> entry : where.jvmValue().entrySet()) {
                    if (i > 0) sql.append(" AND ");
                    sql.append(entry.getKey().stringValue()).append(" = ?");
                    params.add(iValueToJava(entry.getValue()));
                    i++;
                }
            }
            try (PreparedStatement ps = db.connection.prepareStatement(sql.toString())) {
                db.setParams(ps, params);
                int affected = ps.executeUpdate();
                return new NumberValue(affected);
            }
        } catch (SQLException e) {
            throw new AriaRuntimeException("db.delete error: " + e.getMessage());
        }
    }

    // db.execute(dbConn, sql, params...)
    private static IValue<?> slExecute(InvocationData data) throws AriaException {
        try {
            Database db = extractDb(data.get(0));
            String sql = data.get(1).stringValue();
            List<Object> params = new ArrayList<>();
            for (int i = 2; i < data.argCount(); i++) {
                params.add(iValueToJava(data.get(i)));
            }
            try (PreparedStatement ps = db.connection.prepareStatement(sql)) {
                db.setParams(ps, params);
                if (sql.trim().toUpperCase().startsWith("SELECT")) {
                    return resultToIValue(db.resultSetToList(ps.executeQuery()));
                } else {
                    int affected = ps.executeUpdate();
                    return new NumberValue(affected);
                }
            }
        } catch (SQLException e) {
            throw new AriaRuntimeException("db.execute error: " + e.getMessage());
        }
    }

    // db.transaction(dbConn, fn)
    private static IValue<?> slTransaction(InvocationData data) throws AriaException {
        try {
            Database db = extractDb(data.get(0));
            FunctionValue fn = (FunctionValue) data.get(1);
            db.connection.setAutoCommit(false);
            try {
                fn.getCallable().invoke(new InvocationData(null, null, new IValue<?>[0]));
                db.connection.commit();
            } catch (Exception e) {
                db.connection.rollback();
                throw e;
            } finally {
                db.connection.setAutoCommit(true);
            }
            return NoneValue.NONE;
        } catch (SQLException e) {
            throw new AriaRuntimeException("db.transaction error: " + e.getMessage());
        }
    }

    // db.close(dbConn)
    private static IValue<?> slClose(InvocationData data) throws AriaException {
        try {
            Database db = extractDb(data.get(0));
            db.close();
            return NoneValue.NONE;
        } catch (SQLException e) {
            throw new AriaRuntimeException("db.close error: " + e.getMessage());
        }
    }

    private static Database extractDb(IValue<?> val) throws AriaException {
        if (val instanceof StoreOnlyValue<?> sov && sov.jvmValue() instanceof Database db) return db;
        throw new AriaRuntimeException("Expected database connection");
    }

    private static Object iValueToJava(IValue<?> val) {
        if (val instanceof NoneValue) return null;
        if (val instanceof NumberValue nv) {
            double d = nv.numberValue();
            return d == (long) d ? (long) d : d;
        }
        if (val instanceof BooleanValue bv) return bv.booleanValue();
        return val.stringValue();
    }

    private static IValue<?> wrapColumn(Column col) {
        return new StoreOnlyValue<>(col);
    }


    private void setParams(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object param = params.get(i);
            if (param == null) {
                ps.setNull(i + 1, Types.NULL);
            } else if (param instanceof String s) {
                ps.setString(i + 1, s);
            } else if (param instanceof Number n) {
                if (param instanceof Integer || param instanceof Long) {
                    ps.setLong(i + 1, n.longValue());
                } else {
                    ps.setDouble(i + 1, n.doubleValue());
                }
            } else if (param instanceof Boolean b) {
                ps.setBoolean(i + 1, b);
            } else if (param instanceof IValue<?> iv) {
                setIValueParam(ps, i + 1, iv);
            } else {
                ps.setObject(i + 1, param);
            }
        }
    }

    private void setIValueParam(PreparedStatement ps, int index, IValue<?> value) throws SQLException {
        if (value instanceof NoneValue) {
            ps.setNull(index, Types.NULL);
        } else if (value instanceof NumberValue nv) {
            double d = nv.numberValue();
            if (d == (long) d) ps.setLong(index, (long) d);
            else ps.setDouble(index, d);
        } else if (value instanceof BooleanValue bv) {
            ps.setBoolean(index, bv.booleanValue());
        } else {
            ps.setString(index, value.stringValue());
        }
    }

    private List<Map<String, Object>> resultSetToList(ResultSet rs) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>();
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= colCount; i++) {
                row.put(meta.getColumnLabel(i), rs.getObject(i));
            }
            results.add(row);
        }
        return results;
    }

    public static ListValue resultToIValue(List<Map<String, Object>> results) {
        List<IValue<?>> rows = new ArrayList<>();
        for (Map<String, Object> row : results) {
            LinkedHashMap<IValue<?>, IValue<?>> map = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                IValue<?> key = new StringValue(entry.getKey());
                IValue<?> val;
                Object v = entry.getValue();
                if (v == null) val = NoneValue.NONE;
                else if (v instanceof Number n) val = new NumberValue(n.doubleValue());
                else if (v instanceof Boolean b) val = BooleanValue.of(b);
                else val = new StringValue(v.toString());
                map.put(key, val);
            }
            rows.add(new MapValue(map));
        }
        return new ListValue(rows);
    }
}
