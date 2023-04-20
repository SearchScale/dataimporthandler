# Data Import Handler
An Apache Solr™ package for importing records from database systems into Apache Solr collections.

![DIH Build](https://github.com/rohitbemax/dataimporthandler/workflows/DIH%20CI/badge.svg)

## About

This is a 3rd party package for Apache Solr, aiming at maintaining the [Data Import Handler](https://lucene.apache.org/solr/guide/8_6/uploading-structured-data-store-data-with-the-data-import-handler.html) since it will no longer ship with the default Solr download in the future. 

The success of this project relies on contributions from the broader community through Pull Requests. See "Issues" sections and help in any way you see fit.

## Installing and running

* Start Solr (version 9.X master) nodes with -Denable.packages=true

    `bin/solr -c -Denable.packages=true`

* Add repository:

    `bin/solr package add-repo data-import-handler "https://raw.githubusercontent.com/searchscale/dataimporthandler/master/repo/"`

* See available packages:

    `bin/solr package list-available`

* Install the package

    `bin/solr package install data-import-handler`

* Create a products collection

    `curl "http://localhost:8983/solr/admin/collections?action=CREATE&name=products&numShards=1"`

* Deploy package on the collection

    `bin/solr package deploy data-import-handler -y -collections products`

* Create the DB configurations file

    Create a new file `data-config.xml`:
    ```
    <dataConfig>
        <dataSource type="JdbcDataSource" driver="org.mariadb.jdbc.Driver" 
                url="jdbc:mysql://localhost/myproducts" user="root" password="password"/>
        <document>
            <entity name="product" query="select ProductID AS id, Name as name_t from products"></entity>
        </document>
    </dataConfig>
    ```

* Add the configurations and reload the collection

    `./server/scripts/cloud-scripts/zkcli.sh -z localhost:9983 -cmd putfile "/configs/products.AUTOCREATED/data-config.xml" data-config.xml`

    `curl "localhost:8983/solr/admin/collections?action=RELOAD&name=products"`

* Run data import

    `curl http://localhost:8983/solr/products/dataimport?command=full-import`

    `curl "http://localhost:8983/solr/products/select?q=*:*"`

## Contributing

The source code for DIH versions that are compatible with Solr 8.x are in branch_8x branch (branch_9x for Solr 9.x). Please feel free to open issues and/or open pull requests against that branch.

## Known Issues

* Only MariaDB connector supported out-of-the-box right now. The connectors to be used need to be shipped as part of this package, and currently only MariaDB connector is shipped.

* Solr Admin UI's Dataimport tab could be a bit glitchy when used with this package

## Attribution

The code in this repository was originally a fork of [the original DIH code in Solr project](https://github.com/apache/lucene-solr/tree/branch_8_6/solr/contrib/dataimporthandler) and the original code is (C) Apache Software Foundation.

Additional work done in this repository is (C) the respective authors and licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).