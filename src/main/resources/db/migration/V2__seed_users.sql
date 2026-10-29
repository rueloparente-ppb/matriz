-- Development users; all use the password documented in the README (bcrypt, cost 10).
INSERT INTO app_user (username, display_name, password_hash) VALUES
    ('alice', 'Alice Anderson', '$2y$10$83/GcS08yNuZbFpI1jxHA.QU8/Uvs8MFcJzqbcfDmfx7CYDczplDq'),
    ('bob',   'Bob Brown',      '$2y$10$UFbfC5WuzxYJGkeF0aEPau9arNKmPZ9U0cWD7TO29jpv5/RvrnrVS'),
    ('carol', 'Carol Clark',    '$2y$10$g53qzko95KEDcrEKw0NOQ.wzfRtMviHs9XQpnNyJYAtKC.N64irpa');
