curl -XPUT \
  -H "Content-Type: application/json" \
  "https://localhost:8080/v1/permissions?rev=1" -d \
  '{
      "permissions": [
        "newpermission/read",
        "newpermission/write"
      ]
    }'