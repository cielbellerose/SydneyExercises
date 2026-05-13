.PHONY: up down reset psql migrate baseline build load server clean

up:
	docker compose up -d

down:
	docker compose down

reset:
	docker compose down -v
	docker compose up -d
	@echo "Waiting for Postgres to accept connections..."
	@until docker exec realestate-postgres pg_isready -U realestate -d realestate >/dev/null 2>&1; do sleep 1; done
	$(MAKE) migrate

psql:
	docker exec -it realestate-postgres psql -U realestate -d realestate

migrate:
	cd REDataLoader && mvn -q flyway:migrate

baseline:
	cd REDataLoader && mvn -q flyway:baseline

build:
	cd REDataLoader && mvn -q package
	cd REServer && mvn -q package

load:
	cd REDataLoader && mvn -q compile exec:java -Dexec.mainClass=org.example.Main

server:
	cd REServer && mvn -q compile exec:java -Dexec.mainClass=app.REServer

clean:
	cd REDataLoader && mvn -q clean
	cd REServer && mvn -q clean
