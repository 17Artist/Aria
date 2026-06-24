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

package priv.seventeen.artist.aria.annotation;

import priv.seventeen.artist.aria.ast.ASTNode;
import priv.seventeen.artist.aria.ast.expression.AnnotationExpr;
import priv.seventeen.artist.aria.ast.expression.LiteralExpr;
import priv.seventeen.artist.aria.value.IValue;
import priv.seventeen.artist.aria.value.NoneValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AnnotationProcessor {

    public static List<AriaAnnotation> convert(List<AnnotationExpr> exprs) {
        if (exprs == null || exprs.isEmpty()) return Collections.emptyList();
        List<AriaAnnotation> result = new ArrayList<>(exprs.size());
        for (AnnotationExpr expr : exprs) {
            result.add(convertOne(expr));
        }
        return result;
    }

    private static AriaAnnotation convertOne(AnnotationExpr expr) {
        if (expr.getArguments() == null || expr.getArguments().isEmpty()) {
            return new AriaAnnotation(expr.getName());
        }
        IValue<?>[] args = new IValue<?>[expr.getArguments().size()];
        for (int i = 0; i < args.length; i++) {
            ASTNode argNode = expr.getArguments().get(i);
            if (argNode instanceof LiteralExpr lit) {
                args[i] = lit.getValue();
            } else {
                // 非字面量参数暂时用 none
                args[i] = NoneValue.NONE;
            }
        }
        return new AriaAnnotation(expr.getName(), args);
    }
}
