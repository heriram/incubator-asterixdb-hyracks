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
package edu.uci.ics.hyracks.tests.integration;

import java.io.File;

import org.junit.Test;

import edu.uci.ics.hyracks.api.constraints.AbsoluteLocationConstraint;
import edu.uci.ics.hyracks.api.constraints.ExplicitPartitionConstraint;
import edu.uci.ics.hyracks.api.constraints.LocationConstraint;
import edu.uci.ics.hyracks.api.constraints.PartitionConstraint;
import edu.uci.ics.hyracks.api.dataflow.IConnectorDescriptor;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparatorFactory;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryHashFunctionFactory;
import edu.uci.ics.hyracks.api.dataflow.value.ISerializerDeserializer;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;
import edu.uci.ics.hyracks.api.job.JobSpecification;
import edu.uci.ics.hyracks.dataflow.common.data.comparators.IntegerBinaryComparatorFactory;
import edu.uci.ics.hyracks.dataflow.common.data.comparators.UTF8StringBinaryComparatorFactory;
import edu.uci.ics.hyracks.dataflow.common.data.hash.UTF8StringBinaryHashFunctionFactory;
import edu.uci.ics.hyracks.dataflow.common.data.marshalling.IntegerSerializerDeserializer;
import edu.uci.ics.hyracks.dataflow.common.data.marshalling.UTF8StringSerializerDeserializer;
import edu.uci.ics.hyracks.dataflow.common.data.parsers.IValueParserFactory;
import edu.uci.ics.hyracks.dataflow.common.data.parsers.UTF8StringParserFactory;
import edu.uci.ics.hyracks.dataflow.common.data.partition.FieldHashPartitionComputerFactory;
import edu.uci.ics.hyracks.dataflow.std.aggregators.CountAggregatorFactory;
import edu.uci.ics.hyracks.dataflow.std.aggregators.IFieldValueResultingAggregatorFactory;
import edu.uci.ics.hyracks.dataflow.std.aggregators.MultiAggregatorFactory;
import edu.uci.ics.hyracks.dataflow.std.connectors.MToNHashPartitioningConnectorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.connectors.MToNReplicatingConnectorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.connectors.OneToOneConnectorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.file.ConstantFileSplitProvider;
import edu.uci.ics.hyracks.dataflow.std.file.DelimitedDataTupleParserFactory;
import edu.uci.ics.hyracks.dataflow.std.file.FileScanOperatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.file.FileSplit;
import edu.uci.ics.hyracks.dataflow.std.file.IFileSplitProvider;
import edu.uci.ics.hyracks.dataflow.std.group.PreclusteredGroupOperatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.misc.PrinterOperatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.sort.ExternalSortOperatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.sort.InMemorySortOperatorDescriptor;

