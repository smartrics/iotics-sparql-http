# IOTICSpace SPARQL over HTTP 

A connector proxying sparql over HTTP to the gRPC federated graph of an IOTICS network.

## Build and Test

Build with

```commandline
mvn clean package
```

then

```commandline
java -jar iotics-sparql-http-<version>.jar
```

### Test

To run the integration tests, you need to create an .env file with the following content

```properties
PORT=<the port where the HTTP endpoint is listening>
HOST_DNS=<the host DNS where to forward the gRPC requests>
RESOLVER_URL=<the resolver used to manage identities (find it at https://{HOST_DNS}/index.json)>
SEED=<a valid identity seed>
```

To run the proxy with pre-canned authentication 
(useful if you want to use the proxy with existing plugins that don't handle IOTICS Identity) you need to configure
a valid TOKEN adding it to the `.env` file

```properties
TOKEN=<eyJhbG...sjc4Q_Q>
```

## Use

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

