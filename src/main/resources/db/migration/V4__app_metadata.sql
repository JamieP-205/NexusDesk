-- Small facts about the database itself. The demo profile writes mode=demo here, so a demo
-- database (with its public passwords) can't be started as a normal deployment by mistake,
-- and the demo seeder can't add public accounts to a real database.
CREATE TABLE app_metadata (
    name VARCHAR(40) PRIMARY KEY,
    setting VARCHAR(200) NOT NULL
);