public class CountOfCountsTest extends AbstractIntegrationTest {
    @Test
    public void countOfCountsSingleNC() throws Exception {
        JobSpecification spec = new JobSpecification();

        FileSplit[] splits = new FileSplit[] { new FileSplit(NC1_ID, new File("data/words.txt")) };
        IFileSplitProvider splitProvider = new ConstantFileSplitProvider(splits);
        RecordDescriptor desc = new RecordDescriptor(
                new ISerializerDeserializer[] { UTF8StringSerializerDeserializer.INSTANCE });

        FileScanOperatorDescriptor csvScanner = new FileScanOperatorDescriptor(
                spec,
                splitProvider,
                new DelimitedDataTupleParserFactory(new IValueParserFactory[] { UTF8StringParserFactory.INSTANCE }, ','),
                desc);
        PartitionConstraint csvPartitionConstraint = new ExplicitPartitionConstraint(
                new LocationConstraint[] { new AbsoluteLocationConstraint(NC1_ID) });
        csvScanner.setPartitionConstraint(csvPartitionConstraint);

        InMemorySortOperatorDescriptor sorter = new InMemorySortOperatorDescriptor(spec, new int[] { 0 },
                new IBinaryComparatorFactory[] { UTF8StringBinaryComparatorFactory.INSTANCE }, desc);
        PartitionConstraint sorterPartitionConstraint = new ExplicitPartitionConstraint(
                new LocationConstraint[] { new AbsoluteLocationConstraint(NC1_ID) });
        sorter.setPartitionConstraint(sorterPartitionConstraint);

        RecordDescriptor desc2 = new RecordDescriptor(new ISerializerDeserializer[] {
                UTF8StringSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE });
        PreclusteredGroupOperatorDescriptor group = new PreclusteredGroupOperatorDescriptor(
                spec,
                new int[] { 0 },
                new IBinaryComparatorFactory[] { UTF8StringBinaryComparatorFactory.INSTANCE },
                new MultiAggregatorFactory(new IFieldValueResultingAggregatorFactory[] { new CountAggregatorFactory() }),
                desc2);
        PartitionConstraint groupPartitionConstraint = new ExplicitPartitionConstraint(
                new LocationConstraint[] { new AbsoluteLocationConstraint(NC1_ID) });
        group.setPartitionConstraint(groupPartitionConstraint);

        InMemorySortOperatorDescriptor sorter2 = new InMemorySortOperatorDescriptor(spec, new int[] { 1 },
                new IBinaryComparatorFactory[] { IntegerBinaryComparatorFactory.INSTANCE }, desc2);
        PartitionConstraint sorterPartitionConstraint2 = new ExplicitPartitionConstraint(
                new LocationConstraint[] { new AbsoluteLocationConstraint(NC1_ID) });
        sorter2.setPartitionConstraint(sorterPartitionConstraint2);

        RecordDescriptor desc3 = new RecordDescriptor(new ISerializerDeserializer[] {
                IntegerSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE });
        PreclusteredGroupOperatorDescriptor group2 = new PreclusteredGroupOperatorDescriptor(spec, new int[] { 1 },
                new IBinaryComparatorFactory[] { IntegerBinaryComparatorFactory.INSTANCE }, new MultiAggregatorFactory(
                        new IFieldValueResultingAggregatorFactory[] { new CountAggregatorFactory() }), desc3);
        PartitionConstraint groupPartitionConstraint2 = new ExplicitPartitionConstraint(
                new LocationConstraint[] { new AbsoluteLocationConstraint(NC1_ID) });
        group2.setPartitionConstraint(groupPartitionConstraint2);

        PrinterOperatorDescriptor printer = new PrinterOperatorDescriptor(spec);
        PartitionConstraint printerPartitionConstraint = new ExplicitPartitionConstraint(
                new LocationConstraint[] { new AbsoluteLocationConstraint(NC1_ID) });
        printer.setPartitionConstraint(printerPartitionConstraint);

        IConnectorDescriptor conn1 = new MToNHashPartitioningConnectorDescriptor(spec,
                new FieldHashPartitionComputerFactory(new int[] { 0 },
                        new IBinaryHashFunctionFactory[] { UTF8StringBinaryHashFunctionFactory.INSTANCE }));
        spec.connect(conn1, csvScanner, 0, sorter, 0);

        IConnectorDescriptor conn2 = new OneToOneConnectorDescriptor(spec);
        spec.connect(conn2, sorter, 0, group, 0);

        IConnectorDescriptor conn3 = new MToNHashPartitioningConnectorDescriptor(spec,
                new FieldHashPartitionComputerFactory(new int[] { 1 },
                        new IBinaryHashFunctionFactory[] { UTF8StringBinaryHashFunctionFactory.INSTANCE }));
        spec.connect(conn3, group, 0, sorter2, 0);

        IConnectorDescriptor conn4 = new OneToOneConnectorDescriptor(spec);
        spec.connect(conn4, sorter2, 0, group2, 0);

        IConnectorDescriptor conn5 = new MToNReplicatingConnectorDescriptor(spec);
        spec.connect(conn5, group2, 0, printer, 0);

        spec.addRoot(printer);
        runTest(spec);
    }

