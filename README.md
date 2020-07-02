# Data Import Handler
A Solr package for importing records from database systems into Apache Solr collections.

## Installing and running

* Start Solr (version 8.6.0) nodes with -Denable.packages=true

    `bin/solr -c -Denable.packages=true`

* Add repository:

    `bin/solr package add-repo data-import-handler "bin/solr package add-repo data-import-handler "https://raw.githubusercontent.com/rohitbemax/dataimporthandler/master/repo/"

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


## Known Issues

* Only MariaDB connector supported right now. The connectors to be used need to be shipped as part of this package, and currently only MariaDB connector is shipped.

* Solr Admin UI's Dataimport tab could be a bit glitchy when used with this package
