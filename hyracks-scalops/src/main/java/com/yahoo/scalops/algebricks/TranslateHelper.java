package com.yahoo.scalops.algebricks;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableObject;


import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalPlan;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.LogicalVariable;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.ScalarFunctionCallExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.VariableReferenceExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.functions.AlgebricksBuiltinFunctions;
import edu.uci.ics.hyracks.algebricks.core.algebra.functions.FunctionIdentifier;
import edu.uci.ics.hyracks.algebricks.core.algebra.functions.IFunctionInfo;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.AggregateOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.AssignOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.GroupByOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.InnerJoinOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.NestedTupleSourceOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.SelectOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.visitors.VariableUtilities;
import edu.uci.ics.hyracks.algebricks.core.algebra.plan.ALogicalPlanImpl;
import edu.uci.ics.hyracks.algebricks.core.api.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.algebricks.core.utils.Pair;

public class TranslateHelper {

    private static ILogicalOperator createAssignment(ILogicalOperator inputOp, ClosureEvaluator func,
            TranslationContext context) {
        ILogicalExpression funcExpr = new ScalarFunctionCallExpression(func);
        LogicalVariable var = context.newVar();
        ILogicalOperator assignOp = new AssignOperator(var, new MutableObject<ILogicalExpression>(funcExpr));
        assignOp.getInputs().add(new MutableObject<ILogicalOperator>(inputOp));
        return assignOp;
    }

    public static ILogicalOperator createGroupBy(ILogicalOperator inputOp, ClosureEvaluator func,
            List<ClosureEvaluator> aggs, TranslationContext context) throws AlgebricksException {
        // assign operator
        AssignOperator assign = (AssignOperator) createAssignment(inputOp, func, context);
        ILogicalExpression assignExpr = assign.getExpressions().get(0).getValue();
        Mutable<ILogicalExpression> groupExpr = new MutableObject<ILogicalExpression>(assignExpr);

        // get group key
        List<LogicalVariable> assignProducedVars = new ArrayList<LogicalVariable>();
        VariableUtilities.getProducedVariables(assign, assignProducedVars);
        LogicalVariable groupVar = assignProducedVars.get(0);
        Pair<LogicalVariable, Mutable<ILogicalExpression>> key = new Pair<LogicalVariable, Mutable<ILogicalExpression>>(
                groupVar, groupExpr);
        List<Pair<LogicalVariable, Mutable<ILogicalExpression>>> keys = new ArrayList<Pair<LogicalVariable, Mutable<ILogicalExpression>>>();
        keys.add(key);

        // functional dependency columns
        List<Pair<LogicalVariable, Mutable<ILogicalExpression>>> decCols = new ArrayList<Pair<LogicalVariable, Mutable<ILogicalExpression>>>();

        // aggregate variables
        List<LogicalVariable> aggVariables = new ArrayList<LogicalVariable>();
        for (int i = 0; i < aggs.size(); i++)
            aggVariables.add(context.newVar());

        // aggregate expressions
        List<Mutable<ILogicalExpression>> aggExprs = new ArrayList<Mutable<ILogicalExpression>>();
        for (ClosureEvaluator agg : aggs) {
            ILogicalExpression aggExpr = new ScalarFunctionCallExpression(agg);
            aggExprs.add(new MutableObject<ILogicalExpression>(aggExpr));
        }

        // aggregate operator
        AggregateOperator aggOperator = new AggregateOperator(aggVariables, aggExprs);
        NestedTupleSourceOperator nestedTupleSource = new NestedTupleSourceOperator(
                new MutableObject<ILogicalOperator>());
        aggOperator.getInputs().add(new MutableObject<ILogicalOperator>(nestedTupleSource));

        // subplan including the aggregate operator
        List<Mutable<ILogicalOperator>> subRoots = new ArrayList<Mutable<ILogicalOperator>>();
        subRoots.add(new MutableObject<ILogicalOperator>(aggOperator));
        ILogicalPlan subPlan = new ALogicalPlanImpl(subRoots);
        List<ILogicalPlan> subPlans = new ArrayList<ILogicalPlan>();
        subPlans.add(subPlan);

        GroupByOperator groupByOp = new GroupByOperator(keys, decCols, subPlans);
        nestedTupleSource.getDataSourceReference().setValue(groupByOp);
        groupByOp.getInputs().add(new MutableObject<ILogicalOperator>(assign));

        return groupByOp;
    }

    public static ILogicalOperator createJoin(ILogicalOperator leftOp, ILogicalOperator rightOp,
            ClosureEvaluator leftExtractor, ClosureEvaluator rightExtractor, TranslationContext context)
            throws AlgebricksException {
        ILogicalOperator leftAssign = createAssignment(leftOp, leftExtractor, context);
        ILogicalOperator rightAssign = createAssignment(rightOp, rightExtractor, context);

        List<LogicalVariable> leftProducedVars = new ArrayList<LogicalVariable>();
        VariableUtilities.getProducedVariables(leftAssign, leftProducedVars);
        LogicalVariable leftVar = leftProducedVars.get(0);

        List<LogicalVariable> rightProducedVars = new ArrayList<LogicalVariable>();
        VariableUtilities.getProducedVariables(rightAssign, rightProducedVars);
        LogicalVariable rightVar = rightProducedVars.get(0);

        @SuppressWarnings("unchecked")
        ILogicalExpression equals = new ScalarFunctionCallExpression(new IFunctionInfo() {
        	 public FunctionIdentifier getFunctionIdentifier() {
             	return AlgebricksBuiltinFunctions.EQ;
             }
			}, new MutableObject<ILogicalExpression>(new VariableReferenceExpression(
                leftVar)), new MutableObject<ILogicalExpression>(new VariableReferenceExpression(rightVar)));

        ILogicalOperator joinOp = new InnerJoinOperator(new MutableObject<ILogicalExpression>(equals),
                new MutableObject<ILogicalOperator>(leftAssign), new MutableObject<ILogicalOperator>(rightAssign));

        joinOp.getInputs().add(new MutableObject<ILogicalOperator>(leftAssign));
        joinOp.getInputs().add(new MutableObject<ILogicalOperator>(rightAssign));
        return joinOp;
    }

    public static ILogicalOperator createSelect(ILogicalOperator inputOp, ClosureEvaluator cond) {
        ILogicalExpression funcExpr = new ScalarFunctionCallExpression(cond);
        ILogicalOperator selectOp = new SelectOperator(new MutableObject<ILogicalExpression>(funcExpr));
        selectOp.getInputs().get(0).setValue(inputOp);
        return selectOp;
    }

}