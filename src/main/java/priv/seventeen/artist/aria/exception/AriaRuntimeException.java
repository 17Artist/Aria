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

package priv.seventeen.artist.aria.exception;

public class AriaRuntimeException extends AriaException {

    private final int startLine;
    private final int startColumn;
    private final int endLine;

    public AriaRuntimeException(String message) {
        super(message);
        this.startLine = -1;
        this.startColumn = -1;
        this.endLine = -1;
    }

    public AriaRuntimeException(String message, int startLine, int endLine) {
        super(message + " (line " + startLine + "-" + endLine + ")");
        this.startLine = startLine;
        this.startColumn = -1;
        this.endLine = endLine;
    }

    public AriaRuntimeException(String message, int startLine, int startColumn, int endLine) {
        super(message + " (line " + startLine + ":" + startColumn + ")");
        this.startLine = startLine;
        this.startColumn = startColumn;
        this.endLine = endLine;
    }

    public AriaRuntimeException(String message, Throwable cause) {
        super(message, cause);
        this.startLine = -1;
        this.startColumn = -1;
        this.endLine = -1;
    }

    public int getStartLine() { return startLine; }
    public int getStartColumn() { return startColumn; }
    public int getEndLine() { return endLine; }
}
