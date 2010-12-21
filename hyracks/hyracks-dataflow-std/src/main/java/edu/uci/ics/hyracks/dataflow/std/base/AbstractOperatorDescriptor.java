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
package edu.uci.ics.hyracks.dataflow.std.base;

import java.util.UUID;

import org.json.JSONException;
import org.json.JSONObject;

import edu.uci.ics.hyracks.api.constraints.PartitionConstraint;
import edu.uci.ics.hyracks.api.dataflow.IOperatorDescriptor;
import edu.uci.ics.hyracks.api.dataflow.OperatorDescriptorId;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;
import edu.uci.ics.hyracks.api.job.JobSpecification;

public abstract class AbstractOperatorDescriptor implements IOperatorDescriptor {
    private static final long serialVersionUID = 1L;

    protected final OperatorDescriptorId odId;

    protected PartitionConstraint partitionConstraint;

    protected String[] partitions;

    protected final RecordDescriptor[] recordDescriptors;

    protected final int inputArity;

    protected final int outputArity;

    public AbstractOperatorDescriptor(JobSpecification spec, int inputArity, int outputArity) {
        odId = new OperatorDescriptorId(UUID.randomUUID());
        this.inputArity = inputArity;
        this.outputArity = outputArity;
        recordDescriptors = new RecordDescriptor[outputArity];
        spec.getOperatorMap().put(getOperatorId(), this);
    }

    @Override
    public final OperatorDescriptorId getOperatorId() {
        return odId;
    }

    @Override
    public int getInputArity() {
        return inputArity;
    }

    @Override
    public int getOutputArity() {
        return outputArity;
    }

    @Override
    public PartitionConstraint getPartitionConstraint() {
        return partitionConstraint;
    }

    @Override
    public void setPartitionConstraint(PartitionConstraint partitionConstraint) {
        this.partitionConstraint = partitionConstraint;
    }

    @Override
    public RecordDescriptor[] getOutputRecordDescriptors() {
        return recordDescriptors;
    }

    @Override
    public JSONObject toJSON() throws JSONException {
        JSONObject jop = new JSONObject();
        jop.put("type", "operator");
        jop.put("id", getOperatorId().getId().toString());
        jop.put("java-class", getClass().getName());
        jop.put("in-arity", getInputArity());
        jop.put("out-arity", getOutputArity());
        jop.put("partition-constraint", String.valueOf(partitionConstraint));
        return jop;
    }
}