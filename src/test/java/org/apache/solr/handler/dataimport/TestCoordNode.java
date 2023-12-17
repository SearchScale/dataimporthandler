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
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.SimpleSolrResponse;
import org.apache.solr.cloud.SolrCloudTestCase;
import org.apache.solr.common.params.MapSolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.util.LogLevel;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

//@LogLevel("org.apache.solr=DEBUG")
public class TestCoordNode extends SolrCloudTestCase {

    private static final int NUM_NODES = 2;

    @BeforeClass
    public static void setupCluster() throws Exception {
        configureCluster(NUM_NODES)
                .addConfig("_default", getFile("dih/solr").toPath().resolve("configsets").resolve("_default"))
                .withSolrXml(getFile("dih/solr").toPath().resolve("solrcloud.xml"))
                .configure();

        CollectionAdminRequest.createCollection("data", "_default", 1, 1)
                .setPerReplicaState(SolrCloudTestCase.USE_PER_REPLICA_STATE)
                .process(cluster.getSolrClient());

        CollectionAdminRequest.createCollection(".sys.coord.stub", "_default", 1, 1)
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
    @Test
    public void testFirst() throws SolrServerException, IOException {
//"dataConfig","dataconfig-contentstream.xml"
        GenericSolrRequest dih = new GenericSolrRequest(SolrRequest.METHOD.POST, "/dataimport", new MapSolrParams(
                Map.of("command","full-import",
                "synchronous","true",
                        "destination","data",
                        "writerImpl",""
                        //"collection","data" - hell , collection is handled to forward full-import to the coll
                )));
        dih.withContent(xml.getBytes("UTF-8"),"application/json");
        SimpleSolrResponse dihRsp = dih.process(cluster.getSolrClient(".sys.coord.stub"), ".sys.coord.stub");

        assertEquals(0,((NamedList<Object>)dihRsp.getResponse().get("responseHeader")).get("status"));

        QueryRequest q = new QueryRequest(new MapSolrParams(Map.of("q", "*:*")));
        QueryResponse process = q.process(cluster.getSolrClient("data"), "data");
        assertEquals(0, process.getStatus());
        assertEquals(2, process.getResults().getNumFound());
        //fail(dihrsp.toString());
    }
}