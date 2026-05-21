.PHONY: up down reset psql migrate baseline build load clean \
        run-property run-purchasers run-analytics run-gateway run-notification

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
	mvn -q -f pom.xml package

load:
	cd REDataLoader && mvn -q compile exec:java -Dexec.mainClass=org.example.Main

run-property:
	java -jar property-server/target/property-server-1.0-SNAPSHOT-jar-with-dependencies.jar

run-purchasers:
	java -jar purchasers-server/target/purchasers-server-1.0-SNAPSHOT-jar-with-dependencies.jar

run-analytics:
	java -jar analytics-server/target/analytics-server-1.0-SNAPSHOT-jar-with-dependencies.jar

run-gateway:
	java -jar gateway/target/gateway-1.0-SNAPSHOT-jar-with-dependencies.jar

run-notification:
	java -jar notification-server/target/notification-server-1.0-SNAPSHOT-jar-with-dependencies.jar

clean:
	cd REDataLoader && mvn -q clean
	mvn -q -f pom.xml clean
