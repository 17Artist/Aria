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

package priv.seventeen.artist.aria.api;

import priv.seventeen.artist.aria.compiler.ir.IRProgram;
import priv.seventeen.artist.aria.context.Context;
import priv.seventeen.artist.aria.exception.AriaException;
import priv.seventeen.artist.aria.exception.AriaRuntimeException;
import priv.seventeen.artist.aria.parser.ContentTracker;
import priv.seventeen.artist.aria.runtime.Interpreter;
import priv.seventeen.artist.aria.runtime.Result;
import priv.seventeen.artist.aria.value.IValue;

public class AriaCompilationUnit {
    private final String name;
    private final IRProgram program;
    private final Context context;
    private final ContentTracker tracker;

    public AriaCompilationUnit(String name, IRProgram program, Context context, ContentTracker tracker) {
        this.name = name;
        this.program = program;
        this.context = context;
        this.tracker = tracker;
    }

    public IValue<?> execute() throws AriaException {
        try {
            Interpreter interpreter = new Interpreter();
            Result result = interpreter.execute(program, context);
            return result.getValue();
        } catch (AriaException e) {
            throw e;
        } catch (Exception e) {
            throw new AriaRuntimeException("Execution error in '" + name + "': " + e.getMessage(), e);
        }
    }

    public String getName() { return name; }
    public IRProgram getProgram() { return program; }
    public Context getContext() { return context; }
    public ContentTracker getTracker() { return tracker; }
}
