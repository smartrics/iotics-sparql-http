import requests
import urllib.parse

# Define the SPARQL query
sparql_query = """SELECT ?subject ?predicate ?object
WHERE {?subject ?predicate ?object}
LIMIT 10"""

# Encode the SPARQL query
encoded_query = urllib.parse.quote(sparql_query)

# Define the URL with the encoded query
url = f"http://localhost:8080/sparql/local?query={encoded_query}"

# Define the headers
headers = {
    "Accept": "application/sparql-results+json"
}

# Send the HTTP GET request
response = requests.get(url, headers=headers)

# Output the response
print(f"Status Code: {response.status_code}")
print("Headers:", response.headers)
print("Content:", response.text)