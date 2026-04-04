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

package priv.seventeen.artist.aria;

import org.junit.jupiter.api.Test;
import priv.seventeen.artist.aria.annotation.AnnotationRegistry;
import priv.seventeen.artist.aria.annotation.AnnotationRegistry.AnnotatedTarget;
import priv.seventeen.artist.aria.annotation.AriaAnnotation;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.value.IValue;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotationTest {

    private IValue<?> eval(String code) throws AriaException {
        Context ctx = Aria.createContext();
        var unit = Aria.compile("test", ctx, code, Aria.Mode.ARIA);
        return unit.execute();
    }

    private IValue<?> evalJS(String code) throws AriaException {
        Context ctx = Aria.createContext();
        var unit = Aria.compile("test.js", ctx, code, Aria.Mode.JAVASCRIPT);
        return unit.execute();
    }


    @Test
    void testClassAnnotation() throws AriaException {
        AnnotationRegistry registry = Aria.getEngine().getAnnotationRegistry();
        registry.clear();

        eval("""
            @entity
            class User {
                var.name = 'unknown'
            }
            """);

        List<AnnotatedTarget> entities = registry.findClassesByAnnotation("entity");
        assertEquals(1, entities.size());
        assertEquals("User", entities.get(0).name());
    }

    @Test
    void testClassAnnotationWithArgs() throws AriaException {
        AnnotationRegistry registry = Aria.getEngine().getAnnotationRegistry();
        registry.clear();

        eval("""
            @table('users')
            class User {
                var.name = 'unknown'
            }
            """);

        List<AnnotatedTarget> tables = registry.findClassesByAnnotation("table");
        assertEquals(1, tables.size());
        AriaAnnotation ann = tables.get(0).annotation();
        assertEquals("table", ann.name());
        assertTrue(ann.hasArgs());
        assertEquals("users", ann.getArg(0).stringValue());
    }

    @Test
    void testMethodAnnotation() throws AriaException {
        AnnotationRegistry registry = Aria.getEngine().getAnnotationRegistry();
        registry.clear();

        eval("""
            class Controller {
                @route('/api/users')
                getUsers = -> {
                    return 'users'
                }

                @route('/api/items')
                getItems = -> {
                    return 'items'
                }
            }
            """);

        List<AnnotatedTarget> routes = registry.findFunctionsByAnnotation("route");
        assertEquals(2, routes.size());
        assertTrue(routes.stream().anyMatch(t -> "getUsers".equals(t.name())
                && "/api/users".equals(t.annotation().getArg(0).stringValue())));
        assertTrue(routes.stream().anyMatch(t -> "getItems".equals(t.name())
                && "/api/items".equals(t.annotation().getArg(0).stringValue())));
    }

    @Test
    void testFieldAnnotation() throws AriaException {
        AnnotationRegistry registry = Aria.getEngine().getAnnotationRegistry();
        registry.clear();

        eval("""
            class Config {
                @env('DB_HOST')
                var.host = 'localhost'

                @env('DB_PORT')
                var.port = 3306
            }
            """);

        List<AnnotatedTarget> envVars = registry.findByAnnotation("env");
        assertEquals(2, envVars.size());
        assertTrue(envVars.stream().anyMatch(t -> t.kind() == AnnotatedTarget.TargetKind.FIELD
                && "host".equals(t.name()) && "DB_HOST".equals(t.annotation().getArg(0).stringValue())));
        assertTrue(envVars.stream().anyMatch(t -> t.kind() == AnnotatedTarget.TargetKind.FIELD
                && "port".equals(t.name()) && "DB_PORT".equals(t.annotation().getArg(0).stringValue())));
    }

    @Test
    void testMultipleAnnotations() throws AriaException {
        AnnotationRegistry registry = Aria.getEngine().getAnnotationRegistry();
        registry.clear();

        eval("""
            @service
            @singleton
            class UserService {
                var.name = 'UserService'
            }
            """);

        assertEquals(1, registry.findClassesByAnnotation("service").size());
        assertEquals(1, registry.findClassesByAnnotation("singleton").size());
    }

    @Test
    void testAnnotationHandler() throws AriaException {
        AnnotationRegistry registry = Aria.getEngine().getAnnotationRegistry();
        registry.clear();

        List<String> registeredRoutes = new ArrayList<>();
        registry.onAnnotation("route", (ann, target) -> {
            registeredRoutes.add(ann.getArg(0).stringValue() + " -> " + target.name());
        });

        eval("""
            class API {
                @route('/health')
                health = -> { return 'ok' }
            }
            """);

        assertEquals(1, registeredRoutes.size());
        assertEquals("/health -> health", registeredRoutes.get(0));
    }


    @Test
    void testJSClassAnnotation() throws AriaException {
        AnnotationRegistry registry = Aria.getEngine().getAnnotationRegistry();
        registry.clear();

        evalJS("""
            @component('UserList')
            class UserList {
                constructor() {}
                render() { return 'html'; }
            }
            """);

        List<AnnotatedTarget> components = registry.findClassesByAnnotation("component");
        assertEquals(1, components.size());
        assertEquals("UserList", components.get(0).name());
        assertEquals("UserList", components.get(0).annotation().getArg(0).stringValue());
    }

    @Test
    void testJSMethodAnnotation() throws AriaException {
        AnnotationRegistry registry = Aria.getEngine().getAnnotationRegistry();
        registry.clear();

        evalJS("""
            class Router {
                @get('/users')
                listUsers() { return 'users'; }

                @post('/users')
                createUser() { return 'created'; }
            }
            """);

        List<AnnotatedTarget> gets = registry.findFunctionsByAnnotation("get");
        List<AnnotatedTarget> posts = registry.findFunctionsByAnnotation("post");
        assertEquals(1, gets.size());
        assertEquals(1, posts.size());
        assertEquals("listUsers", gets.get(0).name());
        assertEquals("/users", gets.get(0).annotation().getArg(0).stringValue());
    }


    @Test
    void testAnnotationRegistryQuery() throws AriaException {
        AnnotationRegistry registry = Aria.getEngine().getAnnotationRegistry();
        registry.clear();

        eval("""
            @service
            class A { var.x = 1 }

            @service
            class B { var.y = 2 }

            @controller
            class C { var.z = 3 }
            """);

        assertEquals(2, registry.findClassesByAnnotation("service").size());
        assertEquals(1, registry.findClassesByAnnotation("controller").size());
        assertEquals(0, registry.findClassesByAnnotation("nonexistent").size());
        assertEquals(3, registry.getAll().size());
    }
}
