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

the following variables are either red from the environment or via program arguments.
Environment values will always be preferred

| Property  | default | optional | description                                                                                                                                   | 
|-----------|---------|----------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| HOST_DNS  | n/a     | yes      | the DNS of the host where to forward gRPC requests to. If omitted, the host will be taken via the `X-IOTICS-HOST`                             |
| PORT | 8080    | yes | port where the porxy http listener is deployed                                                                                                |
| TOKEN | n/a | yes | a valid token passed on by the proxy in lieu of the one extracted via the `Authorization` header. The latter will always override this value. | 

Example:

```
java -jar iotics-sparql-http-<version>.jar HOST_DNS=myhost.iotics.space PORT=80
```


### Test

To run the integration tests, you need to create an .env file with the following content

```properties
PORT=<the port where the HTTP endpoint is listening>
HOST_DNS=<the host DNS where to forward the gRPC requests>
RESOLVER_URL=<the resolver used to manage identities (find it at https://{HOST_DNS}/index.json)>
SEED=<a valid identity seed>
```

## Use

The proxy should implement most of https://www.w3.org/TR/sparql11-protocol/ for `SELECT`.

### Endpoints

The following endpoints are supported for GET and POST

| Endpoint      | description                                      |
|---------------|--------------------------------------------------|
| /sparql/local | for requests scoped to the local IOTICSpace only | 
| /sparql       | for requests scoped to the network               | 

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

### Required headers for dynamic proxying
```properties
X-IOTICS-HOST: <host DNS>
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

