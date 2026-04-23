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

package priv.seventeen.artist.aria.compiler;

import priv.seventeen.artist.aria.compiler.ir.IRProgram;
import priv.seventeen.artist.aria.compiler.optimize.AlgebraicSimplification;
import priv.seventeen.artist.aria.compiler.optimize.ConstantFolding;
import priv.seventeen.artist.aria.compiler.optimize.DeadCodeElimination;
import priv.seventeen.artist.aria.compiler.optimize.ExpressionFlattening;

public class Optimizer {

    private final ConstantFolding constantFolding = new ConstantFolding();
    private final AlgebraicSimplification algebraicSimplification = new AlgebraicSimplification();
    private final ExpressionFlattening expressionFlattening = new ExpressionFlattening();
    private final DeadCodeElimination deadCodeElimination = new DeadCodeElimination();

    public IRProgram optimize(IRProgram program) {
        IRProgram result = program;
        result = constantFolding.optimize(result);
        result = algebraicSimplification.optimize(result);
        result = expressionFlattening.optimize(result);
        result = deadCodeElimination.optimize(result);
        return result;
    }
}
