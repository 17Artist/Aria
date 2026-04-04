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

import java.util.ArrayList;
import java.util.List;

public class Table {
    private final String name;
    private final List<Column> columns = new ArrayList<>();

    public Table(String name) {
        this.name = name;
    }

    public Column integer(String name) {
        Column col = new Column(name, "INTEGER");
        columns.add(col);
        return col;
    }

    public Column bigint(String name) {
        Column col = new Column(name, "BIGINT");
        columns.add(col);
        return col;
    }

    public Column real(String name) {
        Column col = new Column(name, "REAL");
        columns.add(col);
        return col;
    }

    public Column varchar(String name, int length) {
        Column col = new Column(name, "VARCHAR(" + length + ")");
        columns.add(col);
        return col;
    }

    public Column text(String name) {
        Column col = new Column(name, "TEXT");
        columns.add(col);
        return col;
    }

    public Column bool(String name) {
        Column col = new Column(name, "BOOLEAN");
        columns.add(col);
        return col;
    }

    public Column timestamp(String name) {
        Column col = new Column(name, "TIMESTAMP");
        columns.add(col);
        return col;
    }

    public Column blob(String name) {
        Column col = new Column(name, "BLOB");
        columns.add(col);
        return col;
    }

    public String getName() { return name; }
    public List<Column> getColumns() { return columns; }

    public String createSQL(SQLDialect dialect) {
        StringBuilder sb = new StringBuilder("CREATE TABLE IF NOT EXISTS ");
        sb.append(dialect.quoteIdentifier(name)).append(" (\n");
        for (int i = 0; i < columns.size(); i++) {
            Column col = columns.get(i);
            if (i > 0) sb.append(",\n");
            sb.append("  ").append(dialect.columnDefinition(
                    col.getName(), col.getSqlType(),
                    col.isPrimaryKey(), col.isAutoIncrement(),
                    col.isNullable(), col.isUnique(), col.getDefaultValue()
            ));
        }
        sb.append("\n)");
        return sb.toString();
    }

    public String dropSQL(SQLDialect dialect) {
        return "DROP TABLE IF EXISTS " + dialect.quoteIdentifier(name);
    }
}
