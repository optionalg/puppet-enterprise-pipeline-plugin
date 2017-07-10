# Introduction

This plugin adds Jenkins Pipeline steps for Puppet Enterprise. The provided
steps make it easy to interface with Puppet Enterprise services such as the
code management service and orchestrator service.

# Features

A Pipeline project can use the provided groovy methods to deploy Puppet code to
Puppet Enterprise servers and create Puppet orchestrator jobs.

Puppet Enterprise RBAC access tokens are used to authenticate with the Puppet
Enterprise APIs, so Puppet itself doesn't have to be configured on the Jenkins
server.

```
node {
    puppet.credentials 'pe-access-token'
    puppet.codeDeploy 'production'
    puppet.job 'production', target: 'App[instance]', noop: true, concurrency: 10
}
```

### Experimental Hiera Feature

This plugin also provides an experimental feature that provides a Hiera
key/value store for Hiera. Key/value pairs are set using the provided
`puppet.hiera` method.  Pairs are assigned to specific Puppet environments.  The
[hiera-http](https://github.com/crayfishx/hiera-http)  backend performs a key lookup for the requesting node's
Puppet environment. An example hiera.yaml configuration:

```
:backends:
  - http

:http:
  :host: jenkins.example.com
  :port: 8080
  :output: json
  :use_auth: true
  :auth_user: <user>
  :auth_pass: <pass>
  :cache_timeout: 10
  :failure: graceful
  :paths:
    - /hiera/lookup?path=%{clientcert}&key=%{key}
    - /hiera/lookup?path=%{environment}&key=%{key}
```


## Configuration

### Puppet Master Address

Go to Jenkins > Manage Jenkins > Puppet Enterprise.  Fill out the DNS address
of the Puppet Enterprise Server.  Note, if the Puppet agent is installed on the
Jenkins server, it will be used to configure the Puppet Enterprise Server
address.

The Puppet Enterprise Server CA certificate is automatically pulled from the
Puppet Server's CA API. External CA's are not currently supported.

### Access Token Credentials

This plugin uses the [Plain Credentials plugin](https://wiki.jenkins-ci.org/display/JENKINS/Plain+Credentials+Plugin) to store access tokens.  

First, create a new RBAC access token in Puppet Enterprise. Follow the instructions for [generating a token for use by a service](https://docs.puppet.com/pe/latest/rbac_token_auth.html#generating-a-token-for-use-by-a-service).

Second, create new credentials in Jenkins. For **Kind** specify **Secret
text**.  Set the **Secret** value to the token printed out by the first step.
It is recommended to give the token an easy-to-identiy **ID** by clicking on
the **Advanced** button. That will make it easier to identify which token is
being used from the Jenkins Pipeline scripts.

### Hiera

#### Hiera HTTP authentication

If Jenkins' Global Security is configured to allow unauthenticated read-only
access, the 'use_auth', 'auth_pass', and 'auth_user' parameters in the
hiera.yaml file are unnecessary. Otherwise, create a local Jenkins user that
has Overall/Read permissions use that user's credentials for the hiera.yaml
configuration.

#### Hiera Data Store permissions

If Jenkins' Global Security is configured to use matrix authorization, any user
with the Hiera/View permission is allowed to view the Hiera Data Store page and
any user with the Hiera/Delete permission can delete scopes and keys.  

Note, these permissions have no effect on the ability to lookup specificy Hiera
keys using the /hiera/lookup endpoint.

To set Hiera values from the Jenkins Pipeline script:

```
node {
  puppet.hiera path: 'host.example.com', key: 'keyname', value: 'keyvalue'
  puppet.hiera path: 'dc-location', key: 'keyname', value: 'keyvalue'
  puppet.hiera path: 'production', key: 'keyname', value: 'keyvalue'
}
```

This is experimental because the values are stored in an XML file on the
Jenkins server.  There is no audit history of the data and therefor no way to
replicate past values. Also, if the file is lost due to, for example, disk
failure, the current values are lost.  So only use this if you trust your
Jenkins server backups and don't care about audit history.

## Pipeline Steps

### puppet.credentials

The `puppet.credentials` method sets the Puppet Enterprise RBAC token to be
used for all other Puppet pipeline step methods.

**groovy script invocation**: puppet.credentials 'jenkins-credential'

**Example**

```
  puppet.credentials 'pe-access-token'
```

### puppet.query

The `puppet.query` method queries PuppetDB using the PQL query language. To
learn more about PQL, go here:
[https://docs.puppet.com/puppetdb/4.3/api/query/v4/pql.html]

This method returns an ArrayList object that can be stored in a variable and iterated on.

**groovy script invocation**: puppet.query 'query'

**Parameters**

* credentials - The Jenkins credentials storing the PE RBAC token. String. Required if puppet.credentials not used.

**Example**

```
  puppet.query 'nodes[certname] { catalog_environment = "staging" }', credentials: 'pe-access-token'
  results = puppet.query 'inventory[certname] { trusted.extensions.pp_role = "MyApp" }'

  //The following gets production nodes with failed report, extracts just their
  // certnames, then groups them by three, and finally runs Puppet on each
  // group of three.
  results = puppet.query 'nodes { latest_report_status = "failed" and catalog_environment = "production"}'
  nodes = []
  for (Map node : results) {
    nodes.add(node.certname)
  }
  nodesubgroups = nodes.collate(3) //Break results into groups of 3
  for (String certnames : nodesubgroups ) {
    puppet.job 'production', nodes: certnames
  }
```

### puppet.codeDeploy

The `puppet.codeDeploy` method tells Puppet Enterprise to deploy new Puppet code,
Hiera data, and modules to a specified Puppet environment. To lean more about
code management in Puppet Enterprise, go here: [https://docs.puppet.com/pe/latest/code_mgr.html]

**groovy script invocation**: puppet.codeDeploy 'environment'

**Parameters**

* credentials - The Jenkins credentials storing the PE RBAC token. String. Required if puppet.credentials not used.

**Example**

```
  puppet.codeDeploy 'production', credentials: 'pe-access-token'
  puppet.codeDeploy 'staging'
```

### puppet.job

**groovy script invocation**: puppet.job('environment')

**Parameters**

* credentials - ID of the Jenkins Secret text credentials. String. Required if puppet.credentials not used
* concurrency - Level of maximum concurrency when issuing Puppet runs. Defaults to unlimited. Integer.
* noop - Whether to run Puppet in noop mode. Defaults to false. Boolean
* reports - The type of reports you'd like to be printed to Jenkins console. See options below Example section. Defaults to nodeSummary. Array of Strings.

**Puppet Enterprise 2016.2 - 2016.3 Parameters**

The following parameters should be used with Puppet Enterprise 2016.2 - 2016.3 for definining the job's run target.
Note, the target parameter will work with Puppet Enterprise 2016.4+ but has been deprecated.

* target - Target in environment to deploy to. Can be app, app instance, or app component. Defaults to entire environment. String

**Puppet Enterprise 2016.4+ Parameters**

The following parameters should be used with Puppet Enterprise 2016.4+ for definining the job's run scope.

* nodes - An array of nodes to run Puppet on.
* application - The name of the application to deploy to. Can be all instances or a specific instance. e.g 'MyApp' or 'MyApp[instance-1]'. String.
* query - The PQL query to determine the list of nodes to run Puppet on. String.

**Example**

```
  puppet.job 'staging'
  puppet.job 'production', concurrency: 10, noop: true
  puppet.job 'production', concurrency: 10, noop: true, credentials: 'pe-access-token'
  puppet.job 'production', nodes: ['node1.example.com','node2.example.com']
  puppet.job 'production', application: Rgbank
  puppet.job 'production', application: Rgbank[phase-1]
  puppet.job 'production', query: 'nodes { certname ~ "substring" and environment = "production" }'
  puppet.job 'production', reports: ['resourceChanges', 'nodeChanges']
```

**Reports**

The following report types are available to be printed to the Jenkins console for each job report.
Use the `report` parameter. Multiple reports can be selected by using an array.

- nodeSummary:  Default. Shows a summary of the resource events count with a link to the Puppet Enterprise report.
- nodeChanges:  A list of every resource event per node.
- resourceChanges: A list of every resource event and each node that experienced the change event.


### puppet.nodeGroup

This pipeline step creates, modifies, and deletes node groups in Puppet Enterprise's Node Manager.

**Procedural pipeline invocation**: puppet.nodeGroup

**Declarative piepline invocation**: puppetNodeGroup

**Parameters**

* name - The name of the node group. Required. String
* description - The description of the node group. String
* environment - The environment of the node group. String
* rule - The rules that nodes must match to automatically be added to the node group. See the [node manager rules grammer](https://docs.puppet.com/pe/latest/nc_groups.html#rule-condition-grammar) for syntax.
* parent - The name of the node group's parent group. Required if the node group doesn't exist already. String.
* environmentTrumps - Whether the environment for the group should be the environment member nodes should have their catalog compiled for. Boolean
* classes - The classes that should be assigned to the node group. Hash
* variables - The global variables that should be assigned to the node group. Hash
* delete - Wether or not to delete the group. It will error if the group does not exist. Boolean
* credentials - The Jenkins credentials storing the PE RBAC token. Required if puppet.credentials not used. String

```
  puppet.nodeGroup name: "App Group", parent: "All Nodes", description: "Made by Jenkins", classes: ["class_name": ["parameterkey":"parametervalue"]], rule: ["=", ["trusted","extensions","pp_application"], "app"]
  puppet.nodeGroup name: "App Group", classes: ["class_name": ["parameterkey":"NEWvalue"]]
  puppet.nodeGroup name: "App Group", delete: true
```


### puppet.hiera

**groovy script invocation**: puppet.hiera

**Parameters**

* path - The path (scope) of the data lookup from Hiera. Usually this will be an environment name. Required. String
* key - The name of the key that Hiera will lookup. Required. String
* value - The value of the key to be returned to Hiera's lookup call. Required. Can be string, array, or hash

**Example**

```
  puppet.hiera path: 'staging', key: 'app-build-version', value: 'master'
  puppet.hiera path: 'production', key: 'app-build-version', value: '8f3ea2'
  puppet.hiera path: 'dc1-us-example', key: 'list-example', value: ['a,'b','c']
  puppet.hiera path: 'host.example.com', key: 'hash-example', value: ['a':1, 'bool':false, 'c': 'string']
```

# Compatibility

This plugin is compatible with Puppet Enterprise 2016.2+ and Jenkins 1.642.3+

Some step parameters are only available on newer versions of Puppet Enterprise.
Those parameters are labeled as such.

This plugin has not been tested with Jenkins Declarative Pipelines.
