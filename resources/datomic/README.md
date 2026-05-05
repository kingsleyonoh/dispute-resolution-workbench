# Datomic Local And SQL Storage

Local development uses Datomic Local through `DATOMIC_URI=datomic:local://drw/dev`.
The application smoke path creates an in-memory Datomic Local client for fast setup
verification.

`sql-transactor.properties` configures Datomic Pro SQL storage against the local
Postgres 16 service. Start the transactor from a Datomic Pro distribution with
that properties file when testing peer/transactor SQL storage.
