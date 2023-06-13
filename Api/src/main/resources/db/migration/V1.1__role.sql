-- public.eve_assist_priv_id_seq definition
-- DROP SEQUENCE IF EXISTS eve_assist_priv_id_seq;
CREATE SEQUENCE IF NOT EXISTS eve_assist_priv_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

-- public.eve_assist_role_id_seq definition
-- DROP SEQUENCE IF EXISTS eve_assist_role_id_seq;
CREATE SEQUENCE IF NOT EXISTS eve_assist_role_id_seq
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9223372036854775807
    START 1
    CACHE 1
    NO CYCLE;

-- public.eve_assist_privilege definition
-- DROP TABLE IF EXISTS eve_assist_privilege;
CREATE TABLE IF NOT EXISTS eve_assist_privilege
(
    id        int8        NOT NULL,
    priv_name varchar(25) NOT NULL,
    PRIMARY KEY (id)
);

-- public.eve_assist_role definition
-- DROP TABLE IF EXISTS eve_assist_role;
CREATE TABLE IF NOT EXISTS eve_assist_role
(
    id        int8        NOT NULL,
    role_name varchar(25) NOT NULL,
    PRIMARY KEY (id)
);

-- public.roles_privileges definition
-- DROP TABLE IF EXISTS roles_privileges;
CREATE TABLE IF NOT EXISTS roles_privileges
(
    role_id      int8 NOT NULL,
    privilege_id int8 NOT NULL,
    CONSTRAINT roles_privileges_privilege_fk FOREIGN KEY (privilege_id) REFERENCES eve_assist_privilege,
    CONSTRAINT roles_privileges_role_fk FOREIGN KEY (role_id) REFERENCES eve_assist_role
);

-- public.users_roles definition
-- DROP TABLE IF EXISTS users_roles;
CREATE TABLE IF NOT EXISTS users_roles
(
    user_id int8 NOT NULL,
    role_id int8 NOT NULL,
    CONSTRAINT users_roles_role_fk FOREIGN KEY (role_id) REFERENCES eve_assist_role,
    CONSTRAINT users_roles_user_fk FOREIGN KEY (user_id) REFERENCES eve_assist_user
);

-- public.eve_assist_privilege definition
-- DROP TABLE IF EXISTS eve_assist_privilege;
CREATE TABLE IF NOT EXISTS eve_assist_privilege
(
    id        int8        NOT NULL,
    priv_name varchar(25) NOT NULL,
    PRIMARY KEY (id)
);

-- public.eve_assist_role definition
-- DROP TABLE IF EXISTS eve_assist_role;
CREATE TABLE IF NOT EXISTS eve_assist_role
(
    id        int8        NOT NULL,
    role_name varchar(25) NOT NULL,
    PRIMARY KEY (id)
);

-- public.roles_privileges definition
-- DROP TABLE IF EXISTS roles_privileges;
CREATE TABLE IF NOT EXISTS roles_privileges
(
    role_id      int8 NOT NULL,
    privilege_id int8 NOT NULL,
    CONSTRAINT roles_privileges_privilege_fk FOREIGN KEY (privilege_id) REFERENCES eve_assist_privilege,
    CONSTRAINT roles_privileges_role_fk FOREIGN KEY (role_id) REFERENCES eve_assist_role
);

-- public.users_roles definition
-- DROP TABLE IF EXISTS users_roles;
CREATE TABLE IF NOT EXISTS users_roles
(
    user_id int8 NOT NULL,
    role_id int8 NOT NULL,
    CONSTRAINT users_roles_role_fk FOREIGN KEY (role_id) REFERENCES eve_assist_role,
    CONSTRAINT users_roles_user_fk FOREIGN KEY (user_id) REFERENCES eve_assist_user
);

