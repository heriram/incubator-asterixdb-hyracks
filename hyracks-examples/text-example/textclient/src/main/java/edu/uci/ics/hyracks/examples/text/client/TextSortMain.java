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
package edu.uci.ics.hyracks.examples.text.client;

import java.io.File;
import java.util.EnumSet;

import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import edu.uci.ics.hyracks.api.client.HyracksConnection;
import edu.uci.ics.hyracks.api.client.IHyracksClientConnection;
import edu.uci.ics.hyracks.api.constraints.PartitionConstraintHelper;
import edu.uci.ics.hyracks.api.dataflow.IConnectorDescriptor;
import edu.uci.ics.hyracks.api.dataflow.IOperatorDescriptor;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparatorFactory;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryHashFunctionFactory;
import edu.uci.ics.hyracks.api.dataflow.value.ISerializerDeserializer;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;
import edu.uci.ics.hyracks.api.io.FileReference;
import edu.uci.ics.hyracks.api.job.JobFlag;
import edu.uci.ics.hyracks.api.job.JobId;
import edu.uci.ics.hyracks.api.job.JobSpecification;
import edu.uci.ics.hyracks.data.std.accessors.PointableBinaryComparatorFactory;
import edu.uci.ics.hyracks.data.std.accessors.PointableBinaryHashFunctionFactory;
import edu.uci.ics.hyracks.data.std.primitive.UTF8StringPointable;
import edu.uci.ics.hyracks.dataflow.common.data.marshalling.UTF8StringSerializerDeserializer;
import edu.uci.ics.hyracks.dataflow.common.data.normalizers.UTF8StringNormalizedKeyComputerFactory;
import edu.uci.ics.hyracks.dataflow.common.data.parsers.IValueParserFactory;
import edu.uci.ics.hyracks.dataflow.common.data.parsers.UTF8StringParserFactory;
import edu.uci.ics.hyracks.dataflow.common.data.partition.FieldHashPartitionComputerFactory;
import edu.uci.ics.hyracks.dataflow.std.connectors.MToNPartitioningConnectorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.connectors.OneToOneConnectorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.file.ConstantFileSplitProvider;
import edu.uci.ics.hyracks.dataflow.std.file.DelimitedDataTupleParserFactory;
import edu.uci.ics.hyracks.dataflow.std.file.FileScanOperatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.file.FileSplit;
import edu.uci.ics.hyracks.dataflow.std.file.FrameFileWriterOperatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.file.IFileSplitProvider;
import edu.uci.ics.hyracks.dataflow.std.file.PlainFileWriterOperatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.sort.ExternalSortOperatorDescriptor;

public class TextSortMain {
    private static class Options {
        @Option(name = "-host", usage = "Hyracks Cluster Controller Host name", required = true)
        public String host;

        @Option(name = "-port", usage = "Hyracks Cluster Controller Port (default: 1099)")
        public int port = 1098;

        @Option(name = "-app", usage = "Hyracks Application name", required = true)
        public String app;

        @Option(name = "-infile-splits", usage = "Comma separated list of file-splits for the input. A file-split is <node-name>:<path>", required = true)
        public String inFileSplits;

        @Option(name = "-outfile-splits", usage = "Comma separated list of file-splits for the output", required = true)
        public String outFileSplits;

        @Option(name = "-format", usage = "Specify output format: binary/text (default: text)", required = false)
        public String format = "text";

        @Option(name = "-sortbuffer-size", usage = "Sort buffer size in frames (default: 32768)", required = false)
        public int sbSize = 32768;

        @Option(name = "-forecasting-buffer-lim", usage = "Number of forecasting buffers to use", required = false)
        public int fblim = 0;

        @Option(name = "-runtime-profiling", usage = "Indicates if runtime profiling should be enabled. (default: false)")
        public boolean runtimeProfiling = false;
    }

