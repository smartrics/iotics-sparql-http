import requests
import urllib.parse
import sys

def load_env(file_path):
    env_vars = {}
    with open(file_path) as f:
        for line in f:
            if line.strip() and not line.startswith("#"):
                key, value = line.strip().split('=', 1)
                env_vars[key] = value
    return env_vars

def setUp(endpoint_url, env_vars):
    global client, bearer, headers

    client = requests.Session()
    user_key = env_vars.get("USER_KEY")
    user_seed = env_vars.get("USER_SEED")
    bearer = f"{user_key}:{user_seed}"
    headers = {
        "Authorization": f"Bearer {bearer}"
    }
    return endpoint_url

def test_select_query_via_get(endpoint_url):
    query = "SELECT * WHERE { ?s ?p ?o } LIMIT 10"
    encoded_query = urllib.parse.quote(query)
    url = f"{endpoint_url}?query={encoded_query}"

    response = client.get(url, headers=headers)
    assert response.status_code == 200
    response_body = response.json()
    assert response_body.get("results") is not None
    print("test_select_query_via_get passed")

def test_ask_query_via_get(endpoint_url):
    query = "ASK WHERE { ?s ?p ?o }"
    encoded_query = urllib.parse.quote(query)
    url = f"{endpoint_url}?query={encoded_query}"

    response = client.get(url, headers=headers)
    assert response.status_code == 200
    response_body = response.json()
    assert response_body.get("boolean") is not None
    print("test_ask_query_via_get passed")

def test_describe_query_via_get(endpoint_url):
    resource_uri = find_resource_uri(endpoint_url)
    query = f"DESCRIBE <{resource_uri}>"
    encoded_query = urllib.parse.quote(query)
    url = f"{endpoint_url}?query={encoded_query}"
    headers_copy = headers.copy()
    headers_copy["Accept"] = "application/rdf+xml"

    response = client.get(url, headers=headers_copy)
    assert response.status_code == 200
    response_body = response.text
    assert response_body is not None
    assert "rdf:RDF" in response_body
    print("test_describe_query_via_get passed")

def find_resource_uri(endpoint_url):
    query = "SELECT * WHERE { ?s ?p ?o } LIMIT 1"
    encoded_query = urllib.parse.quote(query)
    url = f"{endpoint_url}?query={encoded_query}"

    response = client.get(url, headers=headers)
    assert response.status_code == 200
    response_body = response.json()
    return response_body["results"]["bindings"][0]["s"]["value"]

def test_construct_query_via_get(endpoint_url):
    query = "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o } LIMIT 10"
    encoded_query = urllib.parse.quote(query)
    url = f"{endpoint_url}?query={encoded_query}"
    headers_copy = headers.copy()
    headers_copy["Accept"] = "text/turtle"

    response = client.get(url, headers=headers_copy)
    assert response.status_code == 200
    response_body = response.text
    assert response_body is not None
    print("test_construct_query_via_get passed")

def test_service_description(endpoint_url):
    url = endpoint_url

    response = client.get(url, headers=headers)
    assert response.status_code == 200
    response_body = response.json()
    assert response_body.get("@context") is not None
    print("test_service_description passed")

def test_query_via_post_form(endpoint_url):
    query = "SELECT * WHERE { ?s ?p ?o } LIMIT 10"
    encoded_query = urllib.parse.quote(query)
    url = endpoint_url
    headers_copy = headers.copy()
    headers_copy["Content-Type"] = "application/x-www-form-urlencoded"

    response = client.post(url, headers=headers_copy, data=encoded_query)
    assert response.status_code == 200
    response_body = response.json()
    assert response_body.get("results") is not None
    print("test_query_via_post_form passed")

def test_query_via_post_sparql(endpoint_url):
    query = "SELECT * WHERE { ?s ?p ?o } LIMIT 10"
    url = endpoint_url
    headers_copy = headers.copy()
    headers_copy["Content-Type"] = "application/sparql-query"
    headers_copy["Accept"] = "*/*"

    response = client.post(url, headers=headers_copy, data=query)
    assert response.status_code == 200
    response_body = response.json()
    assert response_body.get("results") is not None
    print("test_query_via_post_sparql passed")

def test_query_with_default_graph_uri(endpoint_url):
    query = "SELECT * WHERE { ?s ?p ?o } LIMIT 10"
    encoded_query = urllib.parse.quote(query)
    default_graph_uri = "http://example.org/default-graph"
    encoded_default_graph_uri = urllib.parse.quote(default_graph_uri)
    url = f"{endpoint_url}?query={encoded_query}&default-graph-uri={encoded_default_graph_uri}"

    response = client.get(url, headers=headers)
    assert response.status_code == 400
    print("test_query_with_default_graph_uri passed")

def test_query_with_named_graph_uri(endpoint_url):
    query = "SELECT * WHERE { GRAPH <http://example.org/named-graph> { ?s ?p ?o } } LIMIT 10"
    encoded_query = urllib.parse.quote(query)
    url = f"{endpoint_url}?query={encoded_query}"

    response = client.get(url, headers=headers)
    assert response.status_code == 200
    response_body = response.json()
    assert response_body.get("results") is not None
    print("test_query_with_named_graph_uri passed")

def main():
    if len(sys.argv) < 2 or len(sys.argv) > 3:
        print("Usage: python <name_of_your_program>.py <endpoint_url> [<path_to_env_file>]")
        sys.exit(1)

    endpoint_url = sys.argv[1]
    env_file = sys.argv[2] if len(sys.argv) == 3 else ".env"
    env_vars = load_env(env_file)
    setUp(endpoint_url, env_vars)
    test_select_query_via_get(endpoint_url)
    test_ask_query_via_get(endpoint_url)
    test_describe_query_via_get(endpoint_url)
    test_construct_query_via_get(endpoint_url)
    test_service_description(endpoint_url)
    test_query_via_post_form(endpoint_url)
    test_query_via_post_sparql(endpoint_url)
    test_query_with_default_graph_uri(endpoint_url)
    test_query_with_named_graph_uri(endpoint_url)

if __name__ == "__main__":
    main()
