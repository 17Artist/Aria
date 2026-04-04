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

package priv.seventeen.artist.aria.compiler.ir;

import java.util.ArrayList;
import java.util.List;

public class IRBlock {
    private final int id;
    private final List<IRInstruction> instructions = new ArrayList<>();

    public IRBlock(int id) {
        this.id = id;
    }

    public int getId() { return id; }
    public List<IRInstruction> getInstructions() { return instructions; }

    public void emit(IRInstruction instruction) {
        instructions.add(instruction);
    }

    public int size() { return instructions.size(); }
}
