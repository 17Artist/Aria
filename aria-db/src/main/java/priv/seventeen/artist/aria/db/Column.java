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

public class Column {
    private final String name;
    private final String sqlType;
    private boolean primaryKey = false;
    private boolean autoIncrement = false;
    private boolean nullable = false;
    private boolean unique = false;
    private String defaultValue = null;

    public Column(String name, String sqlType) {
        this.name = name;
        this.sqlType = sqlType;
    }

    public Column primaryKey() { this.primaryKey = true; return this; }
    public Column autoIncrement() { this.autoIncrement = true; return this; }
    public Column nullable() { this.nullable = true; return this; }
    public Column unique() { this.unique = true; return this; }
    public Column defaultValue(String value) { this.defaultValue = value; return this; }
    public Column defaultNow() { this.defaultValue = "CURRENT_TIMESTAMP"; return this; }

    public String getName() { return name; }
    public String getSqlType() { return sqlType; }
    public boolean isPrimaryKey() { return primaryKey; }
    public boolean isAutoIncrement() { return autoIncrement; }
    public boolean isNullable() { return nullable; }
    public boolean isUnique() { return unique; }
    public String getDefaultValue() { return defaultValue; }
}
