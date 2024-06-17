import requests
import urllib.parse
import sys
import concurrent.futures
import logging
from logging.handlers import RotatingFileHandler

def setup_logging():
    logger = logging.getLogger("SPARQLTests")
    logger.setLevel(logging.DEBUG)

    # Create console handler for logging to console
    console_handler = logging.StreamHandler()
    console_handler.setLevel(logging.DEBUG)

    # Create rotating file handler for logging to file with rollover
    file_handler = RotatingFileHandler("sparql_tests.log", maxBytes=10*1024*1024, backupCount=5)  # 10MB per file, keep 5 backups
    file_handler.setLevel(logging.DEBUG)

    # Create formatter and add it to the handlers
    formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
    console_handler.setFormatter(formatter)
    file_handler.setFormatter(formatter)

    # Add the handlers to the logger
    logger.addHandler(console_handler)
    logger.addHandler(file_handler)

    return logger

def load_env(file_path):
    env_vars = {}
    with open(file_path) as f:
        for line in f:
            if line.strip() and not line.startswith("#"):
                key, value = line.strip().split('=', 1)
                env_vars[key] = value
    return env_vars

def setUp(endpoint_url, env_vars):
    global client, bearer, headers, globalQuery

    client = requests.Session()
    user_key = env_vars.get("USER_KEY")
    user_seed = env_vars.get("USER_SEED")
    bearer = f"{user_key}:{user_seed}"
    headers = {
        "Authorization": f"Bearer {bearer}"
    }
    globalQuery = "/sparql/local" not in endpoint_url
    return endpoint_url

def check_response(logger, func_name, expected_status_code, actual_status_code, status_message, check_function, check_text=False, text=""):
    if actual_status_code != expected_status_code:
        logger.error(f"{func_name} failed: Expected status code {expected_status_code}, but got {actual_status_code} - {status_message}")
    elif check_text and text not in status_message:
        logger.error(f"{func_name} failed: Expected text '{text}' not found in status message.")
    else:
        check_function()

def check_json_field(logger, func_name, expected_field, actual_json):
    if expected_field not in actual_json:
        logger.error(f"{func_name} failed: Expected field {expected_field}, but it was not found in the response.")
    else:
        logger.info(f"{func_name} passed")

def check_text_contains(logger, func_name, expected_text, actual_text):
    if expected_text not in actual_text:
        logger.error(f"{func_name} failed: Expected text '{expected_text}' not found in the response.")
    else:
        logger.info(f"{func_name} passed")

def test_select_query_via_get(endpoint_url, logger):
    func_name = "test_select_query_via_get"
    query = "SELECT * WHERE { ?s ?p ?o } LIMIT 10"
    encoded_query = urllib.parse.quote(query)
    url = f"{endpoint_url}?query={encoded_query}"

    response = client.get(url, headers=headers)
    check_response(logger, func_name, 200, response.status_code, response.reason, lambda: check_json_field(logger, func_name, "results", response.json()))

def test_ask_query_via_get(endpoint_url, logger):
    func_name = "test_ask_query_via_get"
    query = "ASK WHERE { ?s ?p ?o }"
    encoded_query = urllib.parse.quote(query)
    url = f"{endpoint_url}?query={encoded_query}"

    response = client.get(url, headers=headers)
    check_response(logger, func_name, 200, response.status_code, response.reason, lambda: check_json_field(logger, func_name, "boolean", response.json()))

def test_describe_query_via_get(endpoint_url, logger):
    func_name = "test_describe_query_via_get"
    resource_uri = find_resource_uri(endpoint_url, logger)
    query = f"DESCRIBE <{resource_uri}>"
    encoded_query = urllib.parse.quote(query)
    url = f"{endpoint_url}?query={encoded_query}"
    headers_copy = headers.copy()
    headers_copy["Accept"] = "application/rdf+xml"

    response = client.get(url, headers=headers_copy)
    check_response(logger, func_name, 200, response.status_code, response.reason, lambda: check_text_contains(logger, func_name, "rdf:RDF", response.text))

def find_resource_uri(endpoint_url, logger):
    query = "SELECT * WHERE { ?s ?p ?o } LIMIT 1"
    encoded_query = urllib.parse.quote(query)
    url = f"{endpoint_url}?query={encoded_query}"

    response = client.get(url, headers=headers)
    if response.status_code == 200:
        response_body = response.json()
        return response_body["results"]["bindings"][0]["s"]["value"]
    else:
        logger.error(f"find_resource_uri failed: Status code {response.status_code} - {response.reason}")

