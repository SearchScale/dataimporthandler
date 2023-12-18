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

import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.cloud.CloudDescriptor;
import org.apache.solr.cloud.ZkController;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.cloud.ClusterState;
import org.apache.solr.common.cloud.DocCollection;
import org.apache.solr.common.cloud.Slice;
import org.apache.solr.common.cloud.ZkCoreNodeProps;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.update.AddUpdateCommand;
import org.apache.solr.update.SolrCmdDistributor;
import org.apache.solr.update.processor.UpdateRequestProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.Map;

public class SolrCloudWriter extends SolrWriter /*weird but it seems it's worth to inherit finish */ {

    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public static final String DST_COLL_PARAM = "destination-collection";
    private final Http2SolrClient updateClient;
    private final String destColl;
    private final DocCollection destDocColl;
    private final SolrCmdDistributor solrCmdDistributor;

    public SolrCloudWriter(UpdateRequestProcessor processor, SolrQueryRequest req) {
        super(processor, req);
        updateClient = req.getCoreContainer().getUpdateShardHandler().getUpdateOnlyHttpClient();
        destColl = req.getParams().get(DST_COLL_PARAM);
        if (destColl == null) {
            throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
                    SolrWriter.class.getCanonicalName() + " requires " + DST_COLL_PARAM);
        }
        CoreContainer cc = req.getCoreContainer();
        CloudDescriptor cloudDesc = req.getCore().getCoreDescriptor().getCloudDescriptor();
        ZkController zkController = cc.getZkController();
        ClusterState clusterState = zkController.getClusterState();
        destDocColl = clusterState.getCollection(destColl); // existential assert here

        solrCmdDistributor = new SolrCmdDistributor(req.getCoreContainer().getUpdateShardHandler());
    }

    @Override
    public void commit(boolean optimize) {
        try {
            // can;t manage to commit via SolrCmdDistributor
            // List<SolrCmdDistributor.Node> collectionUrls = getCollectionUrls(destDocColl, EnumSet.of(Replica.Type.TLOG, Replica.Type.NRT), true);
            solrCmdDistributor.blockAndDoRetries();//distribCommit(commit, collectionUrls, new ModifiableSolrParams());
            String baseUrl = destDocColl.getActiveSlicesArr()[0].getLeader().getBaseUrl();
            UpdateRequest cmt = new UpdateRequest();
            cmt.setAction(optimize ? UpdateRequest.ACTION.OPTIMIZE : UpdateRequest.ACTION.COMMIT, true, true);
            cmt.setBasePath(baseUrl);
            cmt.process(updateClient, destColl);
        } catch (Exception e) {
            log.warn("Error commit back: ", e);
        }
    }


    @Override
    public void close() {
        try {
            super.close(); // some legacy housekeeping
        } finally {
            solrCmdDistributor.close();
        }
    }

    @Override
    public void rollback() {
        try {
            solrCmdDistributor.blockAndDoRetries();// but i'd rather nuke'em
            String baseUrl = destDocColl.getActiveSlicesArr()[0].getLeader().getBaseUrl();
            UpdateRequest cmt = new UpdateRequest();
            cmt.rollback();
            cmt.setBasePath(baseUrl);
            cmt.process(updateClient, destColl);
        } catch (Exception e) {
            log.warn("Error rolling back: ", e);
        }
    }

    @Override
    public void deleteByQuery(String q) {
        try {
            //updateClient.deleteByQuery(destColl, q, commitWithin);
            solrCmdDistributor.blockAndDoRetries();
            String baseUrl = destDocColl.getActiveSlicesArr()[0].getLeader().getBaseUrl();
            UpdateRequest cmt = new UpdateRequest();
            cmt.deleteByQuery(q); // TODO pass commitWithin
            cmt.setBasePath(baseUrl);
            cmt.process(updateClient, destColl);
        } catch (Exception e) {
            log.warn("Error deleting by: " + q, e);
        }
    }

    @Override
    public void doDeleteAll() {
        try {
            solrCmdDistributor.blockAndDoRetries();// but i'd rather nuke'em
            String baseUrl = destDocColl.getActiveSlicesArr()[0].getLeader().getBaseUrl();
            UpdateRequest cmt = new UpdateRequest();
            cmt.deleteByQuery("*:*"); // TODO pass commitWithin
            cmt.setBasePath(baseUrl);
            cmt.process(updateClient, destColl);
        } catch (Exception e) {
            log.warn("Error deleting all docs : ", e);
        }
    }

    @Override
    public void deleteDoc(Object key) {
        try {
            updateClient.deleteById(destColl, key.toString(), commitWithin);
            solrCmdDistributor.blockAndDoRetries();
            String baseUrl = destDocColl.getActiveSlicesArr()[0].getLeader().getBaseUrl();
            UpdateRequest cmt = new UpdateRequest();
            cmt.deleteById(key.toString()); // TODO pass commitWithin
            cmt.setBasePath(baseUrl);
            cmt.process(updateClient, destColl);
        } catch (Exception e) {
            log.warn("Error deleting document : " + key, e);
        }
    }

    @Override
    public boolean upload(SolrInputDocument doc) {
        AddUpdateCommand command = new AddUpdateCommand(req);// accesses schema !
        command.solrDoc = doc;

        Slice slice = destDocColl.getRouter().getTargetSlice(command.getIndexedIdStr(), doc, null, req.getParams(), destDocColl);

        try {
            solrCmdDistributor.distribAdd(command, Collections.singletonList(
                    new SolrCmdDistributor.StdNode(new ZkCoreNodeProps(slice.getLeader()), destColl, slice.getName())), new ModifiableSolrParams(Map.of("commitWithin", new String[]{Integer.toString(commitWithin)})));
            return true;
        } catch (Exception e) {
            log.warn("Error creating document : " + doc, e);
            return false;
        }
    }
}
