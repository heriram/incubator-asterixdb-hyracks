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

package edu.uci.ics.hyracks.storage.am.common.api;

import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.ITupleReference;
import edu.uci.ics.hyracks.storage.am.common.dataflow.IIndex;
import edu.uci.ics.hyracks.storage.common.buffercache.IBufferCache;

/**
 * Interface describing the operations of tree-based index structures. Indexes
 * implementing this interface can easily reuse the tree index operators for
 * dataflow. We assume that indexes store tuples with a fixed number of fields.
 * Users must perform operations on an ITreeIndex via an ITreeIndexAccessor.
 */
public interface ITreeIndex extends IIndex {

	/**
	 * Creates an index accessor for performing operations on this index.
	 * (insert/delete/update/search/diskorderscan). An ITreeIndexAccessor is not
	 * thread safe, but different ITreeIndexAccessors can concurrently operate
	 * on the same ITreeIndex
	 * 
	 * @returns ITreeIndexAccessor A tree index accessor for this tree.
	 */
	public ITreeIndexAccessor createAccessor();

 	/**
	 * @param fillFactor
 	 * @throws TreeIndexException if the user tries to instantiate a second bulk
	 * loader
 	 */
	public ITreeIndexBulkLoader createBulkLoader(float fillFactor) throws TreeIndexException;
 

	/**
	 * @return The index's leaf frame factory.
	 */
	public ITreeIndexFrameFactory getLeafFrameFactory();

	/**
	 * @return The index's interior frame factory.
	 */
	public ITreeIndexFrameFactory getInteriorFrameFactory();

	/**
	 * @return The index's free page manager.
	 */
	public IFreePageManager getFreePageManager();

	/**
	 * @return The number of fields tuples of this index have.
	 */
	public int getFieldCount();

	/**
	 * @return The current root page id of this index.
	 */
	public int getRootPageId();

	/**
	 * @return An enum of the concrete type of this index.
	 */
	public IndexType getIndexType();
	
	/**
     * @return The file id of this index.
     */
    public int getFileId();
    
    /**
     * @return BufferCache underlying this tree index.
     */
    public IBufferCache getBufferCache();
}