def test_construct_query_via_get(endpoint_url, logger):
    func_name = "test_construct_query_via_get"
    query = "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o } LIMIT 10"
    encoded_query = urllib.parse.quote(query)
    url = f"{endpoint_url}?query={encoded_query}"
    headers_copy = headers.copy()
    headers_copy["Accept"] = "text/turtle"

    response = client.get(url, headers=headers_copy)
    check_response(logger, func_name, 200, response.status_code, response.reason, lambda: check_text_contains(logger, func_name, "<did:iotics:", response.text))

def test_service_description(endpoint_url, logger):
    func_name = "test_service_description"
    url = endpoint_url

    response = client.get(url, headers=headers)
    check_response(logger, func_name, 200, response.status_code, response.reason, lambda: check_json_field(logger, func_name, "@context", response.json()))

def test_query_via_post_form(endpoint_url, logger):
    func_name = "test_query_via_post_form"
    query = "SELECT * WHERE { ?s ?p ?o } LIMIT 10"
    encoded_query = urllib.parse.quote(query)
    url = endpoint_url
    headers_copy = headers.copy()
    headers_copy["Content-Type"] = "application/x-www-form-urlencoded"

    response = client.post(url, headers=headers_copy, data=encoded_query)
    check_response(logger, func_name, 200, response.status_code, response.reason, lambda: check_json_field(logger, func_name, "results", response.json()))

def test_query_via_post_sparql(endpoint_url, logger):
    func_name = "test_query_via_post_sparql"
    query = "SELECT * WHERE { ?s ?p ?o } LIMIT 10"
    url = endpoint_url
    headers_copy = headers.copy()
    headers_copy["Content-Type"] = "application/sparql-query"
    headers_copy["Accept"] = "*/*"

    response = client.post(url, headers=headers_copy, data=query)
    check_response(logger, func_name, 200, response.status_code, response.reason, lambda: check_json_field(logger, func_name, "results", response.json()))

def test_query_with_default_graph_uri(endpoint_url, logger):
    func_name = "test_query_with_default_graph_uri"
    query = "SELECT * WHERE { ?s ?p ?o } LIMIT 10"
    encoded_query = urllib.parse.quote(query)
    default_graph_uri = "http://example.org/default-graph"
    encoded_default_graph_uri = urllib.parse.quote(default_graph_uri)
    url = f"{endpoint_url}?query={encoded_query}&default-graph-uri={encoded_default_graph_uri}"

    response = client.get(url, headers=headers)
    check_response(logger, func_name, 400, response.status_code, response.reason, lambda: check_text_contains(logger, func_name, "RDF datasets not allowed", response.reason), check_text=True, text="RDF datasets not allowed")

def test_query_with_named_graph_uri(endpoint_url, logger):
    func_name = "test_query_with_named_graph_uri"
    query = "SELECT * WHERE { GRAPH <http://example.org/named-graph> { ?s ?p ?o } } LIMIT 10"
    encoded_query = urllib.parse.quote(query)
    url = f"{endpoint_url}?query={encoded_query}"

    response = client.get(url, headers=headers)
    check_response(logger, func_name, 200, response.status_code, response.reason, lambda: check_json_field(logger, func_name, "results", response.json()))

def run_with_timeout(func, logger, *args, **kwargs):
    try:
        with concurrent.futures.ThreadPoolExecutor() as executor:
            future = executor.submit(func, *args, logger=logger, **kwargs)
            future.result(timeout=5)  # Set the timeout to 5 seconds
    except concurrent.futures.TimeoutError:
        logger.error(f"{func.__name__} timed out")

def main():
    if len(sys.argv) < 2 or len(sys.argv) > 4:
        print("Usage: python <name_of_your_program>.py <endpoint_url> [<number_of_loops>] [<path_to_env_file>]")
        sys.exit(1)

    logger = setup_logging()

    endpoint_url = sys.argv[1]
    num_loops = int(sys.argv[2]) if len(sys.argv) >= 3 else 1
    env_file = sys.argv[3] if len(sys.argv) == 4 else ".env"
    env_vars = load_env(env_file)
    setUp(endpoint_url, env_vars)

    functions = [
        test_select_query_via_get,
        test_ask_query_via_get,
        test_describe_query_via_get,
        test_construct_query_via_get,
        test_service_description,
        test_query_via_post_form,
        test_query_via_post_sparql,
        test_query_with_default_graph_uri,
        test_query_with_named_graph_uri
    ]

    for _ in range(num_loops):
        with concurrent.futures.ThreadPoolExecutor() as executor:
            futures = [executor.submit(run_with_timeout, func, logger, endpoint_url) for func in functions]
            for future in concurrent.futures.as_completed(futures):
                try:
                    future.result()
                except Exception as e:
                    # Log only the function name and the timeout message, no stack trace
                    if isinstance(e, concurrent.futures.TimeoutError):
                        logger.error(f"Function timed out")
                    else:
                        logger.error(f"Error occurred: {e}")

if __name__ == "__main__":
    main()
