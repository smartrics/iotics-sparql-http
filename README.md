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
TOKEN_DURATION=<ISO 8601 period string>
```

Token duration follows the ISO 8601 spec as described [here](https://docs.oracle.com/javase/8/docs/api/java/time/Duration.html#parse-java.lang.CharSequence-). For example:

    "PT20.345S" -- parses as "20.345 seconds"
    "PT15M"     -- parses as "15 minutes" (where a minute is 60 seconds)
    "PT10H"     -- parses as "10 hours" (where an hour is 3600 seconds)
    "P2D"       -- parses as "2 days" (where a day is 24 hours or 86400 seconds)
    "P2DT3H4M"  -- parses as "2 days, 3 hours and 4 minutes"

## Build and run docker image

https://hub.docker.com/repository/docker/smartrics/iotics-sparql-http/general

```shell
docker build -t smartrics/iotics-sparql-http:<tag> .
```

To run, make sure you create a directory locally where logs can be stored. 
For example, `/path/to/host/logs`. 

```shell
docker run -d -p 8080:8080 --env-file .env.demo.dev -v /path/to/host/logs:/app/logs smartrics/iotics-sparql-http:<tag>
```

## Use

The proxy should implement most of https://www.w3.org/TR/sparql11-protocol/ for `SELECT`.

### Endpoints

The following endpoints are supported for GET and POST

| Endpoint        | description                                      |
|-----------------|--------------------------------------------------|
| `/sparql/local` | for requests scoped to the local IOTICSpace only | 
| `/sparql`       | for requests scoped to the network               | 

| Health endpoint | description                                              |
|-----------------|----------------------------------------------------------|
| `/health`       | accepts only GET requests, healthy if response is 200 OK | 

### Required headers

```properties
Accept: application/sparql-results+json
```
### Optional header

```properties
Authorization: Bearer <token>
```
or
```properties
Authorization: Bearer <userKey:userSeed>
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

