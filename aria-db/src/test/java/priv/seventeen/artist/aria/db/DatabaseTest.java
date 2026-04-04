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

import org.junit.jupiter.api.*;
import priv.seventeen.artist.aria.Aria;
import priv.seventeen.artist.aria.callable.CallableManager;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.context.VariableKey;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.StringValue;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DatabaseTest {

    private static Path sqliteFile;

    @BeforeAll
    static void setup() throws Exception {
        Aria.getEngine().initialize();
        Database.register(CallableManager.INSTANCE);
        sqliteFile = Files.createTempFile("aria-db-test", ".db");
    }

    @AfterAll
    static void cleanup() throws Exception {
        try { Files.deleteIfExists(sqliteFile); } catch (Exception ignored) {}
    }

    private IValue<?> eval(String code) throws AriaException {
        Context ctx = Aria.createContext();
        return Aria.eval(code, ctx);
    }

    private IValue<?> eval(String code, Context ctx) throws AriaException {
        return Aria.eval(code, ctx);
    }


    @Test
    @Order(1)
    void testSQLiteConnect() throws AriaException {
        Context ctx = Aria.createContext();
        ctx.getGlobalStorage().getGlobalVariable(VariableKey.of("dbPath")
        ).setValue(new StringValue(sqliteFile.toString()));

        IValue<?> result = eval("""
            var.conn = db.connect('sqlite', global.dbPath)
            return type.typeof(conn)
            """, ctx);
        assertEquals("store", result.stringValue());
    }

    @Test
    @Order(2)
    void testSQLiteCreateTableAndInsert() throws AriaException {
        Context ctx = Aria.createContext();
        ctx.getGlobalStorage().getGlobalVariable(VariableKey.of("dbPath")
        ).setValue(new StringValue(sqliteFile.toString()));

        eval("""
            var.conn = db.connect('sqlite', global.dbPath)
            db.execute(conn, 'CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, age INTEGER)')
            db.execute(conn, 'INSERT INTO users (name, age) VALUES (?, ?)', 'Alice', 25)
            db.execute(conn, 'INSERT INTO users (name, age) VALUES (?, ?)', 'Bob', 30)
            db.close(conn)
            """, ctx);
    }

    @Test
    @Order(3)
    void testSQLiteSelect() throws AriaException {
        Context ctx = Aria.createContext();
        ctx.getGlobalStorage().getGlobalVariable(VariableKey.of("dbPath")
        ).setValue(new StringValue(sqliteFile.toString()));

        IValue<?> result = eval("""
            var.conn = db.connect('sqlite', global.dbPath)
            var.rows = db.execute(conn, 'SELECT * FROM users')
            db.close(conn)
            return rows.size()
            """, ctx);
        assertEquals(2.0, result.numberValue());
    }

    @Test
    @Order(4)
    void testSQLiteInsertWithMap() throws AriaException {
        Context ctx = Aria.createContext();
        ctx.getGlobalStorage().getGlobalVariable(VariableKey.of("dbPath")
        ).setValue(new StringValue(sqliteFile.toString()));

        eval("""
            var.conn = db.connect('sqlite', global.dbPath)
            db.insert(conn, 'users', {'name': 'Charlie', 'age': 35})
            db.close(conn)
            """, ctx);

        // 验证
        IValue<?> result = eval("""
            var.conn = db.connect('sqlite', global.dbPath)
            var.rows = db.execute(conn, 'SELECT * FROM users WHERE name = ?', 'Charlie')
            db.close(conn)
            return rows.size()
            """, ctx);
        assertEquals(1.0, result.numberValue());
    }

    @Test
    @Order(5)
    void testSQLiteUpdate() throws AriaException {
        Context ctx = Aria.createContext();
        ctx.getGlobalStorage().getGlobalVariable(VariableKey.of("dbPath")
        ).setValue(new StringValue(sqliteFile.toString()));

        IValue<?> result = eval("""
            var.conn = db.connect('sqlite', global.dbPath)
            var.affected = db.update(conn, 'users', {'age': 26}, {'name': 'Alice'})
            db.close(conn)
            return affected
            """, ctx);
        assertEquals(1.0, result.numberValue());
    }

    @Test
    @Order(6)
    void testSQLiteDelete() throws AriaException {
        Context ctx = Aria.createContext();
        ctx.getGlobalStorage().getGlobalVariable(VariableKey.of("dbPath")
        ).setValue(new StringValue(sqliteFile.toString()));

        IValue<?> result = eval("""
            var.conn = db.connect('sqlite', global.dbPath)
            var.affected = db.delete(conn, 'users', {'name': 'Charlie'})
            db.close(conn)
            return affected
            """, ctx);
        assertEquals(1.0, result.numberValue());
    }

    @Test
    @Order(7)
    void testSQLiteTransaction() throws AriaException {
        Context ctx = Aria.createContext();
        ctx.getGlobalStorage().getGlobalVariable(VariableKey.of("dbPath")
        ).setValue(new StringValue(sqliteFile.toString()));

        eval("""
            var.conn = db.connect('sqlite', global.dbPath)
            db.transaction(conn, -> {
                db.execute(conn, 'INSERT INTO users (name, age) VALUES (?, ?)', 'Dave', 40)
                db.execute(conn, 'INSERT INTO users (name, age) VALUES (?, ?)', 'Eve', 28)
            })
            db.close(conn)
            """, ctx);

        IValue<?> result = eval("""
            var.conn = db.connect('sqlite', global.dbPath)
            var.rows = db.execute(conn, 'SELECT * FROM users')
            db.close(conn)
            return rows.size()
            """, ctx);
        assertEquals(4.0, result.numberValue()); // Alice, Bob, Dave, Eve
    }

    @Test
    @Order(8)
    void testSQLiteSelectWithMap() throws AriaException {
        Context ctx = Aria.createContext();
        ctx.getGlobalStorage().getGlobalVariable(VariableKey.of("dbPath")
        ).setValue(new StringValue(sqliteFile.toString()));

        IValue<?> result = eval("""
            var.conn = db.connect('sqlite', global.dbPath)
            var.rows = db.select(conn, 'users', {'name': 'Alice'})
            db.close(conn)
            return rows.size()
            """, ctx);
        assertEquals(1.0, result.numberValue());
    }


    @Test
    @Order(20)
    void testMySQLConnectAndCRUD() throws AriaException {
        Context ctx = Aria.createContext();
        try {
            String password = System.getenv("ARIA_TEST_MYSQL_PASSWORD");
            if (password == null) password = "test";
            ctx.getGlobalStorage().getGlobalVariable(VariableKey.of("mysqlPwd")
            ).setValue(new StringValue(password));


            eval("""
                var.conn = db.connect('mysql', 'localhost:3306/?useSSL=false&allowPublicKeyRetrieval=true', 'root', global.mysqlPwd)
                db.execute(conn, 'CREATE DATABASE IF NOT EXISTS aria_test')
                db.close(conn)
                """, ctx);


            eval("""
                var.conn = db.connect('mysql', 'localhost:3306/aria_test?useSSL=false&allowPublicKeyRetrieval=true', 'root', global.mysqlPwd)
                db.execute(conn, 'CREATE TABLE IF NOT EXISTS aria_test_table (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100), score INT)')
                db.execute(conn, 'DELETE FROM aria_test_table')
                db.insert(conn, 'aria_test_table', {'name': 'Alice', 'score': 95})
                db.insert(conn, 'aria_test_table', {'name': 'Bob', 'score': 87})
                var.rows = db.select(conn, 'aria_test_table')
                global.count = rows.size()
                db.update(conn, 'aria_test_table', {'score': 100}, {'name': 'Alice'})
                db.delete(conn, 'aria_test_table', {'name': 'Bob'})
                var.afterDelete = db.select(conn, 'aria_test_table')
                global.afterDelete = afterDelete.size()
                db.execute(conn, 'DROP TABLE IF EXISTS aria_test_table')
                db.close(conn)
                """, ctx);

            double count = ctx.getGlobalStorage().getGlobalVariable(VariableKey.of("count")
            ).getValue().numberValue();
            assertEquals(2.0, count);

            double afterDelete = ctx.getGlobalStorage().getGlobalVariable(VariableKey.of("afterDelete")
            ).getValue().numberValue();
            assertEquals(1.0, afterDelete);


            eval("""
                var.conn = db.connect('mysql', 'localhost:3306/?useSSL=false&allowPublicKeyRetrieval=true', 'root', global.mysqlPwd)
                db.execute(conn, 'DROP DATABASE IF EXISTS aria_test')
                db.close(conn)
                """, ctx);

        } catch (AriaException e) {
            if (e.getMessage() != null && (e.getMessage().contains("Communications link failure")
                    || e.getMessage().contains("Connection refused")
                    || e.getMessage().contains("Access denied"))) {
                System.out.println("[SKIP] MySQL not available: " + e.getMessage());
                return;
            }
            throw e;
        }
    }
}
