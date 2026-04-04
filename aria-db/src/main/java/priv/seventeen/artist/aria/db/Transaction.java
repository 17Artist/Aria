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

import java.sql.Connection;
import java.sql.SQLException;

public class Transaction implements AutoCloseable {
    private final Connection connection;
    private boolean committed = false;

    public Transaction(Connection connection) throws SQLException {
        this.connection = connection;
        this.connection.setAutoCommit(false);
    }

    public Connection getConnection() { return connection; }

    public void commit() throws SQLException {
        connection.commit();
        committed = true;
    }

    public void rollback() throws SQLException {
        if (!committed) {
            connection.rollback();
        }
    }

    @Override
    public void close() throws SQLException {
        try {
            if (!committed) {
                connection.rollback();
            }
        } finally {
            connection.setAutoCommit(true);
        }
    }
}
