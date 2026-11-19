# Example application

Two small libraries and an application that uses both:

- `example-inventory` declares the `inventory` migration (table `inventory_item`).
- `example-orders` declares the `orders` migration with `after=inventory` (table `order_line`,
  which references `inventory_item`).
- `example-app` depends on `example-orders` and has its own changelog (table `shipment`, which
  references `order_line`).

At startup migratory runs `inventory`, then `orders`, then Spring Boot runs the application's
changelog. Neither the application nor `example-orders` lists the other library's changelog.

Run the test (needs Docker for PostgreSQL):

```shell
./mvnw verify -pl migratory-examples/example-app -am
```
