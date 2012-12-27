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
package edu.uci.ics.hyracks.imru.hadoop.config;

import org.apache.hadoop.conf.Configuration;

import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.imru.base.IConfigurationFactory;
import edu.uci.ics.hyracks.imru.util.SerDeUtils;

public class ConfigurationFactory implements IConfigurationFactory {
    private static final long serialVersionUID = 1L;
    private final byte[] data;

    public ConfigurationFactory(Configuration conf) {
        try {
            data = SerDeUtils.serialize(conf);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Configuration createConfiguration() throws HyracksDataException {
        try {
            Configuration conf = new Configuration();
            SerDeUtils.deserialize(conf, data);
            conf.setClassLoader(this.getClass().getClassLoader());
            return conf;
        } catch (Exception e) {
            throw new HyracksDataException(e);
        }
    }
}