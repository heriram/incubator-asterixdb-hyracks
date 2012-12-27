package edu.uci.ics.hyracks.imru.jobgen;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.apache.hadoop.mapreduce.InputSplit;

import edu.uci.ics.hyracks.api.constraints.PartitionConstraintHelper;
import edu.uci.ics.hyracks.api.dataflow.IConnectorDescriptor;
import edu.uci.ics.hyracks.api.dataflow.IOperatorDescriptor;
import edu.uci.ics.hyracks.api.exceptions.HyracksException;
import edu.uci.ics.hyracks.api.job.JobSpecification;
import edu.uci.ics.hyracks.dataflow.std.connectors.MToNReplicatingConnectorDescriptor;
import edu.uci.ics.hyracks.imru.api.IIMRUJobSpecification;
import edu.uci.ics.hyracks.imru.dataflow.MapOperatorDescriptor;
import edu.uci.ics.hyracks.imru.dataflow.UpdateOperatorDescriptor;
import edu.uci.ics.hyracks.imru.file.HDFSInputSplitProvider;
import edu.uci.ics.hyracks.imru.hadoop.config.ConfigurationFactory;
import edu.uci.ics.hyracks.imru.jobgen.clusterconfig.ClusterConfig;

/**
 * Generates JobSpecifications for iterations of iterative map reduce
 * update, using an n-ary aggregation tree. The reducers are
 * assigned to random NC's by the Hyracks scheduler.
 *
 * @author Josh Rosen
 */
public class NAryAggregationIMRUJobFactory extends AbstractIMRUJobFactory {

    private final int fanIn;

    /**
     * Construct a new GenericAggregationIMRUJobFactory.
     *
     * @param inputPaths
     *            A comma-separated list of paths specifying the input
     *            files.
     * @param confFactory
     *            A Hadoop configuration used for HDFS settings.
     * @param fanIn
     *            The number of incoming connections to each
     *            reducer (excluding the level farthest from
     *            the root).
     */
    public NAryAggregationIMRUJobFactory(String inputPaths, ConfigurationFactory confFactory, int fanIn) {
        super(inputPaths, confFactory);
        this.fanIn = fanIn;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public JobSpecification generateJob(IIMRUJobSpecification model, UUID id, int roundNum, String modelInPath,
            String modelOutPath) throws HyracksException {

        JobSpecification spec = new JobSpecification();
        // Create operators
        // File reading and writing
        HDFSInputSplitProvider inputSplitProvider = new HDFSInputSplitProvider(inputPaths,
                confFactory.createConfiguration());
        List<InputSplit> inputSplits = inputSplitProvider.getInputSplits();

        // IMRU Computation
        // We will have one Map operator per input file.
        IOperatorDescriptor mapOperator = new MapOperatorDescriptor(spec, model, modelInPath, confFactory, roundNum);
        // For repeatability of the partition assignments, seed the source of
        // randomness using the job id.
        Random random = new Random(id.getLeastSignificantBits());
        final String[] mapOperatorLocations = ClusterConfig.setLocationConstraint(spec, mapOperator,
                inputSplits, random);

        // Environment updating
        IOperatorDescriptor updateOperator = new UpdateOperatorDescriptor(spec, model, modelInPath, confFactory,
                modelOutPath);
        PartitionConstraintHelper.addPartitionCountConstraint(spec, updateOperator, 1);

        // Reduce aggregation tree.
        IConnectorDescriptor reduceUpdateConn = new MToNReplicatingConnectorDescriptor(spec);
        ReduceAggregationTreeFactory.buildAggregationTree(spec, mapOperator, 0, inputSplits.size(),
                updateOperator, 0, reduceUpdateConn, fanIn, true, mapOperatorLocations, model);

        spec.addRoot(updateOperator);
        return spec;
    }

}