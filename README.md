# IOTICSpace SPARQL over HTTP 

A connector proxying sparql over HTTP to the gRPC federated graph of an IOTICS network.

## Build and Test

Build with

```commandline
mvn clean package
```

### Run

```
java -jar iotics-sparql-http-<version>.jar
```

The following variables are read from the environment or via system properties arguments.
Environment values will always be preferred if not null.

| Property   | default | optional | description                                                                                                                                   | 
|------------|---------|----------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| HOST_DNS   | n/a     | no       | the DNS of the host where to forward gRPC requests to. If omitted, the host will be taken via the `X-IOTICS-HOST`                             |
| PORT       | 8080    | yes      | port where the porxy http listener is deployed                                                                                                |
| AGENT_SEED | n/a     | no       | port where the porxy http listener is deployed                                                                                                |

Example:

```
java -DHOST_DNS=myhost.iotics.space -DPORT=80 -jar iotics-sparql-http-<version>.jar 
```


### Integration Tests

To run the integration tests(manually, from within the IDE), you need to create an .env file with the following content

```properties
PORT=<the port where the HTTP endpoint is listening>
HOST_DNS=<the host DNS where to forward the gRPC requests>
RESOLVER_URL=<the resolver used to manage identities (find it at https://{HOST_DNS}/index.json)>
AGENT_SEED=<a valid identity seed>
USER_SEED=<a valid identity seed>
USER_KEY=<a valid key>
```


## Use

The proxy should implement most of https://www.w3.org/TR/sparql11-protocol/ for `SELECT`.

### Endpoints

The following endpoints are supported for GET and POST

| Endpoint        | description                                      |
|-----------------|--------------------------------------------------|
| `/sparql/local` | for requests scoped to the local IOTICSpace only | 
| `/sparql`       | for requests scoped to the network               | 

### Required headers

```properties
Authorization: Bearer <token>
Accept: application/sparql-results+json
```

### Required headers for a SPARQL via POST

for an unencoded body

```properties
Content-Type: application/sparql-query
```

for a form encoded query in the body

```properties
Content-Type: application/x-www-form-urlencoded
```

### SPARQL request example

Assuming the proxy is deployed on localhost:8080

```http
GET /sparql/local?query=<urlencoded query> HTTP/1.1
Host: localhost
Accept: application/sparql-results+json
Authorization: Bearer eyJhbG...sjc4Q_Q
```

```http
POST /sparql/local HTTP/1.1
Host: localhost
Accept: application/sparql-results+json
Authorization: Bearer eyJhbG...sjc4Q_Q
Content-Type: application/sparql-query

<query>
```

## Limitations

* No support for `default-graph-uri`and `named-graph-uri`
* No support for CORS and OPTION (pre-flight requests)