    @Test
    public void countOfCountsMultiNC() throws Exception {
        JobSpecification spec = new JobSpecification();

        FileSplit[] splits = new FileSplit[] { new FileSplit(NC1_ID, new File("data/words.txt")) };
        IFileSplitProvider splitProvider = new ConstantFileSplitProvider(splits);
        RecordDescriptor desc = new RecordDescriptor(
                new ISerializerDeserializer[] { UTF8StringSerializerDeserializer.INSTANCE });

        FileScanOperatorDescriptor csvScanner = new FileScanOperatorDescriptor(
                spec,
                splitProvider,
                new DelimitedDataTupleParserFactory(new IValueParserFactory[] { UTF8StringParserFactory.INSTANCE }, ','),
                desc);
        PartitionConstraint csvPartitionConstraint = new ExplicitPartitionConstraint(
                new LocationConstraint[] { new AbsoluteLocationConstraint(NC1_ID) });
        csvScanner.setPartitionConstraint(csvPartitionConstraint);

        InMemorySortOperatorDescriptor sorter = new InMemorySortOperatorDescriptor(spec, new int[] { 0 },
                new IBinaryComparatorFactory[] { UTF8StringBinaryComparatorFactory.INSTANCE }, desc);
        PartitionConstraint sorterPartitionConstraint = new ExplicitPartitionConstraint(new LocationConstraint[] {
                new AbsoluteLocationConstraint(NC1_ID), new AbsoluteLocationConstraint(NC2_ID),
                new AbsoluteLocationConstraint(NC1_ID), new AbsoluteLocationConstraint(NC2_ID) });
        sorter.setPartitionConstraint(sorterPartitionConstraint);

        RecordDescriptor desc2 = new RecordDescriptor(new ISerializerDeserializer[] {
                UTF8StringSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE });
        PreclusteredGroupOperatorDescriptor group = new PreclusteredGroupOperatorDescriptor(
                spec,
                new int[] { 0 },
                new IBinaryComparatorFactory[] { UTF8StringBinaryComparatorFactory.INSTANCE },
                new MultiAggregatorFactory(new IFieldValueResultingAggregatorFactory[] { new CountAggregatorFactory() }),
                desc2);
        PartitionConstraint groupPartitionConstraint = new ExplicitPartitionConstraint(new LocationConstraint[] {
                new AbsoluteLocationConstraint(NC1_ID), new AbsoluteLocationConstraint(NC2_ID),
                new AbsoluteLocationConstraint(NC1_ID), new AbsoluteLocationConstraint(NC2_ID) });
        group.setPartitionConstraint(groupPartitionConstraint);

        InMemorySortOperatorDescriptor sorter2 = new InMemorySortOperatorDescriptor(spec, new int[] { 1 },
                new IBinaryComparatorFactory[] { IntegerBinaryComparatorFactory.INSTANCE }, desc2);
        PartitionConstraint sorterPartitionConstraint2 = new ExplicitPartitionConstraint(new LocationConstraint[] {
                new AbsoluteLocationConstraint(NC1_ID), new AbsoluteLocationConstraint(NC2_ID) });
        sorter2.setPartitionConstraint(sorterPartitionConstraint2);

        RecordDescriptor desc3 = new RecordDescriptor(new ISerializerDeserializer[] {
                IntegerSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE });
        PreclusteredGroupOperatorDescriptor group2 = new PreclusteredGroupOperatorDescriptor(spec, new int[] { 1 },
                new IBinaryComparatorFactory[] { IntegerBinaryComparatorFactory.INSTANCE }, new MultiAggregatorFactory(
                        new IFieldValueResultingAggregatorFactory[] { new CountAggregatorFactory() }), desc3);
        PartitionConstraint groupPartitionConstraint2 = new ExplicitPartitionConstraint(new LocationConstraint[] {
                new AbsoluteLocationConstraint(NC1_ID), new AbsoluteLocationConstraint(NC2_ID) });
        group2.setPartitionConstraint(groupPartitionConstraint2);

        PrinterOperatorDescriptor printer = new PrinterOperatorDescriptor(spec);
        PartitionConstraint printerPartitionConstraint = new ExplicitPartitionConstraint(
                new LocationConstraint[] { new AbsoluteLocationConstraint(NC1_ID) });
        printer.setPartitionConstraint(printerPartitionConstraint);

        IConnectorDescriptor conn1 = new MToNHashPartitioningConnectorDescriptor(spec,
                new FieldHashPartitionComputerFactory(new int[] { 0 },
                        new IBinaryHashFunctionFactory[] { UTF8StringBinaryHashFunctionFactory.INSTANCE }));
        spec.connect(conn1, csvScanner, 0, sorter, 0);

        IConnectorDescriptor conn2 = new OneToOneConnectorDescriptor(spec);
        spec.connect(conn2, sorter, 0, group, 0);

        IConnectorDescriptor conn3 = new MToNHashPartitioningConnectorDescriptor(spec,
                new FieldHashPartitionComputerFactory(new int[] { 1 },
                        new IBinaryHashFunctionFactory[] { UTF8StringBinaryHashFunctionFactory.INSTANCE }));
        spec.connect(conn3, group, 0, sorter2, 0);

        IConnectorDescriptor conn4 = new OneToOneConnectorDescriptor(spec);
        spec.connect(conn4, sorter2, 0, group2, 0);

        IConnectorDescriptor conn5 = new MToNReplicatingConnectorDescriptor(spec);
        spec.connect(conn5, group2, 0, printer, 0);

        spec.addRoot(printer);
        runTest(spec);
    }

