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
package edu.uci.ics.hyracks.tests.spillable;

import java.io.File;

import org.junit.Test;

import edu.uci.ics.hyracks.api.constraints.PartitionConstraintHelper;
import edu.uci.ics.hyracks.api.dataflow.IConnectorDescriptor;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparatorFactory;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryHashFunctionFactory;
import edu.uci.ics.hyracks.api.dataflow.value.ISerializerDeserializer;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;
import edu.uci.ics.hyracks.api.io.FileReference;
import edu.uci.ics.hyracks.api.job.JobSpecification;
import edu.uci.ics.hyracks.dataflow.common.data.comparators.UTF8StringBinaryComparatorFactory;
import edu.uci.ics.hyracks.dataflow.common.data.hash.UTF8StringBinaryHashFunctionFactory;
import edu.uci.ics.hyracks.dataflow.common.data.marshalling.FloatSerializerDeserializer;
import edu.uci.ics.hyracks.dataflow.common.data.marshalling.IntegerSerializerDeserializer;
import edu.uci.ics.hyracks.dataflow.common.data.marshalling.UTF8StringSerializerDeserializer;
import edu.uci.ics.hyracks.dataflow.common.data.parsers.FloatParserFactory;
import edu.uci.ics.hyracks.dataflow.common.data.parsers.IValueParserFactory;
import edu.uci.ics.hyracks.dataflow.common.data.parsers.IntegerParserFactory;
import edu.uci.ics.hyracks.dataflow.common.data.parsers.UTF8StringParserFactory;
import edu.uci.ics.hyracks.dataflow.common.data.partition.FieldHashPartitionComputerFactory;
import edu.uci.ics.hyracks.dataflow.std.aggregators.ConcatAggregatorDescriptorFactory;
import edu.uci.ics.hyracks.dataflow.std.aggregators.IAggregatorDescriptorFactory;
import edu.uci.ics.hyracks.dataflow.std.aggregators.IntSumAggregatorDescriptorFactory;
import edu.uci.ics.hyracks.dataflow.std.aggregators.MultiAggregatorDescriptorFactory;
import edu.uci.ics.hyracks.dataflow.std.base.AbstractSingleActivityOperatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.connectors.MToNHashPartitioningConnectorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.connectors.OneToOneConnectorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.file.ConstantFileSplitProvider;
import edu.uci.ics.hyracks.dataflow.std.file.DelimitedDataTupleParserFactory;
import edu.uci.ics.hyracks.dataflow.std.file.FileScanOperatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.file.FileSplit;
import edu.uci.ics.hyracks.dataflow.std.file.IFileSplitProvider;
import edu.uci.ics.hyracks.dataflow.std.file.ITupleParserFactory;
import edu.uci.ics.hyracks.dataflow.std.group.BSTSpillableGroupingTableFactory;
import edu.uci.ics.hyracks.dataflow.std.group.ExternalGroupOperatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.group.HashSpillableGroupingTableFactory;
import edu.uci.ics.hyracks.dataflow.std.misc.PrinterOperatorDescriptor;
import edu.uci.ics.hyracks.tests.integration.AbstractIntegrationTest;


/**
 * @author jarodwen
 *
 */
public class ExternalGroupRefactorTest extends AbstractIntegrationTest {
    final IFileSplitProvider splitProvider = new ConstantFileSplitProvider(new FileSplit[] { new FileSplit(NC2_ID,
            new FileReference(new File("data/tpch0.001/lineitem.tbl"))) });

    final RecordDescriptor desc = new RecordDescriptor(new ISerializerDeserializer[] {
            UTF8StringSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE,
            IntegerSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE,
            IntegerSerializerDeserializer.INSTANCE, FloatSerializerDeserializer.INSTANCE,
            FloatSerializerDeserializer.INSTANCE, FloatSerializerDeserializer.INSTANCE,
            UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
            UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
            UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
            UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE });

    final ITupleParserFactory tupleParserFactory = new DelimitedDataTupleParserFactory(new IValueParserFactory[] {
            UTF8StringParserFactory.INSTANCE, IntegerParserFactory.INSTANCE, IntegerParserFactory.INSTANCE,
            IntegerParserFactory.INSTANCE, IntegerParserFactory.INSTANCE, FloatParserFactory.INSTANCE,
            FloatParserFactory.INSTANCE, FloatParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE,
            UTF8StringParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE,
            UTF8StringParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE,
            UTF8StringParserFactory.INSTANCE, }, '|');

