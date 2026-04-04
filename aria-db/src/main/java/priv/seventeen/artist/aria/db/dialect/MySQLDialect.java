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

package priv.seventeen.artist.aria.db.dialect;

public class MySQLDialect implements SQLDialect {


    @Override public String name() { return "mysql"; }
    @Override public String autoIncrementKeyword() { return "AUTO_INCREMENT"; }
    @Override public String currentTimestampFunction() { return "CURRENT_TIMESTAMP"; }
    @Override public String quoteIdentifier(String id) { return "`" + id + "`"; }

    @Override
    public String columnDefinition(String name, String type, boolean primaryKey, boolean autoIncrement, boolean nullable, boolean unique, String defaultValue) {
        StringBuilder sb = new StringBuilder();
        sb.append(quoteIdentifier(name)).append(" ").append(type);
        if (!nullable && !primaryKey) sb.append(" NOT NULL");
        if (autoIncrement) sb.append(" ").append(autoIncrementKeyword());
        if (primaryKey) sb.append(" PRIMARY KEY");
        if (unique && !primaryKey) sb.append(" UNIQUE");
        if (defaultValue != null) sb.append(" DEFAULT ").append(defaultValue);
        return sb.toString();
    }
}
