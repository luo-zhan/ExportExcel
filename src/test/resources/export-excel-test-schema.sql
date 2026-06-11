CREATE TABLE IF NOT EXISTS demo_user
(
    employee_no
    VARCHAR
(
    20
) PRIMARY KEY,
    name VARCHAR
(
    50
),
    age INT,
    active BOOLEAN,
    department VARCHAR
(
    50
),
    email VARCHAR
(
    100
),
    phone VARCHAR
(
    20
),
    address VARCHAR
(
    200
),
    position VARCHAR
(
    50
),
    bio VARCHAR
(
    500
),
    birth TIMESTAMP,
    income DECIMAL
(
    20,
    4
)
    );
