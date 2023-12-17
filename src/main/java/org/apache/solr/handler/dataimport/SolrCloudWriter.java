package org.apache.solr.handler.dataimport;

import org.apache.solr.common.SolrInputDocument;

import java.util.Map;
import java.util.Set;

// constructor
// license
// connect to cluster client, forward update.
public class SolrCloudWriter implements DIHWriter{
    @Override
    public void commit(boolean optimize) {

    }

    @Override
    public void close() {

    }

    @Override
    public void rollback() {

    }

    @Override
    public void deleteByQuery(String q) {

    }

    @Override
    public void doDeleteAll() {

    }

    @Override
    public void deleteDoc(Object key) {

    }

    @Override
    public boolean upload(SolrInputDocument doc) {

        return false;
    }

    @Override
    public void init(Context context) {

    }

    @Override
    public void setDeltaKeys(Set<Map<String, Object>> deltaKeys) {

    }
}
