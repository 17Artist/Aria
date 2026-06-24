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

import priv.seventeen.artist.aria.db.dialect.SQLDialect;

import java.util.*;

public class Query {

    public enum Type { SELECT, INSERT, UPDATE, DELETE }

    private final Type type;
    private final Table table;
    private String[] selectColumns;
    private final List<String> whereClauses = new ArrayList<>();
    private final List<Object> whereParams = new ArrayList<>();
    private final Map<String, Object> setValues = new LinkedHashMap<>();
    private final Map<String, Object> insertValues = new LinkedHashMap<>();
    private String orderBy;
    private boolean orderDesc = false;
    private int limit = -1;
    private int offset = -1;
    // 聚合
    private String aggregateFunc;
    private String aggregateColumn;

    public Query(Type type, Table table) {
        this.type = type;
        this.table = table;
    }


    public static Query select(Table table) {
        return new Query(Type.SELECT, table);
    }

    public static Query select(Table table, String aggregateFunc, String column) {
        Query q = new Query(Type.SELECT, table);
        q.aggregateFunc = aggregateFunc;
        q.aggregateColumn = column;
        return q;
    }

    public Query columns(String... columns) {
        this.selectColumns = columns;
        return this;
    }


    public static Query insert(Table table) {
        return new Query(Type.INSERT, table);
    }

    public Query value(String column, Object val) {
        insertValues.put(column, val);
        return this;
    }

    public Query values(Map<String, Object> vals) {
        insertValues.putAll(vals);
        return this;
    }


    public static Query update(Table table) {
        return new Query(Type.UPDATE, table);
    }

    public Query set(String column, Object val) {
        setValues.put(column, val);
        return this;
    }


    public static Query delete(Table table) {
        return new Query(Type.DELETE, table);
    }


    public Query where(String clause, Object... params) {
        whereClauses.add(clause);
        whereParams.addAll(Arrays.asList(params));
        return this;
    }

    public Query whereEq(String column, Object value) {
        return where(column + " = ?", value);
    }

    public Query whereGt(String column, Object value) {
        return where(column + " > ?", value);
    }

    public Query whereLt(String column, Object value) {
        return where(column + " < ?", value);
    }


    public Query orderBy(String column) {
        this.orderBy = column;
        this.orderDesc = false;
        return this;
    }

    public Query orderByDesc(String column) {
        this.orderBy = column;
        this.orderDesc = true;
        return this;
    }

    public Query limit(int limit) {
        this.limit = limit;
        return this;
    }

    public Query offset(int offset) {
        this.offset = offset;
        return this;
    }


    public String toSQL(SQLDialect dialect) {
        return switch (type) {
            case SELECT -> buildSelect(dialect);
            case INSERT -> buildInsert(dialect);
            case UPDATE -> buildUpdate(dialect);
            case DELETE -> buildDelete(dialect);
        };
    }

    public List<Object> getParams() {
        return switch (type) {
            case SELECT, DELETE -> new ArrayList<>(whereParams);
            case INSERT -> new ArrayList<>(insertValues.values());
            case UPDATE -> {
                List<Object> params = new ArrayList<>(setValues.values());
                params.addAll(whereParams);
                yield params;
            }
        };
    }

    private String buildSelect(SQLDialect dialect) {
        StringBuilder sb = new StringBuilder("SELECT ");
        if (aggregateFunc != null) {
            sb.append(aggregateFunc).append("(");
            sb.append(aggregateColumn != null ? dialect.quoteIdentifier(aggregateColumn) : "*");
            sb.append(")");
        } else if (selectColumns != null && selectColumns.length > 0) {
            for (int i = 0; i < selectColumns.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(dialect.quoteIdentifier(selectColumns[i]));
            }
        } else {
            sb.append("*");
        }
        sb.append(" FROM ").append(dialect.quoteIdentifier(table.getName()));
        appendWhere(sb);
        if (orderBy != null) {
            sb.append(" ORDER BY ").append(dialect.quoteIdentifier(orderBy));
            if (orderDesc) sb.append(" DESC");
        }
        if (limit >= 0) sb.append(" LIMIT ").append(limit);
        if (offset >= 0) sb.append(" OFFSET ").append(offset);
        return sb.toString();
    }

    private String buildInsert(SQLDialect dialect) {
        StringBuilder sb = new StringBuilder("INSERT INTO ");
        sb.append(dialect.quoteIdentifier(table.getName())).append(" (");
        int i = 0;
        for (String col : insertValues.keySet()) {
            if (i++ > 0) sb.append(", ");
            sb.append(dialect.quoteIdentifier(col));
        }
        sb.append(") VALUES (");
        for (int j = 0; j < insertValues.size(); j++) {
            if (j > 0) sb.append(", ");
            sb.append("?");
        }
        sb.append(")");
        return sb.toString();
    }

    private String buildUpdate(SQLDialect dialect) {
        StringBuilder sb = new StringBuilder("UPDATE ");
        sb.append(dialect.quoteIdentifier(table.getName())).append(" SET ");
        int i = 0;
        for (String col : setValues.keySet()) {
            if (i++ > 0) sb.append(", ");
            sb.append(dialect.quoteIdentifier(col)).append(" = ?");
        }
        appendWhere(sb);
        return sb.toString();
    }

    private String buildDelete(SQLDialect dialect) {
        StringBuilder sb = new StringBuilder("DELETE FROM ");
        sb.append(dialect.quoteIdentifier(table.getName()));
        appendWhere(sb);
        return sb.toString();
    }

    private void appendWhere(StringBuilder sb) {
        if (!whereClauses.isEmpty()) {
            sb.append(" WHERE ");
            for (int i = 0; i < whereClauses.size(); i++) {
                if (i > 0) sb.append(" AND ");
                sb.append(whereClauses.get(i));
            }
        }
    }

    public Type getType() { return type; }
    public Table getTable() { return table; }
}
