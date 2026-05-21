# Queue Event-Based Notification Architecture

## Overview

The RealEstateServer system uses RabbitMQ to deliver postcode-scoped notifications
to interested purchasers without coupling the `property-server` to any of the
downstream concerns. The `property-server` only knows how to emit events; a new
`notification-server` is responsible for turning those events into per-purchaser
messages and printing them.

Two domain events drive notifications:

1. **Property lifecycle** — A new property is put up for sale, a listing's price
   changes, or a property's `for_sale` status toggles. Every purchaser interested
   in that postcode is notified.
2. **Hot property** — Every time a for-sale property's view count is incremented,
   every interested purchaser is told the property is hot.

## Topology

```
┌─────────────────┐   publish    ┌──────────────────┐
│ property-server │ ───────────► │ property.events  │ (queue)
└─────────────────┘              └──────────────────┘
                                          │ consume
                                          ▼
                              ┌─────────────────────┐
                              │ notification-server │
                              │  ┌──────────────┐   │
                              │  │ EventConsumer├───┼── HTTP GET ──► analytics-server
                              │  └──────┬───────┘   │     /analytics/postcode/{pc}/purchasers
                              │         │ publish   │
                              │         ▼           │
                              │ purchaser.messages  │ (queue)
                              │         │ consume   │
                              │         ▼           │
                              │  ┌──────────────┐   │
                              │  │PrinterConsumer│──┼── stdout
                              │  └──────────────┘   │
                              └─────────────────────┘
```

- **Broker:** RabbitMQ 3 (management plugin), added to `docker-compose.yml`
  alongside Postgres. AMQP on `5672`, management UI on
  [http://localhost:15672](http://localhost:15672) (guest/guest).
- **Two queues, no exchange:** `property.events` and `purchaser.messages`,
  declared idempotently by both publisher and consumer so either side can come
  up first.
- **Both consumers run inside `notification-server`** on independent channels so
  a slow printer doesn't block event processing.

## Modules

| Module | Role |
|---|---|
| `property-server` | Emits domain events (publisher only — no consumption) |
| `analytics-server` | Resolves `postcode → purchasers` for the notification service |
| `notification-server` | NEW — consumes events, fans out per-purchaser messages, prints them |
| `gateway`, `purchasers-server` | Unchanged |

### `property-server` event sources

| Endpoint | DAO method | Event emitted |
|---|---|---|
| `POST /property` (`forSale=true`) | `PropertyDAO.newProperty` | `NEW_LISTING` |
| `POST /listing/{id}/price` | `ListingDAO.addPrice` | `PRICE_CHANGE` |
| `POST /property/{id}/forSale` *(new)* | `PropertyDAO.setForSale` | `STATUS_CHANGE` |
| `DELETE /listing/{id}` (withdraw) | `ListingDAO.withdrawListing` | `STATUS_CHANGE` (forSale=false) |
| `GET /property/{id}` (view increment) | `PropertyDAO.incrementViewCount` | `HOT_PROPERTY` *(only if `for_sale`)* |

The publisher is `messaging.EventPublisher`. If RabbitMQ is unreachable at startup
the publisher logs a warning and degrades to a no-op so HTTP behavior is
unaffected — the `property-server` always boots.

### `notification-server` components

| File | Role |
|---|---|
| `app.NotificationServerMain` | Boots RabbitMQ connection, declares queues, starts the two consumers |
| `notification.EventConsumer` | Consumes `property.events`, calls analytics-server, publishes per-purchaser messages |
| `notification.PrinterConsumer` | Consumes `purchaser.messages`, prints formatted text to stdout |
| `notification.Formatter` | Generates subject + body strings per event type |

### `analytics-server` extension

New endpoint `GET /analytics/postcode/{postcode}/purchasers` returns
`{ "postcode": "...", "purchasers": [ {purchaserId, email, name}, ... ] }`.
The existing count endpoint is unchanged.

## Message payloads

### `property.events` — produced by `property-server`

```jsonc
{
  "eventType": "NEW_LISTING | PRICE_CHANGE | STATUS_CHANGE | HOT_PROPERTY",
  "propertyId": "123",
  "postcode": "2000",
  "price":      850000,   // NEW_LISTING, PRICE_CHANGE
  "forSale":    true,     // NEW_LISTING, STATUS_CHANGE
  "viewCount":  42,       // HOT_PROPERTY
  "timestamp":  "2026-05-21T10:15:30Z"
}
```

### `purchaser.messages` — produced by `notification-server`

```jsonc
{
  "purchaserId":    7,
  "purchaserEmail": "alice@example.com",
  "purchaserName":  "Alice",
  "subject":        "Price change in postcode 2000",
  "body":           "Property 123 in 2000 has changed price to $800000.",
  "eventType":      "PRICE_CHANGE",
  "propertyId":     "123",
  "postcode":       "2000",
  "timestamp":      "2026-05-21T10:15:31Z"
}
```

## Running it

```bash
make up                # starts Postgres + RabbitMQ
make reset             # one-time: migrate schema (if needed)
make build             # builds all modules incl. notification-server
# In separate terminals:
make run-property
make run-purchasers
make run-analytics
make run-notification  # prints purchaser notifications here
make run-gateway
```

Configuration env vars (with defaults):

| Variable | Default | Used by |
|---|---|---|
| `RABBITMQ_HOST` | `localhost` | property-server, notification-server |
| `RABBITMQ_PORT` | `5672` | property-server, notification-server |
| `ANALYTICS_SERVICE_URL` | `http://localhost:7073` | notification-server |

## Notes & trade-offs

- **Two simple queues, no exchange.** Chosen for clarity — a topic exchange
  would be more idiomatic RabbitMQ, but the assignment's described pattern
  ("events queue + purchaser-messages queue") maps directly onto this topology.
- **`for_sale` is a boolean, not a status enum.** Pending/Sold transitions are
  not modeled in the schema; we treat listing withdrawal as a `forSale=false`
  status change, and the explicit `POST /property/{id}/forSale` endpoint
  toggles the boolean.
- **"Hot" fires on every view.** Simplest semantics; can be throttled later by
  changing `PropertyController.getPropertyByID` (e.g. emit only when
  `viewCount % N == 0`).
- **No persistence of sent notifications.** Messages are ephemeral — printed
  once and ACKed. Adding a `sent_notifications` table would be the next step
  if we wanted automated assertions in an integration test.

## Verification

Test plan and code-quality re-run (PMD / equivalent) to be added once the
end-to-end run has been demonstrated.
