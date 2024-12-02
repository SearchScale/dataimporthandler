# Data Import Handler
An Apache Solr™ package for importing records from database systems into Apache Solr collections.

![DIH Build](https://github.com/rohitbemax/dataimporthandler/workflows/DIH%20CI/badge.svg)

## About

This is a 3rd party package for Apache Solr, aiming at maintaining the [Data Import Handler](https://lucene.apache.org/solr/guide/8_10/uploading-structured-data-store-data-with-the-data-import-handler.html) since it will no longer ship with the default Solr download in the future. 

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

### Use DIH to import from Snowflake into Solr
Configurations 
1. solr-security-policy : Following permissions should be configured in solr for the Snowflake JDBC driver to be accessible at runtime. The security config file is under /solr/server/etc/security.policy 

```
permission java.security.SecurityPermission "putProviderProperty.BC";
permission java.io.FilePermission "/home/ubuntu/ec2user/caches/Snowflake", "read,write,delete";
permission java.io.FilePermission "/home/ubuntu/ec2user/caches/Snowflake/*", "read,write,delete";
```
2. Data Config 
Snowflake [JDBC Driver](https://docs.snowflake.com/en/developer-guide/jdbc/jdbc)

```
<dataConfig>
        <dataSource type="JdbcDataSource" driver="net.snowflake.client.jdbc.SnowflakeDriver" url="jdbc:snowflake://host:443?db=<>;warehouse=<>;schema=<>;role=<>;account=<>" user="<>" password="<>">
        </dataSource>
        <document>
                <entity name="table_name" query="select id, name from table_name">
                        <field column="id" name="id" ></field>
                        <field column="name" name="name" ></field>
                </entity>
        </document>
</dataConfig>
```
* Replace the "driver" url with the host, port, database schema and user credentials. 

* The password can to be encrypted as per the [docs](https://solr.apache.org/guide/8_6/uploading-structured-data-store-data-with-the-data-import-handler.html#encrypting-a-database-password)

* Use explicit (non-dynamic) fields in Solr schema, that need to be mapped to snowflake.

* Partial updates are not supported by default. Use [script processor](https://solr.apache.org/guide/8_6/uploading-structured-data-store-data-with-the-data-import-handler.html#the-scripttransformer) to implement partial updates. Add the attribute transformer="script:DeltaIndexTransformer" to the entity of the DIH. For example :
```
 <script><![CDATA[
                function DeltaIndexTransformer(row) {
                    row.keySet().forEach(function (field) {
                        if (field != 'id') {
                            value = row.get(field);
                            row.put(field, {
                                'set': value,
                            });
                        }
                    });
                    return row;
                }
                ]]></script>
```



## Contributing

The source code for DIH versions that are compatible with Solr 8.x are in branch_8x branch (branch_9x for Solr 9.x). Please feel free to open issues and/or open pull requests against that branch.

### Releases

In order to upgrade the support for a newer Solr version, you need write access to this repository and should follow these steps:

* Raise and merge a PR to update the Solr version in the pom.xml file. Make sure `mvn clean compile test` works fine.
* Push a tag for the released version. For example: `git tag v9.6.1 -a` and `git push origin --tags`
* Observe the Actions tab to see the release artifacts being built.

## Known Issues

* Only MariaDB connector supported out-of-the-box right now. The connectors to be used need to be shipped as part of this package, and currently only MariaDB connector is shipped.

* Solr Admin UI's Dataimport tab could be a bit glitchy when used with this package

## Attribution

The code in this repository was originally a fork of [the original DIH code in Solr project](https://github.com/apache/lucene-solr/tree/branch_8_6/solr/contrib/dataimporthandler) and the original code is (C) Apache Software Foundation.

Additional work done in this repository is (C) the respective authors and licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
