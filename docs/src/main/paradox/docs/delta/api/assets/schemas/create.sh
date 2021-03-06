curl -X POST \
     -H "Content-Type: application/json" \
     "http://localhost:8080/v1/schemas/myorg/myproj" \
     -d \
'{
  "@context": {
      "this": "http://localhost:8080/v1/schemas/myorg/myproj/e1729302-35b8-4d80-97b2-d63c984e2b5c/shapes",
      "ex": "http://localhost:9999/"
  },
  "@id": "http://localhost:8080/v1/resources/myorg/myproj/e1729302-35b8-4d80-97b2-d63c984e2b5c",
  "shapes": [
    {
      "@id": "this:MyShape",
      "@type": "sh:NodeShape",
      "nodeKind": "sh:BlankNodeOrIRI",
      "targetClass": "ex:Custom",
      "property": [
        {
          "path": "ex:name",
          "datatype": "xsd:string",
          "minCount": 1
        },
        {
          "path": "ex:number",
          "datatype": "xsd:integer",
          "minCount": 1
        },
        {
          "path": "ex:bool",
          "datatype": "xsd:boolean",
          "minCount": 1
        }
      ]
    }
  ]
}'