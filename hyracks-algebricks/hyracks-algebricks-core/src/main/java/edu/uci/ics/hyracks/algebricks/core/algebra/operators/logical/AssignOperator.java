/*
 * Copyright 2009-2010 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical;

import java.util.List;

import org.apache.commons.lang3.mutable.Mutable;

import edu.uci.ics.hyracks.algebricks.common.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.LogicalOperatorTag;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.LogicalVariable;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.IVariableTypeEnvironment;
import edu.uci.ics.hyracks.algebricks.core.algebra.properties.VariablePropagationPolicy;
import edu.uci.ics.hyracks.algebricks.core.algebra.typing.ITypingContext;
import edu.uci.ics.hyracks.algebricks.core.algebra.visitors.ILogicalOperatorVisitor;

/**
 * It corresponds to the Map operator in other algebras.
 * 
 * @author Nicola
 */

public class AssignOperator extends AbstractAssignOperator {

    public AssignOperator(List<LogicalVariable> vars, List<Mutable<ILogicalExpression>> exprs) {
        super(vars, exprs);
    }

    public AssignOperator(LogicalVariable var, Mutable<ILogicalExpression> expr) {
        super();
        this.variables.add(var);
        this.expressions.add(expr);
    }

    @Override
    public LogicalOperatorTag getOperatorTag() {
        return LogicalOperatorTag.ASSIGN;
    }

    @Override
    public <R, T> R accept(ILogicalOperatorVisitor<R, T> visitor, T arg) throws AlgebricksException {
        return visitor.visitAssignOperator(this, arg);
    }

    @Override
    public VariablePropagationPolicy getVariablePropagationPolicy() {
        return new VariablePropagationPolicy() {

            @Override
            public void propagateVariables(IOperatorSchema target, IOperatorSchema... sources)
                    throws AlgebricksException {
                target.addAllVariables(sources[0]);
                for (LogicalVariable v : variables) {
                    target.addVariable(v);
                }
            }
        };

    }

    @Override
    public boolean isMap() {
        return true;
    }

    @Override
    public IVariableTypeEnvironment computeOutputTypeEnvironment(ITypingContext ctx) throws AlgebricksException {
        IVariableTypeEnvironment env = createPropagatingAllInputsTypeEnvironment(ctx);
        int n = variables.size();
        for (int i = 0; i < n; i++) {
            env.setVarType(
                    variables.get(i),
                    ctx.getExpressionTypeComputer().getType(expressions.get(i).getValue(), ctx.getMetadataProvider(),
                            env));
        }
        return env;
    }

}