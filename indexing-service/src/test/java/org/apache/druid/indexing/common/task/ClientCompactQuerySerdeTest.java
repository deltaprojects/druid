/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.druid.indexing.common.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.google.common.collect.ImmutableList;
import org.apache.druid.client.coordinator.CoordinatorClient;
import org.apache.druid.client.indexing.ClientCompactQuery;
import org.apache.druid.client.indexing.ClientCompactQueryTuningConfig;
import org.apache.druid.client.indexing.ClientCompactionIOConfig;
import org.apache.druid.client.indexing.ClientCompactionIntervalSpec;
import org.apache.druid.guice.GuiceAnnotationIntrospector;
import org.apache.druid.guice.GuiceInjectableValues;
import org.apache.druid.guice.GuiceInjectors;
import org.apache.druid.indexing.common.SegmentLoaderFactory;
import org.apache.druid.indexing.common.TestUtils;
import org.apache.druid.indexing.common.stats.RowIngestionMetersFactory;
import org.apache.druid.jackson.DefaultObjectMapper;
import org.apache.druid.java.util.common.Intervals;
import org.apache.druid.segment.IndexSpec;
import org.apache.druid.segment.data.BitmapSerde.DefaultBitmapSerdeFactory;
import org.apache.druid.segment.data.CompressionFactory.LongEncodingStrategy;
import org.apache.druid.segment.data.CompressionStrategy;
import org.apache.druid.segment.realtime.appenderator.AppenderatorsManager;
import org.apache.druid.segment.realtime.firehose.ChatHandlerProvider;
import org.apache.druid.segment.realtime.firehose.NoopChatHandlerProvider;
import org.apache.druid.server.security.AuthTestUtils;
import org.apache.druid.server.security.AuthorizerMapper;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;

public class ClientCompactQuerySerdeTest
{
  private static final RowIngestionMetersFactory ROW_INGESTION_METERS_FACTORY = new TestUtils()
      .getRowIngestionMetersFactory();
  private static final CoordinatorClient COORDINATOR_CLIENT = new CoordinatorClient(null, null);
  private static final AppenderatorsManager APPENDERATORS_MANAGER = new TestAppenderatorsManager();

  @Test
  public void testSerde() throws IOException
  {
    final ObjectMapper mapper = setupInjectablesInObjectMapper(new DefaultObjectMapper());
    final ClientCompactQuery query = new ClientCompactQuery(
        "datasource",
        new ClientCompactionIOConfig(
            new ClientCompactionIntervalSpec(
                Intervals.of("2019/2020"),
                "testSha256OfSortedSegmentIds"
            )
        ),
        new ClientCompactQueryTuningConfig(
            100,
            40000,
            2000L,
            30000L,
            new IndexSpec(
                new DefaultBitmapSerdeFactory(),
                CompressionStrategy.LZ4,
                CompressionStrategy.LZF,
                LongEncodingStrategy.LONGS
            ),
            null,
            1000L
        ),
        new HashMap<>()
    );

    final byte[] json = mapper.writeValueAsBytes(query);
    final CompactionTask task = (CompactionTask) mapper.readValue(json, Task.class);

    Assert.assertEquals(query.getDataSource(), task.getDataSource());
    Assert.assertTrue(task.getIoConfig().getInputSpec() instanceof CompactionIntervalSpec);
    Assert.assertEquals(
        query.getIoConfig().getInputSpec().getInterval(),
        ((CompactionIntervalSpec) task.getIoConfig().getInputSpec()).getInterval()
    );
    Assert.assertEquals(
        query.getIoConfig().getInputSpec().getSha256OfSortedSegmentIds(),
        ((CompactionIntervalSpec) task.getIoConfig().getInputSpec()).getSha256OfSortedSegmentIds()
    );
    Assert.assertEquals(
        query.getTuningConfig().getMaxRowsInMemory().intValue(), task.getTuningConfig().getMaxRowsInMemory()
    );
    Assert.assertEquals(
        query.getTuningConfig().getMaxBytesInMemory().longValue(), task.getTuningConfig().getMaxBytesInMemory()
    );
    Assert.assertEquals(
        query.getTuningConfig().getMaxRowsPerSegment(), task.getTuningConfig().getMaxRowsPerSegment()
    );
    Assert.assertEquals(
        query.getTuningConfig().getMaxTotalRows(), task.getTuningConfig().getMaxTotalRows()
    );
    Assert.assertEquals(
        query.getTuningConfig().getIndexSpec(), task.getTuningConfig().getIndexSpec()
    );
    Assert.assertEquals(
        query.getTuningConfig().getPushTimeout().longValue(), task.getTuningConfig().getPushTimeout()
    );
    Assert.assertEquals(query.getContext(), task.getContext());
  }

  private static ObjectMapper setupInjectablesInObjectMapper(ObjectMapper objectMapper)
  {
    final GuiceAnnotationIntrospector guiceIntrospector = new GuiceAnnotationIntrospector();
    objectMapper.setAnnotationIntrospectors(
        new AnnotationIntrospectorPair(
            guiceIntrospector,
            objectMapper.getSerializationConfig().getAnnotationIntrospector()
        ),
        new AnnotationIntrospectorPair(
            guiceIntrospector,
            objectMapper.getDeserializationConfig().getAnnotationIntrospector()
        )
    );
    GuiceInjectableValues injectableValues = new GuiceInjectableValues(
        GuiceInjectors.makeStartupInjectorWithModules(
            ImmutableList.of(
                binder -> {
                  binder.bind(AuthorizerMapper.class).toInstance(AuthTestUtils.TEST_AUTHORIZER_MAPPER);
                  binder.bind(ChatHandlerProvider.class).toInstance(new NoopChatHandlerProvider());
                  binder.bind(RowIngestionMetersFactory.class).toInstance(ROW_INGESTION_METERS_FACTORY);
                  binder.bind(CoordinatorClient.class).toInstance(COORDINATOR_CLIENT);
                  binder.bind(SegmentLoaderFactory.class).toInstance(new SegmentLoaderFactory(null, objectMapper));
                  binder.bind(AppenderatorsManager.class).toInstance(APPENDERATORS_MANAGER);
                }
            )
        )
    );
    objectMapper.setInjectableValues(injectableValues);
    return objectMapper;
  }
}
