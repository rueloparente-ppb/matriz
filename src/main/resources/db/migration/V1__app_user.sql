CREATE TABLE app_user (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      varchar(50)  NOT NULL,
    display_name  varchar(100) NOT NULL,
    password_hash varchar(100) NOT NULL
);

CREATE UNIQUE INDEX app_user_username_lower_uq ON app_user (lower(username));