    @Test
    public void hashSingleKeyScalarGroupTest() throws Exception {
        JobSpecification spec = new JobSpecification();

        FileScanOperatorDescriptor csvScanner = new FileScanOperatorDescriptor(spec, splitProvider, tupleParserFactory,
                desc);

        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, csvScanner, NC2_ID);

        RecordDescriptor outputRec = new RecordDescriptor(new ISerializerDeserializer[] {
                UTF8StringSerializerDeserializer.INSTANCE, 
                IntegerSerializerDeserializer.INSTANCE
                });

        int[] keyFields = new int[] { 0 };
        int frameLimits = 3;
        int tableSize = 8;

        ExternalGroupOperatorDescriptor grouper = new ExternalGroupOperatorDescriptor(spec, keyFields, frameLimits,
                new IBinaryComparatorFactory[] { UTF8StringBinaryComparatorFactory.INSTANCE },
                new IntSumAggregatorDescriptorFactory(1), outputRec, new HashSpillableGroupingTableFactory(
                        new FieldHashPartitionComputerFactory(keyFields,
                                new IBinaryHashFunctionFactory[] { UTF8StringBinaryHashFunctionFactory.INSTANCE }),
                        tableSize, true));

        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, grouper, NC2_ID, NC1_ID);

        IConnectorDescriptor conn1 = new MToNHashPartitioningConnectorDescriptor(spec,
                new FieldHashPartitionComputerFactory(keyFields,
                        new IBinaryHashFunctionFactory[] { UTF8StringBinaryHashFunctionFactory.INSTANCE }));
        spec.connect(conn1, csvScanner, 0, grouper, 0);
        
        AbstractSingleActivityOperatorDescriptor printer = new PrinterOperatorDescriptor(spec);
        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, printer, NC2_ID, NC1_ID);

        IConnectorDescriptor conn2 = new OneToOneConnectorDescriptor(spec);
        spec.connect(conn2, grouper, 0, printer, 0);

        spec.addRoot(printer);
        runTest(spec);
    }

    @Test
    public void BSTSingleKeyScalarGroupTest() throws Exception {
        JobSpecification spec = new JobSpecification();

        FileScanOperatorDescriptor csvScanner = new FileScanOperatorDescriptor(spec, splitProvider, tupleParserFactory,
                desc);

        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, csvScanner, NC2_ID);

        RecordDescriptor outputRec = new RecordDescriptor(new ISerializerDeserializer[] {
                UTF8StringSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE, });

        int[] keyFields = new int[] { 0 };
        int frameLimits = 3;

        ExternalGroupOperatorDescriptor grouper = new ExternalGroupOperatorDescriptor(spec, keyFields, frameLimits,
                new IBinaryComparatorFactory[] { UTF8StringBinaryComparatorFactory.INSTANCE },
                new IntSumAggregatorDescriptorFactory(1), outputRec, new BSTSpillableGroupingTableFactory());

        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, grouper, NC2_ID, NC1_ID);

        IConnectorDescriptor conn1 = new MToNHashPartitioningConnectorDescriptor(spec,
                new FieldHashPartitionComputerFactory(keyFields,
                        new IBinaryHashFunctionFactory[] { UTF8StringBinaryHashFunctionFactory.INSTANCE }));
        spec.connect(conn1, csvScanner, 0, grouper, 0);

        AbstractSingleActivityOperatorDescriptor printer = new PrinterOperatorDescriptor(spec);
        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, printer, NC2_ID, NC1_ID);

        IConnectorDescriptor conn2 = new OneToOneConnectorDescriptor(spec);
        spec.connect(conn2, grouper, 0, printer, 0);

        spec.addRoot(printer);
        runTest(spec);
    }

    @Test
    public void hashMultipleKeyScalarGroupTest() throws Exception {
        JobSpecification spec = new JobSpecification();

        FileScanOperatorDescriptor csvScanner = new FileScanOperatorDescriptor(spec, splitProvider, tupleParserFactory,
                desc);

        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, csvScanner, NC2_ID);

        RecordDescriptor outputRec = new RecordDescriptor(new ISerializerDeserializer[] {
                UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
                IntegerSerializerDeserializer.INSTANCE, });

        int[] keyFields = new int[] { 0, 9 };
        int frameLimits = 3;
        int tableSize = 8;

        ExternalGroupOperatorDescriptor grouper = new ExternalGroupOperatorDescriptor(spec, keyFields, frameLimits,
                new IBinaryComparatorFactory[] { UTF8StringBinaryComparatorFactory.INSTANCE,
                        UTF8StringBinaryComparatorFactory.INSTANCE }, new IntSumAggregatorDescriptorFactory(1),
                outputRec, new HashSpillableGroupingTableFactory(new FieldHashPartitionComputerFactory(keyFields,
                        new IBinaryHashFunctionFactory[] { UTF8StringBinaryHashFunctionFactory.INSTANCE,
                                UTF8StringBinaryHashFunctionFactory.INSTANCE }), tableSize, true));

        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, grouper, NC2_ID, NC1_ID);

        IConnectorDescriptor conn1 = new MToNHashPartitioningConnectorDescriptor(spec,
                new FieldHashPartitionComputerFactory(keyFields, new IBinaryHashFunctionFactory[] {
                        UTF8StringBinaryHashFunctionFactory.INSTANCE, UTF8StringBinaryHashFunctionFactory.INSTANCE, }));
        spec.connect(conn1, csvScanner, 0, grouper, 0);

        AbstractSingleActivityOperatorDescriptor printer = new PrinterOperatorDescriptor(spec);
        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, printer, NC2_ID, NC1_ID);

        IConnectorDescriptor conn2 = new OneToOneConnectorDescriptor(spec);
        spec.connect(conn2, grouper, 0, printer, 0);

        spec.addRoot(printer);
        runTest(spec);
    }

    @Test
    public void hashMultipleKeyMultipleScalarGroupTest() throws Exception {
        JobSpecification spec = new JobSpecification();

        FileScanOperatorDescriptor csvScanner = new FileScanOperatorDescriptor(spec, splitProvider, tupleParserFactory,
                desc);

        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, csvScanner, NC2_ID);

        RecordDescriptor outputRec = new RecordDescriptor(new ISerializerDeserializer[] {
                UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
                IntegerSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE, });

        int[] keyFields = new int[] { 0, 9 };
        int frameLimits = 3;
        int tableSize = 8;

        ExternalGroupOperatorDescriptor grouper = new ExternalGroupOperatorDescriptor(spec, keyFields, frameLimits,
                new IBinaryComparatorFactory[] { UTF8StringBinaryComparatorFactory.INSTANCE,
                        UTF8StringBinaryComparatorFactory.INSTANCE }, new MultiAggregatorDescriptorFactory(
                        new IAggregatorDescriptorFactory[] { new IntSumAggregatorDescriptorFactory(1, 2),
                                new IntSumAggregatorDescriptorFactory(2, 3) }), outputRec,
                new HashSpillableGroupingTableFactory(new FieldHashPartitionComputerFactory(keyFields,
                        new IBinaryHashFunctionFactory[] { UTF8StringBinaryHashFunctionFactory.INSTANCE,
                                UTF8StringBinaryHashFunctionFactory.INSTANCE }), tableSize, true));

        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, grouper, NC2_ID, NC1_ID);

        IConnectorDescriptor conn1 = new MToNHashPartitioningConnectorDescriptor(spec,
                new FieldHashPartitionComputerFactory(keyFields, new IBinaryHashFunctionFactory[] {
                        UTF8StringBinaryHashFunctionFactory.INSTANCE, UTF8StringBinaryHashFunctionFactory.INSTANCE, }));
        spec.connect(conn1, csvScanner, 0, grouper, 0);

        AbstractSingleActivityOperatorDescriptor printer = new PrinterOperatorDescriptor(spec);
        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, printer, NC2_ID, NC1_ID);

        IConnectorDescriptor conn2 = new OneToOneConnectorDescriptor(spec);
        spec.connect(conn2, grouper, 0, printer, 0);

        spec.addRoot(printer);
        runTest(spec);
    }

    @Test
    public void hashMultipleKeyNonScalarGroupTest() throws Exception {
        JobSpecification spec = new JobSpecification();

        FileScanOperatorDescriptor csvScanner = new FileScanOperatorDescriptor(spec, splitProvider, tupleParserFactory,
                desc);

        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, csvScanner, NC2_ID);

        RecordDescriptor outputRec = new RecordDescriptor(new ISerializerDeserializer[] {
                UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE });

        int[] keyFields = new int[] { 0 };
        int frameLimits = 3;
        int tableSize = 8;

        ExternalGroupOperatorDescriptor grouper = new ExternalGroupOperatorDescriptor(spec, keyFields, frameLimits,
                new IBinaryComparatorFactory[] { UTF8StringBinaryComparatorFactory.INSTANCE }, new ConcatAggregatorDescriptorFactory(9),
                outputRec, new HashSpillableGroupingTableFactory(new FieldHashPartitionComputerFactory(keyFields,
                        new IBinaryHashFunctionFactory[] { UTF8StringBinaryHashFunctionFactory.INSTANCE }), tableSize, true));

        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, grouper, NC2_ID, NC1_ID);

        IConnectorDescriptor conn1 = new MToNHashPartitioningConnectorDescriptor(spec,
                new FieldHashPartitionComputerFactory(keyFields, new IBinaryHashFunctionFactory[] {
                        UTF8StringBinaryHashFunctionFactory.INSTANCE }));
        spec.connect(conn1, csvScanner, 0, grouper, 0);

        AbstractSingleActivityOperatorDescriptor printer = new PrinterOperatorDescriptor(spec);
        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, printer, NC2_ID, NC1_ID);

        IConnectorDescriptor conn2 = new OneToOneConnectorDescriptor(spec);
        spec.connect(conn2, grouper, 0, printer, 0);

        spec.addRoot(printer);
        runTest(spec);
    }
    
    @Test
    public void hashMultipleKeyMultipleFieldsGroupTest() throws Exception {
        JobSpecification spec = new JobSpecification();

        FileScanOperatorDescriptor csvScanner = new FileScanOperatorDescriptor(spec, splitProvider, tupleParserFactory,
                desc);

        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, csvScanner, NC2_ID);

        RecordDescriptor outputRec = new RecordDescriptor(new ISerializerDeserializer[] {
                UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
                IntegerSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE});

        int[] keyFields = new int[] { 0, 9 };
        int frameLimits = 3;
        int tableSize = 8;

        ExternalGroupOperatorDescriptor grouper = new ExternalGroupOperatorDescriptor(spec, keyFields, frameLimits,
                new IBinaryComparatorFactory[] { UTF8StringBinaryComparatorFactory.INSTANCE,
                        UTF8StringBinaryComparatorFactory.INSTANCE }, new MultiAggregatorDescriptorFactory(
                        new IAggregatorDescriptorFactory[] { new IntSumAggregatorDescriptorFactory(1, 2),
                                new IntSumAggregatorDescriptorFactory(2, 3), new ConcatAggregatorDescriptorFactory(9, 4) }), outputRec,
                new HashSpillableGroupingTableFactory(new FieldHashPartitionComputerFactory(keyFields,
                        new IBinaryHashFunctionFactory[] { UTF8StringBinaryHashFunctionFactory.INSTANCE,
                                UTF8StringBinaryHashFunctionFactory.INSTANCE }), tableSize, true));

        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, grouper, NC2_ID, NC1_ID);

        IConnectorDescriptor conn1 = new MToNHashPartitioningConnectorDescriptor(spec,
                new FieldHashPartitionComputerFactory(keyFields, new IBinaryHashFunctionFactory[] {
                        UTF8StringBinaryHashFunctionFactory.INSTANCE, UTF8StringBinaryHashFunctionFactory.INSTANCE, }));
        spec.connect(conn1, csvScanner, 0, grouper, 0);

        AbstractSingleActivityOperatorDescriptor printer = new PrinterOperatorDescriptor(spec);
        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, printer, NC2_ID, NC1_ID);

        IConnectorDescriptor conn2 = new OneToOneConnectorDescriptor(spec);
        spec.connect(conn2, grouper, 0, printer, 0);

        spec.addRoot(printer);
        runTest(spec);
    }
}
