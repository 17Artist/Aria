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

package priv.seventeen.artist.aria.ast.statement;

import priv.seventeen.artist.aria.ast.ASTNode;
import priv.seventeen.artist.aria.parser.SourceLocation;

import java.util.List;

public class ImportStmt extends ASTNode {
    private final List<String> path;
    private final String alias;    // nullable
    private final List<String> names;   // for "import {a,b} from 'x'"
    private final String source;  // nullable

    public ImportStmt(SourceLocation location, List<String> path, String alias) {
        super(location);
        this.path = path;
        this.alias = alias;
        this.names = null;
        this.source = null;
    }

    public ImportStmt(SourceLocation location, List<String> names, String source, boolean namedImport) {
        super(location);
        this.path = null;
        this.alias = null;
        this.names = names;
        this.source = source;
    }

    public List<String> getPath() { return path; }
    public String getAlias() { return alias; }
    public List<String> getNames() { return names; }
    public String getSource() { return source; }
}