    public static void main(String[] args) throws Exception {
        Options options = new Options();
        CmdLineParser parser = new CmdLineParser(options);
        parser.parseArgument(args);

        IHyracksClientConnection hcc = new HyracksConnection(options.host, options.port);

        JobSpecification job = createJob(parseFileSplits(options.inFileSplits), parseFileSplits(options.outFileSplits),
                options.sbSize, options.format, options.fblim);

        long start = System.currentTimeMillis();
        JobId jobId = hcc.createJob(options.app, job, options.runtimeProfiling ? EnumSet.of(JobFlag.PROFILE_RUNTIME)
                : EnumSet.noneOf(JobFlag.class));
        hcc.start(jobId);
        hcc.waitForCompletion(jobId);
        long end = System.currentTimeMillis();
        System.err.println(start + " " + end + " " + (end - start));
    }

    private static FileSplit[] parseFileSplits(String fileSplits) {
        String[] splits = fileSplits.split(",");
        FileSplit[] fSplits = new FileSplit[splits.length];
        for (int i = 0; i < splits.length; ++i) {
            String s = splits[i].trim();
            int idx = s.indexOf(':');
            if (idx < 0) {
                throw new IllegalArgumentException("File split " + s + " not well formed");
            }
            fSplits[i] = new FileSplit(s.substring(0, idx), new FileReference(new File(s.substring(idx + 1))));
        }
        return fSplits;
    }

    private static JobSpecification createJob(FileSplit[] inSplits, FileSplit[] outSplits, int sbSize, String format,
            int fblim) {
        JobSpecification spec = new JobSpecification();

        IFileSplitProvider ordersSplitProvider = new ConstantFileSplitProvider(inSplits);
        RecordDescriptor ordersDesc = new RecordDescriptor(new ISerializerDeserializer[] {
                UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE });

        FileScanOperatorDescriptor ordScanner = new FileScanOperatorDescriptor(spec, ordersSplitProvider,
                new DelimitedDataTupleParserFactory(new IValueParserFactory[] { UTF8StringParserFactory.INSTANCE,
                        UTF8StringParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE,
                        UTF8StringParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE,
                        UTF8StringParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE,
                        UTF8StringParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE,
                        UTF8StringParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE,
                        UTF8StringParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE,
                        UTF8StringParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE,
                        UTF8StringParserFactory.INSTANCE }, ','), ordersDesc);
        createPartitionConstraint(spec, ordScanner, inSplits);

        int[] keys = new int[] { 0 };

        IBinaryComparatorFactory[] cfs = new IBinaryComparatorFactory[] { PointableBinaryComparatorFactory
                .of(UTF8StringPointable.FACTORY) };
        IOperatorDescriptor sorter = new ExternalSortOperatorDescriptor(spec, sbSize, keys,
                new UTF8StringNormalizedKeyComputerFactory(), cfs, ordersDesc, fblim);
        createPartitionConstraint(spec, sorter, outSplits);

        IConnectorDescriptor scanSortConn = new MToNPartitioningConnectorDescriptor(spec,
                new FieldHashPartitionComputerFactory(keys,
                        new IBinaryHashFunctionFactory[] { PointableBinaryHashFunctionFactory
                                .of(UTF8StringPointable.FACTORY) }));
        spec.connect(scanSortConn, ordScanner, 0, sorter, 0);

        IFileSplitProvider outSplitProvider = new ConstantFileSplitProvider(outSplits);
        IOperatorDescriptor writer = "text".equalsIgnoreCase(format) ? new PlainFileWriterOperatorDescriptor(spec,
                outSplitProvider, ",") : new FrameFileWriterOperatorDescriptor(spec, outSplitProvider);
        createPartitionConstraint(spec, writer, outSplits);

        IConnectorDescriptor sortPrinterConn = new OneToOneConnectorDescriptor(spec);
        spec.connect(sortPrinterConn, sorter, 0, writer, 0);

        spec.addRoot(writer);
        return spec;
    }

    private static void createPartitionConstraint(JobSpecification spec, IOperatorDescriptor op, FileSplit[] splits) {
        String[] parts = new String[splits.length];
        for (int i = 0; i < splits.length; ++i) {
            parts[i] = splits[i].getNodeName();
        }
        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, op, parts);
    }
}