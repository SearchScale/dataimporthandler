/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.solr.handler.dataimport;

import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.CollectionAdminRequest;
import org.apache.solr.client.solrj.request.GenericSolrRequest;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.SimpleSolrResponse;
import org.apache.solr.cloud.SolrCloudTestCase;
import org.apache.solr.common.params.MapSolrParams;
import org.apache.solr.common.util.NamedList;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class TestAnotherCollectionWriter extends SolrCloudTestCase {

  private static final int NUM_NODES = 2;
  public static final String DATA_COLLECTION = "data";
  public static final String COORD_COLLECTION = ".sys.coord.stub";

  @BeforeClass
  public static void setupCluster() throws Exception {
    configureCluster(NUM_NODES)
            .addConfig("_default", getFile("dih/solr").toPath().resolve("configsets").resolve("_default"))
            .withSolrXml(getFile("dih/solr").toPath().resolve("solrcloud.xml"))
            .configure();

    CollectionAdminRequest.createCollection(DATA_COLLECTION, "_default", 1, 1)
            .setPerReplicaState(SolrCloudTestCase.USE_PER_REPLICA_STATE)
            .process(cluster.getSolrClient());
    // here just mimic coordinator node flow
    CollectionAdminRequest.createCollection(COORD_COLLECTION, "_default", 1, 1)
            .setPerReplicaState(SolrCloudTestCase.USE_PER_REPLICA_STATE)
            .process(cluster.getSolrClient());
  }

  static String xml = "<root>\n"
          + "<b>\n"
          + "  <id>1</id>\n"
          + "  <c>Hello C1</c>\n"
          + "</b>\n"
          + "<b>\n"
          + "  <id>2</id>\n"
          + "  <c>Hello C2</c>\n"
          + "</b>\n" + "</root>";

  @Before
  public void putOneDoc() throws SolrServerException, IOException {
    new UpdateRequest().deleteByQuery("*:*").process(cluster.getSolrClient(), DATA_COLLECTION);
    new UpdateRequest().add("id", "101").commit(cluster.getSolrClient(), DATA_COLLECTION);
  }

  @Test
  public void testAnotherCollectionFullImport() throws SolrServerException, IOException {
    int expectedAfterImport = 2;
    MapSolrParams commandParam = new MapSolrParams(new LinkedHashMap<>());
    dataImport(commandParam, expectedAfterImport);
  }

  @Test
  public void testNoClear() throws SolrServerException, IOException {
    int expectedAfterImport = 3;
    MapSolrParams commandParam = new MapSolrParams(new LinkedHashMap<>(Map.of("clean", "false")));
    dataImport(commandParam, expectedAfterImport);
  }

  @Test
  public void testNoCommit() throws SolrServerException, IOException {
    int expectedAfterImport = 1;
    MapSolrParams commandParam = new MapSolrParams(new LinkedHashMap<>(Map.of("commit", "false")));
    dataImport(commandParam, expectedAfterImport);
    new UpdateRequest().commit(cluster.getSolrClient(), DATA_COLLECTION);
    assertDocCount(2);
  }

  private static void dataImport(MapSolrParams commandParam, int expectedAfterImport) throws SolrServerException, IOException {
    commandParam.getMap().putAll(Map.of("command", "full-import",
            "synchronous", "true",
            "destination-collection", DATA_COLLECTION,
            "writerImpl", "SolrCloudWriter"
    ));
    GenericSolrRequest dih = new GenericSolrRequest(SolrRequest.METHOD.POST, "/dataimport",
            commandParam);
    dih.withContent(xml.getBytes(StandardCharsets.UTF_8), "application/json");
    SimpleSolrResponse dihRsp = dih.process(cluster.getSolrClient(), COORD_COLLECTION);

    assertEquals(0, ((NamedList<Object>) dihRsp.getResponse().get("responseHeader")).get("status"));

    assertDocCount(expectedAfterImport);
  }

  private static void assertDocCount(int expectedAfterImport) throws SolrServerException, IOException {
    QueryRequest q = new QueryRequest(new MapSolrParams(Map.of("q", "*:*")));
    QueryResponse process = q.process(cluster.getSolrClient(), DATA_COLLECTION);
    assertEquals(0, process.getStatus());
    assertEquals(expectedAfterImport, process.getResults().getNumFound());
  }
}