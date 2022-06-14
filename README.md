## Explore locally

Go to http://localhost:8081/graphiql

### Searches

Customers:

```
{
  customers {
    	id,name
  }
}
```

Customer by name:

```
{
  customersByName(name: "Nils") {
    	id,name,__typename
  }
}
```



## Use Postman

Send Post Request to http://localhost:8081/graphql with body:
```
{
"query": "{customers {id,name}}"
}
```

## Links

- https://www.youtube.com/watch?v=kVSYVhmvNCI