    @Test
    public void countOfCountsExternalSortMultiNC() throws Exception {
        JobSpecification spec = new JobSpecification();

        FileSplit[] splits = new FileSplit[] { new FileSplit(NC1_ID, new File("data/words.txt")) };
        IFileSplitProvider splitProvider = new ConstantFileSplitProvider(splits);
        RecordDescriptor desc = new RecordDescriptor(
                new ISerializerDeserializer[] { UTF8StringSerializerDeserializer.INSTANCE });

        FileScanOperatorDescriptor csvScanner = new FileScanOperatorDescriptor(
                spec,
                splitProvider,
                new DelimitedDataTupleParserFactory(new IValueParserFactory[] { UTF8StringParserFactory.INSTANCE }, ','),
                desc);
        PartitionConstraint csvPartitionConstraint = new ExplicitPartitionConstraint(
                new LocationConstraint[] { new AbsoluteLocationConstraint(NC1_ID) });
        csvScanner.setPartitionConstraint(csvPartitionConstraint);

        ExternalSortOperatorDescriptor sorter = new ExternalSortOperatorDescriptor(spec, 3, new int[] { 0 },
                new IBinaryComparatorFactory[] { UTF8StringBinaryComparatorFactory.INSTANCE }, desc);
        PartitionConstraint sorterPartitionConstraint = new ExplicitPartitionConstraint(new LocationConstraint[] {
                new AbsoluteLocationConstraint(NC1_ID), new AbsoluteLocationConstraint(NC2_ID),
                new AbsoluteLocationConstraint(NC1_ID), new AbsoluteLocationConstraint(NC2_ID) });
        sorter.setPartitionConstraint(sorterPartitionConstraint);

        RecordDescriptor desc2 = new RecordDescriptor(new ISerializerDeserializer[] {
                UTF8StringSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE });
        PreclusteredGroupOperatorDescriptor group = new PreclusteredGroupOperatorDescriptor(
                spec,
                new int[] { 0 },
                new IBinaryComparatorFactory[] { UTF8StringBinaryComparatorFactory.INSTANCE },
                new MultiAggregatorFactory(new IFieldValueResultingAggregatorFactory[] { new CountAggregatorFactory() }),
                desc2);
        PartitionConstraint groupPartitionConstraint = new ExplicitPartitionConstraint(new LocationConstraint[] {
                new AbsoluteLocationConstraint(NC1_ID), new AbsoluteLocationConstraint(NC2_ID),
                new AbsoluteLocationConstraint(NC1_ID), new AbsoluteLocationConstraint(NC2_ID) });
        group.setPartitionConstraint(groupPartitionConstraint);

        InMemorySortOperatorDescriptor sorter2 = new InMemorySortOperatorDescriptor(spec, new int[] { 1 },
                new IBinaryComparatorFactory[] { IntegerBinaryComparatorFactory.INSTANCE }, desc2);
        PartitionConstraint sorterPartitionConstraint2 = new ExplicitPartitionConstraint(new LocationConstraint[] {
                new AbsoluteLocationConstraint(NC1_ID), new AbsoluteLocationConstraint(NC2_ID) });
        sorter2.setPartitionConstraint(sorterPartitionConstraint2);

        RecordDescriptor desc3 = new RecordDescriptor(new ISerializerDeserializer[] {
                IntegerSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE });
        PreclusteredGroupOperatorDescriptor group2 = new PreclusteredGroupOperatorDescriptor(spec, new int[] { 1 },
                new IBinaryComparatorFactory[] { IntegerBinaryComparatorFactory.INSTANCE }, new MultiAggregatorFactory(
                        new IFieldValueResultingAggregatorFactory[] { new CountAggregatorFactory() }), desc3);
        PartitionConstraint groupPartitionConstraint2 = new ExplicitPartitionConstraint(new LocationConstraint[] {
                new AbsoluteLocationConstraint(NC1_ID), new AbsoluteLocationConstraint(NC2_ID) });
        group2.setPartitionConstraint(groupPartitionConstraint2);

        PrinterOperatorDescriptor printer = new PrinterOperatorDescriptor(spec);
        PartitionConstraint printerPartitionConstraint = new ExplicitPartitionConstraint(
                new LocationConstraint[] { new AbsoluteLocationConstraint(NC1_ID) });
        printer.setPartitionConstraint(printerPartitionConstraint);

        IConnectorDescriptor conn1 = new MToNHashPartitioningConnectorDescriptor(spec,
                new FieldHashPartitionComputerFactory(new int[] { 0 },
                        new IBinaryHashFunctionFactory[] { UTF8StringBinaryHashFunctionFactory.INSTANCE }));
        spec.connect(conn1, csvScanner, 0, sorter, 0);

        IConnectorDescriptor conn2 = new OneToOneConnectorDescriptor(spec);
        spec.connect(conn2, sorter, 0, group, 0);

        IConnectorDescriptor conn3 = new MToNHashPartitioningConnectorDescriptor(spec,
                new FieldHashPartitionComputerFactory(new int[] { 1 },
                        new IBinaryHashFunctionFactory[] { UTF8StringBinaryHashFunctionFactory.INSTANCE }));
        spec.connect(conn3, group, 0, sorter2, 0);

        IConnectorDescriptor conn4 = new OneToOneConnectorDescriptor(spec);
        spec.connect(conn4, sorter2, 0, group2, 0);

        IConnectorDescriptor conn5 = new MToNReplicatingConnectorDescriptor(spec);
        spec.connect(conn5, group2, 0, printer, 0);

        spec.addRoot(printer);
        runTest(spec);
    }
}